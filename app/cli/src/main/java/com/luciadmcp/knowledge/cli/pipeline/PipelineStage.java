package com.luciadmcp.knowledge.cli.pipeline;

/**
 * The eight ingestion stages, in the fixed order they run.
 *
 * <p><b>Declaration order IS execution order.</b> The orchestrator runs the stages by iterating the
 * values of this enum, and the checkpoint records completed stages by name, so reordering or
 * renaming a constant changes the pipeline contract. The order is the one fixed in epics.md /
 * ARCHITECTURE-SPINE.md: catalog &rarr; relationships &rarr; examples/workflows &rarr; lexical index
 * &rarr; embeddings &rarr; vector index &rarr; validation &rarr; atomic activation.
 *
 * <p>This story only stubs the stages; the real per-stage work (parsing, storage, embedding,
 * indexing) is Epic 2 onward. The "discover inputs" and "pre-scan symbols" steps mentioned in the
 * examples work belong <i>inside</i> {@link #EXAMPLES_WORKFLOWS}; they are not extra top-level
 * stages — the eight-stage shape is fixed.
 *
 * <p>Lives in {@code cli} because {@code cli ingest} is the sole build coordinator (AD-17).
 */
public enum PipelineStage {

    /** Build the canonical symbol catalog from Javadoc. */
    CATALOG,
    /** Build the typed relationship graph over catalog symbols. */
    RELATIONSHIPS,
    /** Parse and link examples and workflow documents. */
    EXAMPLES_WORKFLOWS,
    /** Build the lexical (BM25) index. */
    LEXICAL_INDEX,
    /** Compute embeddings for indexed records. */
    EMBEDDINGS,
    /** Build the vector index from the embeddings. */
    VECTOR_INDEX,
    /** Validate the assembled build before it may be activated. */
    VALIDATION,
    /** Atomically activate the validated build. */
    ACTIVATION
}
