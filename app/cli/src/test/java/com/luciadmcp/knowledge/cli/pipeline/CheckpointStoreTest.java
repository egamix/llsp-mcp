package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The resume checkpoint: it round-trips completed stages under the build's staging dir, and it
 * refuses to reuse a checkpoint written for a different sealed input digest (AC 4).
 */
class CheckpointStoreTest {

    private static BuildContext contextAt(Path stagingDir, String digest) {
        return new BuildContext(
                "b-" + digest.substring(0, Math.min(8, digest.length())), digest, stagingDir, "2024.0");
    }

    /** AC 1: marked stages persist and are reloaded by a fresh store over the same context. */
    @Test
    void completedStagesRoundTripThroughDisk(@TempDir Path tmp) {
        BuildContext ctx = contextAt(tmp.resolve("staging"), "digest-round-trip");

        CheckpointStore writer = new CheckpointStore(ctx);
        writer.markCompleted(PipelineStage.CATALOG);
        writer.markCompleted(PipelineStage.RELATIONSHIPS);

        CheckpointStore reader = new CheckpointStore(ctx);
        assertTrue(reader.isCompleted(PipelineStage.CATALOG));
        assertTrue(reader.isCompleted(PipelineStage.RELATIONSHIPS));
        assertFalse(reader.isCompleted(PipelineStage.EMBEDDINGS));
        assertEquals(
                EnumSet.of(PipelineStage.CATALOG, PipelineStage.RELATIONSHIPS),
                reader.completedStages());
    }

    /** The checkpoint lands only under the staging dir (AD-25), never elsewhere. */
    @Test
    void checkpointFileLivesUnderStagingDir(@TempDir Path tmp) {
        Path staging = tmp.resolve("staging");
        BuildContext ctx = contextAt(staging, "digest-location");
        new CheckpointStore(ctx).markCompleted(PipelineStage.CATALOG);
        assertTrue(Files.isRegularFile(staging.resolve(CheckpointStore.CHECKPOINT_FILE)),
                "checkpoint must be written inside the coordinator-owned staging dir");
    }

    /** AC 4: a checkpoint whose recorded digest differs from the current build is not reused. */
    @Test
    void checkpointFromDifferentDigestIsNotReused(@TempDir Path tmp) {
        Path staging = tmp.resolve("staging");

        // Write a checkpoint under one digest, marking a stage complete.
        BuildContext original = contextAt(staging, "digest-original");
        new CheckpointStore(original).markCompleted(PipelineStage.CATALOG);

        // A build with a different digest but pointed at the same staging dir must ignore it. (In
        // practice a changed digest already yields a different buildId and staging dir; this is the
        // defensive guard.)
        BuildContext changed = contextAt(staging, "digest-changed");
        CheckpointStore store = new CheckpointStore(changed);
        assertFalse(store.isCompleted(PipelineStage.CATALOG),
                "a checkpoint from a different sealed input digest must not be reused");
        assertTrue(store.completedStages().isEmpty());
    }

    /** An unreadable checkpoint (unknown stage name) is treated as absent, but not silently. */
    @Test
    void unreadableCheckpointStartsFreshAndLogsAWarning(@TempDir Path tmp) throws Exception {
        Path staging = tmp.resolve("staging");
        Files.createDirectories(staging);
        Files.writeString(
                staging.resolve(CheckpointStore.CHECKPOINT_FILE),
                "digest=digest-corrupt\nstage=NOT_A_REAL_STAGE\n",
                StandardCharsets.UTF_8);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        CheckpointStore store;
        try (PrintStream captured = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            System.setErr(captured);
            store = new CheckpointStore(contextAt(staging, "digest-corrupt"));
        } finally {
            System.setErr(originalErr);
        }

        assertTrue(store.completedStages().isEmpty(), "an unreadable checkpoint must start fresh");
        String logged = errBuf.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("checkpoint"), () -> "expected a warning about the checkpoint, got: " + logged);
    }

    /** Stages are written in fixed pipeline order regardless of the order they were marked. */
    @Test
    void checkpointStagesAreWrittenInPipelineOrder(@TempDir Path tmp) throws Exception {
        Path staging = tmp.resolve("staging");
        BuildContext ctx = contextAt(staging, "digest-order");
        CheckpointStore store = new CheckpointStore(ctx);
        // Mark out of order.
        store.markCompleted(PipelineStage.EMBEDDINGS);
        store.markCompleted(PipelineStage.CATALOG);

        List<String> lines = Files.readAllLines(
                staging.resolve(CheckpointStore.CHECKPOINT_FILE), StandardCharsets.UTF_8);
        assertEquals("digest=digest-order", lines.get(0));
        assertEquals("stage=CATALOG", lines.get(1), "CATALOG precedes EMBEDDINGS in pipeline order");
        assertEquals("stage=EMBEDDINGS", lines.get(2));
    }
}
