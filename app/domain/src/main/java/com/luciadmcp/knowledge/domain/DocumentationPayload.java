package com.luciadmcp.knowledge.domain;

import java.util.List;

/** Ordered durable content contract. Parsing/persistence implementations belong to Stories 2.9-2.11. */

public record DocumentationPayload(String recordId, String sourceHash, List<Section> sections, String contentHash) {

    public static final int MAX_SECTIONS = 256, MAX_BLOCKS = 4096, MAX_BYTES = 1_048_576;

    public enum BlockKind { PARAGRAPH, CODE, TABLE, LIST, NOTE }

    public record Link(String label, String originalTarget, String sdkVersion, SourceLocator locator) {

        public Link { if (label == null || originalTarget == null || sdkVersion == null || locator == null) throw new IllegalArgumentException("link fields required"); }

    }

    public record Table(List<String> headers, List<List<String>> rows) {

        public Table { headers=List.copyOf(headers); rows=rows.stream().map(List::copyOf).toList(); int width=headers.size(); if (rows.stream().anyMatch(r -> r.size()!=width)) throw new IllegalArgumentException("ragged table"); }

    }

    public record ListItem(String text, List<ListItem> children) {

        public ListItem { if(text==null || children==null) throw new IllegalArgumentException("list item required"); children=List.copyOf(children); }

    }

    public record Block(BlockKind kind, String label, String text, List<Link> links,

            java.util.Optional<Table> table, List<ListItem> items, boolean ordered) {

        public Block(BlockKind kind,String label,String text,List<Link> links) { this(kind,label,text,links,java.util.Optional.empty(),List.of(),false); }

        public Block { if (kind == null || label == null || text == null || links == null || table == null || items == null) throw new IllegalArgumentException("block fields required"); links = List.copyOf(links); items=List.copyOf(items); if(table.isPresent() && kind!=BlockKind.TABLE || !items.isEmpty() && kind!=BlockKind.LIST) throw new IllegalArgumentException("block structure mismatch"); }

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

    public String searchableText() {
        java.util.ArrayList<String> text=new java.util.ArrayList<>();
        for(var section:sections) { text.add(section.title()); for(var block:section.blocks()) {
            text.add(block.label()); text.add(block.text()); block.links().forEach(link -> text.add(link.label()));
            block.table().ifPresent(table -> { text.addAll(table.headers()); table.rows().forEach(text::addAll); });
            searchableItems(text,block.items());
        } }
        return String.join("\n",text);
    }
    private static void searchableItems(List<String> text,List<ListItem> items) { for(var item:items) { text.add(item.text()); searchableItems(text,item.children()); } }
    private static final class BoundedFields extends java.util.ArrayList<String> {
        private long bytes;
        @Override public boolean add(String field) {
            if(size()>=65536 || field!=null && field.length()>MAX_BYTES) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
            bytes+=field==null ? 0 : field.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(bytes>MAX_BYTES) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE"); return super.add(field);
        }
        @Override public boolean addAll(java.util.Collection<? extends String> values) { for(String field:values)add(field);return !values.isEmpty(); }
    }

    public static List<String> canonicalFields(List<Section> sections) {

        java.util.ArrayList<String> fields = new BoundedFields();

        if(sections.size()>MAX_SECTIONS) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
        fields.add(Integer.toString(sections.size()));

        for (Section s : sections) {

            fields.add(s.id()); fields.add(s.title()); locatorFields(fields, s.locator()); fields.add(Integer.toString(s.blocks().size()));

            for (Block b : s.blocks()) {

                fields.add(b.table().isPresent() ? "table" : "absent");

                if(b.table().isPresent()) { Table t=b.table().get(); fields.add(Integer.toString(t.headers().size())); fields.addAll(t.headers()); fields.add(Integer.toString(t.rows().size())); for(var row:t.rows()) fields.addAll(row); }

                fields.add(Boolean.toString(b.ordered())); listFields(fields,b.items(),0);

                fields.add(b.kind().name()); fields.add(b.label()); fields.add(b.text()); fields.add(Integer.toString(b.links().size()));

                for (Link l : b.links()) { fields.add(l.label()); fields.add(l.originalTarget()); fields.add(l.sdkVersion()); locatorFields(fields, l.locator()); }

            }

        }

        return java.util.Collections.unmodifiableList(fields);

    }

    private static void listFields(java.util.List<String> f,List<ListItem> items,int depth) {

        if(depth>32) throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");

        f.add(Integer.toString(items.size())); for(var item:items) { f.add(item.text()); listFields(f,item.children(),depth+1); }

    }

    private static void locatorFields(java.util.List<String> f, SourceLocator l) { f.add(l.kind().name()); f.add(l.relativePath()); f.add(l.anchor().orElse(null)); f.add(l.reason()); f.add(l.lines().map(r -> r.first()+":"+r.last()).orElse(null)); f.add(l.section().orElse(null)); }

}
