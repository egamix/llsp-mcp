package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(EnumSet.allOf(PipelineStage.class), result.completedStages());
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

    /** AC 7 / NFR-13: an interrupted-then-resumed run reproduces a clean run's stage set and manifest. */
    @Test
    void restartReproducesCleanRunStagesAndManifest(@TempDir Path tmp) throws Exception {
        // Clean uninterrupted run over one input set.
        IngestArguments cleanArgs = PipelineTestSupport.argsIn(tmp.resolve("clean"));
        List<PipelineStage> cleanLog = new ArrayList<>();
        PipelineResult clean =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(cleanLog), FailurePolicy.FAIL_FAST)
                        .runToResult(cleanArgs);

        // Interrupted-then-resumed run over a different-but-structurally-identical input set.
        IngestArguments resumedArgs = PipelineTestSupport.argsIn(tmp.resolve("resumed"));
        List<PipelineStage> crashLog = new ArrayList<>();
        List<Stage> crashing = PipelineTestSupport.recordingStages(crashLog);
        PipelineTestSupport.injectCrash(crashing, PipelineStage.EMBEDDINGS, crashLog);
        IngestPipelineRunner interrupted = new IngestPipelineRunner(crashing, FailurePolicy.FAIL_FAST);
        assertThrows(RuntimeException.class, () -> interrupted.runToResult(resumedArgs));
        PipelineResult resumed =
                new IngestPipelineRunner(PipelineTestSupport.recordingStages(new ArrayList<>()), FailurePolicy.FAIL_FAST)
                        .runToResult(resumedArgs);

        // Same completed-stage set (path-independent: all eight, in fixed order).
        assertEquals(clean.completedStages(), resumed.completedStages());
        assertEquals(EnumSet.allOf(PipelineStage.class), resumed.completedStages());

        // Manifest identity is a deterministic function of the inputs: recomputing it for the same
        // inputs yields the same manifest the resumed run reports. The toolchain fingerprint is a
        // pure function of the stub component versions, so it too reproduces exactly.
        BuildContext expectedCtx = BuildContext.forInputs(resumedArgs);
        String expectedFingerprint = com.luciadmcp.knowledge.domain.ContentHashes.toolchainFingerprint(
                IngestPipelineRunner.STUB_PARSER_VERSION,
                IngestPipelineRunner.STUB_SCHEMA_VERSION,
                IngestPipelineRunner.STUB_NORMALIZER_VERSION,
                IngestPipelineRunner.STUB_RELATIONSHIP_RULES_VERSION,
                IngestPipelineRunner.STUB_EMBEDDING_CONFIG_ID);
        assertEquals(
                new com.luciadmcp.knowledge.domain.IndexManifest(
                        expectedCtx.buildId(), IngestPipelineRunner.STUB_SDK_VERSION, expectedFingerprint),
                resumed.manifest());
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
}
