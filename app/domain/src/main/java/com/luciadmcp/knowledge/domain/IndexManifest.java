package com.luciadmcp.knowledge.domain;

/**
 * Metadata describing one immutable index build.
 *
 * <p>Skeleton identity only: a build id plus the SDK version the build was produced from
 * (AD-12). The full manifest (embedding fingerprint, validation state, activation pointer,
 * retention) is filled in by the index-lifecycle epic (Epic 6) and is not invented here.
 *
 * @param buildId    stable identity of this index build
 * @param sdkVersion SDK version this build was produced from (AD-12)
 */
public record IndexManifest(String buildId, String sdkVersion) {
}
