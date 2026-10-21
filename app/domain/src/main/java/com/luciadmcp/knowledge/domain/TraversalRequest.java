package com.luciadmcp.knowledge.domain;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Query scope is supplied by a trusted, immutable-build store handle before seed lookup. */
public record TraversalRequest(String seedKey, Mode mode, Direction direction,
        Set<RelationshipType> types, TraversalLimits limits, String continuation) {
    public enum Mode { DIRECT, RECURSIVE }
    public enum Direction { OUT, IN, BOTH }
    public TraversalRequest(String seedKey) { this(seedKey, null, null, null, null, null); }
    public TraversalRequest {
        boundedText(seedKey);
        mode = mode == null ? Mode.DIRECT : mode;
        direction = direction == null ? Direction.OUT : direction;
        types = types == null ? Set.of() : Set.copyOf(types);
        limits = limits == null ? TraversalLimits.DEFAULT : limits;
        continuation = continuation == null ? "" : continuation;
        if (continuation.length() > 4096) throw new IllegalArgumentException("continuation exceeds 4 KiB");
    }
    public static void boundedText(String value) {
        if (value == null || value.isBlank() || value.length() > 2048
                || value.getBytes(StandardCharsets.UTF_8).length > 2048)
            throw new IllegalArgumentException("scope/key must be nonblank and at most 2 KiB");
    }
}
