package com.luciadmcp.knowledge.catalog.sqlite;

import static org.junit.jupiter.api.Assertions.*;

import com.luciadmcp.knowledge.domain.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceResolutionStoreTest {
    private String seed(Path db, String version, String key) {
        try (var catalog = new SqliteCatalogStore(db)) {
            return catalog.put(new CatalogRecord(version, key, "TYPE", key, key, key + ".html", "")).recordId();
        }
    }

    private ReferenceResolution unresolved(String version, String source, String token, String locator) {
        return new ReferenceResolution(version, source, "Source.html", "", RelationshipType.SEE_ALSO,
                token, locator, ReferenceResolution.Status.UNRESOLVED, ReferenceResolution.Kind.LOCAL_LOCATOR,
                ReferenceResolution.Reason.MISSING_LOCATOR, List.of(), "");
    }

    @Test void reopenRetryConflictAndVersionIsolation(@TempDir Path dir) {
        Path db = dir.resolve("catalog.sqlite");
        String source = seed(db, "1", "Source"), target = seed(db, "1", "Target");
        String otherSource = seed(db, "2", "Source");
        var unresolved = unresolved("1", source, "Missing", "../Missing.html");
        var exact = new ReferenceResolution("1", source, "Source.html", "", RelationshipType.RETURN_TYPE,
                "Target", "Target.html", ReferenceResolution.Status.EXACT, ReferenceResolution.Kind.LOCAL_LOCATOR,
                ReferenceResolution.Reason.MATCHED, List.of(target), target);
        try (var store = new SqliteRelationshipStore(db)) {
            store.putResolution(unresolved);
            store.putResolution(exact);
            store.put(new RelationshipEdge("Source", "Target", RelationshipType.RETURN_TYPE, "1"));
            store.putResolution(unresolved("2", otherSource, "Missing", "Missing.html"));
        }
        try (var store = new SqliteRelationshipStore(db)) {
            var before = store.resolutions(new RelationshipScope("1"));
            store.putResolution(exact); store.putResolution(unresolved);
            assertEquals(before, store.resolutions(new RelationshipScope("1")));
            assertEquals(2, before.size());
            assertTrue(before.contains(exact)); assertTrue(before.contains(unresolved));
            assertEquals(1, store.resolutions(new RelationshipScope("2")).size());
            assertTrue(store.edges(new RelationshipScope("2")).isEmpty());
            assertEquals("DOCUMENTED_SDK_ONLY", exact.scope().boundary());
            var conflict = new ReferenceResolution("1", source, "Source.html", "", RelationshipType.RETURN_TYPE,
                    "Target", "Target.html", ReferenceResolution.Status.UNRESOLVED, ReferenceResolution.Kind.LOCAL_LOCATOR,
                    ReferenceResolution.Reason.MISSING_LOCATOR, List.of(), "");
            assertThrows(IllegalStateException.class, () -> store.putResolution(conflict));
            assertEquals(before, store.resolutions(new RelationshipScope("1")));
        }
    }

    @Test void ambiguousCandidatesSortDeduplicateAndRejectInvalidProvenance(@TempDir Path dir) {
        Path db = dir.resolve("catalog.sqlite");
        String source = seed(db, "1", "Source"), a = seed(db, "1", "A"), b = seed(db, "1", "B");
        String foreign = seed(db, "2", "A");
        var ambiguous = new ReferenceResolution("1", source, "Source.html", "", RelationshipType.SEE_ALSO,
                "Thing", "", ReferenceResolution.Status.AMBIGUOUS, ReferenceResolution.Kind.SIMPLE_NAME,
                ReferenceResolution.Reason.COMPETING_CANDIDATES, List.of(b, a, b), "");
        try (var store = new SqliteRelationshipStore(db)) {
            store.putResolution(ambiguous);
            assertEquals(List.of(a,b).stream().sorted().toList(), store.resolutions(ambiguous.scope()).get(0).candidateIds());
            var invalidCandidate = new ReferenceResolution("1", source, "Source.html", "", RelationshipType.RETURN_TYPE,
                    "A", "", ReferenceResolution.Status.EXACT, ReferenceResolution.Kind.SIMPLE_NAME,
                    ReferenceResolution.Reason.MATCHED, List.of(foreign), foreign);
            assertThrows(IllegalStateException.class, () -> store.putResolution(invalidCandidate));
            assertThrows(IllegalStateException.class, () -> store.putResolution(unresolved("1", "absent", "X", "")));
            var invalidPath = new ReferenceResolution("1", source, "Wrong.html", "", RelationshipType.SEE_ALSO,
                    "X", "", ReferenceResolution.Status.UNRESOLVED, ReferenceResolution.Kind.SIMPLE_NAME,
                    ReferenceResolution.Reason.MISSING_NAME, List.of(), "");
            assertThrows(IllegalStateException.class, () -> store.putResolution(invalidPath));
        }
    }
}
