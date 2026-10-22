package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.CatalogRecord;
import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolKind;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolRecord;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The real {@link PipelineStage#CATALOG} stage (Story 2.4): the end-to-end pass-one persistence path.
 * It runs the {@code ingest.javadoc} discover&rarr;extract&rarr;mint chain (2.1&ndash;2.3) via
 * {@link JavadocCatalogSource}, scopes each minted record to an SDK version, attaches its relative
 * HTML path and a derived anchor, and submits the resulting logical tuples to the {@link CatalogStore}
 * port — which alone computes IDs and persists version-scoped rows (AD-22).
 *
 * <p><b>Dependency direction (AD-1).</b> This stage lives in {@code cli}, the sole build coordinator
 * (AD-17). It drives Javadoc parsing only through {@link JavadocCatalogSource} (no JSoup type crosses
 * the boundary) and reaches the store only through the {@code domain} {@link CatalogStore} port; the
 * concrete SQLite store is supplied by the wiring via a factory, so this class names no engine type.
 *
 * <p><b>SDK version.</b> The stage scopes every record to {@link BuildContext#sdkVersion()}, which
 * carries the explicit {@code --sdk-version} when supplied and a default label otherwise. The version
 * is optional: an absent one no longer aborts the build (the version column and
 * {@code (sdkVersion, canonicalKey)} uniqueness still apply, just under the default label).
 *
 * <h2>Failure mapping</h2>
 * <ul>
 *   <li><b>Extraction / minting issues (2.2 / 2.3) and discovery warnings (2.1)</b> &rarr;
 *       {@link FailureSeverity#RECOVERABLE}, mapped onto the {@link FailurePolicy} by the runner:
 *       fail-fast aborts, collect-and-continue records and proceeds. Never a silent drop or a
 *       fabricated value (AD-4).</li>
 *   <li><b>Identity clash from the store</b> &rarr; {@link FailureSeverity#BUILD_INVALIDATING}: two
 *       distinct logical records claiming one identity compromises catalog integrity.</li>
 * </ul>
 */
public final class CatalogStage implements Stage {

    private final Path javadocRoot;
    private final JavadocCatalogSource source;
    private final Function<BuildContext, CatalogStore> storeFactory;

    /**
     * @param javadocRoot  the Javadoc tree root to ingest (validated to exist by {@code IngestCommand})
     * @param source       the Story-2.1&ndash;2.3 discover/extract/mint facade
     * @param storeFactory opens the build-scoped {@link CatalogStore} for a {@link BuildContext}
     */
    public CatalogStage(
            Path javadocRoot,
            JavadocCatalogSource source,
            Function<BuildContext, CatalogStore> storeFactory) {
        this.javadocRoot = javadocRoot;
        this.source = source;
        this.storeFactory = storeFactory;
    }

    @Override
    public void execute(BuildContext context, Consumer<StageFailure> failures) {
        // The version is optional; BuildContext already resolves an absent --sdk-version to a default
        // label, so scoping (and catalogDbPath) stay consistent without a mandatory input (AD-12).
        String version = context.sdkVersion();
        try { java.nio.file.Files.deleteIfExists(context.stagingDir().resolve(CoverageSnapshot.FILE)); }
        catch(java.io.IOException e) {
            failures.accept(StageFailure.of(PipelineStage.CATALOG,FailureSeverity.BUILD_INVALIDATING,"COVERAGE_RESET_FAILED"));return;
        }

        JavadocCatalogSource.Result result;
        try {
            result = source.ingest(javadocRoot);
        } catch (RuntimeException e) {
            failures.accept(StageFailure.of(
                    PipelineStage.CATALOG,
                    FailureSeverity.BUILD_INVALIDATING,
                    "Javadoc discovery/extraction failed under " + javadocRoot + ": " + e.getMessage()));
            return;
        }

        CoverageAccounting accounting = new CoverageAccounting(result);
        for (var page : result.inventory()) if (page.type() == com.luciadmcp.knowledge.ingest.javadoc.PageType.UNKNOWN)
            failures.accept(StageFailure.ofFile(PipelineStage.CATALOG, FailureSeverity.RECOVERABLE,
                    javadocRoot.resolve(page.relativePath()), page.reason().name()));

        // Discovery warnings (unreadable/unclassifiable pages, search-index disagreements) are recorded
        // issues, never silently dropped (AD-4). They are page-local, so recoverable.
        for (String warning : result.discoveryWarnings()) {
            failures.accept(StageFailure.of(
                    PipelineStage.CATALOG, FailureSeverity.RECOVERABLE, warning));
        }

        CatalogStore store;
        try {
            store = storeFactory.apply(context);
        } catch (RuntimeException e) {
            // A store that will not open cannot yield a valid catalog — abort, do not propagate raw
            // past the failure policy.
            failures.accept(StageFailure.of(
                    PipelineStage.CATALOG,
                    FailureSeverity.BUILD_INVALIDATING,
                    "could not open catalog store: " + e.getMessage()));
            saveCoverage(accounting,context,failures);
            return;
        }
        try {
            for (JavadocCatalogSource.PageResult page : result.pages()) {
                persistPage(version, page, store, failures, accounting);
            }
            for(var page:result.narratives()) {
                accounting.begin(page.relativePath(),page.issues());
                Path file=javadocRoot.resolve(page.relativePath());
                for(String issue:page.issues())failures.accept(StageFailure.ofFile(PipelineStage.CATALOG,FailureSeverity.RECOVERABLE,file,issue));
                if(page.blocks().isEmpty() || page.sourceHash().isEmpty())continue;
                var documented=com.luciadmcp.knowledge.ingest.javadoc.JavadocDocumentationAssembler.assembleNarrative(version,page);
                String key="narrative:"+page.relativePath();
                accounting.sections(key,documented.payload().sections().size());
                var written=store.putNarrative(documented.record(),documented.payload());
                if(written.status()!=CatalogStore.PutStatus.IDENTITY_CLASH)accounting.persisted(key);
                else accounting.failed(page.relativePath(),"IDENTITY_CLASH");
                if(written.status()==CatalogStore.PutStatus.IDENTITY_CLASH)failures.accept(StageFailure.ofFile(PipelineStage.CATALOG,FailureSeverity.BUILD_INVALIDATING,file,"identity clash persisting narrative "+written.recordId()));
            }
        } catch (RuntimeException e) {
            accounting.storageFailure();
            // A genuine storage error from a put (not the IDENTITY_CLASH status path, which is handled
            // in persistPage) compromises catalog integrity — route it through the policy, not raw.
            failures.accept(StageFailure.of(
                    PipelineStage.CATALOG,
                    FailureSeverity.BUILD_INVALIDATING,
                    "catalog persistence failed: " + e.getMessage()));
        } finally {
            saveCoverage(accounting,context,failures);
            try {
                store.close();
            } catch (RuntimeException e) {
                failures.accept(StageFailure.of(
                        PipelineStage.CATALOG,
                        FailureSeverity.BUILD_INVALIDATING,
                        "could not close catalog store: " + e.getMessage()));
            }
        }
    }

    private static void saveCoverage(CoverageAccounting accounting, BuildContext context, Consumer<StageFailure> failures) {
        try { accounting.snapshot().write(context); }
        catch(RuntimeException e) { failures.accept(StageFailure.of(PipelineStage.CATALOG,FailureSeverity.BUILD_INVALIDATING,"COVERAGE_WRITE_FAILED")); }
    }

    private void persistPage(
            String version,
            JavadocCatalogSource.PageResult page,
            CatalogStore store,
            Consumer<StageFailure> failures, CoverageAccounting accounting) {
        accounting.begin(page.relativePath(),page.issues());
        Path file = javadocRoot.resolve(page.relativePath());

        for (String issue : page.issues()) {
            failures.accept(StageFailure.ofFile(
                    PipelineStage.CATALOG, issue.contains("duplicate canonical key") ? FailureSeverity.BUILD_INVALIDATING : FailureSeverity.RECOVERABLE, file, issue));
        }

        for (SymbolRecord record : page.records()) {
            CatalogRecord tuple = toCatalogRecord(version, record, com.luciadmcp.knowledge.ingest.javadoc.JavadocDocumentationAssembler.anchor(page, record).orElse(""));
            com.luciadmcp.knowledge.ingest.javadoc.JavadocDocumentationAssembler.Documented documented;
            try { documented = com.luciadmcp.knowledge.ingest.javadoc.JavadocDocumentationAssembler.assemble(tuple, page, record); }
            catch (IllegalArgumentException e) {
                accounting.failed(page.relativePath(),"PAYLOAD_REJECTED");
                failures.accept(StageFailure.ofFile(PipelineStage.CATALOG,FailureSeverity.RECOVERABLE,file,"PAYLOAD_REJECTED"));
                continue;
            }
            accounting.sections(record.canonicalKey(),documented.payload().sections().size());
            CatalogStore.PutResult result = store.putDocumented(tuple, documented.record(), documented.payload());
            if (result.status() != CatalogStore.PutStatus.IDENTITY_CLASH) accounting.persisted(record.canonicalKey());
            if (result.status() == CatalogStore.PutStatus.IDENTITY_CLASH) {
                accounting.failed(page.relativePath(),"IDENTITY_CLASH");
                // A real identity clash compromises catalog integrity — never overwrite or drop (AD-4).
                failures.accept(StageFailure.ofFile(
                        PipelineStage.CATALOG,
                        FailureSeverity.BUILD_INVALIDATING,
                        file,
                        "identity clash persisting canonical key '" + record.canonicalKey()
                                + "' (SDK version " + version + "); computed record id "
                                + result.recordId()));
            }
        }
    }

    /**
     * Builds the resolved logical tuple for one minted record: the SDK version (AD-12), the provisional
     * canonical key, the kind, the enclosing type FQN, the simple name, the source-relative HTML path,
     * and the {@link #anchorFor(SymbolRecord) derived anchor} that (with the path) resolves back to
     * source. The store computes the ID — this never does (AD-22).
     */
    private static CatalogRecord toCatalogRecord(String version, SymbolRecord record, String actualAnchor) {
        return new CatalogRecord(
                version,
                record.canonicalKey(),
                record.kind().name(),
                record.typeFqn(),
                record.simpleName(),
                record.relativePath(),
                actualAnchor);
    }

    /**
     * The on-page HTML fragment locating this element, derived deterministically from the record. A
     * type-level record has no member fragment (the page itself is the location), so its anchor is
     * empty. A field / enum constant anchors on its bare name; a method / constructor anchors on the
     * modern Javadoc member id {@code name(ErasedParam,ErasedParam)}, with {@code <init>} as the
     * constructor name. With {@code relativePath}, this reconstructs the source
     * location (matrix row "Resolvable to source").
     */
    static String anchorFor(SymbolRecord record) {
        if (record.kind() == SymbolKind.TYPE) {
            return "";
        }
        if (record.kind() == SymbolKind.FIELD || record.kind() == SymbolKind.ENUM_CONSTANT) {
            return record.simpleName();
        }
        // Modern doclets distinguish constructors from legal same-named methods in their locators.
        // Keep locator spelling independent of the provisional identity's method: discriminator.
        String locatorName = record.kind() == SymbolKind.CONSTRUCTOR ? "<init>" : record.simpleName();
        return locatorName + "(" + String.join(",", record.erasedParameterTypes()) + ")";
    }
}
