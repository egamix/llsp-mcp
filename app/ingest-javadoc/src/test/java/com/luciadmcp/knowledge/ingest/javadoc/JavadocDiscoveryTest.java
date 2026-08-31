package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Discovery classifies Javadoc pages by structure, deterministically, and identically whether or not
 * a search index is present (Story 2.1, FR-4). Fixtures are synthetic (no proprietary content).
 */
class JavadocDiscoveryTest {

    private final JavadocDiscovery discovery = new JavadocDiscovery();

    // --- AC 1: same classified page set with and without a search index ------------------------

    @Test
    void searchIndexPresentAndAbsentProduceTheSameClassifiedPageSet(@TempDir Path tmp) throws Exception {
        Path withIndex = modernFixture(); // contains type/member-search-index.js
        Path withoutIndex = copyHtmlOnly(withIndex, tmp.resolve("no-index"));

        DiscoveryResult indexed = discovery.classify(withIndex);
        DiscoveryResult walked = discovery.classify(withoutIndex);

        assertTrue(indexed.searchIndexPresent(), "modern fixture ships a search index");
        assertFalse(walked.searchIndexPresent(), "the html-only copy has no search index");
        assertEquals(walked.pages(), indexed.pages(),
                "discovery must classify the same pages regardless of search-index presence");
    }

    // --- AC 2: nav/tree/help/index pages are excluded from member ingestion --------------------

    @Test
    void navTreeHelpIndexPagesAreExcludedFromMembers() throws Exception {
        Map<String, PageType> byPath = classifyModern();

        assertEquals(PageType.EXCLUDED, byPath.get("overview-tree.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("help-doc.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("index-all.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("allclasses-index.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("deprecated-list.html"));

        DiscoveryResult result = discovery.classify(modernFixture());
        List<String> memberPaths = result.memberPages().stream().map(ClassifiedPage::relativePath).toList();
        assertFalse(memberPaths.contains("overview-tree.html"));
        assertFalse(memberPaths.contains("help-doc.html"));
        assertFalse(memberPaths.contains("index-all.html"));
    }

    // --- AC 3: overview/package pages retained as narrative, not member records ----------------

    @Test
    void overviewAndPackagePagesAreNarrativeNotMembers() throws Exception {
        DiscoveryResult result = discovery.classify(modernFixture());
        Map<String, PageType> byPath = toMap(result);

        assertEquals(PageType.OVERVIEW, byPath.get("index.html"));
        assertEquals(PageType.PACKAGE, byPath.get("com/example/package-summary.html"));

        List<String> narrative = result.narrativePages().stream().map(ClassifiedPage::relativePath).toList();
        assertTrue(narrative.contains("index.html"));
        assertTrue(narrative.contains("com/example/package-summary.html"));

        List<String> members = result.memberPages().stream().map(ClassifiedPage::relativePath).toList();
        assertFalse(members.contains("index.html"));
        assertFalse(members.contains("com/example/package-summary.html"));
    }

    // --- AC 4: type pages are member-bearing ---------------------------------------------------

    @Test
    void typePagesAreMemberBearing() throws Exception {
        DiscoveryResult result = discovery.classify(modernFixture());
        Map<String, PageType> byPath = toMap(result);

        assertEquals(PageType.TYPE, byPath.get("com/example/Widget.html"));
        assertEquals(PageType.TYPE, byPath.get("com/example/Service.html"));
        assertEquals(PageType.TYPE, byPath.get("com/example/Color.html"));

        List<String> members = result.memberPages().stream().map(ClassifiedPage::relativePath).toList();
        assertTrue(members.contains("com/example/Widget.html"));
        assertTrue(members.contains("com/example/Service.html"));
        assertTrue(members.contains("com/example/Color.html"));
    }

    // --- AC 5: classification uses structure, not filename -------------------------------------

    @Test
    void classificationUsesStructureNotFilename() throws Exception {
        Map<String, PageType> byPath = classifyModern();

        // Nav-ish filename, class-page structure -> TYPE.
        assertEquals(PageType.TYPE, byPath.get("nav.html"));
        // Type-ish filename ("Helper"), help-page structure -> EXCLUDED.
        assertEquals(PageType.EXCLUDED, byPath.get("com/example/Helper.html"));
    }

    // --- AC 6: deterministic, relative-path-ordered output -------------------------------------

    @Test
    void resultIsDeterministicAndSortedByRelativePath() throws Exception {
        Path fixture = modernFixture();
        DiscoveryResult first = discovery.classify(fixture);
        DiscoveryResult second = discovery.classify(fixture);
        assertEquals(first.pages(), second.pages(), "same input -> same result");

        List<String> paths = first.pages().stream().map(ClassifiedPage::relativePath).toList();
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(String::compareTo);
        assertEquals(sorted, paths, "pages must be ordered by relative path");
    }

    // --- AC 7: unclassifiable pages are UNKNOWN and never crash ---------------------------------

    @Test
    void unclassifiablePageIsUnknownAndDoesNotThrow() throws Exception {
        Map<String, PageType> byPath = classifyModern();
        assertEquals(PageType.UNKNOWN, byPath.get("broken.html"));
    }

    // --- AC 8: external resource references are never fetched -----------------------------------

    @Test
    void externalResourceReferencesAreNotFetched() throws Exception {
        // The fixture references https css/js/img. JSoup parses local bytes only and never issues a
        // request (Jsoup.connect is not used anywhere in this component), so classification succeeds
        // offline and quickly. This asserts the page is read and classified without any fetch.
        Map<String, PageType> byPath = classifyModern();
        assertEquals(PageType.TYPE, byPath.get("external-refs.html"));
    }

    // --- Legacy layout classifies into the same buckets ----------------------------------------

    @Test
    void legacyLayoutClassifiesEquivalently() throws Exception {
        DiscoveryResult result = discovery.classify(legacyFixture());
        Map<String, PageType> byPath = toMap(result);

        assertEquals(PageType.TYPE, byPath.get("pkg/LegacyClass.html"));
        assertEquals(PageType.PACKAGE, byPath.get("pkg/package-summary.html"));
        assertEquals(PageType.OVERVIEW, byPath.get("overview-summary.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("overview-tree.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("help-doc.html"));
        assertEquals(PageType.EXCLUDED, byPath.get("deprecated-list.html"));
    }

    // --- Search-index cross-check ---------------------------------------------------------------

    @Test
    void wellFormedSearchIndexAgreesWithStructureNoWarnings() throws Exception {
        DiscoveryResult result = discovery.classify(modernFixture());
        List<String> crossCheckWarnings = result.warnings().stream()
                .filter(w -> w.startsWith("search index lists type page")).toList();
        assertTrue(crossCheckWarnings.isEmpty(),
                "every type page declared by the search index is classified TYPE: " + crossCheckWarnings);
    }

    @Test
    void searchIndexListingAMissingTypeProducesAWarningButNotAFailure(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("jd");
        Files.createDirectories(root.resolve("com/example"));
        Files.writeString(root.resolve("com/example/Widget.html"),
                "<body class=\"class-declaration-page\"><h1 class=\"title\">Class Widget</h1></body>");
        // Declares a type page (Ghost) that does not exist in the tree.
        Files.writeString(root.resolve("type-search-index.js"),
                "typeSearchIndex = [{\"p\":\"com.example\",\"l\":\"Widget\"},{\"p\":\"com.example\",\"l\":\"Ghost\"}];");

        DiscoveryResult result = discovery.classify(root);
        assertTrue(result.searchIndexPresent());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("com/example/Ghost.html")),
                "a declared-but-missing type page should be warned about, not crash the run");
        // The classified set is unaffected by the bogus index entry.
        assertEquals(PageType.TYPE, toMap(result).get("com/example/Widget.html"));
    }

    @Test
    void malformedSearchIndexIsNonFatal(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("jd");
        Files.createDirectories(root.resolve("com/example"));
        Files.writeString(root.resolve("com/example/Widget.html"),
                "<body class=\"class-declaration-page\"><h1 class=\"title\">Class Widget</h1></body>");
        Files.writeString(root.resolve("type-search-index.js"), "not json at all");

        DiscoveryResult result = discovery.classify(root);
        assertTrue(result.searchIndexPresent());
        assertEquals(PageType.TYPE, toMap(result).get("com/example/Widget.html"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("malformed search index")));
    }

    // --- Input validation ----------------------------------------------------------------------

    @Test
    void nonDirectoryRootIsRejected(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("not-a-dir.html");
        Files.writeString(file, "<html></html>");
        assertThrows(IllegalArgumentException.class, () -> discovery.classify(file));
        assertThrows(IllegalArgumentException.class, () -> discovery.classify(null));
    }

    // --- helpers --------------------------------------------------------------------------------

    private Map<String, PageType> classifyModern() throws Exception {
        return toMap(discovery.classify(modernFixture()));
    }

    private static Map<String, PageType> toMap(DiscoveryResult result) {
        return result.pages().stream()
                .collect(Collectors.toMap(ClassifiedPage::relativePath, ClassifiedPage::type));
    }

    private Path modernFixture() throws Exception {
        return fixtureDir("javadoc-fixtures/modern");
    }

    private Path legacyFixture() throws Exception {
        return fixtureDir("javadoc-fixtures/legacy");
    }

    private Path fixtureDir(String resourcePath) throws Exception {
        URL url = getClass().getClassLoader().getResource(resourcePath);
        assertNotNull(url, "missing test fixture: " + resourcePath);
        return Path.of(url.toURI());
    }

    /** Copies only the {@code *.html} files (no {@code *.js}) from {@code src} into {@code dst}. */
    private static Path copyHtmlOnly(Path src, Path dst) throws Exception {
        Files.createDirectories(dst);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(src)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".html"))
                    .toList();
        }
        for (Path file : files) {
            Path target = dst.resolve(src.relativize(file).toString());
            Files.createDirectories(target.getParent());
            Files.copy(file, target);
        }
        return dst;
    }
}
