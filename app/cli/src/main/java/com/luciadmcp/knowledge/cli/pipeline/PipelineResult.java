package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.IndexManifest;
import java.util.List;
import java.util.Set;

/**
 * The in-memory outcome of one pipeline run, for callers and tests.
 *
 * <p>There is no machine-readable JSON report here — that is Story 1.4. This exposes only what the
 * orchestration story needs to prove its behavior: which stages completed, what failures were
 * collected, whether activation occurred, and the (stub) manifest identity used for the
 * restart-reproducibility check (NFR-13).
 *
 * @param buildContext       the build identity and staging directory for this run
 * @param manifest           the stub manifest identity ({@code buildId} + placeholder SDK version)
 * @param completedStages    the stages that completed, in fixed pipeline order
 * @param failures           every failure collected during the run
 * @param activationOccurred whether the {@link PipelineStage#ACTIVATION} stage ran to completion
 * @param aborted            whether the run stopped early (fail-fast, or a build-invalidating failure)
 */
public record PipelineResult(
        BuildContext buildContext,
        IndexManifest manifest,
        Set<PipelineStage> completedStages,
        List<StageFailure> failures,
        boolean activationOccurred,
        boolean aborted) {

    public PipelineResult {
        completedStages = Set.copyOf(completedStages);
        failures = List.copyOf(failures);
    }
}
