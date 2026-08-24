package com.luciadmcp.knowledge.cli;

import java.nio.file.Path;

/**
 * Marked boundary for training-material ingestion — the sole AD-28 exception.
 *
 * <p><b>AD-28:</b> {@code ingest.trainings} is the <i>only</i> path permitted network access and
 * non-Java tooling execution (docling), and only when run from the <b>separate ingest-time image</b>
 * built in Epic 8/9 — never from the serving image and never from this Epic 1 code. Accordingly this
 * seam is <b>deliberately unimplemented here</b>: Story 1.2 accepts the {@code --trainings} argument
 * and names where its handling will land, but adds no docling, no Python invocation, no HTTP client,
 * and no network code on any path (AD-9).
 *
 * <p>Story 9.x (running inside the ingest-time image) provides the real implementation. Any offline
 * serving-image rebuild simply omits {@code --trainings}, so this seam is never reached there.
 */
public final class TrainingIngestSeam {

    private TrainingIngestSeam() {
    }

    /**
     * Placeholder for training-material ingestion. Not implemented in Epic 1.
     *
     * @param trainingsDir local training-materials directory
     * @throws UnsupportedOperationException always, until Epic 9 supplies the implementation inside
     *                                        the separate ingest-time image
     */
    public static void ingest(Path trainingsDir) {
        throw new UnsupportedOperationException(
                "Training-material ingestion (AD-28) is implemented only in the separate "
                        + "ingest-time image (Epic 9), not in the Epic 1 CLI.");
    }
}
