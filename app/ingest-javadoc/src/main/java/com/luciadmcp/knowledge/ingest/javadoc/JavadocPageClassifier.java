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
 * Classification refines in Story 2.2; here it only needs to bucket pages correctly.
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

    PageType classify(Document doc) {
        PageType byBody = classifyByBodyClass(doc);
        if (byBody != null) {
            return byBody;
        }
        return classifyByStructure(doc);
    }

    /** Modern path: map the {@code <body>} class token to a page type. Returns null if none matches. */
    private PageType classifyByBodyClass(Document doc) {
        Element body = doc.body();
        if (body == null) {
            return null;
        }
        for (String raw : body.classNames()) {
            String token = raw.toLowerCase(Locale.ROOT);
            if (TYPE_BODY_CLASSES.contains(token)) {
                return PageType.TYPE;
            }
            if (EXCLUDED_BODY_CLASSES.contains(token)) {
                return PageType.EXCLUDED;
            }
            if (token.equals("package-declaration-page")) {
                return PageType.PACKAGE;
            }
            // Root package listing and module pages are narrative, not member-bearing.
            if (token.equals("package-index-page") || token.equals("module-declaration-page")
                    || token.equals("module-index-page")) {
                return PageType.OVERVIEW;
            }
        }
        return null;
    }

    /** Legacy / body-class-less path: decide from the primary heading and member structure. */
    private PageType classifyByStructure(Document doc) {
        boolean hasMembers = !doc.select(
                "section.class-description, table.memberSummary, .memberSummary, "
                        + "ul.blockList > li.blockList, ul.memberSummary").isEmpty();
        String heading = primaryHeadingLower(doc);

        if (heading != null) {
            if (startsWithAny(heading, "class ", "interface ", "enum ", "enum class ",
                    "annotation type ", "annotation interface ", "record ", "record class ")) {
                return PageType.TYPE;
            }
            if (heading.startsWith("package ")) {
                return PageType.PACKAGE;
            }
            if (heading.contains("hierarchy")) { // "Hierarchy For Package ...", "Class Hierarchy"
                return PageType.EXCLUDED;
            }
            if (heading.equals("api help") || heading.startsWith("help")) {
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
        }

        // A type page whose heading was atypical but which clearly carries member blocks.
        if (hasMembers) {
            return PageType.TYPE;
        }
        return PageType.UNKNOWN;
    }

    /** First heading's normalized text, or null. Legacy uses {@code h2.title}; modern uses {@code h1}. */
    private static String primaryHeadingLower(Document doc) {
        Element h = doc.selectFirst("h1.title, h2.title, main h1, main h2, h1, h2");
        if (h == null) {
            return null;
        }
        return h.text().trim().toLowerCase(Locale.ROOT);
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
