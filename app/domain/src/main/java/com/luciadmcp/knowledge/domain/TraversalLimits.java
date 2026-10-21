package com.luciadmcp.knowledge.domain;

/** Provisional Story 2.7 work limits; visited includes the seed, items include nodes and edges. */
public record TraversalLimits(int depth, int visitedNodes, int adjacencyRows, int resultItems,
        int pageItems, long executionNanos, int sqlSteps, int responseBytes) {
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    public static final TraversalLimits DEFAULT = new TraversalLimits(3, 1_000, 5_000, 1_000,
            10, 1_000_000_000L, 1_000_000, MAX_RESPONSE_BYTES);
    public TraversalLimits {
        bound(depth, 8); bound(visitedNodes, 10_000); bound(adjacencyRows, 50_000);
        bound(resultItems, 10_000); bound(pageItems, 50); bound(executionNanos, 5_000_000_000L);
        bound(sqlSteps, 10_000_000); bound(responseBytes, MAX_RESPONSE_BYTES);
        if (responseBytes < 65_536) throw new IllegalArgumentException("responseBytes must allow metadata reserve (65536)");
    }
    private static void bound(long value, long cap) {
        if (value <= 0 || value > cap) throw new IllegalArgumentException("limit outside 1.." + cap);
    }
}
