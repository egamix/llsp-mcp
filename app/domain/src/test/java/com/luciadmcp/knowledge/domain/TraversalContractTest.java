package com.luciadmcp.knowledge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TraversalContractTest {
    @Test void defaultsAndBoundaries() {
        var query = new TraversalRequest("seed");
        assertEquals(TraversalRequest.Mode.DIRECT, query.mode());
        assertEquals(TraversalRequest.Direction.OUT, query.direction());
        assertEquals(10, query.limits().pageItems());
        assertEquals(1, query.limits().depth());
        assertThrows(IllegalArgumentException.class, () -> new TraversalRequest("seed",null,null,null,null,"界".repeat(1500)));
        assertThrows(IllegalArgumentException.class, () -> new TraversalRequest("x".repeat(2049)));
        assertThrows(IllegalArgumentException.class, () -> new TraversalRequest("界".repeat(700)));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(0,1,1,1,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(9,1,1,1,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,10001,1,1,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,51,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,1,1,1,1048577));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,1,1,1,65535));
        assertEquals(65536,new TraversalLimits(1,1,1,1,1,1,1,65536).responseBytes());
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,0,1,1,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,0,1,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,50001,1,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,0,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,10001,1,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,0,1,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,1,0,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,1,5000000001L,1,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,1,1,0,65536));
        assertThrows(IllegalArgumentException.class, () -> new TraversalLimits(1,1,1,1,1,1,10000001,65536));
    }
}
