package com.luciadmcp.knowledge.domain;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SymbolCoordinateTest {
    @Test void canonicalWireRoundTripsQualifiedParameters() {
        var c = new SymbolCoordinate("p", "1", "java", "a", "a.T", "m", List.of("x.Value[]", "y.Value"), "METHOD");
        assertEquals("symbol:1:cA.MQ.amF2YQ.YQ.YS5U.bQ.TUVUSE9E.Mg.eC5WYWx1ZVtd.eS5WYWx1ZQ", c.serialize());
        assertEquals(c, SymbolCoordinate.parse(c.serialize()));
        assertThrows(IllegalArgumentException.class, () -> SymbolCoordinate.parse(c.serialize().replace("symbol:1:", "symbol:2:")));
        assertThrows(IllegalArgumentException.class, () -> SymbolCoordinate.parse(c.serialize() + "."));
    }
}
