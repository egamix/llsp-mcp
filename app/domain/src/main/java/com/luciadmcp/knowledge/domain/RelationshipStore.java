package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for the versioned typed-relationship graph store (Story 2.5, AD-1). Mirrors
 * {@link CatalogStore}: the {@code catalog.sqlite} module provides the sole implementation and no
 * other module reaches the storage engine except through this interface.
 *
 * <p>Pass two submits resolved logical {@link RelationshipEdge} tuples — a
 * {@code (sourceSymbol, targetSymbol, type, sdkVersion)} where both endpoints are version-scoped
 * canonical keys already present in the catalog. The store is the sole authority that computes each
 * edge's ID (AD-22) via {@link ContentHashes#relationshipHash(String, String, RelationshipType,
 * String, String)} after mapping each endpoint key to its stored {@code record_id}; callers never
 * compute an ID and never assume a hash recipe.
 *
 * <p><b>Version scoping (AD-12).</b> Every persisted edge carries its {@code sdkVersion} and connects
 * only same-version symbols. <b>Determinism (NFR-13).</b> Edge IDs are pure content hashes, so an
 * interrupted-then-resumed build persists byte-identical rows and IDs, and reads return a deterministic
 * order.
 *
 * <p>The SQLite edges schema/DDL remains a governance approval gate (Epic 3); the current
 * implementation is a clearly-labeled provisional schema (Story 2.5), mirroring the Story 2.4 catalog
 * precedent.
 */
public interface RelationshipStore extends AutoCloseable {

    /** Admission identity of a validated read-only serving handle; write handles have none. */
    default Optional<SymbolSourceBinding> servingBinding() { return Optional.empty(); }

    /** Bounded consumer traversal; supported only by a trusted immutable-build handle. */
    default TraversalResult traverse(TraversalRequest request) {
        throw new UnsupportedOperationException("bounded traversal requires a pinned build handle");
    }

    /**
     * Persists one logical edge, mapping each endpoint canonical key to its stored {@code record_id},
     * computing the edge ID from those IDs (AD-22), and deduplicating by it. The write is an idempotent
     * upsert: submitting the same logical edge twice leaves a single row ({@link PutStatus#DUPLICATE}).
     * A <em>different</em> logical edge that resolves to the same identity is an explicit clash
     * ({@link PutStatus#IDENTITY_CLASH}) — never a silent overwrite or drop (AD-4). Structural edges
     * pass {@code evidence=""}; the evidence dimension exists for Epic-3 example reuse (AD-30).
     *
     * @param edge the resolved logical edge tuple (both endpoints are catalog canonical keys)
     * @return the computed edge ID and the outcome ({@link PutStatus})
     */
    PutResult put(RelationshipEdge edge);

    /** The stored edge for a computed edge ID, or empty if none. */
    Optional<StoredRelationshipEdge> findByEdgeId(String edgeId);

    /** Every persisted edge, in a deterministic order (ascending edge ID). */
    List<StoredRelationshipEdge> allEdges();

    /** Audit reads above retain all versions; consumer reads bind an explicit SDK scope. */
    default List<StoredRelationshipEdge> edges(RelationshipScope scope) {
        return allEdges().stream().filter(e -> e.edge().sdkVersion().equals(scope.sdkVersion())).toList();
    }

    /** Persist occurrence evidence atomically; identical retries are no-ops, conflicts are errors. */
    default void putResolution(ReferenceResolution resolution) {
        throw new UnsupportedOperationException("reference evidence storage is required");
    }

    default List<ReferenceResolution> resolutions(RelationshipScope scope) {
        throw new UnsupportedOperationException("reference evidence storage is required");
    }

    /** Closes the underlying store. Overridden to declare no checked exception for try-with-resources. */
    @Override
    void close();

    /** The outcome of a {@link #put(RelationshipEdge)} call. */
    enum PutStatus {

        /** The edge was newly inserted. */
        INSERTED,

        /** An identical edge was already present; the upsert was a no-op (idempotent). */
        DUPLICATE,

        /**
         * A different logical edge already occupies this identity — a real identity clash. The
         * existing row is left untouched; the caller records an issue per the failure policy (AD-4).
         */
        IDENTITY_CLASH
    }

    /**
     * The result of a {@link #put(RelationshipEdge)}: the store-computed edge ID (AD-22) and the
     * outcome.
     *
     * @param edgeId the computed edge ID
     * @param status whether the edge was inserted, already present, or clashed
     */
    record PutResult(String edgeId, PutStatus status) {

        public PutResult {
            if (edgeId == null || edgeId.isBlank()) {
                throw new IllegalArgumentException("edgeId must not be blank");
            }
            if (status == null) {
                throw new IllegalArgumentException("status must not be null");
            }
        }
    }
}
