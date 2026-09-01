package com.luciadmcp.knowledge.catalog.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.domain.CatalogRecord;
import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.domain.ContentHashes;
import com.luciadmcp.knowledge.domain.IdentityFields;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The versioned SQLite catalog store (Story 2.4): version-scoped uniqueness with no cross-version
 * collision, idempotent upsert, path/anchor round-trip, deterministic content-hash IDs, resume
 * determinism, and explicit identity-clash detection (never a silent overwrite/drop, AD-4).
 * Fixtures are synthetic; no proprietary content.
 */
class SqliteCatalogStoreTest {

    private static CatalogRecord type(String sdkVersion, String fqn, String path) {
        return new CatalogRecord(sdkVersion, fqn, "TYPE", fqn, simpleOf(fqn), path, "");
    }

    private static CatalogRecord method(String sdkVersion, String fqn, String member, String erased, String path) {
        String key = fqn + "#" + member + "(" + erased + ")";
        return new CatalogRecord(sdkVersion, key, "METHOD", fqn, member, path, member + "(" + erased + ")");
    }

    private static String simpleOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static Path db(Path dir) {
        return dir.resolve("catalog.sqlite");
    }

    /** Matrix row "Cross-version, same simple name": two SDK versions, one catalog, no collision. */
    @Test
    void crossVersionSameNameGetsDistinctVersionScopedRows(@TempDir Path tmp) {
        CatalogRecord v1 = type("2024.0", "com.example.Widget", "com/example/Widget.html");
        CatalogRecord v2 = type("2025.0", "com.example.Widget", "com/example/Widget.html");

        String id1;
        String id2;
        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            id1 = store.put(v1).recordId();
            id2 = store.put(v2).recordId();

            assertNotEquals(id1, id2, "same simple name in two SDK versions must not collide on id");
            assertEquals(2, store.allRecords().size(), "both version-scoped rows must persist");
            assertEquals(Optional.of(new StoredCatalogRecord(id1, v1)), store.findByKey("2024.0", "com.example.Widget"));
            assertEquals(Optional.of(new StoredCatalogRecord(id2, v2)), store.findByKey("2025.0", "com.example.Widget"));
        }
    }

    /** Matrix row "Duplicate computed ID within one build": identical tuple upserts to one row. */
    @Test
    void duplicateSubmissionIsIdempotent(@TempDir Path tmp) {
        CatalogRecord r = method("2024.0", "com.example.Widget", "contains", "Point", "com/example/Widget.html");
        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            CatalogStore.PutResult first = store.put(r);
            CatalogStore.PutResult second = store.put(r);

            assertEquals(CatalogStore.PutStatus.INSERTED, first.status());
            assertEquals(CatalogStore.PutStatus.DUPLICATE, second.status(), "re-submitting the same tuple is a no-op");
            assertEquals(first.recordId(), second.recordId(), "the same logical tuple yields the same id");
            assertEquals(1, store.allRecords().size(), "an idempotent upsert leaves exactly one row");
        }
    }

    /** Matrix row "Resolvable to source": relativePath + anchor round-trip unchanged. */
    @Test
    void relativePathAndAnchorRoundTrip(@TempDir Path tmp) {
        CatalogRecord r = method("2024.0", "com.example.Widget", "map", "Function", "com/example/Widget.html");
        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            store.put(r);
            StoredCatalogRecord back = store.findByKey("2024.0", "com.example.Widget#map(Function)").orElseThrow();
            assertEquals("com/example/Widget.html", back.record().relativePath());
            assertEquals("map(Function)", back.record().anchor());
            assertEquals(r, back.record(), "the whole tuple must round-trip");
        }
    }

    /** AD-22: the id is exactly ContentHashes.recordHash over the IdentityFields spec — no other recipe. */
    @Test
    void recordIdIsTheIdentityFieldsContentHash(@TempDir Path tmp) {
        CatalogRecord r = type("2024.0", "com.example.Widget", "com/example/Widget.html");
        String expected = ContentHashes.recordHash(
                IdentityFields.entityTypeFor(r), IdentityFields.identityValues(r));
        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            assertEquals(expected, store.put(r).recordId());
        }
    }

    /** Matrix row "Interrupted then resumed build": re-running over the same DB reproduces rows/ids. */
    @Test
    void resumeOverSameFileReproducesRowsAndIds(@TempDir Path tmp) {
        CatalogRecord t = type("2024.0", "com.example.Widget", "com/example/Widget.html");
        CatalogRecord m = method("2024.0", "com.example.Widget", "contains", "Point", "com/example/Widget.html");

        List<StoredCatalogRecord> firstRun;
        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            store.put(t);
            store.put(m);
            firstRun = store.allRecords();
        }

        // Reopen the same file (a resumed run) and re-submit the same tuples: idempotent, identical rows.
        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            assertEquals(CatalogStore.PutStatus.DUPLICATE, store.put(t).status());
            assertEquals(CatalogStore.PutStatus.DUPLICATE, store.put(m).status());
            assertEquals(firstRun, store.allRecords(), "a resumed run must reproduce identical rows and ids");
            assertEquals(2, store.allRecords().size());
        }
    }

    /** NFR-13: two independent builds of the same inputs assign byte-identical ids. */
    @Test
    void independentBuildsOfSameInputsAssignSameIds(@TempDir Path tmp) throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a"));
        Path b = Files.createDirectories(tmp.resolve("b"));
        CatalogRecord r = method("2024.0", "com.example.Widget", "contains", "Point", "com/example/Widget.html");

        String idA;
        String idB;
        try (CatalogStore sa = new SqliteCatalogStore(db(a))) {
            idA = sa.put(r).recordId();
        }
        try (CatalogStore sb = new SqliteCatalogStore(db(b))) {
            idB = sb.put(r).recordId();
        }
        assertEquals(idA, idB, "the same logical record must hash to the same id across builds");
    }

    /** AD-4: two distinct tuples claiming one identity is an explicit clash, not a silent overwrite. */
    @Test
    void differentTupleAtSameIdentityIsAnIdentityClash(@TempDir Path tmp) {
        CatalogRecord original = type("2024.0", "com.example.Widget", "com/example/Widget.html");
        // Same (sdkVersion, canonicalKey) but a different source path — a genuine identity clash.
        CatalogRecord conflicting = type("2024.0", "com.example.Widget", "com/example/other/Widget.html");

        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            assertEquals(CatalogStore.PutStatus.INSERTED, store.put(original).status());
            CatalogStore.PutResult clash = store.put(conflicting);

            assertEquals(CatalogStore.PutStatus.IDENTITY_CLASH, clash.status());
            StoredCatalogRecord kept = store.findByKey("2024.0", "com.example.Widget").orElseThrow();
            assertEquals(original, kept.record(), "the original row must be left untouched on a clash");
            assertEquals(1, store.allRecords().size(), "a clash must not add a second row");
        }
    }

    /**
     * AD-4 via the SQL path: two records sharing {@code (sdk_version, canonical_key)} but hashing to
     * different record ids (a type vs. a member entity) collide on the {@code UNIQUE(sdk_version,
     * canonical_key)} index, not on the primary key — so the SQL-constraint clash branch runs and maps
     * to {@link CatalogStore.PutStatus#IDENTITY_CLASH}.
     */
    @Test
    void sameVersionKeyDifferentRecordIdIsAnIdentityClashViaUniqueIndex(@TempDir Path tmp) {
        // Same (sdkVersion, canonicalKey), different kind -> different entity -> different record id.
        CatalogRecord asType = new CatalogRecord(
                "2024.0", "com.example.Widget", "TYPE", "com.example.Widget", "Widget", "com/example/Widget.html", "");
        CatalogRecord asMember = new CatalogRecord(
                "2024.0", "com.example.Widget", "METHOD", "com.example.Widget", "Widget", "com/example/Widget.html", "Widget()");

        try (CatalogStore store = new SqliteCatalogStore(db(tmp))) {
            CatalogStore.PutResult first = store.put(asType);
            CatalogStore.PutResult second = store.put(asMember);

            assertEquals(CatalogStore.PutStatus.INSERTED, first.status());
            assertNotEquals(first.recordId(), second.recordId(),
                    "the two entities must hash to different record ids");
            assertEquals(CatalogStore.PutStatus.IDENTITY_CLASH, second.status(),
                    "a same-version-key/different-id collision must surface as an identity clash");
            assertEquals(1, store.allRecords().size(), "the clash must not add a second row");
        }
    }

    /** allRecords is deterministically ordered (ascending id), independent of insertion order. */
    @Test
    void allRecordsIsDeterministicallyOrdered(@TempDir Path tmp) {
        CatalogRecord a = method("2024.0", "com.example.Widget", "alpha", "int", "com/example/Widget.html");
        CatalogRecord z = method("2024.0", "com.example.Widget", "zeta", "int", "com/example/Widget.html");

        List<StoredCatalogRecord> forward;
        try (CatalogStore store = new SqliteCatalogStore(db(tmp.resolve("f")))) {
            store.put(a);
            store.put(z);
            forward = store.allRecords();
        }
        List<StoredCatalogRecord> reverse;
        try (CatalogStore store = new SqliteCatalogStore(db(tmp.resolve("r")))) {
            store.put(z);
            store.put(a);
            reverse = store.allRecords();
        }
        assertEquals(forward, reverse, "row order must not depend on insertion order");
        assertTrue(forward.get(0).recordId().compareTo(forward.get(1).recordId()) <= 0,
                "records must be ordered by ascending id");
    }
}
