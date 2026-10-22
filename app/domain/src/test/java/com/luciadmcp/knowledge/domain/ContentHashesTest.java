package com.luciadmcp.knowledge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The five content hashes are pure and deterministic: same input → same hex (AC 1), a fixed input
 * hits a pinned known-answer (guards recipe drift, AC 2), and a change to one field changes only
 * that hash (locality, AC 3). Field framing is collision-free (AC 2).
 */
class ContentHashesTest {

    // --- determinism (AC 1) -------------------------------------------------------------------

    @Test
    void sameInputsProduceSameHashEveryTime() {
        byte[] bytes = "package com.example;".getBytes(StandardCharsets.UTF_8);
        assertEquals(ContentHashes.sourceHash(bytes), ContentHashes.sourceHash(bytes.clone()));

        List<String> id = List.of("com.example.Foo", "PUBLIC");
        assertEquals(
                ContentHashes.recordHash("ApiElement", id),
                ContentHashes.recordHash("ApiElement", id));

        assertEquals(
                ContentHashes.relationshipHash("2024.0", "Foo", RelationshipType.EXTENDS_CLASS, "Bar", "FQN_LITERAL"),
                ContentHashes.relationshipHash("2024.0", "Foo", RelationshipType.EXTENDS_CLASS, "Bar", "FQN_LITERAL"));

        assertEquals(ContentHashes.semanticHash("normalized text"), ContentHashes.semanticHash("normalized text"));

        assertEquals(
                ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1"),
                ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1"));
    }

    // --- known-answer (AC 2) — pinned from the recipe; a change here means the recipe drifted ----

    @Test
    void sourceHashMatchesKnownSha256() {
        // SHA-256("abc") — the canonical NIST test vector; sourceHash is a bare digest of the bytes.
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ContentHashes.sourceHash("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void semanticHashMatchesKnownSha256() {
        // semanticHash hashes the UTF-8 text verbatim, so it equals SHA-256("abc") too.
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ContentHashes.semanticHash("abc"));
    }

    @Test
    void recordHashIsPinned() {
        assertEquals(
                "5cf3287d9d1854c9700527dfe91b173630f462a53bf05b0325544e0441419d65",
                ContentHashes.recordHash("ApiElement", List.of("com.example.Foo", "PUBLIC")));
    }

    @Test
    void relationshipHashIsPinned() {
        assertEquals(
                "d9c00269d935a073175af8d48bad5c2c5d92810a08737b5447f162bb74ab2b6f",
                ContentHashes.relationshipHash("2024.0", "Foo", RelationshipType.EXTENDS_CLASS, "Bar", "FQN_LITERAL"));
    }

    @Test
    void toolchainFingerprintIsPinned() {
        assertEquals(
                "b8a07c08074302ef0a249293de0d65bf492b9204bb14780a8061353b3e16da94",
                ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1"));
    }

    // --- locality: one field changes, only that hash changes (AC 3) ---------------------------

    @Test
    void changingOneIdentityFieldChangesOnlyThatRecordHash() {
        String a = ContentHashes.recordHash("ApiElement", List.of("com.example.Foo", "PUBLIC"));
        String b = ContentHashes.recordHash("ApiElement", List.of("com.example.Foo", "PRIVATE"));
        String unrelated = ContentHashes.recordHash("ApiElement", List.of("com.example.Other", "PUBLIC"));

        assertNotEquals(a, b, "a differing identity field must change the record hash");
        // The unrelated record's hash is a pure function of its own fields — unaffected by a/b.
        assertEquals(
                ContentHashes.recordHash("ApiElement", List.of("com.example.Other", "PUBLIC")),
                unrelated);
    }

    @Test
    void changingOneRelationshipFieldChangesOnlyThatRelationshipHash() {
        String a = ContentHashes.relationshipHash(
                "2024.0", "Foo", RelationshipType.EXTENDS_CLASS, "Bar", "FQN_LITERAL");
        String b = ContentHashes.relationshipHash(
                "2024.0", "Foo", RelationshipType.EXTENDS_CLASS, "Baz", "FQN_LITERAL");
        String unrelated = ContentHashes.relationshipHash(
                "2024.0", "Other", RelationshipType.IMPLEMENTS, "Qux", "FQN_LITERAL");

        assertNotEquals(a, b, "a differing target id must change the relationship hash");
        // The unrelated relationship's hash is a pure function of its own fields — unaffected by a/b.
        assertEquals(
                ContentHashes.relationshipHash(
                        "2024.0", "Other", RelationshipType.IMPLEMENTS, "Qux", "FQN_LITERAL"),
                unrelated);
    }

    @Test
    void differentEntityTypesDoNotCollide() {
        assertNotEquals(
                ContentHashes.recordHash("ApiElement", List.of("X")),
                ContentHashes.recordHash("MethodElement", List.of("X")));
    }

    @Test
    void changingOneToolchainComponentChangesFingerprint() {
        String base = ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1");
        assertNotEquals(base, ContentHashes.toolchainFingerprint("p2", "s1", "n1", "r1", "e1"));
        assertNotEquals(base, ContentHashes.toolchainFingerprint("p1", "s2", "n1", "r1", "e1"));
        assertNotEquals(base, ContentHashes.toolchainFingerprint("p1", "s1", "n2", "r1", "e1"));
        assertNotEquals(base, ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r2", "e1"));
        assertNotEquals(base, ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e2"));
    }

    // --- framing is collision-free (AC 2) -----------------------------------------------------

    @Test
    void fieldFramingIsCollisionFree() {
        // Without length-prefix framing, ("a","bc") and ("ab","c") would concatenate identically.
        assertNotEquals(
                ContentHashes.recordHash("E", List.of("a", "bc")),
                ContentHashes.recordHash("E", List.of("ab", "c")));
    }

    @Test
    void nullFieldIsDistinctFromEmptyField() {
        String withNull = ContentHashes.relationshipHash("v", "s", RelationshipType.IMPLEMENTS, "t", null);
        String withEmpty = ContentHashes.relationshipHash("v", "s", RelationshipType.IMPLEMENTS, "t", "");
        assertNotEquals(withNull, withEmpty, "a null evidence must not hash the same as empty evidence");
    }

    // --- guards -------------------------------------------------------------------------------

    @Test
    void nullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentHashes.sourceHash(null));
        assertThrows(IllegalArgumentException.class, () -> ContentHashes.recordHash("E", null));
        assertThrows(IllegalArgumentException.class, () -> ContentHashes.recordHash(" ", List.of("x")));
        assertThrows(IllegalArgumentException.class, () -> ContentHashes.semanticHash(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContentHashes.relationshipHash("v", "s", null, "t", "e"));
    }
}
