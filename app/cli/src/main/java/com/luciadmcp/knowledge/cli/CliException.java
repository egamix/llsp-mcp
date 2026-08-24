package com.luciadmcp.knowledge.cli;

/**
 * Thrown when an invocation violates the CLI argument contract. Carries a {@link CliError} so the
 * failure is rendered as a structured, non-silent rejection with a non-zero exit code (AC 3).
 */
public final class CliException extends RuntimeException {

    private final transient CliError error;

    public CliException(CliError error) {
        super(error.render());
        this.error = error;
    }

    public CliError error() {
        return error;
    }
}
