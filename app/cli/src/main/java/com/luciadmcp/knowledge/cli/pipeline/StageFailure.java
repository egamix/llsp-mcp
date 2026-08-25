package com.luciadmcp.knowledge.cli.pipeline;

import java.nio.file.Path;
import java.util.Optional;

/**
 * One failure recorded by a stage: which stage raised it, how severe it is, the offending file when
 * one applies (e.g. a malformed optional example), and a human-readable message.
 *
 * <p>The {@code file} is present when the failure is attributable to a specific input file and empty
 * otherwise. This is the in-memory failure shape only; the machine-readable JSON report over these
 * failures is Story 1.4.
 *
 * @param stage    the stage that recorded the failure
 * @param severity whether the failure is recoverable or invalidates the build
 * @param file     the offending file, when the failure is tied to one
 * @param message  human-readable explanation
 */
public record StageFailure(
        PipelineStage stage,
        FailureSeverity severity,
        Optional<Path> file,
        String message) {

    public StageFailure {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        file = file == null ? Optional.empty() : file;
    }

    /** A failure tied to a specific offending file. */
    public static StageFailure ofFile(
            PipelineStage stage, FailureSeverity severity, Path file, String message) {
        return new StageFailure(stage, severity, Optional.ofNullable(file), message);
    }

    /** A failure with no single offending file. */
    public static StageFailure of(PipelineStage stage, FailureSeverity severity, String message) {
        return new StageFailure(stage, severity, Optional.empty(), message);
    }
}
