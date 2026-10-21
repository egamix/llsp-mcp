package com.luciadmcp.knowledge.domain;
import java.util.Optional;
/** Actual source position or explicit absence. Never synthesize an anchor from a symbol. */
public record SourceLocator(Kind kind, String relativePath, Optional<String> anchor, String reason) {
    public enum Kind { SOURCE, ABSENT, EXTERNAL }
    public SourceLocator {
        if (kind == null || relativePath == null || anchor == null || reason == null) throw new IllegalArgumentException("locator fields required");
        if (kind == Kind.SOURCE && (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("\\") || relativePath.contains(":") || java.util.Arrays.asList(relativePath.split("/")).contains(".."))) throw new IllegalArgumentException("source-relative path required");
        if (anchor.isPresent() && anchor.get().isBlank()) throw new IllegalArgumentException("empty anchor must be absent");
        if (kind == Kind.ABSENT && (reason.isBlank() || anchor.isPresent())) throw new IllegalArgumentException("absent locator requires reason, no anchor");
    }
}
