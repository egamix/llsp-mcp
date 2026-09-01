package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pass-one facade {@link JavadocCatalogSource}: it preserves discovery order, merges each page's
 * extraction and minting issues, propagates discovery warnings, and returns an empty result for an
 * empty tree. Fixtures are synthetic HTML (no proprietary content).
 */
class JavadocCatalogSourceTest {

    private final JavadocCatalogSource source = new JavadocCatalogSource();

    private static final String CLEAN = """
            <!DOCTYPE html><html><head><title>T</title></head>
            <body class="class-declaration-page">
            <main>
            <h1 class="title">Class T</h1>
            <section class="class-description"><div class="block">A synthetic type.</div></section>
            </main></body></html>
            """;

    // A type page with no description section (extraction issue) and a member with no signature
    // (minting issue) — used to prove the two issue channels are merged per page.
    private static final String DOUBLE_ISSUE = """
            <!DOCTYPE html><html><head><title>Beta</title></head>
            <body class="class-declaration-page">
            <main>
            <h1 class="title">Class Beta</h1>
            <section class="method-details" id="method-detail">
            <ul class="member-list"><li>
            <section class="detail" id="doThing">
            <h3>doThing</h3>
            <div class="block">Does the thing.</div>
            </section>
            </li></ul>
            </section>
            </main></body></html>
            """;

    private static void write(Path root, String relativePath, String html) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html, StandardCharsets.UTF_8);
    }

    /** Pages come back in discovery order (ascending relative path), independent of write order. */
    @Test
    void preservesDiscoveryOrder(@TempDir Path tmp) throws Exception {
        write(tmp, "com/example/Zeta.html", CLEAN);
        write(tmp, "com/example/Alpha.html", CLEAN);

        List<String> paths = source.ingest(tmp).pages().stream()
                .map(JavadocCatalogSource.PageResult::relativePath)
                .toList();
        assertEquals(List.of("com/example/Alpha.html", "com/example/Zeta.html"), paths,
                "pages must be in ascending relative-path (discovery) order");
    }

    /** A page's extraction issues and minting issues are merged into one per-page issue list. */
    @Test
    void mergesExtractionAndMintingIssuesPerPage(@TempDir Path tmp) throws Exception {
        write(tmp, "com/example/Beta.html", DOUBLE_ISSUE);

        JavadocCatalogSource.Result result = source.ingest(tmp);
        assertEquals(1, result.pages().size());
        List<String> issues = result.pages().get(0).issues();

        assertTrue(issues.stream().anyMatch(i -> i.contains("type-description")),
                () -> "expected the extraction issue (missing description): " + issues);
        assertTrue(issues.stream().anyMatch(i -> i.contains("unparseable signature")),
                () -> "expected the minting issue (unparseable signature): " + issues);
        // The type record and the degraded member record are still minted (AD-4: recorded, not dropped).
        assertFalse(result.pages().get(0).records().isEmpty());
    }

    /** Discovery warnings (here: a search index disagreeing with classification) propagate through. */
    @Test
    void propagatesDiscoveryWarnings(@TempDir Path tmp) throws Exception {
        write(tmp, "com/example/Widget.html", CLEAN);
        // A type-search-index.js declaring a type page that does not exist / is not classified TYPE.
        Files.writeString(
                tmp.resolve("type-search-index.js"),
                "typeSearchIndex = [{\"p\":\"com.example\",\"l\":\"Ghost\"}];updateSearchResults();",
                StandardCharsets.UTF_8);

        List<String> warnings = source.ingest(tmp).discoveryWarnings();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("com/example/Ghost.html")),
                () -> "discovery warnings must propagate through the facade: " + warnings);
    }

    /** An empty tree yields no pages and no warnings. */
    @Test
    void emptyTreeYieldsEmptyResult(@TempDir Path tmp) {
        JavadocCatalogSource.Result result = source.ingest(tmp);
        assertTrue(result.pages().isEmpty(), "no pages for an empty tree");
        assertTrue(result.discoveryWarnings().isEmpty(), "no warnings for an empty tree");
    }
}
