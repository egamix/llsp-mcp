package com.luciadmcp.knowledge.cli;

import com.luciadmcp.knowledge.cli.pipeline.FailurePolicy;

/**
 * The full parsed form of one {@code cli ingest} invocation: the six-path {@link IngestArguments}
 * plus the per-run {@link FailurePolicy}.
 *
 * <p>The policy is orchestration configuration, not an FR-1 input path, so it is deliberately kept
 * out of {@link IngestArguments} (which stays exactly the six paths) and out of
 * {@code application.yaml} (AD-16 keeps that serve-time only). It rides alongside the arguments here
 * and is handed to the pipeline factory by {@link IngestCommand}.
 *
 * @param arguments the validated six-path inputs
 * @param policy    the failure policy for this run (default {@link FailurePolicy#DEFAULT})
 */
public record IngestInvocation(IngestArguments arguments, FailurePolicy policy) {

    public IngestInvocation {
        if (arguments == null) {
            throw new IllegalArgumentException("arguments must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
    }
}
