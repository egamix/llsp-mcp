package com.luciadmcp.knowledge.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The validated inputs to a single {@code cli ingest} invocation: exactly the six local paths from
 * FR-1 and nothing else.
 *
 * <p>These are <b>one-shot invocation arguments</b>, not runtime configuration. They are
 * deliberately NOT placed in {@code application.yaml}: AD-16 makes {@code application.yaml} the sole
 * <i>serve-time</i> config, and per-run ingest inputs are not serve-time config. A later epic must
 * not migrate these into config.
 *
 * <p>The five offline paths ({@code javadoc}, {@code samples}, {@code guides}, {@code model},
 * {@code out}) are required. {@code trainings} is optional: it is the sole AD-28 network / non-Java
 * exception and is handled only from the separate ingest-time image (Epic 9), so an offline
 * serving-image rebuild omits it. See {@link TrainingIngestSeam}.
 *
 * <p>{@code sdkVersion} is an optional explicit build input threaded through {@code BuildContext}
 * (AD-12): the catalog is version-scoped. When it is absent the build does not abort — records are
 * scoped under a default version label (the version column and uniqueness still apply). It is a value,
 * not a filesystem path, so like {@code --failure-policy} it is not one of the FR-1 paths; it rides
 * here because it identifies the corpus being ingested, not a serve-time setting (AD-16).
 *
 * @param javadoc    local Javadoc directory (input, must exist)
 * @param samples    local samples/workflows directory (input, must exist)
 * @param guides     local guides directory (input, must exist)
 * @param model      local model directory (input, must exist)
 * @param out        local output directory (created if absent)
 * @param trainings  local training-materials directory, or empty when not ingesting trainings (AD-28)
 * @param sdkVersion the SDK version the ingested corpus belongs to, or empty when not supplied (AD-12)
 */
public record IngestArguments(
        Path javadoc,
        Path samples,
        Path guides,
        Path model,
        Path out,
        Optional<Path> trainings,
        Optional<String> sdkVersion) {

    public IngestArguments {
        if (javadoc == null || samples == null || guides == null || model == null || out == null) {
            throw new IllegalArgumentException("offline ingest paths must not be null");
        }
        trainings = trainings == null ? Optional.empty() : trainings;
        sdkVersion = sdkVersion == null ? Optional.empty() : sdkVersion;
    }
}
