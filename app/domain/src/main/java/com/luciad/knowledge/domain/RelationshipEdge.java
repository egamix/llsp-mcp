package com.luciad.knowledge.domain;

/**
 * A typed edge in the relationship graph between two canonical symbols.
 *
 * <p>Skeleton identity only. The type is drawn from the fixed {@link RelationshipType}
 * enum and the edge is scoped to an SDK version (AD-12). The canonical-symbol/relationship
 * serialization grammar is a deferred approval gate (Epic 3) — only the fields exist here,
 * not their wire format.
 *
 * @param sourceSymbol canonical symbol of the source element
 * @param targetSymbol canonical symbol of the target element
 * @param type         relationship type (fixed enum)
 * @param sdkVersion   SDK version this edge belongs to (AD-12)
 */
public record RelationshipEdge(
        String sourceSymbol,
        String targetSymbol,
        RelationshipType type,
        String sdkVersion) {
}
