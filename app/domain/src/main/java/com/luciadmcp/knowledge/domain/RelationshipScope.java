package com.luciadmcp.knowledge.domain;

/** One completed, documented SDK catalog; no external or inferred universe is included. */
public record RelationshipScope(String sdkVersion) {
    public RelationshipScope {
        if (sdkVersion == null || sdkVersion.isBlank()) {
            throw new IllegalArgumentException("sdkVersion must not be blank");
        }
    }

    public String boundary() { return "DOCUMENTED_SDK_ONLY"; }
}
