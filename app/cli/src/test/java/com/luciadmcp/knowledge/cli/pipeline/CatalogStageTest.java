package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.catalog.sqlite.SqliteCatalogStore;
import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.domain.CatalogRecord;
import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real CATALOG stage (Story 2.4): it runs discover&rarr;extract&rarr;mint, scopes records to the
 * SDK version, attaches the relative path + derived anchor, and submits logical tuples through the
 * {@link CatalogStore} port. It maps 2.1&ndash;2.3 issues onto recoverable failures and a store
 * identity clash onto a build-invalidating failure. An absent {@code --sdk-version} does not abort:
 * records are scoped under a default label. Fixtures are synthetic HTML.
 */
class CatalogStageTest {

    // --- a synthetic modern type page with one clean method -----------------------------------------
    private static final String CLEAN_PAGE = """
            <!DOCTYPE html><html><head><title>Widget</title></head>
            <body class="class-declaration-page">
            <main>
            <h1 class="title">Class Widget</h1>
            <section class="class-description"><div class="block">A synthetic widget.</div></section>
            <section class="method-details" id="method-detail">
            <ul class="member-list"><li>
            <section class="detail" id="doThing(int)">
            <h3>doThing</h3>
            <div class="member-signature">public void doThing(int n)</div>
            <div class="block">Does the thing.</div>
            </section>
            </li></ul>
            </section>
            </main></body></html>
            """;

    // --- a modern type page missing its description section (a recoverable extraction issue) --------
    private static final String NO_DESCRIPTION_PAGE = """
            <!DOCTYPE html><html><head><title>Broken</title></head>
            <body class="class-declaration-page">
            <main><h1 class="title">Class Broken</h1></main>
            </body></html>
            """;

    private static Path writeType(Path root, String relativePath, String html) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return file;
    }

    private static BuildContext context(Path tmp) {
        return context(tmp, "2024.0");
    }

    private static BuildContext context(Path tmp, String sdkVersion) {
        return new BuildContext("b-test", "digest-test", tmp.resolve("staging"), sdkVersion);
    }

    /** Headline: discover→extract→mint→submit, each record version-scoped with a resolvable anchor. */
    @Test
    void mintsAndSubmitsVersionScopedRecordsWithAnchors(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeType(javadoc, "com/example/Widget.html", CLEAN_PAGE);

        RecordingStore store = new RecordingStore();
        CatalogStage stage = new CatalogStage(
                javadoc, new JavadocCatalogSource(), fixedStore(store));

        List<StageFailure> failures = new ArrayList<>();
        stage.execute(context(tmp), failures::add);

        assertTrue(failures.isEmpty(), () -> "a clean page should produce no failures: " + failures);
        assertEquals(2, store.submitted.size(), "the type record plus its one method must be submitted");

        CatalogRecord type = byKey(store, "com.example.Widget");
        assertEquals("2024.0", type.sdkVersion());
        assertEquals("TYPE", type.kind());
        assertEquals("com/example/Widget.html", type.relativePath());
        assertEquals("", type.anchor(), "a type-level record has no member anchor");

        CatalogRecord method = byKey(store, "com.example.Widget#doThing(int)");
        assertEquals("METHOD", method.kind());
        assertEquals("com/example/Widget.html", method.relativePath());
        assertEquals("doThing(int)", method.anchor(), "a method anchors on its modern member id");
    }

    @Test
    void constructorAndClassNamedMethodPersistWithDistinctSourceLocators(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeType(javadoc, "com/example/Widget.html", """
                <!DOCTYPE html><html><body class="class-declaration-page"><main>
                <h1 class="title">Class Widget</h1>
                <section class="class-description"><div class="block">A widget.</div></section>
                <section class="constructor-details" id="constructor-detail"><ul class="member-list"><li>
                <section class="detail" id="&lt;init&gt;(int)"><h3>Widget</h3>
                <div class="member-signature">public Widget(int n)</div>
                <div class="block">Creates a widget.</div></section></li></ul></section>
                <section class="method-details" id="method-detail"><ul class="member-list"><li>
                <section class="detail" id="Widget(int)"><h3>Widget</h3>
                <div class="member-signature">public void Widget(int n)</div>
                <div class="block">Updates a widget.</div></section></li></ul></section>
                </main></body></html>
                """);
        Path db = tmp.resolve("catalog.sqlite");
        List<StageFailure> failures = new ArrayList<>();
        new CatalogStage(javadoc, new JavadocCatalogSource(), ctx -> new SqliteCatalogStore(db))
                .execute(context(tmp), failures::add);
        assertTrue(failures.isEmpty(), () -> failures.toString());
        try (SqliteCatalogStore store = new SqliteCatalogStore(db)) {
            assertEquals(3, store.allRecords().size());
            StoredCatalogRecord constructor = store.findByKey("2024.0", "com.example.Widget#Widget(int)").orElseThrow();
            StoredCatalogRecord method = store.findByKey("2024.0", "com.example.Widget#method:Widget(int)").orElseThrow();
            assertEquals("<init>(int)", constructor.record().anchor());
            assertEquals("Widget(int)", method.record().anchor());
            org.junit.jupiter.api.Assertions.assertNotEquals(constructor.recordId(), method.recordId());
            var index = RelationshipsStage.ResolutionIndex.build(store.allRecords(), "2024.0");
            for (StoredCatalogRecord member : List.of(constructor, method)) {
                var reference = new com.luciadmcp.knowledge.ingest.javadoc.TypeRef(
                        "Widget", "com/example/Widget.html", member.record().anchor());
                var resolution = index.resolve("com.example.Widget", reference,
                        com.luciadmcp.knowledge.domain.RelationshipType.SEE_ALSO, "2024.0");
                assertEquals(com.luciadmcp.knowledge.domain.ReferenceResolution.Status.EXACT, resolution.status());
                assertEquals(member.recordId(), resolution.selectedTargetId());
            }
        }
    }

    /**
     * AC 1 end-to-end: two SDK-version trees sharing a simple name, ingested through the real store
     * into one catalog, produce two version-scoped rows with distinct ids and no cross-version
     * collision, each resolvable to source (relative path + anchor).
     */
    @Test
    void twoSdkVersionsSameSimpleNameCoexistWithoutCollision(@TempDir Path tmp) throws Exception {
        Path v1 = Files.createDirectories(tmp.resolve("javadoc-v1"));
        Path v2 = Files.createDirectories(tmp.resolve("javadoc-v2"));
        // Same relative path + simple name in both synthetic trees.
        writeType(v1, "com/example/Widget.html", CLEAN_PAGE);
        writeType(v2, "com/example/Widget.html", CLEAN_PAGE);

        Path dbPath = tmp.resolve("catalog.sqlite");
        // Each stage execution owns (and closes) its own connection to the shared catalog file, exactly
        // as a real per-build run — and a resumed run — does.
        Function<BuildContext, CatalogStore> perRun = ctx -> new SqliteCatalogStore(dbPath);
        List<StageFailure> failures = new ArrayList<>();

        new CatalogStage(v1, new JavadocCatalogSource(), perRun)
                .execute(context(tmp, "2024.0"), failures::add);
        new CatalogStage(v2, new JavadocCatalogSource(), perRun)
                .execute(context(tmp, "2025.0"), failures::add);

        assertTrue(failures.isEmpty(), () -> "clean cross-version ingest should not fail: " + failures);

        try (SqliteCatalogStore store = new SqliteCatalogStore(dbPath)) {
            StoredCatalogRecord r1 = store.findByKey("2024.0", "com.example.Widget").orElseThrow();
            StoredCatalogRecord r2 = store.findByKey("2025.0", "com.example.Widget").orElseThrow();
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    r1.recordId(), r2.recordId(), "the same simple name in two versions must not collide");
            assertEquals("com/example/Widget.html", r1.record().relativePath());
            assertEquals("com/example/Widget.html", r2.record().relativePath());
            // 2 types + 2 methods across the two versions, all version-scoped.
            assertEquals(4, store.allRecords().size());
        }
    }

    /**
     * Matrix row "sdkVersion not supplied": no {@code --sdk-version} does NOT abort. The build proceeds
     * and records are scoped under the default version label (version scoping is retained; only the
     * mandatory-input friction was removed). Verified through the production wiring end-to-end.
     */
    @Test
    void withoutSdkVersionBuildProceedsAndScopesUnderDefaultLabel(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeType(javadoc, "com/example/Widget.html", CLEAN_PAGE);
        IngestArguments args = new IngestArguments(
                javadoc,
                Files.createDirectories(tmp.resolve("samples")),
                Files.createDirectories(tmp.resolve("guides")),
                Files.createDirectories(tmp.resolve("model")),
                tmp.resolve("out"),
                Optional.empty(),
                Optional.empty()); // no --sdk-version

        PipelineResult result =
                IngestPipelineRunner.withCatalogStage(FailurePolicy.FAIL_FAST, args).runToResult(args);

        assertFalse(result.aborted(), () -> "an absent version must not abort the build: " + result.failures());
        String defaultLabel = IngestPipelineRunner.STUB_SDK_VERSION;
        assertEquals(defaultLabel, result.manifest().sdkVersion(), "absent version resolves to the default label");

        Path dbPath = BuildContext.forInputs(args).catalogDbPath();
        assertTrue(Files.isRegularFile(dbPath), () -> "the catalog db must exist at " + dbPath);
        try (SqliteCatalogStore store = new SqliteCatalogStore(dbPath)) {
            assertTrue(store.findByKey(defaultLabel, "com.example.Widget").isPresent(),
                    "records are still version-scoped, under the default label");
            assertEquals(2, store.allRecords().size());
        }
    }

    /** Matrix row "Minting issue from 2.3": an extraction/mint issue maps to a recoverable failure. */
    @Test
    void extractionIssueMapsToRecoverableFailureAndStillPersistsTheTypeRecord(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeType(javadoc, "com/example/Broken.html", NO_DESCRIPTION_PAGE);

        RecordingStore store = new RecordingStore();
        CatalogStage stage = new CatalogStage(
                javadoc, new JavadocCatalogSource(), fixedStore(store));

        List<StageFailure> failures = new ArrayList<>();
        stage.execute(context(tmp), failures::add);

        assertFalse(failures.isEmpty(), "the missing description must be recorded as an issue");
        assertTrue(failures.stream().allMatch(f -> f.severity() == FailureSeverity.RECOVERABLE),
                () -> "extraction issues are recoverable: " + failures);
        assertTrue(failures.stream().anyMatch(f -> f.message().contains("type-description")),
                () -> "the recorded issue should name the missing description: " + failures);
        // AD-4: the issue is recorded, not a silent drop — the type record is still minted and submitted.
        assertEquals(1, store.submitted.size());
        assertEquals("com.example.Broken", store.submitted.get(0).canonicalKey());
    }

    /** AD-4: a store-reported identity clash is a build-invalidating failure, never swallowed. */
    @Test
    void identityClashFromStoreIsBuildInvalidating(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeType(javadoc, "com/example/Widget.html", CLEAN_PAGE);

        RecordingStore store = new RecordingStore();
        store.forceClash = true; // every put reports a clash
        CatalogStage stage = new CatalogStage(
                javadoc, new JavadocCatalogSource(), fixedStore(store));

        List<StageFailure> failures = new ArrayList<>();
        stage.execute(context(tmp), failures::add);

        assertTrue(failures.stream().anyMatch(f -> f.severity() == FailureSeverity.BUILD_INVALIDATING
                        && f.message().contains("identity clash")),
                () -> "a store identity clash must surface as a build-invalidating failure: " + failures);
    }

    /**
     * Production wiring (Fix #7): {@link IngestPipelineRunner#withCatalogStage} runs the real
     * CatalogStage over a synthetic tree with a real {@code --sdk-version} and persists a catalog at
     * {@link BuildContext#catalogDbPath()}, with the manifest carrying the real version.
     */
    @Test
    void withCatalogStageRunnerPersistsCatalogAtBuildScopedPath(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeType(javadoc, "com/example/Widget.html", CLEAN_PAGE);
        IngestArguments args = new IngestArguments(
                javadoc,
                Files.createDirectories(tmp.resolve("samples")),
                Files.createDirectories(tmp.resolve("guides")),
                Files.createDirectories(tmp.resolve("model")),
                tmp.resolve("out"),
                Optional.empty(),
                Optional.of("2024.0"));

        PipelineResult result =
                IngestPipelineRunner.withCatalogStage(FailurePolicy.FAIL_FAST, args).runToResult(args);

        assertFalse(result.aborted(), () -> "a clean wired run should not abort: " + result.failures());
        assertEquals("2024.0", result.manifest().sdkVersion(), "the manifest must carry the real version");

        Path dbPath = BuildContext.forInputs(args).catalogDbPath();
        assertTrue(Files.isRegularFile(dbPath), () -> "the catalog db must exist at " + dbPath);
        try (SqliteCatalogStore store = new SqliteCatalogStore(dbPath)) {
            assertTrue(store.findByKey("2024.0", "com.example.Widget").isPresent(),
                    "the wired real CatalogStage must have been fed args.javadoc()+args.sdkVersion()");
            assertTrue(store.findByKey("2024.0", "com.example.Widget#doThing(int)").isPresent());
            assertEquals(2, store.allRecords().size());
        }
    }

    private static CatalogRecord byKey(RecordingStore store, String canonicalKey) {
        return store.submitted.stream()
                .filter(r -> r.canonicalKey().equals(canonicalKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no submitted record with key " + canonicalKey));
    }

    private static Function<BuildContext, CatalogStore> fixedStore(CatalogStore store) {
        return ctx -> store;
    }

    /** An in-memory {@link CatalogStore} that captures submissions; optionally reports every put as a clash. */
    private static final class RecordingStore implements CatalogStore {
        final List<CatalogRecord> submitted = new ArrayList<>();
        boolean forceClash = false;
        boolean closed = false;

        @Override
        public PutResult put(CatalogRecord record) {
            submitted.add(record);
            String id = "id-" + record.sdkVersion() + "-" + record.canonicalKey();
            return new PutResult(id, forceClash ? PutStatus.IDENTITY_CLASH : PutStatus.INSERTED);
        }

        @Override
        public Optional<StoredCatalogRecord> findByKey(String sdkVersion, String canonicalKey) {
            return Optional.empty();
        }

        @Override
        public Optional<StoredCatalogRecord> findByRecordId(String recordId) {
            return Optional.empty();
        }

        @Override
        public List<StoredCatalogRecord> allRecords() {
            return List.of();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
