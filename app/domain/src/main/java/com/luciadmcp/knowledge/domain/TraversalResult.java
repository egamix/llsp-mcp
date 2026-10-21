package com.luciadmcp.knowledge.domain;

import java.util.List;

/** Exhaustion and counts concern this bounded query only, never corpus completeness. */
public record TraversalResult(String buildId, RelationshipScope scope, String seedKey,
        TraversalRequest query, Status status, List<Item> items, int visitedNodes,
        int scannedRows, int windowItems, Cutoff cutoff, CountRelation countRelation,
        String continuation) {
    public enum Status { OK, SEED_NOT_FOUND, INVALID_CONTINUATION, EXECUTION_LIMIT }
    public enum Cutoff { NONE, DEPTH, VISITED_NODES, ADJACENCY_ROWS, RESULT_ITEMS, RESPONSE_BYTES }
    public enum CountRelation { EXACT, LOWER_BOUND, UNKNOWN }
    /** A node key or a typed stored edge. Nodes precede their admitting edges. */
    public record Item(String nodeKey, StoredRelationshipEdge edge) {
        public Item {
            if ((nodeKey == null) == (edge == null)) throw new IllegalArgumentException("one item kind required");
        }
        public static Item node(String key) { return new Item(key, null); }
        public static Item edge(StoredRelationshipEdge edge) { return new Item(null, edge); }
    }
    public TraversalResult { items = List.copyOf(items); }
    public int returnedItems() { return items.size(); }
    public TraversalLimits appliedLimits() { return query.limits(); }
}
