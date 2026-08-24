package com.luciadmcp.knowledge.cli;

import java.util.Optional;

/**
 * One structured error shape for CLI failures: a stable {@code code}, a human-readable
 * {@code message}, and the offending argument when one applies.
 *
 * <p>Mirrors the "one structured error shape" convention the served MCP tools use
 * (ARCHITECTURE-SPINE.md#Consistency-Conventions) so that error handling stays uniform across the
 * appliance. Rendered to stderr by {@link CliMain}; never swallowed (AC 3).
 *
 * @param code         stable machine-readable failure code
 * @param message      human-readable explanation
 * @param offendingArg the argument/value that triggered the failure, when applicable
 */
public record CliError(CliErrorCode code, String message, Optional<String> offendingArg) {

    public CliError {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        offendingArg = offendingArg == null ? Optional.empty() : offendingArg;
    }

    /** Error tied to a specific offending argument or value. */
    public static CliError of(CliErrorCode code, String message, String offendingArg) {
        return new CliError(code, message, Optional.ofNullable(offendingArg));
    }

    /** Error with no single offending argument (e.g. a missing required option). */
    public static CliError of(CliErrorCode code, String message) {
        return new CliError(code, message, Optional.empty());
    }

    /** Single-line rendering used for stderr output, e.g. {@code error [UNKNOWN_OPTION]: ...}. */
    public String render() {
        StringBuilder sb = new StringBuilder("error [").append(code).append("]: ").append(message);
        offendingArg.ifPresent(arg -> sb.append(" (argument: '").append(arg).append("')"));
        return sb.toString();
    }
}
