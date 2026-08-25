package com.luciadmcp.knowledge.cli;

import com.luciadmcp.knowledge.cli.pipeline.FailurePolicy;
import com.luciadmcp.knowledge.cli.pipeline.IngestPipelineRunner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Executes the {@code ingest} subcommand: validate the six-path contract on disk, then hand the
 * inputs to the {@link IngestPipeline} seam.
 *
 * <p>Input directories must already exist; the output directory is created if absent. No network is
 * touched on any path (AD-9). The training-materials path, if supplied, is validated as a local
 * directory but its network/non-Java handling is not performed here — that is the AD-28 exception
 * owned by the separate ingest-time image (Epic 9); see {@link TrainingIngestSeam}.
 *
 * <p>The pipeline is built per run from a factory keyed on the parsed {@link FailurePolicy}, because
 * the policy is chosen at invocation time via {@code --failure-policy}. Production wires the real
 * {@link IngestPipelineRunner} with its eight no-op stages; tests can inject any factory (including
 * one that returns {@link IngestPipeline#noOp()}).
 */
public final class IngestCommand {

    private final Function<FailurePolicy, IngestPipeline> pipelineFactory;

    private IngestCommand(Function<FailurePolicy, IngestPipeline> pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    /**
     * Injects a fixed pipeline regardless of policy — for tests that do not exercise the policy or
     * staging (e.g. {@link IngestPipeline#noOp()}). The explicitly-typed lambda in the delegating
     * constructors keeps them distinct from this one during overload resolution.
     */
    public IngestCommand(IngestPipeline pipeline) {
        this((FailurePolicy policy) -> pipeline);
    }

    /** Production default: the real resumable pipeline with the eight no-op stages. */
    public IngestCommand() {
        this((FailurePolicy policy) -> IngestPipelineRunner.withDefaultStages(policy));
    }

    /**
     * Builds a command from a pipeline factory keyed on the parsed {@link FailurePolicy} — for tests
     * that need to observe the policy that reached the runner.
     *
     * @param pipelineFactory builds the pipeline for the failure policy parsed from the invocation
     */
    public static IngestCommand withPipelineFactory(
            Function<FailurePolicy, IngestPipeline> pipelineFactory) {
        return new IngestCommand(pipelineFactory);
    }

    /**
     * @param ingestArgs tokens after the {@code ingest} subcommand
     * @throws CliException if the argument contract is violated
     */
    public void run(String[] ingestArgs) {
        IngestInvocation invocation = IngestArgumentParser.parse(ingestArgs);
        IngestArguments args = invocation.arguments();

        requireExistingDirectory(IngestArgumentParser.OPT_JAVADOC, args.javadoc());
        requireExistingDirectory(IngestArgumentParser.OPT_SAMPLES, args.samples());
        requireExistingDirectory(IngestArgumentParser.OPT_GUIDES, args.guides());
        requireExistingDirectory(IngestArgumentParser.OPT_MODEL, args.model());
        args.trainings().ifPresent(t -> requireExistingDirectory(IngestArgumentParser.OPT_TRAININGS, t));

        ensureOutputDirectory(args.out());

        pipelineFactory.apply(invocation.policy()).run(args);
    }

    private static void requireExistingDirectory(String option, Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new CliException(CliError.of(
                    CliErrorCode.NOT_A_DIRECTORY,
                    "value for " + option + " must be an existing directory", dir.toString()));
        }
    }

    private static void ensureOutputDirectory(Path out) {
        if (Files.exists(out) && !Files.isDirectory(out)) {
            throw new CliException(CliError.of(
                    CliErrorCode.NOT_A_DIRECTORY,
                    "value for " + IngestArgumentParser.OPT_OUT + " exists but is not a directory",
                    out.toString()));
        }
        try {
            Files.createDirectories(out);
        } catch (IOException e) {
            throw new UncheckedIOException("could not create output directory: " + out, e);
        }
    }
}
