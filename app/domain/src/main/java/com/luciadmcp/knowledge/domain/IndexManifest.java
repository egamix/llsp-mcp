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
public record IndexManifest(String buildId, String sdkVersion, String toolchainFingerprint) {

    public IndexManifest {
        if (buildId == null || buildId.isBlank()) {
            throw new IllegalArgumentException("buildId must not be blank");
        }
        if (sdkVersion == null || sdkVersion.isBlank()) {
            throw new IllegalArgumentException("sdkVersion must not be blank");
        }
        if (toolchainFingerprint == null || toolchainFingerprint.isBlank()) {
            throw new IllegalArgumentException("toolchainFingerprint must not be blank");
        }
    }
}
