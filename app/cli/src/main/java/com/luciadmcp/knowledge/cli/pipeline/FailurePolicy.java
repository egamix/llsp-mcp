package com.luciadmcp.knowledge.cli.pipeline;

/**
 * How the pipeline reacts to a {@link FailureSeverity#RECOVERABLE} stage failure.
 *
 * <p>This governs recoverable failures only. A {@link FailureSeverity#BUILD_INVALIDATING} failure
 * always aborts the run and blocks activation regardless of the policy in force (AD-5) — the policy
 * cannot loosen that.
 *
 * <p>This is per-run orchestration configuration supplied at invocation time. It is deliberately NOT
 * one of the six FR-1 input paths and NOT serve-time config, so it is neither added to
 * {@code IngestArguments} nor placed in {@code application.yaml} (AD-16).
 */
public enum FailurePolicy {

    /** Abort the run on the first recoverable stage failure. */
    FAIL_FAST,

    /** Record each recoverable stage failure (with its file and stage) and keep running. This is the default:
     * real Javadoc trees always contain some recoverable gaps (members without descriptions, images). */
    COLLECT_AND_CONTINUE;

    /** The policy used when a run does not specify one. */
    public static final FailurePolicy DEFAULT = COLLECT_AND_CONTINUE;
}
