package com.luciadmcp.knowledge.cli.pipeline;

/**
 * The outcome of a single pipeline stage within one run, as reported in the ingestion report.
 *
 * <ul>
 *   <li>{@link #COMPLETED} — ran to completion this run and was checkpointed.</li>
 *   <li>{@link #SKIPPED_ON_RESUME} — already completed in a prior run; skipped on resume.</li>
 *   <li>{@link #FAILED} — ran this run but was not checkpointed: a fail-fast or build-invalidating
 *       abort, or a recoverable failure under collect-and-continue (the run continues past it, but
 *       the stage itself must be re-run on a future resume rather than silently skipped).</li>
 *   <li>{@link #NOT_RUN} — never reached because the run aborted at an earlier stage.</li>
 * </ul>
 */
enum StageStatus {
    COMPLETED,
    SKIPPED_ON_RESUME,
    FAILED,
    NOT_RUN
}
