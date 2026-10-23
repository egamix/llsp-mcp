package com.luciadmcp.knowledge.domain;

/** Identity read from one validated persisted build, independent of requested target context. */
public record SymbolSourceBinding(String sdkVersion, String buildId, String capabilityFingerprint) {
    public SymbolSourceBinding {
        ExactSymbolQuery.text(sdkVersion); ExactSymbolQuery.text(buildId); ExactSymbolQuery.text(capabilityFingerprint);
    }
}
