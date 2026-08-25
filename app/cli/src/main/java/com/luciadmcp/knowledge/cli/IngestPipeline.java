package com.luciadmcp.knowledge.cli;

/**
 * Seam for the ingestion pipeline. {@code cli ingest} validates its arguments and then hands an
 * {@link IngestArguments} to this seam.
 *
 * <p>The real multi-stage, resumable pipeline and its {@code fail-fast | collect-and-continue}
 * policy now exist as {@code com.luciadmcp.knowledge.cli.pipeline.IngestPipelineRunner}
 * (<b>Story 1.3</b>). Its stages are still no-ops — real parsing, storage, embedding, and indexing
 * are Epic 2 onward. Deterministic content hashes and the machine-readable report remain
 * <b>Story 1.4</b>. Nothing here touches the network on any path (AD-9). The {@link #noOp()}
 * implementation is retained for tests that need neither staging nor stage execution.
 *
 * <p>AD-17: {@code cli ingest} is the sole build coordinator — it, not any ingest module, owns the
 * single {@code buildId} and staging-directory creation. The runner computes that {@code buildId}
 * once per invocation, so the seam keeps that ownership in {@code cli}.
 */
@FunctionalInterface
public interface IngestPipeline {

    /**
     * Runs ingestion for the given inputs.
     *
     * @param args validated ingest inputs
     */
    void run(IngestArguments args);

    /**
     * The Story-1.2 placeholder pipeline: does nothing. Replaced by the real pipeline in Story 1.3.
     */
    static IngestPipeline noOp() {
        return args -> {
            // Intentionally empty. Offline ingestion stages are Story 1.3.
            // The training-materials path (args.trainings()) is NOT processed here: its network /
            // non-Java handling is the sole AD-28 exception and lives only in the separate
            // ingest-time image (Epic 9) via TrainingIngestSeam. See that type.
        };
    }
}
