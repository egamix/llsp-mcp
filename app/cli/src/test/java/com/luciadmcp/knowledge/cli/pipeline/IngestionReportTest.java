package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.IngestArguments;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The machine-readable ingestion report (AC 6, 7): every run — clean, fail-fast abort,
 * collect-and-continue, or build-invalidating abort — writes a deterministic JSON report to
 * {@code {out}/{buildId}/ingestion-report.json}, and two runs over identical inputs produce
 * byte-identical report content.
 */
class IngestionReportTest {

    /** A clean run writes a report: every stage COMPLETED, not aborted, activation occurred. */
    @Test
    void cleanRunWritesReportWithAllStagesCompleted(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        IngestPipelineRunner.withDefaultStages().runToResult(args);

        String json = readReport(args);
        assertWellFormedJson(json);
        assertTrue(json.contains("\"aborted\": false"), json);
        assertTrue(json.contains("\"activationOccurred\": true"), json);
        for (PipelineStage stage : PipelineStage.values()) {
            assertTrue(
                    json.contains("{\"stage\": \"" + stage.name() + "\", \"status\": \"COMPLETED\"}"),
                    () -> "clean run should mark " + stage + " COMPLETED\n" + json);
        }
        // Relationship resolution is structurally present and zero while stages are stubs.
        assertTrue(json.contains("\"relationshipResolution\""), json);
        assertTrue(json.contains("\"byType\": []"), json);
        assertTrue(json.contains("\"byResolutionKind\": []"), json);
    }

    /** Fail-fast on a recoverable failure writes a report: failing stage FAILED, tail NOT_RUN, aborted. */
    @Test
    void failFastRunWritesReportWithFailedStageAndNotRunTail(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        List<Stage> stages = PipelineTestSupport.recordingStages(new ArrayList<>());
        PipelineTestSupport.injectFailure(
                stages,
                PipelineStage.EXAMPLES_WORKFLOWS,
                StageFailure.of(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, "bad example"));
        new IngestPipelineRunner(stages, FailurePolicy.FAIL_FAST).runToResult(args);

        String json = readReport(args);
        assertWellFormedJson(json);
        assertTrue(json.contains("\"aborted\": true"), json);
        assertTrue(json.contains("\"activationOccurred\": false"), json);
        assertTrue(
                json.contains("{\"stage\": \"EXAMPLES_WORKFLOWS\", \"status\": \"FAILED\"}"), json);
        assertTrue(
                json.contains("{\"stage\": \"ACTIVATION\", \"status\": \"NOT_RUN\"}"),
                () -> "stages after the abort must be NOT_RUN\n" + json);
        // The recoverable failure appears with its stage, severity, null file, and message.
        assertTrue(
                json.contains("\"stage\": \"EXAMPLES_WORKFLOWS\", \"severity\": \"RECOVERABLE\", "
                        + "\"file\": null, \"message\": \"bad example\""),
                json);
    }

    /** Collect-and-continue writes a report: the recoverable failure is recorded with its file. */
    @Test
    void collectAndContinueWritesReportWithRecordedFileFailure(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        Path badExample = args.samples().resolve("Malformed.java");
        List<Stage> stages = PipelineTestSupport.recordingStages(new ArrayList<>());
        PipelineTestSupport.injectFailure(
                stages,
                PipelineStage.EXAMPLES_WORKFLOWS,
                StageFailure.ofFile(
                        PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, badExample, "unparseable"));
        new IngestPipelineRunner(stages, FailurePolicy.COLLECT_AND_CONTINUE).runToResult(args);

        String json = readReport(args);
        assertWellFormedJson(json);
        assertTrue(json.contains("\"aborted\": false"), json);
        assertTrue(json.contains("\"activationOccurred\": true"), json);
        // The offending file is present (JSON-escaped), so a backslash path survives serialization.
        String escaped = badExample.toString().replace("\\", "\\\\");
        assertTrue(json.contains("\"file\": \"" + escaped + "\""),
                () -> "failure should carry the escaped offending file\n" + json);
    }

    /** A build-invalidating failure aborts and still writes a report with ACTIVATION NOT_RUN. */
    @Test
    void buildInvalidatingRunWritesReportOnAbort(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        List<Stage> stages = PipelineTestSupport.recordingStages(new ArrayList<>());
        PipelineTestSupport.injectFailure(
                stages,
                PipelineStage.VALIDATION,
                StageFailure.of(PipelineStage.VALIDATION, FailureSeverity.BUILD_INVALIDATING, "invalid build"));
        new IngestPipelineRunner(stages, FailurePolicy.COLLECT_AND_CONTINUE).runToResult(args);

        String json = readReport(args);
        assertWellFormedJson(json);
        assertTrue(json.contains("\"aborted\": true"), json);
        assertTrue(json.contains("{\"stage\": \"VALIDATION\", \"status\": \"FAILED\"}"), json);
        assertTrue(json.contains("{\"stage\": \"ACTIVATION\", \"status\": \"NOT_RUN\"}"), json);
        assertTrue(json.contains("\"severity\": \"BUILD_INVALIDATING\""), json);
    }

    /** AC 7: two clean runs over identical inputs produce byte-identical report content. */
    @Test
    void identicalRunsProduceByteIdenticalReports(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);

        IngestPipelineRunner.withDefaultStages().runToResult(args);
        byte[] first = Files.readAllBytes(reportPath(args));

        // Wipe the build output (report + checkpoint) so the second run is fresh, not a resume.
        deleteRecursively(args.out());
        IngestPipelineRunner.withDefaultStages().runToResult(args);
        byte[] second = Files.readAllBytes(reportPath(args));

        assertEquals(new String(first, StandardCharsets.UTF_8), new String(second, StandardCharsets.UTF_8),
                "identical inputs must produce byte-identical report content");
    }

    private static Path reportPath(IngestArguments args) {
        String buildId = BuildContext.forInputs(args).buildId();
        return args.out().resolve(buildId).resolve(IngestPipelineRunner.REPORT_FILE);
    }

    private static String readReport(IngestArguments args) throws IOException {
        return Files.readString(reportPath(args), StandardCharsets.UTF_8);
    }

    /** Cheap structural check (no JSON dependency): balanced braces/brackets and even quote count. */
    private static void assertWellFormedJson(String json) {
        assertTrue(json.startsWith("{"), "report must be a JSON object");
        assertTrue(json.stripTrailing().endsWith("}"), "report must close the top-level object");
        int braces = 0;
        int brackets = 0;
        int quotes = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                    quotes++;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    quotes++;
                }
                case '{' -> braces++;
                case '}' -> braces--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                default -> {
                    // structural whitespace and scalars are irrelevant to the balance check
                }
            }
            assertTrue(braces >= 0 && brackets >= 0, "unbalanced closing delimiter in report");
        }
        assertEquals(0, braces, "unbalanced braces in report");
        assertEquals(0, brackets, "unbalanced brackets in report");
        assertEquals(0, quotes % 2, "unterminated string in report");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}
