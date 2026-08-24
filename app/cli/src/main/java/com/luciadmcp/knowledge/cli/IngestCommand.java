package com.luciadmcp.knowledge.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Executes the {@code ingest} subcommand: validate the six-path contract on disk, then hand the
 * inputs to the {@link IngestPipeline} seam.
 *
 * <p>Input directories must already exist; the output directory is created if absent. No network is
 * touched on any path (AD-9). The training-materials path, if supplied, is validated as a local
 * directory but its network/non-Java handling is not performed here — that is the AD-28 exception
 * owned by the separate ingest-time image (Epic 9); see {@link TrainingIngestSeam}.
 */
public final class IngestCommand {

    private final IngestPipeline pipeline;

    public IngestCommand(IngestPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** Uses the Story-1.2 no-op pipeline placeholder. */
    public IngestCommand() {
        this(IngestPipeline.noOp());
    }

    /**
     * @param ingestArgs tokens after the {@code ingest} subcommand
     * @throws CliException if the argument contract is violated
     */
    public void run(String[] ingestArgs) {
        IngestArguments args = IngestArgumentParser.parse(ingestArgs);

        requireExistingDirectory(IngestArgumentParser.OPT_JAVADOC, args.javadoc());
        requireExistingDirectory(IngestArgumentParser.OPT_SAMPLES, args.samples());
        requireExistingDirectory(IngestArgumentParser.OPT_GUIDES, args.guides());
        requireExistingDirectory(IngestArgumentParser.OPT_MODEL, args.model());
        args.trainings().ifPresent(t -> requireExistingDirectory(IngestArgumentParser.OPT_TRAININGS, t));

        ensureOutputDirectory(args.out());

        pipeline.run(args);
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
