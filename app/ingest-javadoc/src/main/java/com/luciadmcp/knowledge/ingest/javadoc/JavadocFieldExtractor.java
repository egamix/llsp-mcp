package com.luciadmcp.knowledge.ingest.javadoc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Extracts the documented fields of a {@link PageType#TYPE} page into a layout-agnostic
 * {@link ExtractedType} (Story 2.2, FR-5).
 *
 * <p>The same fields are pulled from all three Javadoc generator eras seen in real trees — JDK 8
 * legacy ({@code ul.blockList}/{@code li.blockList}/{@code h4}/{@code dl}), JDK 11–16 modern
 * ({@code section.class-description}, {@code section.detail}, {@code dl.notes}), and JDK 17+ modern
 * ({@code section.detail} under {@code ul.member-list}) — so the catalog does not depend on which
 * generator produced the source (AC 1). Prose is whitespace-normalized via {@link Element#text()} so a
 * modern and a legacy page describing the same element produce equal records.
 *
 * <p>Scope boundaries (deliberately narrow):
 * <ul>
 *   <li>Extracts type-level description/{@code @since}/{@code @deprecated} and per-member
 *       description/params/returns/throws/{@code @since}/{@code @deprecated}. No canonical symbols
 *       (Story 2.3), no persistence (Story 2.4), no relationships (Story 2.5+).</li>
 *   <li>Runs on {@link PageType#TYPE} pages only; a non-type page yields an empty result plus an
 *       issue rather than guessed members (AC 3).</li>
 *   <li>Never throws on malformed/incomplete markup: it extracts what it can and records each thing
 *       it could not extract as an explicit issue (AC 5). It expresses degradation in this module's
 *       own terms — it does <b>not</b> import or reference the {@code cli} failure policy (AD-1); the
 *       CATALOG stage maps issues onto {@code fail-fast | collect-and-continue} in Story 2.4.</li>
 *   <li>Pure library: reads local bytes only, {@code Jsoup.connect} is never used (AD-9); HTML is
 *       parsed, never rendered or executed (AD-10); writes nothing; no pipeline wiring.</li>
 * </ul>
 */
public final class JavadocFieldExtractor {

    // Type-level description container: modern section.class-description (JDK 17+ / 11–16), the
    // intermediate HTML5 doclet's section.description, and legacy div.description.
    private static final String TYPE_DESC_SCOPE =
            "section.class-description, section.description, div.description";

    // A member's description/deprecation-comment block, and the deprecation markers, across eras.
    private static final String BLOCK = ".block";
    private static final String DEPRECATED_LABEL = "span.deprecated-label, span.deprecatedLabel";
    private static final String DEPRECATION_BLOCK =
            "div.deprecation-block, .deprecation-block, .deprecationBlock";
    private static final String DEPRECATION_COMMENT = ".deprecation-comment, .deprecationComment";

    // A member's signature text container across eras.
    private static final String SIGNATURE = ".member-signature, .memberSignature, pre";

    private final JavadocPageClassifier classifier = new JavadocPageClassifier();

    /**
     * Parses {@code file} from local bytes and extracts its fields. A read failure is non-fatal: it
     * yields an empty result plus an issue, never a thrown exception out of normal extraction.
     *
     * @param file         the type page to read
     * @param relativePath the page's {@code /}-separated path relative to the Javadoc root
     * @return the extracted fields and any issues
     */
    public ExtractionResult extract(Path file, String relativePath) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        try {
            // charset=null lets JSoup honor a BOM/meta charset (legacy trees may be ISO-8859-1),
            // falling back to UTF-8 when none is declared. Local-bytes parse only (AD-9).
            Document doc = Jsoup.parse(file.toFile(), null);
            return extract(doc, relativePath);
        } catch (IOException e) {
            return empty(relativePath, "failed to read/parse " + relativePath + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the fields of an already-parsed type page. The intended caller passes a page classified
     * {@link PageType#TYPE}; a non-type page is skipped (empty result + issue) rather than mined for
     * members (AC 3).
     *
     * @param doc          the parsed page
     * @param relativePath the page's {@code /}-separated path relative to the Javadoc root
     * @return the extracted fields and any issues
     */
    public ExtractionResult extract(Document doc, String relativePath) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must not be null");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }

        PageType pageType = classifier.classify(doc);
        if (pageType != PageType.TYPE) {
            return empty(relativePath, "skipped non-TYPE page (" + pageType + "): " + relativePath);
        }

        List<String> issues = new ArrayList<>();

        // --- Type-level fields ---
        Element scope = doc.selectFirst(TYPE_DESC_SCOPE);
        String typeDescription = "";
        String typeSince = "";
        Deprecation typeDeprecation = Deprecation.NONE;
        if (scope != null) {
            Element descBlock = firstNonDeprecationBlock(scope);
            typeDescription = descBlock == null ? "" : norm(descBlock);
            if (typeDescription.isEmpty()) {
                issues.add(relativePath + ": type description is empty");
            }
            typeSince = notesValue(scope, "since");
            typeDeprecation = findDeprecation(scope);
        } else {
            issues.add(relativePath + ": no type-description section found");
        }

        // --- Members (source order) ---
        List<ExtractedMember> members = new ArrayList<>();
        for (Element block : memberBlocks(doc)) {
            ExtractedMember member = extractMember(block, relativePath, issues);
            if (member != null) {
                members.add(member);
            }
        }

        ExtractedType type =
                new ExtractedType(relativePath, typeDescription, typeSince, typeDeprecation, members);
        return new ExtractionResult(type, issues);
    }

    /**
     * The member blocks on the page, in document order. Modern pages carry one {@code section.detail}
     * per member; legacy pages carry {@code li.blockList} member blocks (identified by a direct
     * {@code h4} heading). A page is one era, so exactly one of the two is non-empty.
     */
    private static List<Element> memberBlocks(Document doc) {
        List<Element> modern = doc.select("section.detail");
        if (!modern.isEmpty()) {
            return modern;
        }
        List<Element> legacy = new ArrayList<>();
        for (Element li : doc.select("li.blockList, li.blockListLast")) {
            if (hasDirectChild(li, "h4")) {
                legacy.add(li);
            }
        }
        return legacy;
    }

    private ExtractedMember extractMember(Element block, String relativePath, List<String> issues) {
        String name = headingText(block);
        if (name.isEmpty()) {
            issues.add(relativePath + ": member with no recoverable name; skipped");
            return null;
        }
        MemberKind kind = memberKind(block, relativePath, name, issues);
        Element sig = block.selectFirst(SIGNATURE);
        String signature = sig == null ? "" : norm(sig);

        Deprecation deprecation = findDeprecation(block);

        Element descBlock = firstNonDeprecationBlock(block);
        String description = descBlock == null ? "" : norm(descBlock);
        // A member documented solely via @deprecated (a non-empty deprecation comment, no separate
        // description block) is documented — not missing docs. Only flag a genuine empty.
        boolean documentedByDeprecation = deprecation.present() && !deprecation.comment().isEmpty();
        if (description.isEmpty() && !documentedByDeprecation) {
            issues.add(relativePath + ": member '" + name + "' is missing its description");
        }

        Notes notes = parseNotes(block, relativePath, name, issues);

        return new ExtractedMember(kind, name, signature, description,
                notes.params, notes.returns, notes.throwsTypes, notes.since, deprecation);
    }

    /**
     * The member's kind, decided by the <b>enclosing details-group</b> section — never by the
     * member's own {@code section.detail} id (which is the member signature, so a method named
     * {@code getField}/{@code getConstructor} must not self-match). Modern groups carry the kind in
     * the group section's id/class (e.g. {@code constructor-details}, {@code field-details},
     * {@code constant-details}); legacy carries it in the nearest ancestor {@code h3} heading
     * ("Constructor Detail"/"Field Detail"/"Enum Constant Detail"/...). An unrecognized group is
     * recorded as an issue and defaulted to {@code METHOD} rather than silently guessed (AD-4).
     */
    private static MemberKind memberKind(
            Element block, String relativePath, String name, List<String> issues) {
        // Probe = the enclosing details-group descriptor (modern id/class) plus the legacy heading,
        // never the member section's own id. Starting the group search at block.parent() excludes
        // block itself, so closest-style self-inclusion cannot misfire on the member's signature.
        String descriptor = groupDescriptor(block.parent());
        String heading = nearestSectionHeading(block);
        String probe = ((descriptor == null ? "" : descriptor) + " "
                + (heading == null ? "" : heading.toLowerCase(Locale.ROOT))).trim();

        if (probe.contains("constructor")) {
            return MemberKind.CONSTRUCTOR;
        }
        if (probe.contains("constant")) { // "constant-details" / "Enum Constant Detail"
            return MemberKind.ENUM_CONSTANT;
        }
        if (probe.contains("field")) {
            return MemberKind.FIELD;
        }
        if (probe.contains("method")) {
            return MemberKind.METHOD;
        }
        // Recognized nothing (e.g. an annotation-interface element group, or a future doclet
        // section): don't fabricate a kind — record it and default to METHOD (AD-4).
        issues.add(relativePath + ": member '" + name
                + "' is in an unrecognized detail section; kind defaulted to METHOD");
        return MemberKind.METHOD;
    }

    /**
     * Lowercased {@code id + class} of the nearest ancestor (self-inclusive of {@code start}) whose
     * id/class names a Javadoc detail group (contains {@code "detail"}), e.g.
     * {@code "method-detail method-details"}, or null if none. Used to read a modern member's kind
     * from its enclosing group rather than from its own signature-bearing section id.
     */
    private static String groupDescriptor(Element start) {
        for (Element a = start; a != null; a = a.parent()) {
            String combined = (a.id() + " " + a.className()).toLowerCase(Locale.ROOT);
            if (combined.contains("detail")) {
                return combined;
            }
        }
        return null;
    }

    /** Text of the nearest ancestor's direct {@code h3} section heading (legacy), or null. */
    private static String nearestSectionHeading(Element block) {
        for (Element a = block.parent(); a != null; a = a.parent()) {
            for (Element child : a.children()) {
                if (child.tagName().equals("h3")) {
                    String text = child.text().trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    /** First member/type heading text ({@code h2}/{@code h3}/{@code h4}) within {@code block}, or "". */
    private static String headingText(Element block) {
        Element h = block.selectFirst("h2, h3, h4");
        return h == null ? "" : h.text().trim();
    }

    /** The tag notes ({@code @param}/{@code @return}/{@code @throws}/{@code @since}) parsed from a scope. */
    private Notes parseNotes(Element scope, String relativePath, String memberName, List<String> issues) {
        Notes notes = new Notes();
        Element dl = notesList(scope);
        if (dl == null) {
            return notes; // no tag notes is normal, not an issue
        }

        String currentLabel = null;
        int ddsForCurrent = 0;
        for (Element el : dl.children()) {
            String tag = el.tagName();
            if (tag.equals("dt")) {
                if (currentLabel != null && ddsForCurrent == 0 && isConsumedLabel(currentLabel)) {
                    issues.add(relativePath + ": member '" + memberName
                            + "' has a truncated '" + currentLabel + "' tag with no value");
                }
                currentLabel = normalizeLabel(el.text());
                ddsForCurrent = 0;
            } else if (tag.equals("dd") && currentLabel != null) {
                dispatchNote(currentLabel, el, notes);
                ddsForCurrent++;
            }
        }
        if (currentLabel != null && ddsForCurrent == 0 && isConsumedLabel(currentLabel)) {
            issues.add(relativePath + ": member '" + memberName
                    + "' has a truncated '" + currentLabel + "' tag with no value");
        }
        return notes;
    }

    /**
     * The structural tag-notes {@code dl} within {@code scope}: prefer {@code dl.notes}; otherwise
     * fall back to the first {@code dl} that is <b>not</b> nested inside a description {@code .block}
     * (an author-written definition list inside the prose must never be mistaken for the notes list).
     */
    private static Element notesList(Element scope) {
        Element dl = scope.selectFirst("dl.notes");
        if (dl != null) {
            return dl;
        }
        for (Element candidate : scope.select("dl")) {
            if (candidate.closest(BLOCK) == null) {
                return candidate;
            }
        }
        return null;
    }

    private static void dispatchNote(String label, Element dd, Notes notes) {
        switch (label) {
            // Only positional value parameters. "type parameters" (a generic method's <T>) is a
            // separate concept and must not land in the positional param list — ignored this story.
            case "parameters" -> notes.params.add(splitNameProse(dd, Param::new));
            case "returns" -> notes.returns = norm(dd);
            case "throws", "exceptions" ->
                    notes.throwsTypes.add(splitNameProse(dd, ThrownException::new));
            case "since" -> notes.since = norm(dd);
            default -> { /* Type Parameters:, See Also:, Overrides: — out of scope for this story */ }
        }
    }

    /** Labels this extractor actually consumes — a dangling one of these is a real truncation. */
    private static boolean isConsumedLabel(String label) {
        return switch (label) {
            case "parameters", "returns", "throws", "exceptions", "since" -> true;
            default -> false;
        };
    }

    /**
     * Splits a {@code Parameters:}/{@code Throws:} {@code dd} of the form {@code <code>name</code> -
     * prose} into (name, prose). The name is taken from the leading {@code code}/{@code a} element
     * when present; otherwise the text is split on the first {@code " - "} separator.
     */
    private static <T> T splitNameProse(Element dd, java.util.function.BiFunction<String, String, T> ctor) {
        Element codeEl = dd.selectFirst("code, a");
        String name = codeEl == null ? "" : codeEl.text().trim();
        String full = norm(dd);
        String prose;
        if (!name.isEmpty() && full.startsWith(name)) {
            prose = stripLeadingDash(full.substring(name.length()));
        } else {
            int dash = full.indexOf(" - ");
            if (dash >= 0) {
                name = full.substring(0, dash).trim();
                prose = full.substring(dash + 3).trim();
            } else {
                prose = full;
            }
        }
        return ctor.apply(name, prose);
    }

    private static String stripLeadingDash(String s) {
        String t = s.trim();
        if (t.startsWith("-")) {
            t = t.substring(1).trim();
        }
        return t;
    }

    /** The value of a {@code dl}/{@code dl.notes} label within {@code scope} (e.g. type-level Since:). */
    private static String notesValue(Element scope, String labelWanted) {
        Element dl = notesList(scope);
        if (dl == null) {
            return "";
        }
        String currentLabel = null;
        for (Element el : dl.children()) {
            String tag = el.tagName();
            if (tag.equals("dt")) {
                currentLabel = normalizeLabel(el.text());
            } else if (tag.equals("dd") && labelWanted.equals(currentLabel)) {
                return norm(el);
            }
        }
        return "";
    }

    /** First {@code .block} in {@code scope} that is not a deprecation comment and is non-empty, or null. */
    private static Element firstNonDeprecationBlock(Element scope) {
        for (Element b : scope.select(BLOCK)) {
            if (b.selectFirst(DEPRECATED_LABEL) != null || b.selectFirst(DEPRECATION_COMMENT) != null) {
                continue; // this .block is the deprecation notice, not the description
            }
            if (!b.text().trim().isEmpty()) {
                return b;
            }
        }
        return null;
    }

    /** The {@code @deprecated} state within {@code scope}: modern deprecation-block or legacy label. */
    private static Deprecation findDeprecation(Element scope) {
        Element label = scope.selectFirst(DEPRECATED_LABEL);
        Element deprBlock = scope.selectFirst(DEPRECATION_BLOCK);
        if (label == null && deprBlock == null) {
            return Deprecation.NONE;
        }
        Element comment = scope.selectFirst(DEPRECATION_COMMENT);
        if (comment != null) {
            return Deprecation.of(norm(comment));
        }
        // No dedicated comment element: recover the prose rendered as bare text in the deprecation
        // container (the block if present, else the label's parent), minus the label's own text.
        Element container = deprBlock != null ? deprBlock : (label == null ? null : label.parent());
        if (container == null) {
            return Deprecation.of("");
        }
        String full = norm(container);
        String labelText = label == null ? "" : norm(label);
        String prose = !labelText.isEmpty() && full.startsWith(labelText)
                ? full.substring(labelText.length()).trim()
                : full;
        return Deprecation.of(prose);
    }

    private static boolean hasDirectChild(Element parent, String tagName) {
        for (Element child : parent.children()) {
            if (child.tagName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    /** Lowercased, colon-stripped label text (e.g. {@code "Parameters:"} &rarr; {@code "parameters"}). */
    private static String normalizeLabel(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (t.endsWith(":")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /** Whitespace-normalized text of an element (JSoup {@link Element#text()} collapses runs). */
    private static String norm(Element el) {
        return el.text().trim();
    }

    private static ExtractionResult empty(String relativePath, String issue) {
        ExtractedType type =
                new ExtractedType(relativePath, "", "", Deprecation.NONE, List.of());
        return new ExtractionResult(type, List.of(issue));
    }

    /** Mutable accumulator for a member's tag notes while parsing a {@code dl}. */
    private static final class Notes {
        final List<Param> params = new ArrayList<>();
        final List<ThrownException> throwsTypes = new ArrayList<>();
        String returns = "";
        String since = "";
    }
}
