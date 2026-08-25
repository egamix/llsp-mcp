package com.luciadmcp.knowledge.cli.pipeline;

/**
 * The outcome of a single pipeline stage within one run, as reported in the ingestion report.
 *
 * <ul>
 *   <li>{@link #COMPLETED} — ran to completion this run and was checkpointed.</li>
 *   <li>{@link #SKIPPED_ON_RESUME} — already completed in a prior run; skipped on resume.</li>
 *   <li>{@link #FAILED} — ran this run but did not complete (fail-fast, or a build-invalidating
 *       failure); it is not checkpointed.</li>
 *   <li>{@link #NOT_RUN} — never reached because the run aborted at an earlier stage.</li>
 * </ul>
 */
public enum StageStatus {
    COMPLETED,
    SKIPPED_ON_RESUME,
    FAILED,
    NOT_RUN
}
