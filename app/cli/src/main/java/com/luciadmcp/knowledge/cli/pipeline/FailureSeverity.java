package com.luciadmcp.knowledge.cli.pipeline;

/**
 * Whether a stage failure may be tolerated under a lenient {@link FailurePolicy}, or must always
 * abort the build.
 */
public enum FailureSeverity {

    /**
     * A localized failure that a lenient policy may skip past — for example one malformed optional
     * example. Under {@link FailurePolicy#COLLECT_AND_CONTINUE} the run records it and continues;
     * under {@link FailurePolicy#FAIL_FAST} the run aborts on the first one.
     */
    RECOVERABLE,

    /**
     * A failure that compromises the integrity of the build — catalog integrity, index
     * compatibility, a required manifest, or final validation. It always aborts the run and blocks
     * activation, regardless of the {@link FailurePolicy} (AD-5).
     */
    BUILD_INVALIDATING
}
