package com.luciadmcp.knowledge.cli;

/**
 * Seam for the ingestion pipeline. {@code cli ingest} validates its arguments and then hands an
 * {@link IngestArguments} to this seam.
 *
 * <p>The real multi-stage, resumable pipeline (and its {@code fail-fast | collect-and-continue}
 * policy) is <b>Story 1.3</b>; deterministic IDs and the machine-readable report are
 * <b>Story 1.4</b>. This story ships only the entrypoint and the argument contract, so the default
 * implementation is a no-op ({@link #noOp()}). Nothing here touches the network on any path (AD-9).
 *
 * <p>AD-17: {@code cli ingest} is the sole build coordinator — it (not any ingest module) will own
 * the single {@code buildId} and build-directory creation once Story 1.3/1.4 land. The seam keeps
 * that ownership in {@code cli}.
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
