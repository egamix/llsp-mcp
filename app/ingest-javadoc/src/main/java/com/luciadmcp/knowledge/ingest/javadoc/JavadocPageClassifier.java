package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.Locale;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Classifies a single parsed Javadoc page by DOM structure, never by filename (FR-4, AC 5).
 *
 * <p>Two structural signals are used, in order:
 * <ol>
 *   <li><b>Modern generators (JDK 11+)</b> stamp the page kind on the {@code <body>} class token
 *       (e.g. {@code class-declaration-page}, {@code package-declaration-page}, {@code tree-page}).
 *       That class attribute is part of the DOM, so keying off it is structural, not filename-based.</li>
 *   <li><b>Legacy generators (JDK 8)</b> carry no body-class hint; the primary heading text plus the
 *       presence of member-summary/detail blocks decide the type.</li>
 * </ol>
 *
 * <p>Anything that matches neither is {@link PageType#UNKNOWN} — recorded, never guessed (AD-4).
 * Classification refines in Story 2.2; here it only needs to bucket pages correctly. This stage does
 * NOT extract members (names, kinds, signatures) — that is the canonical catalog work in Epic 3.
 */
final class JavadocPageClassifier {

    // Modern <body> class tokens that denote a member-bearing type page.
    private static final Set<String> TYPE_BODY_CLASSES = Set.of(
            "class-declaration-page",
            "interface-declaration-page",
            "enum-declaration-page",
            "annotation-type-declaration-page",
            "annotation-interface-declaration-page",
            "record-declaration-page");

    // Modern <body> class tokens for nav/tree/help/index-family pages excluded from ingestion.
    private static final Set<String> EXCLUDED_BODY_CLASSES = Set.of(
            "tree-page",
            "package-tree-page",
            "class-use-page",      // "Uses of Class/Interface X" — cross-reference, not member content
            "package-use-page",    // "Uses of Package X"
            "doc-file-page",       // hand-authored doc-files/ pages carried alongside the API
            "help-page",
            "index-page",
            "single-index-page",
            "all-classes-index-page",
            "all-packages-index-page",
            "deprecated-list-page",
            "constants-summary-page",
            "serialized-form-page",
            "search-page",
            "new-api-list-page",
            "preview-list-page",
            "system-properties-page");

    private static final String MEMBER_BLOCK_SELECTOR =
            "section.class-description, table.memberSummary, .memberSummary, "
                    + "ul.blockList > li.blockList, ul.memberSummary";

    // Heading selectors in PRIORITY order — queried one at a time, not as a grouped selector.
    // A grouped "a, b" selectFirst returns the first match in *document* order, which on a legacy
    // page with a stray leading heading would pick that over the real title. Trying each selector
    // in turn keeps the specific title class (h1.title/h2.title) ahead of any bare h1/h2.
    private static final String[] HEADING_SELECTORS = {
            "h1.title", "h2.title", "main h1", "main h2", "h1", "h2"};

    /**
     * Diagnostic breakdown of how a page was classified — the full extent of what this stage
     * "understands" about a page. Surfaced only via DEBUG logging; not persisted. Member extraction
     * (the per-method/field breakdown) is a later story, so this carries only page-level signals.
     *
     * @param type     the resolved page bucket
     * @param signal   which rule fired (e.g. {@code body-class:class-declaration-page}, {@code heading},
     *                 {@code member-blocks}, {@code no-signal})
     * @param heading  the page's primary heading in original case (e.g. {@code "Class TLcdModel"}), or null
     * @param hasMembers whether member-summary/detail blocks are present on the page
     */
    record Analysis(PageType type, String signal, String heading, boolean hasMembers) {}

    /** Buckets a page. Kept as the lean hot path; see {@link #analyze} for the diagnostic breakdown. */
    PageType classify(Document doc) {
        return analyze(doc).type();
    }

    /** Classifies {@code doc} and reports the signals behind the decision (for DEBUG logging). */
    Analysis analyze(Document doc) {
        String heading = primaryHeading(doc);
        boolean hasMembers = hasMemberBlocks(doc);

        // Modern path: the <body> class token is authoritative.
        Element body = doc.body();
        if (body != null) {
            for (String raw : body.classNames()) {
                String token = raw.toLowerCase(Locale.ROOT);
                if (TYPE_BODY_CLASSES.contains(token)) {
                    return new Analysis(PageType.TYPE, "body-class:" + token, heading, hasMembers);
                }
                if (EXCLUDED_BODY_CLASSES.contains(token)) {
                    return new Analysis(PageType.EXCLUDED, "body-class:" + token, heading, hasMembers);
                }
                if (token.equals("package-declaration-page")) {
                    return new Analysis(PageType.PACKAGE, "body-class:" + token, heading, hasMembers);
                }
                // Root package listing and module pages are narrative, not member-bearing.
                if (token.equals("package-index-page") || token.equals("module-declaration-page")
                        || token.equals("module-index-page")) {
                    return new Analysis(PageType.OVERVIEW, "body-class:" + token, heading, hasMembers);
                }
            }
        }

        // Legacy overview titles are customizable with -doctitle; the package table is stable.
        if (doc.selectFirst("table.overviewSummary") != null) {
            return new Analysis(PageType.OVERVIEW, "overview-summary", heading, hasMembers);
        }

        // Legacy / body-class-less path: decide from the primary heading, then member structure.
        PageType byHeading = classifyByHeading(heading);
        if (byHeading != null) {
            return new Analysis(byHeading, "heading", heading, hasMembers);
        }
        if (hasMembers) {
            // Atypical heading but clearly a member-bearing type page.
            return new Analysis(PageType.TYPE, "member-blocks", heading, hasMembers);
        }
        return new Analysis(PageType.UNKNOWN, "no-signal", heading, hasMembers);
    }

    /** Maps a primary heading to a page type by its leading words, or null if none apply. */
    private static PageType classifyByHeading(String headingOriginal) {
        if (headingOriginal == null) {
            return null;
        }
        String heading = headingOriginal.toLowerCase(Locale.ROOT);
        if (startsWithAny(heading, "class ", "interface ", "enum ", "enum class ",
                "annotation type ", "annotation interface ", "record ", "record class ")) {
            return PageType.TYPE;
        }
        if (heading.startsWith("package ")) {
            return PageType.PACKAGE;
        }
        if (heading.startsWith("uses of ")) { // "Uses of Class/Interface/Package ..." cross-ref pages
            return PageType.EXCLUDED;
        }
        if (heading.contains("hierarchy")) { // "Hierarchy For Package ...", "Class Hierarchy"
            return PageType.EXCLUDED;
        }
        if (heading.equals("api help") || heading.startsWith("help")
                || heading.equals("how this api document is organized")) {
            return PageType.EXCLUDED;
        }
        if (heading.startsWith("deprecated")) {
            return PageType.EXCLUDED;
        }
        if (heading.startsWith("constant field values") || heading.startsWith("constant values")) {
            return PageType.EXCLUDED;
        }
        if (heading.startsWith("serialized form")) {
            return PageType.EXCLUDED;
        }
        if (heading.equals("index") || heading.startsWith("index ") || heading.startsWith("all ")) {
            return PageType.EXCLUDED;
        }
        if (heading.startsWith("overview")) {
            return PageType.OVERVIEW;
        }
        return null;
    }

    private static boolean hasMemberBlocks(Document doc) {
        return !doc.select(MEMBER_BLOCK_SELECTOR).isEmpty();
    }

    /**
     * The primary heading's text in original case, or null. Selectors are tried in priority order
     * (title-class headings first, then {@code main} headings, then any {@code h1}/{@code h2}) so a
     * stray leading heading cannot outrank the real title. Legacy uses {@code h2.title}; modern uses
     * {@code h1}.
     */
    private static String primaryHeading(Document doc) {
        for (String selector : HEADING_SELECTORS) {
            Element h = doc.selectFirst(selector);
            if (h != null) {
                String text = h.text().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
