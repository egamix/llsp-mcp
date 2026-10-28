package com.luciadmcp.knowledge.ingest.examples;

import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.ingest.examples.JavaExampleExtractor.Usage;
import com.luciadmcp.knowledge.ingest.examples.JavaExampleExtractor.UsageKind;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExampleLinkerTest {
    static final List<String> KEYS = List.of(
            "com.luciad.a.Point", "com.luciad.a.Point#move(double,double)", "com.luciad.a.Point#scale(double)", "com.luciad.a.Point#scale(double,double)",
            "com.luciad.a.Base", "com.luciad.a.Base#describe()",
            "com.luciad.a.Layer", "com.luciad.b.Layer", "com.luciad.a.Builder", "com.luciad.a.Builder.Nested");
    final ExampleLinker linker = new ExampleLinker(KEYS, Map.of("com.luciad.a.Point", List.of("com.luciad.a.Base")));

    @Test void resolvesByFullyQualifiedNameExplicitImportAndUniqueWildcard() {
        assertEquals(Optional.of("com.luciad.a.Point"), linker.resolveType("com.luciad.a.Point", "samples", List.of()));
        assertEquals(Optional.of("com.luciad.a.Point"), linker.resolveType("Point", "samples", List.of("com.luciad.a.Point")));
        assertEquals(Optional.of("com.luciad.a.Point"), linker.resolveType("Point", "samples", List.of("com.luciad.a.*")));
        assertEquals(Optional.of("com.luciad.a.Builder.Nested"), linker.resolveType("Builder.Nested", "samples", List.of("com.luciad.a.Builder")));
    }

    @Test void ambiguousOrShadowedNamesStayUnlinked() {
        assertEquals(Optional.empty(), linker.resolveType("Layer", "samples", List.of("com.luciad.a.*", "com.luciad.b.*")));
        // An explicit non-SDK import shadows any wildcard match.
        assertEquals(Optional.empty(), linker.resolveType("Point", "samples", List.of("java.awt.Point", "com.luciad.a.*")));
        assertEquals(Optional.empty(), linker.resolveType("String", "samples", List.of("com.luciad.a.*")));
    }

    @Test void callsLinkOnlyToASingleOverloadIncludingInheritedOnes() {
        var links = linker.link("samples", List.of("com.luciad.a.*"), List.of(
                new Usage(UsageKind.CONSTRUCT, "Point", "", 3),
                new Usage(UsageKind.CALL, "Point", "move", 4),
                new Usage(UsageKind.CALL, "Point", "scale", 5),
                new Usage(UsageKind.CALL, "Point", "describe", 6),
                new Usage(UsageKind.CALL, "Point", "missing", 7)));
        var found = links.stream().map(l -> l.type() + " " + l.targetKey()).toList();
        assertEquals(List.of("CONSTRUCTS com.luciad.a.Point", "USES_TYPE com.luciad.a.Point",
                "CALLS com.luciad.a.Point#move(double,double)", "CALLS com.luciad.a.Base#describe()"), found);
        assertEquals("L3 new Point", links.getFirst().evidence());
        assertTrue(links.stream().noneMatch(l -> l.type() == RelationshipType.CALLS && l.targetKey().contains("scale")), "two overloads: no guess");
    }
}
