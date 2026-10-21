package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.IngestArguments;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavior of the orchestrator: resume, the two failure policies, build-invalidating aborts, and
 * restart reproducibility. Stages are injected stubs that log, fail, or crash — no real work runs.
 */
class IngestPipelineRunnerTest {

    /** AC 1: after a crash mid-run, a restart with the same inputs skips completed stages. */
    @Test
    void resumesFromLastCompletedStage(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);

        // First run crashes at LEXICAL_INDEX (index 3); CATALOG, RELATIONSHIPS, EXAMPLES_WORKFLOWS
        // complete and are checkpointed before the crash.
        List<PipelineStage> firstRun = new ArrayList<>();
        List<Stage> crashing = PipelineTestSupport.recordingStages(firstRun);
        PipelineTestSupport.injectCrash(crashing, PipelineStage.LEXICAL_INDEX, firstRun);
        IngestPipelineRunner first = new IngestPipelineRunner(crashing, FailurePolicy.FAIL_FAST);
        assertThrows(RuntimeException.class, () -> first.runToResult(args));

        // Restart with the same inputs and all-clean stages: only LEXICAL_INDEX..ACTIVATION run.
        List<PipelineStage> secondRun = new ArrayList<>();
        IngestPipelineRunner resumed =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(secondRun), FailurePolicy.FAIL_FAST);
        PipelineResult result = resumed.runToResult(args);

        assertEquals(
                List.of(
                        PipelineStage.LEXICAL_INDEX,
                        PipelineStage.EMBEDDINGS,
                        PipelineStage.VECTOR_INDEX,
                        PipelineStage.VALIDATION,
                        PipelineStage.ACTIVATION),
                secondRun,
                "resume must run only the stages not completed before the crash");
        assertEquals(EnumSet.allOf(PipelineStage.class), result.completedStages());
        assertTrue(result.activationOccurred());
        assertFalse(result.aborted());
    }

    /** AC 2 + AC 5: collect-and-continue records a recoverable failure (with file + stage) and finishes. */
    @Test
    void collectAndContinueRecordsRecoverableFailureAndCompletes(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);
        Path badExample = tmp.resolve("samples").resolve("Malformed.java");

        List<PipelineStage> log = new ArrayList<>();
        List<Stage> stages = PipelineTestSupport.recordingStages(log);
        StageFailure failure = StageFailure.ofFile(
                PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, badExample, "unparseable example");
        PipelineTestSupport.injectFailure(stages, PipelineStage.EXAMPLES_WORKFLOWS, failure);

        PipelineResult result = new IngestPipelineRunner(stages, FailurePolicy.COLLECT_AND_CONTINUE)
                .runToResult(args);

        assertFalse(result.aborted(), "collect-and-continue must not abort on a recoverable failure");
        assertTrue(result.activationOccurred(), "the run should reach ACTIVATION");
        assertEquals(
                EnumSet.complementOf(EnumSet.of(PipelineStage.EXAMPLES_WORKFLOWS)),
                result.completedStages(),
                "a stage that recorded a recoverable failure must not be checkpointed, so a resume re-runs it");
        assertEquals(1, result.failures().size());
        StageFailure recorded = result.failures().get(0);
        assertEquals(PipelineStage.EXAMPLES_WORKFLOWS, recorded.stage());
        assertEquals(FailureSeverity.RECOVERABLE, recorded.severity());
        assertEquals(Optional.of(badExample), recorded.file(), "failure must carry the offending file");
    }

    /** AC 3: fail-fast aborts on the first recoverable failure; later stages do not run. */
    @Test
    void failFastAbortsOnFirstRecoverableFailure(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);

        List<PipelineStage> log = new ArrayList<>();
        List<Stage> stages = PipelineTestSupport.recordingStages(log);
        PipelineTestSupport.injectFailure(
                stages,
                PipelineStage.EXAMPLES_WORKFLOWS,
                StageFailure.of(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, "bad example"));

        PipelineResult result = new IngestPipelineRunner(stages, FailurePolicy.FAIL_FAST).runToResult(args);

        assertTrue(result.aborted());
        assertFalse(result.activationOccurred());
        // EXAMPLES_WORKFLOWS ran (and failed) but was not marked complete; nothing after it ran.
        assertEquals(
                List.of(PipelineStage.CATALOG, PipelineStage.RELATIONSHIPS, PipelineStage.EXAMPLES_WORKFLOWS),
                log,
                "no stage after the first recoverable failure should execute");
        assertEquals(
                EnumSet.of(PipelineStage.CATALOG, PipelineStage.RELATIONSHIPS),
                result.completedStages(),
                "the failing stage must not be recorded complete");
    }

    /** AC 6: a build-invalidating failure aborts and blocks ACTIVATION even under collect-and-continue. */
    @Test
    void buildInvalidatingFailureBlocksActivationRegardlessOfPolicy(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);

        List<PipelineStage> log = new ArrayList<>();
        List<Stage> stages = PipelineTestSupport.recordingStages(log);
        PipelineTestSupport.injectFailure(
                stages,
                PipelineStage.VALIDATION,
                StageFailure.of(PipelineStage.VALIDATION, FailureSeverity.BUILD_INVALIDATING, "final validation failed"));

        PipelineResult result = new IngestPipelineRunner(stages, FailurePolicy.COLLECT_AND_CONTINUE)
                .runToResult(args);

        assertTrue(result.aborted());
        assertFalse(result.activationOccurred(), "activation must be blocked when the build is invalidated");
        assertFalse(log.contains(PipelineStage.ACTIVATION), "ACTIVATION must never run on an invalidated build");
        assertFalse(result.completedStages().contains(PipelineStage.VALIDATION),
                "the invalidating stage must not be recorded complete");
        assertFalse(result.completedStages().contains(PipelineStage.ACTIVATION));
        assertTrue(result.failures().stream()
                        .anyMatch(f -> f.severity() == FailureSeverity.BUILD_INVALIDATING),
                "the invalidating failure must be reported");
    }

    /** AC 6: a build-invalidating failure also aborts and blocks ACTIVATION under fail-fast. */
    @Test
    void buildInvalidatingFailureBlocksActivationUnderFailFast(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp);

        List<PipelineStage> log = new ArrayList<>();
        List<Stage> stages = PipelineTestSupport.recordingStages(log);
        PipelineTestSupport.injectFailure(
                stages,
                PipelineStage.VALIDATION,
                StageFailure.of(PipelineStage.VALIDATION, FailureSeverity.BUILD_INVALIDATING, "final validation failed"));

        PipelineResult result = new IngestPipelineRunner(stages, FailurePolicy.FAIL_FAST).runToResult(args);

        assertTrue(result.aborted());
        assertFalse(result.activationOccurred(), "activation must be blocked when the build is invalidated");
        assertFalse(log.contains(PipelineStage.ACTIVATION), "ACTIVATION must never run on an invalidated build");
        assertFalse(result.completedStages().contains(PipelineStage.VALIDATION),
                "the invalidating stage must not be recorded complete");
        assertTrue(result.failures().stream()
                        .anyMatch(f -> f.severity() == FailureSeverity.BUILD_INVALIDATING),
                "the invalidating failure must be reported");
    }

    /** AC 7 / NFR-13: an interrupted-then-resumed run reproduces a clean run's stage set and manifest. */
    @Test
    void restartReproducesCleanRunStagesAndManifest(@TempDir Path tmp) throws Exception {
        // Clean uninterrupted run over one input set.
        IngestArguments cleanArgs = PipelineTestSupport.argsIn(tmp.resolve("clean"));
        List<PipelineStage> cleanLog = new ArrayList<>();
        PipelineResult clean =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(cleanLog), FailurePolicy.FAIL_FAST)
                        .runToResult(cleanArgs);

        // Interrupted-then-resumed run over a different-but-structurally-identical input set. Two
        // independently executed builds necessarily get different buildIds (buildId digests every
        // path, including `out`, and each run needs its own `out`/staging dir to avoid colliding
        // with the other) — so this test cross-checks the path-independent parts of the outcome
        // directly against `clean`, and checks each run's own buildId for drift, rather than
        // asserting a buildId equality that the design doesn't support.
        IngestArguments resumedArgs = PipelineTestSupport.argsIn(tmp.resolve("resumed"));
        List<PipelineStage> crashLog = new ArrayList<>();
        List<Stage> crashing = PipelineTestSupport.recordingStages(crashLog);
        PipelineTestSupport.injectCrash(crashing, PipelineStage.EMBEDDINGS, crashLog);
        IngestPipelineRunner interrupted = new IngestPipelineRunner(crashing, FailurePolicy.FAIL_FAST);
        assertThrows(RuntimeException.class, () -> interrupted.runToResult(resumedArgs));
        PipelineResult resumed =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(new ArrayList<>()), FailurePolicy.FAIL_FAST)
                        .runToResult(resumedArgs);

        // Same completed-stage set, activation outcome, and abort outcome as the clean run.
        assertEquals(clean.completedStages(), resumed.completedStages());
        assertEquals(EnumSet.allOf(PipelineStage.class), resumed.completedStages());
        assertEquals(clean.activationOccurred(), resumed.activationOccurred());
        assertEquals(clean.aborted(), resumed.aborted());

        // Same manifest content wherever it's input-independent: sdkVersion and toolchainFingerprint
        // are pure functions of the stub component versions, so a clean run and an
        // interrupted-then-resumed run must report identical values for both.
        assertEquals(clean.manifest().sdkVersion(), resumed.manifest().sdkVersion());
        assertEquals(clean.manifest().toolchainFingerprint(), resumed.manifest().toolchainFingerprint());

        // Each run's own buildId must match a fresh recomputation from its own inputs — catches the
        // runner drifting from BuildContext.forInputs rather than reusing the value it started with.
        assertEquals(BuildContext.forInputs(cleanArgs).buildId(), clean.manifest().buildId());
        assertEquals(BuildContext.forInputs(resumedArgs).buildId(), resumed.manifest().buildId());
    }

    /** AC 4: a changed input set gets a new buildId and runs fresh rather than resuming prior work. */
    @Test
    void changedInputsProduceNewBuildIdAndFreshRun(@TempDir Path tmp) throws Exception {
        // Run A to completion under one input set; its checkpoint lands in A's staging dir.
        IngestArguments argsA = PipelineTestSupport.argsIn(tmp.resolve("a"));
        new IngestPipelineRunner(
                        PipelineTestSupport.recordingStages(new ArrayList<>()), FailurePolicy.FAIL_FAST)
                .runToResult(argsA);

        // Input set B differs in one path, so it hashes to a different buildId and staging dir.
        IngestArguments argsB = PipelineTestSupport.argsIn(tmp.resolve("b"));
        BuildContext ctxA = BuildContext.forInputs(argsA);
        BuildContext ctxB = BuildContext.forInputs(argsB);
        org.junit.jupiter.api.Assertions.assertNotEquals(ctxA.buildId(), ctxB.buildId(),
                "a changed input set must produce a different buildId");
        org.junit.jupiter.api.Assertions.assertNotEquals(ctxA.stagingDir(), ctxB.stagingDir());

        // B runs all eight stages fresh — it does not resume from A's completed work.
        List<PipelineStage> logB = new ArrayList<>();
        PipelineResult resultB =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(logB), FailurePolicy.FAIL_FAST)
                        .runToResult(argsB);
        assertEquals(List.of(PipelineStage.values()), logB, "changed inputs must run every stage fresh");
        assertEquals(EnumSet.allOf(PipelineStage.class), resultB.completedStages());
    }

    /** The manifest carries the explicit SDK version threaded through BuildContext, not the stub. */
    @Test
    void manifestCarriesExplicitSdkVersion(@TempDir Path tmp) throws Exception {
        IngestArguments args = PipelineTestSupport.argsIn(tmp, Optional.of("2024.5"));
        PipelineResult result =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(new ArrayList<>()), FailurePolicy.FAIL_FAST)
                        .runToResult(args);
        assertEquals("2024.5", result.manifest().sdkVersion(),
                "the manifest must carry the supplied SDK version, not the stub fallback");
        assertNotEquals(IngestPipelineRunner.STUB_SDK_VERSION, result.manifest().sdkVersion());
    }

    /** The runner rejects a stage list that is not exactly one stage per PipelineStage. */
    @Test
    void constructorRejectsWrongStageCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new IngestPipelineRunner(List.of(Stage.noOp()), FailurePolicy.FAIL_FAST));
    }

    /** The default policy is FAIL_FAST, and the production wiring builds its eight stubs cleanly. */
    @Test
    void defaultStagesFactoryUsesFailFastDefault(@TempDir Path tmp) throws Exception {
        assertSame(FailurePolicy.FAIL_FAST, FailurePolicy.DEFAULT);
        PipelineResult result = IngestPipelineRunner.withDefaultStages().runToResult(
                PipelineTestSupport.argsIn(tmp));
        assertEquals(EnumSet.allOf(PipelineStage.class), result.completedStages(),
                "the eight production no-op stages should all complete");
    }
    @Test
    void corruptDatabaseDoesNotMaskHandledInvalidatingFailureOrAbortReport(@TempDir Path tmp) throws Exception {
        var args = PipelineTestSupport.argsIn(tmp);
        var context = BuildContext.forInputs(args);
        java.nio.file.Files.createDirectories(context.catalogDbPath().getParent());
        java.nio.file.Files.writeString(context.catalogDbPath(), "not a SQLite database");
        var original = StageFailure.of(PipelineStage.CATALOG, FailureSeverity.BUILD_INVALIDATING,
                "catalog storage failed before report");
        var stages = PipelineTestSupport.recordingStages(new ArrayList<>());
        PipelineTestSupport.injectFailure(stages, PipelineStage.CATALOG, original);
        var result = new IngestPipelineRunner(stages, FailurePolicy.FAIL_FAST).runToResult(args);
        assertTrue(result.aborted());
        assertFalse(result.activationOccurred());
        assertEquals(List.of(original), result.failures());
        var report = java.nio.file.Files.readString(args.out().resolve(context.buildId()).resolve("ingestion-report.json"));
        assertTrue(report.contains("\"aborted\": true"), report);
        assertTrue(report.contains("catalog storage failed before report"), report);
        assertTrue(report.contains("\"relationshipResolution\": null"), report);
        assertTrue(report.contains("\"severity\": \"BUILD_INVALIDATING\""), report);
    }

    @Test
    void unreadableResolutionSummaryCannotReturnSuccessfulResult(@TempDir Path tmp) throws Exception {
        var args = PipelineTestSupport.argsIn(tmp);
        var context = BuildContext.forInputs(args);
        java.nio.file.Files.createDirectories(context.catalogDbPath().getParent());
        java.nio.file.Files.writeString(context.catalogDbPath(), "not a SQLite database");
        var runner = IngestPipelineRunner.withDefaultStages();
        assertThrows(IllegalStateException.class, () -> runner.runToResult(args));
        assertFalse(java.nio.file.Files.exists(args.out().resolve(context.buildId()).resolve("ingestion-report.json")));
    }

}
