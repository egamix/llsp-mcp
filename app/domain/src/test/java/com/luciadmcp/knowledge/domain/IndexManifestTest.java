package com.luciadmcp.knowledge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The manifest carries the AD-6 compatibility fingerprint. Two builds from the same toolchain carry
 * an equal fingerprint; a change to any one component yields a different one, so a later load can
 * tell them apart (AC 4). The refuse-on-load gate is Epic 6 — only the value lives here.
 */
class IndexManifestTest {

    @Test
    void carriesToolchainFingerprintFromComponents() {
        String fp = ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1");
        IndexManifest manifest = new IndexManifest("b-abc", "2024.0", fp);
        assertEquals(fp, manifest.toolchainFingerprint());
    }

    @Test
    void equalToolchainReproducesEqualManifestFingerprint() {
        IndexManifest a = new IndexManifest(
                "b-abc", "2024.0", ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1"));
        IndexManifest b = new IndexManifest(
                "b-abc", "2024.0", ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1"));
        assertEquals(a, b);
    }

    @Test
    void changedToolchainComponentChangesManifestFingerprint() {
        IndexManifest base = new IndexManifest(
                "b-abc", "2024.0", ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e1"));
        IndexManifest changed = new IndexManifest(
                "b-abc", "2024.0", ContentHashes.toolchainFingerprint("p1", "s1", "n1", "r1", "e2"));
        assertNotEquals(base.toolchainFingerprint(), changed.toolchainFingerprint());
        assertNotEquals(base, changed);
    }

    @Test
    void blankFieldsAreRejected() {
        String fp = ContentHashes.toolchainFingerprint("p", "s", "n", "r", "e");
        assertThrows(IllegalArgumentException.class, () -> new IndexManifest(" ", "2024.0", fp));
        assertThrows(IllegalArgumentException.class, () -> new IndexManifest("b-abc", " ", fp));
        assertThrows(IllegalArgumentException.class, () -> new IndexManifest("b-abc", "2024.0", " "));
    }
}
