package com.luciadmcp.knowledge.domain;
import java.util.List;
/** Ordered durable content contract. Parsing/persistence implementations belong to Stories 2.9–2.11. */
public record DocumentationPayload(String recordId, String sourceHash, List<Section> sections, String contentHash) {
    public static final int MAX_SECTIONS = 256, MAX_BLOCKS = 4096, MAX_BYTES = 1_048_576;
    public enum BlockKind { PARAGRAPH, CODE, TABLE, LIST, NOTE }
    public record Link(String label, String originalTarget, String sdkVersion, SourceLocator locator) {
        public Link { if (label == null || originalTarget == null || sdkVersion == null || locator == null) throw new IllegalArgumentException("link fields required"); }
    }
    public record Block(BlockKind kind, String label, String text, List<Link> links) {
        public Block { if (kind == null || label == null || text == null || links == null) throw new IllegalArgumentException("block fields required"); links = List.copyOf(links); }
    }
    public record Section(String id, String title, SourceLocator locator, List<Block> blocks) {
        public Section { if (id == null || id.isBlank() || title == null || locator == null || blocks == null) throw new IllegalArgumentException("section fields required"); blocks = List.copyOf(blocks); }
    }
    public DocumentationPayload {
        if (recordId == null || recordId.isBlank() || sourceHash == null || !sourceHash.matches("[0-9a-f]{64}") || sections == null || contentHash == null) throw new IllegalArgumentException("payload fields required");
        sections = List.copyOf(sections);
        if (sections.size() > MAX_SECTIONS || sections.stream().mapToInt(s -> s.blocks().size()).sum() > MAX_BLOCKS) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
        if (sections.stream().map(Section::id).distinct().count() != sections.size()) throw new IllegalArgumentException("duplicate section id");
        List<String> fields = canonicalFields(sections);
        long bytes = fields.stream().filter(java.util.Objects::nonNull).mapToLong(v -> v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        if (bytes > MAX_BYTES) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
        if (!contentHash.equals(ContentHashes.contentHash(fields))) throw new IllegalArgumentException("content hash mismatch");
    }
    public String searchableText() { return sections.stream().flatMap(s -> s.blocks().stream()).map(Block::text).collect(java.util.stream.Collectors.joining("\n")); }
    public static List<String> canonicalFields(List<Section> sections) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        fields.add(Integer.toString(sections.size()));
        for (Section s : sections) {
            fields.add(s.id()); fields.add(s.title()); locatorFields(fields, s.locator()); fields.add(Integer.toString(s.blocks().size()));
            for (Block b : s.blocks()) {
                fields.add(b.kind().name()); fields.add(b.label()); fields.add(b.text()); fields.add(Integer.toString(b.links().size()));
                for (Link l : b.links()) { fields.add(l.label()); fields.add(l.originalTarget()); fields.add(l.sdkVersion()); locatorFields(fields, l.locator()); }
            }
        }
        return java.util.Collections.unmodifiableList(fields);
    }
    private static void locatorFields(java.util.List<String> f, SourceLocator l) { f.add(l.kind().name()); f.add(l.relativePath()); f.add(l.anchor().orElse(null)); f.add(l.reason()); }
}
