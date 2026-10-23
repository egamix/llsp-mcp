package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * Field extraction produces equal fields for modern (JDK 11–16, JDK 17+) and legacy (JDK 8) layouts
 * over the same API surface, and degrades gracefully on broken/incomplete markup (Story 2.2, FR-5).
 * Fixtures are synthetic (no proprietary content).
 */
class JavadocFieldExtractorTest {

    private final JavadocFieldExtractor extractor = new JavadocFieldExtractor();

    // A single relative path used for all three layout fixtures so equality compares fields only,
    // not the (necessarily different) on-disk fixture locations.
    private static final String PATH = "com/example/Widget.html";

    @Test
    void fileSourceHashMatchesTheExactRawBytes() throws Exception {
        var file = fixture("jdk17/Widget.html");
        var result = extractor.extract(file, PATH);
        assertEquals(com.luciadmcp.knowledge.domain.ContentHashes.sourceHash(java.nio.file.Files.readAllBytes(file)),
                result.sourceHash().orElseThrow());
    }

    // --- AC 1 (headline): modern and legacy extract equal fields over the same API surface ---------

    @Test
    void modernAndLegacyLayoutsExtractEqualFields() throws Exception {
        ExtractionResult jdk8 = extractor.extract(fixture("jdk8/Widget.html"), PATH);
        ExtractionResult jdk11 = extractor.extract(fixture("jdk11/Widget.html"), PATH);
        ExtractionResult jdk17 = extractor.extract(fixture("jdk17/Widget.html"), PATH);

        // No issues on well-formed fixtures.
        assertTrue(jdk8.issues().isEmpty(), () -> "jdk8 issues: " + jdk8.issues());
        assertTrue(jdk11.issues().isEmpty(), () -> "jdk11 issues: " + jdk11.issues());
        assertTrue(jdk17.issues().isEmpty(), () -> "jdk17 issues: " + jdk17.issues());

        // The whole extracted record is equal across all three generator eras — except the raw
        // signature string, which legitimately differs across eras (simple vs fully-qualified param
        // types, U+200B word breaks). Making that raw text era-independent is Story 2.3's canonical
        // minter, proven in CanonicalSymbolMinterTest; here we compare every other field.
        assertEquals(withoutSignatures(jdk8.type()), withoutSignatures(jdk11.type()),
                "JDK 8 legacy vs JDK 11–16 modern must match (signature excluded)");
        assertEquals(withoutSignatures(jdk11.type()), withoutSignatures(jdk17.type()),
                "JDK 11–16 modern vs JDK 17+ modern must match (signature excluded)");
    }

    /** A copy of {@code type} with every member signature blanked, for era comparison. */
    private static ExtractedType withoutSignatures(ExtractedType type) {
        List<ExtractedMember> members = type.members().stream()
                .map(m -> new ExtractedMember(m.kind(), m.name(), "", m.description(), m.params(),
                        m.returns(), m.throwsTypes(), m.since(), m.deprecation()))
                .toList();
        return new ExtractedType(type.relativePath(), type.description(), type.since(),
                type.deprecation(), members);
    }

    // --- AC 3 + type-level fields: only TYPE pages are mined; type description/since/deprecated ------

    @Test
    void typeLevelFieldsAreExtracted() throws Exception {
        ExtractedType type = extractor.extract(fixture("jdk17/Widget.html"), PATH).type();

        assertEquals(PATH, type.relativePath());
        assertEquals("A synthetic widget type.", type.description());
        assertEquals("1.0", type.since());
        assertEquals(new Deprecation(true, "Use NewWidget instead."), type.deprecation());
    }

    @Test
    void membersAreExtractedInSourceOrderWithCorrectKinds() throws Exception {
        List<ExtractedMember> members = extractor.extract(fixture("jdk8/Widget.html"), PATH).type().members();

        assertEquals(9, members.size());
        assertEquals(
                List.of("Widget", "doThing", "oldThing", "contains", "contains", "map", "merge", "scale", "MAX"),
                members.stream().map(ExtractedMember::name).toList());
        assertEquals(
                List.of(MemberKind.CONSTRUCTOR, MemberKind.METHOD, MemberKind.METHOD, MemberKind.METHOD,
                        MemberKind.METHOD, MemberKind.METHOD, MemberKind.METHOD, MemberKind.METHOD,
                        MemberKind.FIELD),
                members.stream().map(ExtractedMember::kind).toList());
    }

    @Test
    void enumConstantsClassifyConsistentlyAcrossEras() throws Exception {
        String path = "com/example/Direction.html";
        ExtractionResult jdk8 = extractor.extract(fixture("jdk8/Direction.html"), path);
        ExtractionResult jdk11 = extractor.extract(fixture("jdk11/Direction.html"), path);
        ExtractionResult jdk17 = extractor.extract(fixture("jdk17/Direction.html"), path);

        assertTrue(jdk8.issues().isEmpty(), () -> "jdk8 issues: " + jdk8.issues());
        assertTrue(jdk11.issues().isEmpty(), () -> "jdk11 issues: " + jdk11.issues());
        assertTrue(jdk17.issues().isEmpty(), () -> "jdk17 issues: " + jdk17.issues());

        for (var result : List.of(jdk8, jdk11, jdk17)) {
            assertEquals(jdk8.type().description(), result.type().description());
            assertEquals(jdk8.type().members().stream().map(m -> List.of(m.kind(), m.name(), m.signature(), m.description())).toList(),
                    result.type().members().stream().map(m -> List.of(m.kind(), m.name(), m.signature(), m.description())).toList());
        }
        assertTrue(jdk8.type().members().get(0).anchor().isEmpty());
        assertEquals("NORTH", jdk17.type().members().get(0).anchor().orElseThrow());

        assertEquals(List.of("NORTH", "SOUTH"),
                jdk11.type().members().stream().map(ExtractedMember::name).toList());
        assertEquals(List.of(MemberKind.ENUM_CONSTANT, MemberKind.ENUM_CONSTANT),
                jdk11.type().members().stream().map(ExtractedMember::kind).toList(),
                "enum constants must classify as ENUM_CONSTANT, not METHOD");
    }

    @Test
    void methodWhoseNameContainsFieldOrConstructorIsNotMisclassified() {
        // Regression for the member-kind self-id match: on a modern page the member's own
        // section.detail id IS the signature, so a method like getField(int)/getConstructor() must
        // take its kind from the enclosing details group ("Method Detail"), never from its own id.
        Document modern = Jsoup.parse(
                "<html><body class=\"class-declaration-page\"><main>"
                        + "<section class=\"class-description\" id=\"class-description\">"
                        + "<div class=\"block\">A type.</div></section>"
                        + "<section class=\"method-details\" id=\"method-detail\"><h2>Method Detail</h2>"
                        + "<ul class=\"member-list\">"
                        + "<li><section class=\"detail\" id=\"getField(int)\"><h3>getField</h3>"
                        + "<div class=\"member-signature\">int getField(int i)</div>"
                        + "<div class=\"block\">Gets the field.</div></section></li>"
                        + "<li><section class=\"detail\" id=\"getConstructor()\"><h3>getConstructor</h3>"
                        + "<div class=\"member-signature\">Object getConstructor()</div>"
                        + "<div class=\"block\">Gets it.</div></section></li>"
                        + "</ul></section></main></body></html>");

        List<ExtractedMember> members = extractor.extract(modern, PATH).type().members();
        assertEquals(List.of(MemberKind.METHOD, MemberKind.METHOD),
                members.stream().map(ExtractedMember::kind).toList());
    }

    @Test
    void methodTagFieldsAreExtracted() throws Exception {
        ExtractedMember doThing = memberNamed(extractor.extract(fixture("jdk11/Widget.html"), PATH), "doThing");

        assertEquals(MemberKind.METHOD, doThing.kind());
        assertEquals("void doThing(int count, String label)", doThing.signature());
        assertEquals("Does the thing.", doThing.description());
        assertEquals(List.of(new Param("count", "how many"), new Param("label", "the label")),
                doThing.params());
        assertEquals("the result", doThing.returns());
        assertEquals(List.of(new ThrownException("IOException", "if it fails")), doThing.throwsTypes());
        assertEquals("1.1", doThing.since());
        assertEquals(Deprecation.NONE, doThing.deprecation());
    }

    @Test
    void deprecatedMemberCarriesItsComment() throws Exception {
        ExtractedMember oldThing = memberNamed(extractor.extract(fixture("jdk17/Widget.html"), PATH), "oldThing");

        assertEquals(new Deprecation(true, "Use doThing instead."), oldThing.deprecation());
        assertEquals("Legacy operation.", oldThing.description());
    }

    @Test
    void nonTypePageIsSkippedWithAnIssueNotMinedForMembers() {
        Document pkg = Jsoup.parse(
                "<html><body class=\"package-declaration-page\">"
                        + "<h1 class=\"title\">Package com.example</h1></body></html>");

        ExtractionResult result = extractor.extract(pkg, "com/example/package-summary.html");

        assertTrue(result.type().members().isEmpty(), "a non-type page must not yield members");
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("non-TYPE")),
                "the skip must be recorded as an issue: " + result.issues());
    }

    // --- AC 5 / AC 6: broken/incomplete markup degrades into explicit issues, never a throw ---------

    @Test
    void brokenModernPageDegradesWithIssuesAndDoesNotThrow() throws Exception {
        ExtractionResult result = extractor.extract(fixture("broken/modern-broken.html"), "com/example/Broken.html");

        // The nameless member is skipped; the recoverable one is still extracted.
        List<String> names = result.type().members().stream().map(ExtractedMember::name).toList();
        assertEquals(List.of("lonely"), names);
        assertEquals("", memberNamed(result, "lonely").description());

        assertTrue(hasIssue(result, "type description is empty"));
        assertTrue(hasIssue(result, "member 'lonely' is missing its description"));
        assertTrue(hasIssue(result, "truncated"));
        assertTrue(hasIssue(result, "no recoverable name"));
    }

    @Test
    void brokenLegacyPageDegradesWithIssuesAndDoesNotThrow() throws Exception {
        ExtractionResult result = extractor.extract(fixture("broken/legacy-broken.html"), "pkg/BrokenLegacy.html");

        List<String> names = result.type().members().stream().map(ExtractedMember::name).toList();
        assertEquals(List.of("ghost"), names);
        assertTrue(hasIssue(result, "type description is empty"));
        assertTrue(hasIssue(result, "member 'ghost' is missing its description"));
        assertTrue(hasIssue(result, "truncated"));
    }

    // --- AC 7: deterministic and stable ------------------------------------------------------------

    @Test
    void extractionIsDeterministic() throws Exception {
        Path fixture = fixture("jdk17/Widget.html");
        assertEquals(extractor.extract(fixture, PATH), extractor.extract(fixture, PATH),
                "same input -> same result");
    }

    // --- Input validation --------------------------------------------------------------------------

    @Test
    void nullAndBlankInputsAreRejected() {
        Document doc = Jsoup.parse("<html><body class=\"class-declaration-page\"></body></html>");
        assertThrows(IllegalArgumentException.class, () -> extractor.extract((Document) null, PATH));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(doc, "  "));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract((Path) null, PATH));
    }

    // --- helpers -----------------------------------------------------------------------------------

    private static ExtractedMember memberNamed(ExtractionResult result, String name) {
        return result.type().members().stream()
                .filter(m -> m.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no member named " + name));
    }

    private static boolean hasIssue(ExtractionResult result, String fragment) {
        return result.issues().stream().anyMatch(i -> i.contains(fragment));
    }

    private Path fixture(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource("javadoc-fixtures/extract/" + name);
        assertNotNull(url, "missing test fixture: " + name);
        return Path.of(url.toURI());
    }
}
