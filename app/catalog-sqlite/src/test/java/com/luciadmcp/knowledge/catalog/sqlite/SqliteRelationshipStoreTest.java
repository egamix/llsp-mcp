package com.luciadmcp.knowledge.catalog.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.domain.CatalogRecord;
import com.luciadmcp.knowledge.domain.ContentHashes;
import com.luciadmcp.knowledge.domain.RelationshipEdge;
import com.luciadmcp.knowledge.domain.RelationshipStore;
import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.domain.StoredRelationshipEdge;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The versioned SQLite relationship store (Story 2.5): it maps endpoint canonical keys to their stored
 * {@code record_id}s, computes {@code edge_id} via {@link ContentHashes#relationshipHash} as the sole
 * edge-ID authority (AD-22), upserts idempotently, reproduces byte-identical rows/ids on resume
 * (NFR-13), returns a deterministic order, and refuses an endpoint with no catalog record. Fixtures are
 * synthetic; no proprietary content.
 */
class SqliteRelationshipStoreTest {

    private static final String V = "2024.0";

    /** Seeds the shared catalog file with the type records the edges connect. */
    private static void seedRecords(Path db, String... fqns) {
        try (SqliteCatalogStore catalog = new SqliteCatalogStore(db)) {
            for (String fqn : fqns) {
                catalog.put(new CatalogRecord(V, fqn, "TYPE", fqn, simpleOf(fqn), pathOf(fqn), ""));
            }
        }
    }

    private static String simpleOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String pathOf(String fqn) {
        return fqn.replace('.', '/') + ".html";
    }

    private static Path db(Path dir) {
        return dir.resolve("catalog.sqlite");
    }

    private static RelationshipEdge extendsClass(String sub, String sup) {
        return new RelationshipEdge(sub, sup, RelationshipType.EXTENDS_CLASS, V);
    }

    /** AD-22: edge_id is exactly relationshipHash over the resolved endpoint record ids — no other recipe. */
    @Test
    void edgeIdIsTheRelationshipContentHashOverResolvedRecordIds(@TempDir Path tmp) {
        Path db = db(tmp);
        seedRecords(db, "com.example.Widget", "com.example.Base");

        String sourceId;
        String targetId;
        try (SqliteCatalogStore catalog = new SqliteCatalogStore(db)) {
            sourceId = catalog.findByKey(V, "com.example.Widget").orElseThrow().recordId();
            targetId = catalog.findByKey(V, "com.example.Base").orElseThrow().recordId();
        }
        String expected =
                ContentHashes.relationshipHash(V, sourceId, RelationshipType.EXTENDS_CLASS, targetId, "");

        try (RelationshipStore store = new SqliteRelationshipStore(db)) {
            RelationshipStore.PutResult result = store.put(extendsClass("com.example.Widget", "com.example.Base"));
            assertEquals(RelationshipStore.PutStatus.INSERTED, result.status());
            assertEquals(expected, result.edgeId(), "edge id must be the relationshipHash of the record ids");
        }
    }

    /** Matrix row "Duplicate edge within a build": identical logical edge upserts to one row. */
    @Test
    void duplicateSubmissionIsIdempotent(@TempDir Path tmp) {
        Path db = db(tmp);
        seedRecords(db, "com.example.Widget", "com.example.Base");
        try (RelationshipStore store = new SqliteRelationshipStore(db)) {
            RelationshipStore.PutResult first = store.put(extendsClass("com.example.Widget", "com.example.Base"));
            RelationshipStore.PutResult second = store.put(extendsClass("com.example.Widget", "com.example.Base"));

            assertEquals(RelationshipStore.PutStatus.INSERTED, first.status());
            assertEquals(RelationshipStore.PutStatus.DUPLICATE, second.status(),
                    "re-submitting the same logical edge is a no-op");
            assertEquals(first.edgeId(), second.edgeId(), "the same logical edge yields the same id");
            assertEquals(1, store.allEdges().size(), "an idempotent upsert leaves exactly one row");
        }
    }

    /** The submitted logical edge round-trips (endpoints reconstructed as canonical keys). */
    @Test
    void edgeRoundTripsAsCanonicalKeys(@TempDir Path tmp) {
        Path db = db(tmp);
        seedRecords(db, "com.example.Widget", "com.example.Base");
        RelationshipEdge edge = extendsClass("com.example.Widget", "com.example.Base");
        try (RelationshipStore store = new SqliteRelationshipStore(db)) {
            String edgeId = store.put(edge).edgeId();
            StoredRelationshipEdge back = store.findByEdgeId(edgeId).orElseThrow();
            assertEquals(edge, back.edge(), "the logical edge must round-trip unchanged");
            assertEquals(List.of(back), store.allEdges());
        }
    }

    /** Matrix row "Interrupted then resumed": re-running over the same file reproduces rows/ids. */
    @Test
    void resumeOverSameFileReproducesEdgesAndIds(@TempDir Path tmp) {
        Path db = db(tmp);
        seedRecords(db, "com.example.Widget", "com.example.Base", "com.example.Drawable");

        List<StoredRelationshipEdge> firstRun;
        try (RelationshipStore store = new SqliteRelationshipStore(db)) {
            store.put(extendsClass("com.example.Widget", "com.example.Base"));
            store.put(new RelationshipEdge(
                    "com.example.Widget", "com.example.Drawable", RelationshipType.IMPLEMENTS, V));
            firstRun = store.allEdges();
        }

        // A resumed run re-submits the same edges: idempotent, byte-identical rows and ids.
        try (RelationshipStore store = new SqliteRelationshipStore(db)) {
            assertEquals(RelationshipStore.PutStatus.DUPLICATE,
                    store.put(extendsClass("com.example.Widget", "com.example.Base")).status());
            assertEquals(RelationshipStore.PutStatus.DUPLICATE,
                    store.put(new RelationshipEdge(
                            "com.example.Widget", "com.example.Drawable", RelationshipType.IMPLEMENTS, V)).status());
            assertEquals(firstRun, store.allEdges(), "a resumed run must reproduce identical edges and ids");
            assertEquals(2, store.allEdges().size());
        }
    }

    /** NFR-13: allEdges is deterministically ordered (ascending id), independent of insertion order. */
    @Test
    void allEdgesIsDeterministicallyOrdered(@TempDir Path tmp) {
        seedRecords(db(tmp.resolve("f")), "com.example.A", "com.example.B", "com.example.C");
        seedRecords(db(tmp.resolve("r")), "com.example.A", "com.example.B", "com.example.C");

        List<StoredRelationshipEdge> forward;
        try (RelationshipStore store = new SqliteRelationshipStore(db(tmp.resolve("f")))) {
            store.put(new RelationshipEdge("com.example.A", "com.example.B", RelationshipType.SEE_ALSO, V));
            store.put(new RelationshipEdge("com.example.A", "com.example.C", RelationshipType.SEE_ALSO, V));
            forward = store.allEdges();
        }
        List<StoredRelationshipEdge> reverse;
        try (RelationshipStore store = new SqliteRelationshipStore(db(tmp.resolve("r")))) {
            store.put(new RelationshipEdge("com.example.A", "com.example.C", RelationshipType.SEE_ALSO, V));
            store.put(new RelationshipEdge("com.example.A", "com.example.B", RelationshipType.SEE_ALSO, V));
            reverse = store.allEdges();
        }
        assertEquals(forward, reverse, "edge order must not depend on insertion order");
        assertTrue(forward.get(0).edgeId().compareTo(forward.get(1).edgeId()) <= 0,
                "edges must be ordered by ascending id");
    }

    /** NFR-13: two independent builds of the same inputs assign byte-identical edge ids. */
    @Test
    void independentBuildsOfSameInputsAssignSameIds(@TempDir Path tmp) {
        seedRecords(db(tmp.resolve("a")), "com.example.Widget", "com.example.Base");
        seedRecords(db(tmp.resolve("b")), "com.example.Widget", "com.example.Base");

        String idA;
        String idB;
        try (RelationshipStore sa = new SqliteRelationshipStore(db(tmp.resolve("a")))) {
            idA = sa.put(extendsClass("com.example.Widget", "com.example.Base")).edgeId();
        }
        try (RelationshipStore sb = new SqliteRelationshipStore(db(tmp.resolve("b")))) {
            idB = sb.put(extendsClass("com.example.Widget", "com.example.Base")).edgeId();
        }
        assertEquals(idA, idB, "the same logical edge must hash to the same id across builds");
    }

    /**
     * AD-4 / AD-22: the store resolves endpoints itself; an endpoint canonical key with no catalog
     * record for the version is an integrity failure, refused (never invented an id from the key text).
     */
    @Test
    void endpointWithNoCatalogRecordIsRefused(@TempDir Path tmp) {
        Path db = db(tmp);
        seedRecords(db, "com.example.Widget"); // Base is NOT seeded
        try (RelationshipStore store = new SqliteRelationshipStore(db)) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> store.put(extendsClass("com.example.Widget", "com.example.Base")));
            assertTrue(e.getMessage().contains("com.example.Base"),
                    () -> "the message should name the unresolved endpoint: " + e.getMessage());
        }
    }
}
