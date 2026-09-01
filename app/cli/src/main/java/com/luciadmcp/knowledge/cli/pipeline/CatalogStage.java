package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.CatalogRecord;
import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolKind;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolRecord;
import java.nio.file.Path;
import java.util.Optional;
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
 * <h2>Failure mapping</h2>
 * <ul>
 *   <li><b>Missing SDK version</b> &rarr; {@link FailureSeverity#BUILD_INVALIDATING}: version-scoped
 *       keys cannot be minted without it, so the build aborts with a clear error.</li>
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
    private final Optional<String> sdkVersion;
    private final JavadocCatalogSource source;
    private final Function<BuildContext, CatalogStore> storeFactory;

    /**
     * @param javadocRoot  the Javadoc tree root to ingest (validated to exist by {@code IngestCommand})
     * @param sdkVersion   the explicit SDK-version build input, or empty (then the build aborts)
     * @param source       the Story-2.1&ndash;2.3 discover/extract/mint facade
     * @param storeFactory opens the build-scoped {@link CatalogStore} for a {@link BuildContext}
     */
    public CatalogStage(
            Path javadocRoot,
            Optional<String> sdkVersion,
            JavadocCatalogSource source,
            Function<BuildContext, CatalogStore> storeFactory) {
        this.javadocRoot = javadocRoot;
        this.sdkVersion = sdkVersion == null ? Optional.empty() : sdkVersion;
        this.source = source;
        this.storeFactory = storeFactory;
    }

    @Override
    public void execute(BuildContext context, Consumer<StageFailure> failures) {
        if (sdkVersion.isEmpty() || sdkVersion.get().isBlank()) {
            // Keys cannot be version-scoped without a version (AD-12): abort the build (matrix row
            // "Missing/undeterminable sdkVersion").
            failures.accept(StageFailure.of(
                    PipelineStage.CATALOG,
                    FailureSeverity.BUILD_INVALIDATING,
                    "no SDK version supplied (--sdk-version); catalog keys cannot be version-scoped"));
            return;
        }
        String version = sdkVersion.get();

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
            return;
        }
        try {
            for (JavadocCatalogSource.PageResult page : result.pages()) {
                persistPage(version, page, store, failures);
            }
        } catch (RuntimeException e) {
            // A genuine storage error from a put (not the IDENTITY_CLASH status path, which is handled
            // in persistPage) compromises catalog integrity — route it through the policy, not raw.
            failures.accept(StageFailure.of(
                    PipelineStage.CATALOG,
                    FailureSeverity.BUILD_INVALIDATING,
                    "catalog persistence failed: " + e.getMessage()));
        } finally {
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

    private void persistPage(
            String version,
            JavadocCatalogSource.PageResult page,
            CatalogStore store,
            Consumer<StageFailure> failures) {
        Path file = javadocRoot.resolve(page.relativePath());

        for (String issue : page.issues()) {
            failures.accept(StageFailure.ofFile(
                    PipelineStage.CATALOG, FailureSeverity.RECOVERABLE, file, issue));
        }

        for (SymbolRecord record : page.records()) {
            CatalogRecord tuple = toCatalogRecord(version, record);
            CatalogStore.PutResult result = store.put(tuple);
            if (result.status() == CatalogStore.PutStatus.IDENTITY_CLASH) {
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
    private static CatalogRecord toCatalogRecord(String version, SymbolRecord record) {
        return new CatalogRecord(
                version,
                record.canonicalKey(),
                record.kind().name(),
                record.typeFqn(),
                record.simpleName(),
                record.relativePath(),
                anchorFor(record));
    }

    /**
     * The on-page HTML fragment locating this element, derived deterministically from the record. A
     * type-level record has no member fragment (the page itself is the location), so its anchor is
     * empty. A field / enum constant anchors on its bare name; a method / constructor anchors on the
     * modern Javadoc member id {@code name(ErasedParam,ErasedParam)} — exactly the erased-generic
     * variant the minter already computed. With {@code relativePath}, this reconstructs the source
     * location (matrix row "Resolvable to source").
     */
    static String anchorFor(SymbolRecord record) {
        if (record.kind() == SymbolKind.TYPE) {
            return "";
        }
        if (record.kind() == SymbolKind.FIELD || record.kind() == SymbolKind.ENUM_CONSTANT) {
            return record.simpleName();
        }
        return record.simpleName() + "(" + String.join(",", record.erasedParameterTypes()) + ")";
    }
}
