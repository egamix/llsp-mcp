package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.cli.IngestArguments;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared fixtures for the pipeline tests: synthetic {@link IngestArguments} rooted under a temp
 * directory, and {@link Stage} lists that log which stages actually executed.
 *
 * <p>No real parsing happens anywhere here — the whole point of the story is that stages are stubs,
 * so failure and resume behavior is driven entirely by injected stages that log, throw, or record a
 * {@link StageFailure}. No path touches the network (AD-9); the {@code trainings}/AD-28 path is never
 * populated.
 */
final class PipelineTestSupport {

    private PipelineTestSupport() {
    }

    /** Six-path arguments under {@code base}: five existing input dirs plus an {@code out} dir. */
    static IngestArguments argsIn(Path base) throws IOException {
        Path javadoc = Files.createDirectories(base.resolve("javadoc"));
        Path samples = Files.createDirectories(base.resolve("samples"));
        Path guides = Files.createDirectories(base.resolve("guides"));
        Path model = Files.createDirectories(base.resolve("model"));
        Path out = base.resolve("out");
        return new IngestArguments(javadoc, samples, guides, model, out, Optional.empty());
    }

    /**
     * Eight stages (one per {@link PipelineStage}, in order) that each append their own stage to
     * {@code executionLog} when run. The returned list is mutable so a test can replace one entry
     * with a stage that fails or throws.
     */
    static List<Stage> recordingStages(List<PipelineStage> executionLog) {
        List<Stage> stages = new ArrayList<>();
        for (PipelineStage stage : PipelineStage.values()) {
            PipelineStage self = stage;
            stages.add((context, failures) -> executionLog.add(self));
        }
        return stages;
    }

    /**
     * Wraps the recording stage at {@code stage}'s position so it still logs, then records
     * {@code failure}. Preserves the log entry so tests can see the failing stage did run.
     */
    static void injectFailure(List<Stage> stages, PipelineStage stage, StageFailure failure) {
        Stage base = stages.get(stage.ordinal());
        stages.set(stage.ordinal(), (context, failures) -> {
            base.execute(context, failures);
            failures.accept(failure);
        });
    }

    /**
     * Replaces the stage at {@code stage}'s position with one that logs then throws, simulating a
     * process crash after the earlier stages have already been checkpointed.
     */
    static void injectCrash(List<Stage> stages, PipelineStage stage, List<PipelineStage> executionLog) {
        stages.set(stage.ordinal(), (context, failures) -> {
            executionLog.add(stage);
            throw new RuntimeException("simulated interruption at " + stage);
        });
    }
}
