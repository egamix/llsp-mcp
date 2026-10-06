package com.luciadmcp.knowledge.domain;

/**
 * A persisted relationship edge read back from {@link RelationshipStore}: the store-computed edge ID
 * (AD-22) paired with the logical {@link RelationshipEdge} that was submitted.
 *
 * <p>The {@code edgeId} is assigned solely by the store from the {@link ContentHashes#relationshipHash
 * relationshipHash} recipe; it is never supplied by pass two. On read, the store reconstructs the
 * logical edge's version-scoped canonical endpoint keys from the persisted {@code record_id}s, so the
 * same logical edge always round-trips to the same {@link RelationshipEdge} and the same ID (NFR-13
 * determinism).
 *
 * @param edgeId the deterministic content-hash ID the store assigned (AD-22)
 * @param edge   the logical edge tuple, endpoints as version-scoped canonical keys
 */
public record StoredRelationshipEdge(String edgeId, RelationshipEdge edge) {

    public StoredRelationshipEdge {
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edgeId must not be blank");
        }
        if (edge == null) {
            throw new IllegalArgumentException("edge must not be null");
        }
    }
}
