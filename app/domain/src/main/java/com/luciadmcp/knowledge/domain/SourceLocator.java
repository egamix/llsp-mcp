package com.luciadmcp.knowledge.domain;
import java.util.Optional;
/** Actual source position or explicit absence. Never synthesize an anchor from a symbol. */
public record SourceLocator(Kind kind, String relativePath, Optional<String> anchor, String reason, Optional<LineRange> lines, Optional<String> section) {
    public record LineRange(int first, int last) { public LineRange { if (first < 1 || last < first) throw new IllegalArgumentException("invalid line range"); } }
    public SourceLocator(Kind kind, String path, Optional<String> anchor, String reason) { this(kind,path,anchor,reason,Optional.empty(),Optional.empty()); }
    public enum Kind { SOURCE, ABSENT, EXTERNAL }
    public SourceLocator {
        if (kind == null || relativePath == null || anchor == null || reason == null || lines == null || section == null) throw new IllegalArgumentException("locator fields required");
        if (kind == Kind.SOURCE && (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("\\") || relativePath.contains(":") || java.util.Arrays.asList(relativePath.split("/")).contains(".."))) throw new IllegalArgumentException("source-relative path required");
        if (anchor.isPresent() && anchor.get().isBlank()) throw new IllegalArgumentException("empty anchor must be absent");
        if (kind == Kind.ABSENT && (reason.isBlank() || anchor.isPresent() || lines.isPresent() || section.isPresent())) throw new IllegalArgumentException("absent locator requires reason, no anchor");
    }
}
