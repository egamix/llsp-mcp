package com.luciadmcp.knowledge.cli;

import com.luciadmcp.knowledge.cli.pipeline.FailurePolicy;
import com.luciadmcp.knowledge.cli.pipeline.IngestPipelineRunner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
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
        args.trainings().ifPresent(t -> {
            requireExistingDirectory(IngestArgumentParser.OPT_TRAININGS, t);
            TrainingIngestSeam.ingest(t);
        });

        requireOutputNotNestedInInputs(args);
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

    /**
     * Rejects {@code --out} the same as or nested within an input directory: the pipeline's own
     * report/checkpoint writes would then land inside an input root, and the content-hashing that
     * {@code BuildContext} folds into the sealed digest would pick them up on the next run, breaking
     * the buildId-determinism guarantee.
     *
     * <p>Real paths are compared, not just normalized ones: each input directory is required to exist
     * by this point, so resolving it is safe, and this also catches a symlink/junction whose real
     * target sits inside an input root even though the literal {@code --out} text does not.
     */
    private static void requireOutputNotNestedInInputs(IngestArguments args) {
        Path out = realOrAbsoluteNormalized(args.out());
        requireNotAncestor(IngestArgumentParser.OPT_JAVADOC, args.javadoc(), out);
        requireNotAncestor(IngestArgumentParser.OPT_SAMPLES, args.samples(), out);
        requireNotAncestor(IngestArgumentParser.OPT_GUIDES, args.guides(), out);
        requireNotAncestor(IngestArgumentParser.OPT_MODEL, args.model(), out);
        args.trainings().ifPresent(t -> requireNotAncestor(IngestArgumentParser.OPT_TRAININGS, t, out));
    }

    private static void requireNotAncestor(String inputOption, Path input, Path out) {
        // The input directory's existence was already validated, so resolving its real path is safe.
        Path realInput;
        try {
            realInput = input.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("could not resolve real path for " + inputOption + ": " + input, e);
        }
        if (out.startsWith(realInput)) {
            throw new CliException(CliError.of(
                    CliErrorCode.OUTPUT_NESTED_IN_INPUT,
                    "value for " + IngestArgumentParser.OPT_OUT + " must not be the same as or nested within "
                            + inputOption,
                    out.toString()));
        }
    }

    private static final int MAX_SYMLINK_HOPS = 32;

    /**
     * Resolves {@code --out} to its real path even when the leaf itself does not exist yet (or is a
     * symlink/junction whose target does not exist yet), by walking up to the nearest ancestor that
     * does exist — following any symlinks encountered along the way — and re-appending the
     * not-yet-created suffix literally. {@link Path#toRealPath()} alone cannot do this: it requires
     * the final target to exist, which a not-yet-created {@code --out} or a freshly created symlink
     * to a not-yet-created target never does.
     */
    private static Path realOrAbsoluteNormalized(Path out) {
        Path current = out.toAbsolutePath().normalize();
        Deque<Path> suffix = new ArrayDeque<>();

        for (int hops = 0; hops <= MAX_SYMLINK_HOPS; hops++) {
            if (Files.isSymbolicLink(current)) {
                Path target;
                try {
                    target = Files.readSymbolicLink(current);
                } catch (IOException e) {
                    throw new UncheckedIOException("could not read symlink: " + current, e);
                }
                current = target.isAbsolute()
                        ? target.normalize()
                        : current.getParent().resolve(target).normalize();
                continue;
            }
            if (Files.exists(current)) {
                Path real;
                try {
                    real = current.toRealPath();
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "could not resolve real path for " + IngestArgumentParser.OPT_OUT + ": " + out, e);
                }
                for (Path part : suffix) {
                    real = real.resolve(part);
                }
                return real;
            }
            Path parent = current.getParent();
            if (parent == null) {
                return out.toAbsolutePath().normalize();
            }
            Path name = current.getFileName();
            if (name != null) {
                suffix.addFirst(name);
            }
            current = parent;
        }
        // A symlink chain this deep is not a real configuration worth resolving further; fall back to
        // the literal path rather than looping indefinitely (also guards against a symlink cycle).
        return out.toAbsolutePath().normalize();
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
