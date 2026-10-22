package com.luciadmcp.knowledge.domain;

/**
 * Metadata describing one immutable index build.
 *
 * <p>Identity plus the compatibility fingerprint: a build id, the SDK version the build was produced
 * from (AD-12), and the {@code toolchainFingerprint} (AD-6) over the parser/schema/normalizer/
 * relationship-rules/embedding-config versions. A change to any toolchain component changes the
 * fingerprint; a later load can compare it against the runtime and refuse a mismatch. The refuse-on-
 * load gate itself is Epic 6 — this record only carries the value.
 *
 * <p>The remaining manifest fields (validation state, activation pointer, retention) are filled in
 * by the index-lifecycle epic (Epic 6) and are not invented here.
 *
 * @param buildId              stable identity of this index build
 * @param sdkVersion           SDK version this build was produced from (AD-12)
 * @param toolchainFingerprint compatibility fingerprint over the toolchain component versions (AD-6)
 */
public record IndexManifest(String buildId, String sdkVersion, String toolchainFingerprint,
        int manifestVersion, String profile, String inputDigest, String recipeDigest, String runtimeDigest,
        java.util.List<String> capabilities, String capabilityFingerprint) {
    public IndexManifest(String buildId, String sdkVersion, String toolchainFingerprint) {
        this(buildId, sdkVersion, toolchainFingerprint, 1, ContractVersions.PROFILE,
                buildId.startsWith("b-") ? buildId.substring(2) : buildId,
                ContentHashes.recipeFingerprint(ContractVersions.PROFILE, java.util.List.of()),
                ContentHashes.runtimeFingerprint(), java.util.List.of("catalog", "relationships"),
                ContentHashes.digest("capabilities/1", java.util.List.of("catalog", "relationships")));
    }
    /** Content-capable production builds promise a payload for every catalog record. */
    public static IndexManifest withDocumentation(String buildId, String sdkVersion, String toolchainFingerprint) {
        var base = new IndexManifest(buildId, sdkVersion, toolchainFingerprint);
        var capabilities = java.util.List.of("api-documentation", "catalog", "narrative-documentation", "relationships", "structured-documentation");
        return new IndexManifest(buildId, sdkVersion, toolchainFingerprint, base.manifestVersion(),
                base.profile(), base.inputDigest(), base.recipeDigest(), base.runtimeDigest(), capabilities,
                ContentHashes.digest("capabilities/1", capabilities));
    }

    public IndexManifest {
        if (buildId == null || buildId.isBlank() || sdkVersion == null || sdkVersion.isBlank()
                || toolchainFingerprint == null || toolchainFingerprint.isBlank() || manifestVersion != 1
                || profile == null || profile.isBlank() || inputDigest == null || recipeDigest == null
                || runtimeDigest == null || capabilities == null || capabilityFingerprint == null)
            throw new IllegalArgumentException("complete versioned manifest required");
        capabilities = capabilities.stream().distinct().sorted().toList();
        if (!ContentHashes.digest("capabilities/1", capabilities).equals(capabilityFingerprint))
            throw new IllegalArgumentException("capability fingerprint mismatch");
    }
}
