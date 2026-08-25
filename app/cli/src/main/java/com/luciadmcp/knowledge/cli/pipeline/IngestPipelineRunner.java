package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.cli.IngestPipeline;
import com.luciadmcp.knowledge.domain.IndexManifest;
import java.util.ArrayList;
import java.util.List;

/**
 * The multi-stage, resumable ingestion pipeline with a configurable failure policy.
 *
 * <p>This is the orchestration skeleton (Story 1.3). It owns the run loop, checkpoint/resume, and
 * the failure-policy decision; it does not do real stage work — the {@link Stage}s it runs are
 * no-ops in production and Epics 2+ fill them in. Deterministic content hashes and the JSON report
 * are Story 1.4.
 *
 * <p>AD-17: the coordinator computes the single {@link BuildContext} once per invocation and passes
 * it to every stage. AD-25: resume state lives only in the coordinator-owned staging directory. AD-9:
 * no path here — including resume — performs a network request.
 *
 * <h2>Failure handling</h2>
 * <ul>
 *   <li>A {@link FailureSeverity#RECOVERABLE} failure is governed by the {@link FailurePolicy}:
 *       under {@link FailurePolicy#FAIL_FAST} the run aborts on the first one; under
 *       {@link FailurePolicy#COLLECT_AND_CONTINUE} it is recorded and the run continues.</li>
 *   <li>A {@link FailureSeverity#BUILD_INVALIDATING} failure always aborts the run and blocks
 *       activation, regardless of policy (AD-5). The {@link PipelineStage#ACTIVATION} stage does not
 *       run and no manifest is activated.</li>
 * </ul>
 */
public final class IngestPipelineRunner implements IngestPipeline {

    /**
     * Placeholder SDK version for the stub manifest. The real value is read from the Javadoc during
     * catalog extraction (Epic 2, AD-12); it is unknown while the stages are stubs.
     */
    static final String STUB_SDK_VERSION = "0.0.0-stub";

    private final List<Stage> stages;
    private final FailurePolicy policy;

    /**
     * @param stages exactly one stage per {@link PipelineStage}, in {@link PipelineStage} order
     * @param policy the failure policy governing recoverable failures
     */
    public IngestPipelineRunner(List<Stage> stages, FailurePolicy policy) {
        PipelineStage[] all = PipelineStage.values();
        if (stages == null || stages.size() != all.length) {
            throw new IllegalArgumentException(
                    "expected exactly " + all.length + " stages, one per PipelineStage");
        }
        if (stages.contains(null)) {
            throw new IllegalArgumentException("stages must not contain null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.stages = List.copyOf(stages);
        this.policy = policy;
    }

    /** Wires the eight production no-op stages with the given policy. */
    public static IngestPipelineRunner withDefaultStages(FailurePolicy policy) {
        PipelineStage[] all = PipelineStage.values();
        List<Stage> noOps = new ArrayList<>(all.length);
        for (int i = 0; i < all.length; i++) {
            noOps.add(Stage.noOp());
        }
        return new IngestPipelineRunner(noOps, policy);
    }

    /** Wires the eight production no-op stages with the default policy ({@link FailurePolicy#DEFAULT}). */
    public static IngestPipelineRunner withDefaultStages() {
        return withDefaultStages(FailurePolicy.DEFAULT);
    }

    @Override
    public void run(IngestArguments args) {
        runToResult(args);
    }

    /**
     * Runs the pipeline and returns its in-memory outcome. Completed stages are skipped on resume;
     * each newly completed stage is checkpointed before the next begins.
     *
     * @param args the validated six-path inputs
     * @return the outcome: completed stages, collected failures, and whether activation occurred
     */
    public PipelineResult runToResult(IngestArguments args) {
        BuildContext context = BuildContext.forInputs(args);
        CheckpointStore checkpoint = new CheckpointStore(context);
        IndexManifest manifest = new IndexManifest(context.buildId(), STUB_SDK_VERSION);

        List<StageFailure> collected = new ArrayList<>();
        PipelineStage[] all = PipelineStage.values();

        for (int i = 0; i < all.length; i++) {
            PipelineStage stage = all[i];
            if (checkpoint.isCompleted(stage)) {
                continue; // resume: already done in a prior run
            }

            List<StageFailure> stageFailures = new ArrayList<>();
            stages.get(i).execute(context, stageFailures::add);
            collected.addAll(stageFailures);

            if (containsSeverity(stageFailures, FailureSeverity.BUILD_INVALIDATING)) {
                // Always abort and block activation, regardless of policy (AD-5). The stage is not
                // marked complete and ACTIVATION never runs.
                return aborted(context, manifest, checkpoint, collected);
            }

            if (policy == FailurePolicy.FAIL_FAST
                    && containsSeverity(stageFailures, FailureSeverity.RECOVERABLE)) {
                // Fail-fast: abort on the first recoverable failure. Stage not marked complete.
                return aborted(context, manifest, checkpoint, collected);
            }

            // Clean, or recoverable under collect-and-continue: the stage is done, so record it and
            // move on. Recording lets a resumed run skip it.
            checkpoint.markCompleted(stage);
        }

        boolean activationOccurred = checkpoint.isCompleted(PipelineStage.ACTIVATION);
        return new PipelineResult(
                context, manifest, checkpoint.completedStages(), collected, activationOccurred, false);
    }

    private static PipelineResult aborted(
            BuildContext context,
            IndexManifest manifest,
            CheckpointStore checkpoint,
            List<StageFailure> collected) {
        return new PipelineResult(
                context, manifest, checkpoint.completedStages(), collected, false, true);
    }

    private static boolean containsSeverity(List<StageFailure> failures, FailureSeverity severity) {
        return failures.stream().anyMatch(f -> f.severity() == severity);
    }
}
