package com.luciadmcp.knowledge.ingest.javadoc;

import java.io.IOException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
            MessageDigest digest = sourceDigest();
            Document doc;
            try (DigestInputStream input = new DigestInputStream(java.nio.file.Files.newInputStream(file), digest)) {
                doc = Jsoup.parse(input, null, "");
            }
            ExtractionResult result = extract(doc, relativePath);
            return new ExtractionResult(result.type(), result.issues(), Optional.of(HexFormat.of().formatHex(digest.digest())));
        } catch (IOException e) {
            return empty(relativePath, "failed to read/parse " + relativePath + ": " + e.getMessage());
        }
    }

    private static MessageDigest sourceDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required by the Java platform", e);
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
        TypeHeader header = TypeHeader.EMPTY;
        List<TypeRef> typeSeeAlso = List.of();
        if (scope != null) {
            Element descBlock = firstNonDeprecationBlock(scope);
            typeDescription = descBlock == null ? "" : norm(descBlock);
            if (typeDescription.isEmpty()) {
                issues.add(relativePath + ": type description is empty");
            }
            typeSince = notesValue(scope, "since");
            typeDeprecation = findDeprecation(scope);
            // Story 2.5 relationship-source references (resolved in pass two, never here — AD-4).
            header = parseTypeHeader(scope, relativePath);
            typeSeeAlso = seeAlsoRefs(scope, relativePath);
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

        ExtractedType type = new ExtractedType(
                relativePath, typeDescription, typeSince, typeDeprecation, members,
                header.superclass(), header.superInterfaces(), header.implementedInterfaces(), typeSeeAlso,
                scope == null || scope.selectFirst(".type-signature, .typeSignature, pre") == null ? "" : norm(scope.selectFirst(".type-signature, .typeSignature, pre")),
                scope == null ? List.of() : parseNotes(scope, relativePath, "type", issues).typeParameters,
                scope == null ? Optional.empty() : actualAnchor(scope));
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

    /** Only anchors physically owned by this element; group/detail headings are not member citations. */
    private static Optional<String> actualAnchor(Element block) {
        if (!block.id().isBlank()) return Optional.of(block.id());
        for (Element child : block.children()) {
            if (child.tagName().equals("a")) {
                String value = child.hasAttr("name") ? child.attr("name") : child.id();
                if (!value.isBlank()) return Optional.of(value);
            }
        }
        Element previous = block.previousElementSibling();
        if (previous == null && block.parent() != null && block.parent().tagName().equals("ul"))
            previous = block.parent().previousElementSibling();
        if (previous != null && previous.tagName().equals("a")) {
            String value = previous.hasAttr("name") ? previous.attr("name") : previous.id();
            if (!value.isBlank() && !List.of("constructor.detail", "method.detail", "field.detail",
                    "enum.constant.detail", "constructor-detail", "method-detail", "field-detail",
                    "enum-constant-detail").contains(value)) return Optional.of(value);
        }
        return Optional.empty();
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

        // Story 2.5 relationship-source references (resolved in pass two, never here — AD-4). The
        // return/field type is the erased leading type of the signature, carrying its link href when
        // the signature rendered the type as a link; overrides/specified-by/see-also come from notes.
        TypeRef returnType = kind == MemberKind.METHOD ? leadingTypeRef(sig, signature, relativePath) : null;
        TypeRef fieldType =
                (kind == MemberKind.FIELD || kind == MemberKind.ENUM_CONSTANT)
                        ? leadingTypeRef(sig, signature, relativePath)
                        : null;

        return new ExtractedMember(kind, name, signature, description,
                notes.params, notes.returns, notes.throwsTypes, notes.since, deprecation,
                returnType, fieldType, notes.overrides, notes.specifiedBy, List.copyOf(notes.seeAlso),
                parameterReferences(sig, signature, relativePath),
                throwsReferences(sig, signature, relativePath, notes.throwsRefs), notes.typeParameters, actualAnchor(block));
    }

    /**
     * The erased leading-type reference of a member: the return type (method) or declared type
     * (field/enum constant). The name is {@link SignatureNormalizer#leadingType(String) the erased
     * type}; its href comes from that position in the signature, independently of annotation links.
     * Returns {@code null} when no leading type
     * is recoverable (a constructor, {@code void}, or a bare declaration) — pass two additionally
     * ignores primitives, which name no documented type.
     */
    private static TypeRef leadingTypeRef(Element sig, String signature, String relativePath) {
        Optional<String> type = SignatureNormalizer.leadingType(signature);
        if (type.isEmpty() || isPrimitiveOrVoid(type.get())) {
            return null; // a constructor, void, or a primitive names no documented type reference
        }
        return positionedSignatureRef(sig, type.get(), relativePath, -1);
    }

    /** Language primitives and {@code void} — never documented types, so never a type reference. */
    private static List<TypeRef> parameterReferences(Element sig, String signature, String relativePath) {
        List<String> tokens = SignatureNormalizer.qualifiedParameterTypes(signature);
        List<TypeRef> result = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) result.add(positionedSignatureRef(sig, tokens.get(i), relativePath, i));
        return List.copyOf(result);
    }

    private static List<TypeRef> throwsReferences(Element sig, String signature, String relativePath, List<TypeRef> notes) {
        List<TypeRef> result = new ArrayList<>();
        List<String> tokens = SignatureNormalizer.qualifiedThrowsTypes(signature);
        for (int i = 0; i < tokens.size(); i++) result.add(positionedSignatureRef(sig, tokens.get(i), relativePath, -2 - i));
        for (TypeRef note : notes) {
            boolean matched = result.stream().anyMatch(ref -> ref.name().equals(note.name())
                    || (!note.name().contains(".") && lastDotSegment(ref.name()).equals(note.name())));
            if (note.hasHref()) {
                result.removeIf(ref -> !ref.hasHref() && ref.name().equals(note.name()));
                result.add(note);
            } else if (!matched) result.add(note);
        }
        return List.copyOf(result);
    }

    /** Mask links before parsing so return, annotation and repeated parameter names cannot steal hrefs. */
    private static TypeRef positionedSignatureRef(Element sig, String token, String relativePath, int slot) {
        if (sig == null) return TypeRef.ofName(token);
        Element masked = sig.clone();
        List<Element> links = new ArrayList<>(sig.select("a[href]"));
        List<Element> maskedLinks = new ArrayList<>(masked.select("a[href]"));
        for (int i = 0; i < maskedLinks.size(); i++) maskedLinks.get(i).text("__linkedType" + i + "__");
        String maskedToken;
        if (slot == -1) maskedToken = SignatureNormalizer.leadingType(norm(masked)).orElse("");
        else if (slot < -1) {
            List<String> thrown = SignatureNormalizer.qualifiedThrowsTypes(norm(masked));
            int index = -2 - slot;
            maskedToken = index < thrown.size() ? thrown.get(index) : "";
        } else {
            List<String> parameters = SignatureNormalizer.qualifiedParameterTypes(norm(masked));
            maskedToken = slot < parameters.size() ? parameters.get(slot) : "";
        }
        String base = maskedToken.replace("[]", "").replace("...", "");
        for (int i = 0; i < links.size(); i++) {
            if (base.equals("__linkedType" + i + "__")) return makeRef(token, links.get(i).attr("href"), relativePath);
        }
        return TypeRef.ofName(token);
    }

    private static TypeRef signatureTypeRef(Element sig, String token, String relativePath) {
        String base = token.replace("[]", "").replace("...", "");
        if (sig != null) {
            for (Element a : sig.select("a[href]")) {
                if (a.closest(".annotations") != null) continue;
                if (a.previousSibling() != null && a.previousSibling().toString().stripTrailing().endsWith("@")) continue;
                String text = a.text().trim();
                if (text.equals(base) || text.equals(lastDotSegment(base))) {
                    return makeRef(token, a.attr("href"), relativePath);
                }
            }
        }
        return TypeRef.ofName(token);
    }

    private static boolean isPrimitiveOrVoid(String name) {
        String base = name;
        while (base.endsWith("[]")) {
            base = base.substring(0, base.length() - 2);
        }
        return switch (base) {
            case "void", "boolean", "byte", "char", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
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
                dispatchNote(currentLabel, el, notes, relativePath);
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

    private static void dispatchNote(String label, Element dd, Notes notes, String relativePath) {
        switch (label) {
            case "type parameters" -> notes.typeParameters.add(splitNameProse(dd, Param::new));
            case "parameters" -> notes.params.add(splitNameProse(dd, Param::new));
            case "returns" -> notes.returns = norm(dd);
            case "throws", "exceptions" -> {
                ThrownException thrown = splitNameProse(dd, ThrownException::new);
                notes.throwsTypes.add(thrown);
                notes.throwsRefs.add(signatureTypeRef(dd, thrown.type(), relativePath));
            }
            case "since" -> notes.since = norm(dd);
            // Story 2.5 relationship-source references. Overrides/Specified By target a specific
            // supertype method — its member link is the first anchor in the dd; both are member-precise
            // and resolve in pass two via the link href. See Also gathers every link in the dd.
            case "overrides" -> notes.overrides = firstLinkRef(dd, relativePath);
            case "specified by" -> notes.specifiedBy = firstLinkRef(dd, relativePath);
            case "see also" -> notes.seeAlso.addAll(allLinkRefs(dd, relativePath));
            default -> { /* Type Parameters: and other notes — out of scope for this story */ }
        }
    }

    /** Labels this extractor actually consumes — a dangling one of these is a real truncation. */
    private static boolean isConsumedLabel(String label) {
        return switch (label) {
            case "type parameters", "parameters", "returns", "throws", "exceptions", "since" -> true;
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
        final List<TypeRef> throwsRefs = new ArrayList<>();
        final List<Param> params = new ArrayList<>();
        final List<Param> typeParameters = new ArrayList<>();
        final List<ThrownException> throwsTypes = new ArrayList<>();
        final List<TypeRef> seeAlso = new ArrayList<>();
        String returns = "";
        String since = "";
        TypeRef overrides;
        TypeRef specifiedBy;
    }

    // --- Story 2.5 relationship-source reference extraction ----------------------------------------

    /** The type header's superclass / super-interfaces / implemented-interfaces references. */
    private record TypeHeader(
            TypeRef superclass, List<TypeRef> superInterfaces, List<TypeRef> implementedInterfaces) {
        static final TypeHeader EMPTY = new TypeHeader(null, List.of(), List.of());
    }

    /**
     * Parses the type declaration header within {@code scope} into its direct relationship-source
     * references. Modern layouts render the header in {@code .type-signature}; legacy JDK 8 renders it
     * as a {@code <pre>} in the description (not inside a {@code .block}). The {@code extends} clause is
     * a class's superclass or an interface's super-interfaces (decided by the {@code interface}
     * keyword); the {@code implements} clause is a class/enum's interfaces. Link hrefs are captured
     * where present; an undocumented header (no signature element) yields {@link TypeHeader#EMPTY}.
     */
    private static TypeHeader parseTypeHeader(Element scope, String relativePath) {
        Element sig = scope.selectFirst(".type-signature, .typeSignature");
        if (sig == null) {
            for (Element pre : scope.select("pre")) {
                if (pre.closest(BLOCK) == null) { // not a prose code sample
                    sig = pre;
                    break;
                }
            }
        }
        if (sig == null) {
            return TypeHeader.EMPTY;
        }
        String text = eraseAngles(norm(sig));
        Map<String, TypeRef> links = signatureLinks(sig, relativePath);

        List<TypeRef> extendsRefs = refsFor(namesAfterKeyword(text, "extends", "implements", "permits"), links);
        List<TypeRef> implementsRefs = refsFor(namesAfterKeyword(text, "implements", "permits"), links);

        if (isInterfaceSignature(text)) {
            // An interface's `extends` clause lists its super-interfaces; it has no `implements`.
            return new TypeHeader(null, extendsRefs, List.of());
        }
        TypeRef superclass = extendsRefs.isEmpty() ? null : extendsRefs.get(0);
        return new TypeHeader(superclass, List.of(), implementsRefs);
    }

    /** Whether the declaration keyword is an interface (covers {@code @interface}), by word boundary. */
    private static boolean isInterfaceSignature(String signatureText) {
        return signatureText.matches("(?s).*\\binterface\\b.*");
    }

    /** Link text &rarr; reference, for every {@code <a href>} in a signature element. */
    private static Map<String, TypeRef> signatureLinks(Element sig, String relativePath) {
        Map<String, TypeRef> links = new HashMap<>();
        for (Element a : sig.select("a[href]")) {
            String key = a.text().trim();
            if (!key.isEmpty()) {
                links.putIfAbsent(key, makeRef(key, a.attr("href"), relativePath));
            }
        }
        return links;
    }

    /** Builds a reference per name, attaching the matching link href (by name) when the header had one. */
    private static List<TypeRef> refsFor(List<String> names, Map<String, TypeRef> links) {
        List<TypeRef> refs = new ArrayList<>();
        for (String name : names) {
            String simple = lastDotSegment(name);
            TypeRef linked = links.get(name);
            if (linked == null) {
                linked = links.get(simple);
            }
            refs.add(linked != null ? new TypeRef(name, linked.relativePath(), linked.anchor(),
                    linked.originalLocator(), linked.locatorKind())
                    : TypeRef.ofName(name));
        }
        return refs;
    }

    /**
     * The comma-separated type names following {@code keyword} (as a standalone word) up to the first
     * {@code stopWords} keyword or the end. Returns an empty list if the keyword is absent.
     */
    private static List<String> namesAfterKeyword(String text, String keyword, String... stopWords) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\b" + keyword + "\\b").matcher(text);
        if (!m.find()) {
            return List.of();
        }
        String rest = text.substring(m.end());
        int cut = rest.length();
        for (String stop : stopWords) {
            java.util.regex.Matcher s =
                    java.util.regex.Pattern.compile("\\b" + stop + "\\b").matcher(rest);
            if (s.find() && s.start() < cut) {
                cut = s.start();
            }
        }
        rest = rest.substring(0, cut);
        List<String> names = new ArrayList<>();
        for (String part : rest.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                // Defensive: a clause segment should be a single token post generic-erase; keep the
                // last whitespace-delimited token so stray modifiers cannot leak in.
                int sp = name.lastIndexOf(' ');
                if (sp >= 0) {
                    name = name.substring(sp + 1).trim();
                }
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** The type-level {@code See Also} references gathered from the notes list in {@code scope}. */
    private static List<TypeRef> seeAlsoRefs(Element scope, String relativePath) {
        Element dl = notesList(scope);
        if (dl == null) {
            return List.of();
        }
        List<TypeRef> refs = new ArrayList<>();
        String currentLabel = null;
        for (Element el : dl.children()) {
            String tag = el.tagName();
            if (tag.equals("dt")) {
                currentLabel = normalizeLabel(el.text());
            } else if (tag.equals("dd") && "see also".equals(currentLabel)) {
                refs.addAll(allLinkRefs(el, relativePath));
            }
        }
        return refs;
    }

    /** The reference for the first {@code <a href>} in {@code dd}, or {@code null} if none. */
    private static TypeRef firstLinkRef(Element dd, String relativePath) {
        Element a = dd.selectFirst("a[href]");
        return a == null ? null : makeRef(a.text().trim(), a.attr("href"), relativePath);
    }

    /** References for every {@code <a href>} in {@code dd}, in document order. */
    private static List<TypeRef> allLinkRefs(Element dd, String relativePath) {
        List<TypeRef> refs = new ArrayList<>();
        for (Element a : dd.select("a[href]")) {
            refs.add(makeRef(a.text().trim(), a.attr("href"), relativePath));
        }
        return refs;
    }

    /**
     * Builds a reference from a documented name and its (possibly absent) href, resolving the href to
     * a root-relative path against the referring {@code pageRelativePath} so it matches the path the
     * catalog persists. External and escaping hrefs keep their original locators and classification;
     * a pure {@code #fragment} targets the referring page itself.
     */
    private static TypeRef makeRef(String name, String href, String pageRelativePath) {
        if (href == null || href.isBlank()) {
            return TypeRef.ofName(name);
        }
        if (href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || href.startsWith("//")) {
            return new TypeRef(name, "", "", href, TypeRef.LocatorKind.EXTERNAL);
        }
        if (href.startsWith("/") || href.contains("\\")) {
            return new TypeRef(name, "", "", href, TypeRef.LocatorKind.ROOT_ESCAPE);
        }
        int hash = href.indexOf('#');
        String pathPart = hash >= 0 ? href.substring(0, hash) : href;
        String anchor = hash >= 0 ? href.substring(hash + 1) : "";
        String targetPath = pathPart.isEmpty()
                ? pageRelativePath
                : resolveRelative(parentDir(pageRelativePath), pathPart);
        return new TypeRef(name, targetPath, anchor, href,
                (targetPath.equals("..") || targetPath.startsWith("../")) ? TypeRef.LocatorKind.ROOT_ESCAPE : TypeRef.LocatorKind.LOCAL);
    }

    /** The parent directory of a {@code /}-separated relative path, or {@code ""} for a top-level path. */
    private static String parentDir(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? "" : relativePath.substring(0, slash);
    }

    /**
     * Resolves a {@code /}-separated relative path against {@code baseDir}, collapsing {@code .} and
     * {@code ..} segments — the offline, deterministic equivalent of resolving a page-relative href to
     * a root-relative path.
     */
    private static String resolveRelative(String baseDir, String rel) {
        List<String> segments = new ArrayList<>();
        if (!baseDir.isEmpty()) {
            for (String s : baseDir.split("/")) {
                if (!s.isEmpty()) {
                    segments.add(s);
                }
            }
        }
        for (String s : rel.split("/")) {
            if (s.isEmpty() || s.equals(".")) {
                continue;
            }
            if (s.equals("..")) {
                if (!segments.isEmpty() && !segments.get(segments.size() - 1).equals("..")) {
                    segments.remove(segments.size() - 1);
                } else {
                    segments.add("..");
                }
            } else {
                segments.add(s);
            }
        }
        return String.join("/", segments);
    }

    /** The last dotted segment of a possibly-qualified name ({@code com.example.Base} &rarr; {@code Base}). */
    private static String lastDotSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** Removes balanced {@code <...>} generic-argument groups from text at any nesting depth. */
    private static String eraseAngles(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (depth > 0) {
                    depth--;
                }
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
