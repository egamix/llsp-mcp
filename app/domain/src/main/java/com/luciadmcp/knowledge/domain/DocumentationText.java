package com.luciadmcp.knowledge.domain;

import java.util.ArrayList;
import java.util.List;

/** The persisted lexical projection; shared with citation verification. */
public final class DocumentationText {
    private DocumentationText() {}
    public static String block(DocumentationPayload.Block block) {
        var parts=new ArrayList<String>(); parts.add(block.text());
        block.links().forEach(link->parts.add(link.label()));
        block.table().ifPresent(table->{ parts.addAll(table.headers()); table.rows().forEach(parts::addAll); });
        items(parts,block.items()); return String.join("\n",parts);
    }
    private static void items(List<String> parts,List<DocumentationPayload.ListItem> items) {
        for(var item:items) { parts.add(item.text()); items(parts,item.children()); }
    }
}
