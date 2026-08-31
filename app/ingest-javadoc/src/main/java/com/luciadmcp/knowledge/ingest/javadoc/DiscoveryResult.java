package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.List;

/**
 * The outcome of discovering and classifying a Javadoc directory (Story 2.1).
 *
 * @param pages             every discovered page, classified, in ascending relative-path order
 *                          (deterministic: same directory &rarr; same list)
 * @param searchIndexPresent whether a {@code *-search-index.js} was found and used as a corroborating
 *                          assist; discovery and classification produce the same {@code pages} either way
 * @param warnings          non-fatal notes: unclassifiable/unreadable pages and malformed or
 *                          disagreeing search-index entries. Never alters {@code pages}; recorded so a
 *                          later stage can apply the failure policy (which lives in {@code cli}, not here).
 */
public record DiscoveryResult(List<ClassifiedPage> pages, boolean searchIndexPresent, List<String> warnings) {

    public DiscoveryResult {
        pages = List.copyOf(pages);
        warnings = List.copyOf(warnings);
    }

    /** The member-bearing pages ({@link PageType#TYPE}) later stages extract members from. */
    public List<ClassifiedPage> memberPages() {
        return pages.stream().filter(p -> p.type().isMemberBearing()).toList();
    }

    /** The narrative pages ({@link PageType#PACKAGE}/{@link PageType#OVERVIEW}) kept for their prose. */
    public List<ClassifiedPage> narrativePages() {
        return pages.stream().filter(p -> p.type().isNarrative()).toList();
    }
}
