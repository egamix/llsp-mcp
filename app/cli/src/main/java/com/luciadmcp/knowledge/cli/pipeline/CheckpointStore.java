package com.luciadmcp.knowledge.cli.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the resume checkpoint for one build, inside its coordinator-owned staging
 * directory and nowhere else.
 *
 * <p>AD-25: checkpoint state belongs only to the in-progress build's staging area and must be
 * invisible to serve, so this writes strictly under {@link BuildContext#stagingDir()}. AD-9: reads
 * and writes touch local disk only; no path here performs a network request.
 *
 * <p>Format is minimal line-based text — a {@code digest=} header naming the sealed input digest,
 * then one {@code stage=NAME} line per completed {@link PipelineStage}, written in fixed pipeline
 * order so the file is byte-identical for a given completed-stage set (this backs NFR-13). A JSON
 * dependency is deliberately avoided; the machine-readable JSON report is Story 1.4.
 *
 * <p>On load, a checkpoint whose digest does not match the current build's
 * {@link BuildContext#sealedInputDigest()} is treated as absent (a fresh run). In practice a changed
 * input set already produces a different {@code buildId} and therefore a different staging directory,
 * so this is a defensive check rather than the primary mechanism.
 */
public final class CheckpointStore {

    static final String CHECKPOINT_FILE = "checkpoint.txt";
    private static final String DIGEST_PREFIX = "digest=";
    private static final String STAGE_PREFIX = "stage=";

    private final BuildContext context;
    private final Path checkpointFile;
    private final EnumSet<PipelineStage> completed = EnumSet.noneOf(PipelineStage.class);

    /**
     * Opens the checkpoint for the given build, loading any prior completed-stage set whose digest
     * matches this build.
     *
     * @param context the coordinator-owned build context
     */
    public CheckpointStore(BuildContext context) {
        this.context = context;
        this.checkpointFile = context.stagingDir().resolve(CHECKPOINT_FILE);
        load();
    }

    /** The stages recorded complete for this build so far, in fixed pipeline order. */
    public Set<PipelineStage> completedStages() {
        return Collections.unmodifiableSet(EnumSet.copyOf(orderedCompleted()));
    }

    /** Whether the given stage has already completed and may be skipped on resume. */
    public boolean isCompleted(PipelineStage stage) {
        return completed.contains(stage);
    }

    /**
     * Records a stage as completed and rewrites the checkpoint so on-disk state stays consistent.
     * Creates the staging directory if it does not yet exist.
     *
     * @param stage the stage that just completed successfully
     */
    public void markCompleted(PipelineStage stage) {
        completed.add(stage);
        persist();
    }

    private void load() {
        if (!Files.isRegularFile(checkpointFile)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(checkpointFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read checkpoint: " + checkpointFile, e);
        }

        String recordedDigest = null;
        EnumSet<PipelineStage> loaded = EnumSet.noneOf(PipelineStage.class);
        for (String line : lines) {
            if (line.startsWith(DIGEST_PREFIX)) {
                recordedDigest = line.substring(DIGEST_PREFIX.length()).trim();
            } else if (line.startsWith(STAGE_PREFIX)) {
                String name = line.substring(STAGE_PREFIX.length()).trim();
                if (!name.isEmpty()) {
                    loaded.add(PipelineStage.valueOf(name));
                }
            }
        }

        // A checkpoint from a different input set is not reused.
        if (context.sealedInputDigest().equals(recordedDigest)) {
            completed.addAll(loaded);
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        lines.add(DIGEST_PREFIX + context.sealedInputDigest());
        for (PipelineStage stage : orderedCompleted()) {
            lines.add(STAGE_PREFIX + stage.name());
        }
        try {
            Files.createDirectories(context.stagingDir());
            Files.write(checkpointFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write checkpoint: " + checkpointFile, e);
        }
    }

    /** Completed stages in fixed pipeline order (stable output regardless of insertion order). */
    private EnumSet<PipelineStage> orderedCompleted() {
        return completed.isEmpty() ? EnumSet.noneOf(PipelineStage.class) : EnumSet.copyOf(completed);
    }
}
