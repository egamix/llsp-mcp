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

    /** Canonical bounded wire representation for this provisional contract. */
    public String toJson() {
        StringBuilder json = new StringBuilder("{\"buildId\":").append(quote(buildId))
                .append(",\"sdkVersion\":").append(quote(scope.sdkVersion()))
                .append(",\"boundary\":").append(quote(scope.boundary()))
                .append(",\"seedKey\":").append(quote(seedKey))
                .append(",\"mode\":").append(quote(query.mode().name()))
                .append(",\"direction\":").append(quote(query.direction().name()))
                .append(",\"types\":[");
        boolean comma = false;
        for (String type : query.types().stream().map(Enum::name).sorted().toList()) {
            if (comma) json.append(','); json.append(quote(type)); comma = true;
        }
        TraversalLimits l = appliedLimits();
        json.append("],\"limits\":{\"depth\":").append(l.depth())
                .append(",\"visitedNodes\":").append(l.visitedNodes())
                .append(",\"adjacencyRows\":").append(l.adjacencyRows())
                .append(",\"resultItems\":").append(l.resultItems())
                .append(",\"pageItems\":").append(l.pageItems())
                .append(",\"executionNanos\":").append(l.executionNanos())
                .append(",\"sqlSteps\":").append(l.sqlSteps())
                .append(",\"responseBytes\":").append(l.responseBytes())
                .append("},\"status\":").append(quote(status.name()))
                .append(",\"visitedNodes\":").append(visitedNodes)
                .append(",\"scannedRows\":").append(scannedRows)
                .append(",\"windowItems\":").append(windowItems)
                .append(",\"returnedItems\":").append(returnedItems())
                .append(",\"cutoff\":").append(quote(cutoff.name()))
                .append(",\"countRelation\":").append(quote(countRelation.name()))
                .append(",\"continuation\":").append(quote(continuation)).append(",\"items\":[");
        comma = false;
        for (Item item : items) {
            if (comma) json.append(','); comma = true;
            if (item.nodeKey() != null) json.append("{\"nodeKey\":").append(quote(item.nodeKey())).append('}');
            else {
                RelationshipEdge edge = item.edge().edge();
                json.append("{\"edgeId\":").append(quote(item.edge().edgeId()))
                        .append(",\"sourceKey\":").append(quote(edge.sourceSymbol()))
                        .append(",\"targetKey\":").append(quote(edge.targetSymbol()))
                        .append(",\"type\":").append(quote(edge.type().name()))
                        .append(",\"sdkVersion\":").append(quote(edge.sdkVersion())).append('}');
            }
        }
        return json.append("]}").toString();
    }
    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Escaping every non-ASCII UTF-16 unit also handles unpaired surrogates deterministically.
            if (c < 32 || c > 126) {
                out.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) out.append("0123456789abcdef".charAt((c >> shift) & 15));
            }
            else { if (c == '"' || c == '\\') out.append('\\'); out.append(c); }
        }
        return out.append('"').toString();
    }
}
