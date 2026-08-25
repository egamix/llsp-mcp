package com.luciadmcp.knowledge.cli.pipeline;

import java.util.function.Consumer;

/**
 * One unit of pipeline work, corresponding to a single {@link PipelineStage}.
 *
 * <p>This is the seam Epics 2+ fill in. In this story the production stages are no-ops: they parse,
 * store, embed, and index nothing. A stage reports problems by handing {@link StageFailure}s to the
 * supplied collector; the orchestrator, not the stage, decides whether a failure aborts the run
 * (see {@link IngestPipelineRunner}). A stage that returns without recording a failure is treated as
 * having succeeded.
 *
 * <p>Tests drive failure scenarios by injecting stages that record specific failures — real parsing
 * is never required to exercise the policy and resume logic.
 */
@FunctionalInterface
public interface Stage {

    /**
     * Executes this stage.
     *
     * @param context  the coordinator-owned build context (AD-17)
     * @param failures sink for any {@link StageFailure} this stage raises
     */
    void execute(BuildContext context, Consumer<StageFailure> failures);

    /** A production stub that does nothing. Real stage work is Epic 2 onward. */
    static Stage noOp() {
        return (context, failures) -> {
            // Intentionally empty: no parsing, storage, embedding, or indexing in this story.
        };
    }
}
