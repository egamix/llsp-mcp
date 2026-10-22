package com.luciadmcp.knowledge.domain;
/** Separate from SDK relationship traversal. Required cycles or candidate targets invalidate activation. */
public record KnowledgeDependency(String sourceId, String targetId, Type type, String sdkVersion) {
    public enum Type { REQUIRED, OPTIONAL, SUPERSEDES }
    public KnowledgeDependency { if (sourceId == null || sourceId.isBlank() || targetId == null || targetId.isBlank() || type == null || sdkVersion == null || sdkVersion.isBlank()) throw new IllegalArgumentException("dependency fields required"); }
}
