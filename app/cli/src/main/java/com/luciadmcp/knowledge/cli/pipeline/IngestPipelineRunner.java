package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.SqliteCatalogStore;
import com.luciadmcp.knowledge.catalog.sqlite.SqliteRelationshipStore;
import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.cli.IngestPipeline;
import com.luciadmcp.knowledge.domain.ContentHashes;
import com.luciadmcp.knowledge.domain.IndexManifest;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The multi-stage, resumable ingestion pipeline with a configurable failure policy.
 *
 * <p>This is the orchestration skeleton (Story 1.3), extended in Story 1.4 to carry the manifest's
 * toolchain fingerprint and to emit a machine-readable JSON ingestion report on every run. It owns
 * the run loop, checkpoint/resume, and the failure-policy decision; it does not do real stage work —
 * the {@link Stage}s it runs are no-ops in production and Epics 2+ fill them in.
 *
 * <p>AD-17: the coordinator computes the single {@link BuildContext} once per invocation and passes
 * it to every stage. AD-25: resume state lives only in the coordinator-owned staging directory. AD-9:
 * no path here — including resume and report-writing — performs a network request.
 *
 * <h2>Failure handling</h2>
 * <ul>
 *   <li>A {@link FailureSeverity#RECOVERABLE} failure is governed by the {@link FailurePolicy}:
 *       under {@link FailurePolicy#FAIL_FAST} the run aborts on the first one; under
 *       {@link FailurePolicy#COLLECT_AND_CONTINUE} it is recorded and the run continues.</li>
 *   <li>A {@link FailureSeverity#BUILD_INVALIDATING} failure always aborts the run and blocks
 *       activation, regardless of policy (AD-5). The {@link PipelineStage#ACTIVATION} stage does not
 *       run and no manifest is activated.</li>
 * </ul>
 *
 * <h2>Ingestion report</h2>
 * <p>Every run that reaches a return point — clean or aborted — attempts to write
 * {@code {out}/{buildId}/ingestion-report.json} before returning, so a failed build is diagnosable
 * from a machine-readable artifact rather than logs (AC 6, 7). The report is deterministic:
 * identical inputs produce byte-identical report content. A run that crashes with an uncaught
 * exception before reaching a return point (see {@link Stage}) writes no report.
 */
public final class IngestPipelineRunner implements IngestPipeline {

    /**
     * Default SDK-version label used when no {@code --sdk-version} is supplied (AD-12). The version is
     * optional: {@link BuildContext} resolves an absent input to this label, and the {@link CatalogStage}
     * scopes records under it (the version column and {@code (sdkVersion, canonicalKey)} uniqueness still
     * apply) — an absent version does not abort the build. An explicit {@code --sdk-version} overrides it.
     */
    static final String STUB_SDK_VERSION = "0.0.0-stub";

    // Placeholder toolchain component versions. Each real value arrives with the stage that owns it
    // (parser/schema in Epic 2, normalizer/relationship-rules in Epic 3, embedding config in Epic 4);
    // they are distinct stub identifiers now so the fingerprint is a genuine function of five inputs.
    static final String STUB_PARSER_VERSION = "parser/0.0.0-stub";
    static final String STUB_SCHEMA_VERSION = "schema/0.0.0-stub";
    static final String STUB_NORMALIZER_VERSION = "normalizer/0.0.0-stub";
    static final String STUB_RELATIONSHIP_RULES_VERSION = "relationship-rules/0.0.0-stub";
    static final String STUB_EMBEDDING_CONFIG_ID = "embedding/0.0.0-stub";

    /** Fixed name of the machine-readable report, under the per-build output directory. */
    static final String REPORT_FILE = "ingestion-report.json";

    private final List<Stage> stages;
    private final FailurePolicy policy;

    /**
     * @param stages exactly one stage per {@link PipelineStage}, in {@link PipelineStage} order
     * @param policy the failure policy governing recoverable failures
     */
    public IngestPipelineRunner(List<Stage> stages, FailurePolicy policy) {
        PipelineStage[] all = PipelineStage.values();
        if (stages == null || stages.size() != all.length) {
            throw new IllegalArgumentException(
                    "expected exactly " + all.length + " stages, one per PipelineStage");
        }
        if (stages.contains(null)) {
            throw new IllegalArgumentException("stages must not contain null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.stages = List.copyOf(stages);
        this.policy = policy;
    }

    /** Wires the eight production no-op stages with the given policy. */
    public static IngestPipelineRunner withDefaultStages(FailurePolicy policy) {
        PipelineStage[] all = PipelineStage.values();
        List<Stage> noOps = new ArrayList<>(all.length);
        for (int i = 0; i < all.length; i++) {
            noOps.add(Stage.noOp());
        }
        return new IngestPipelineRunner(noOps, policy);
    }

    /** Wires the eight production no-op stages with the default policy ({@link FailurePolicy#DEFAULT}). */
    public static IngestPipelineRunner withDefaultStages() {
        return withDefaultStages(FailurePolicy.DEFAULT);
    }

    /**
     * Production wiring for Stories 2.4&ndash;2.5: a real {@link CatalogStage} at the
     * {@link PipelineStage#CATALOG} position and a real {@link RelationshipsStage} at the
     * {@link PipelineStage#RELATIONSHIPS} position, with the other six stages still no-ops (real
     * index/embedding work is Epic 3+). Both stages reach the stores only through {@code domain} ports;
     * the concrete SQLite stores are opened per build from the shared
     * {@link BuildContext#catalogDbPath()} — the catalog writes {@code records}, the relationship graph
     * writes {@code edges} into the same build-scoped file.
     *
     * @param policy the failure policy governing recoverable failures
     * @param args   the validated ingest inputs (javadoc root + optional SDK version)
     */
    public static IngestPipelineRunner withCatalogStage(FailurePolicy policy, IngestArguments args) {
        JavadocCatalogSource source = new JavadocCatalogSource();
        Stage catalog = new CatalogStage(
                args.javadoc(),
                source,
                ctx -> new SqliteCatalogStore(ctx.catalogDbPath()));
        Stage relationships = new RelationshipsStage(
                args.javadoc(),
                source,
                ctx -> new SqliteCatalogStore(ctx.catalogDbPath()),
                ctx -> new SqliteRelationshipStore(ctx.catalogDbPath()));
        PipelineStage[] all = PipelineStage.values();
        List<Stage> stages = new ArrayList<>(all.length);
        for (PipelineStage stage : all) {
            stages.add(switch (stage) {
                case CATALOG -> catalog;
                case RELATIONSHIPS -> relationships;
                case VALIDATION -> new BuildValidationStage(new com.luciadmcp.knowledge.domain.StandardBuildValidator(), ctx -> new SqliteCatalogStore(ctx.catalogDbPath()));
                default -> Stage.noOp();
            });
        }
        return new IngestPipelineRunner(stages, policy);
    }

    @Override
    public void run(IngestArguments args) {
        runToResult(args);
    }

    /**
     * Runs the pipeline and returns its in-memory outcome. Completed stages are skipped on resume;
     * each newly completed stage is checkpointed before the next begins. A JSON ingestion report is
     * written before every return, including the aborted-return paths.
     *
     * @param args the validated six-path inputs
     * @return the outcome: completed stages, collected failures, and whether activation occurred
     */
    public PipelineResult runToResult(IngestArguments args) {
        BuildContext context = BuildContext.forInputs(args);
        CheckpointStore checkpoint = new CheckpointStore(context);
        String toolchainFingerprint = ContentHashes.toolchainFingerprint(
                STUB_PARSER_VERSION,
                STUB_SCHEMA_VERSION,
                STUB_NORMALIZER_VERSION,
                STUB_RELATIONSHIP_RULES_VERSION,
                STUB_EMBEDDING_CONFIG_ID);
        // Story 2.4: the manifest now carries the build's real SDK version threaded through
        // BuildContext (AD-12). STUB_SDK_VERSION survives only as BuildContext.forInputs' fallback
        // when no --sdk-version is supplied — the persisted path uses the real value.
        IndexManifest manifest =
                new IndexManifest(context.buildId(), context.sdkVersion(), toolchainFingerprint);

        List<StageFailure> collected = new ArrayList<>();
        PipelineStage[] all = PipelineStage.values();

        // Per-stage throughput for the report; every stage starts NOT_RUN and is updated as reached.
        Map<PipelineStage, StageStatus> statuses = new EnumMap<>(PipelineStage.class);
        for (PipelineStage stage : all) {
            statuses.put(stage, StageStatus.NOT_RUN);
        }

        for (int i = 0; i < all.length; i++) {
            PipelineStage stage = all[i];
            if (checkpoint.isCompleted(stage)) {
                statuses.put(stage, StageStatus.SKIPPED_ON_RESUME);
                continue; // resume: already done in a prior run
            }

            List<StageFailure> stageFailures = new ArrayList<>();
            stages.get(i).execute(context, stageFailures::add);
            collected.addAll(stageFailures);

            if (containsSeverity(stageFailures, FailureSeverity.BUILD_INVALIDATING)) {
                // Always abort and block activation, regardless of policy (AD-5). The stage is not
                // marked complete and ACTIVATION never runs.
                statuses.put(stage, StageStatus.FAILED);
                return aborted(args, context, manifest, checkpoint, collected, statuses);
            }

            if (policy == FailurePolicy.FAIL_FAST
                    && containsSeverity(stageFailures, FailureSeverity.RECOVERABLE)) {
                // Fail-fast: abort on the first recoverable failure. Stage not marked complete.
                statuses.put(stage, StageStatus.FAILED);
                return aborted(args, context, manifest, checkpoint, collected, statuses);
            }

            if (stageFailures.isEmpty()) {
                // Clean: the stage is done, so record it. Recording lets a resumed run skip it.
                checkpoint.markCompleted(stage);
                statuses.put(stage, StageStatus.COMPLETED);
            } else {
                // Recoverable under collect-and-continue: recorded above, run continues, but the
                // stage is NOT checkpointed — a resume must re-run it so its failure is reproduced
                // rather than silently dropped from a later run's results.
                statuses.put(stage, StageStatus.FAILED);
            }
        }

        boolean activationOccurred = checkpoint.isCompleted(PipelineStage.ACTIVATION);
        writeReport(args, manifest, statuses, collected, activationOccurred, false);
        return new PipelineResult(
                context, manifest, checkpoint.completedStages(), collected, activationOccurred, false);
    }

    private static PipelineResult aborted(
            IngestArguments args,
            BuildContext context,
            IndexManifest manifest,
            CheckpointStore checkpoint,
            List<StageFailure> collected,
            Map<PipelineStage, StageStatus> statuses) {
        writeReport(args, manifest, statuses, collected, false, true);
        return new PipelineResult(
                context, manifest, checkpoint.completedStages(), collected, false, true);
    }

    /** Builds the report from run state and writes it to {@code {out}/{buildId}/ingestion-report.json}. */
    private static void writeReport(
            IngestArguments args,
            IndexManifest manifest,
            Map<PipelineStage, StageStatus> statuses,
            List<StageFailure> collected,
            boolean activationOccurred,
            boolean aborted) {
        List<IngestionReport.StageEntry> stageEntries = new ArrayList<>();
        for (PipelineStage stage : PipelineStage.values()) {
            stageEntries.add(new IngestionReport.StageEntry(stage, statuses.get(stage)));
        }

        List<IngestionReport.FailureEntry> failureEntries = new ArrayList<>();
        for (StageFailure f : collected) {
            String file = f.file().map(Path::toString).orElse(null);
            failureEntries.add(new IngestionReport.FailureEntry(
                    f.stage(), f.severity(), file, f.message()));
        }

        IngestionReport.ResolutionSummary resolutionSummary = IngestionReport.ResolutionSummary.empty();
        Path catalogPath = args.out().resolve(".staging").resolve(manifest.buildId()).resolve(manifest.sdkVersion()).resolve(BuildContext.CATALOG_DB_FILE);
        if (java.nio.file.Files.exists(catalogPath)) {
            try (var store = new SqliteRelationshipStore(catalogPath)) {
                resolutionSummary = IngestionReport.ResolutionSummary.from(store.resolutions(
                        new com.luciadmcp.knowledge.domain.RelationshipScope(manifest.sdkVersion())));
            } catch (RuntimeException e) {
                if (!aborted) throw e;
                // Keep the original handled failure and aborted result even when storage cannot reopen.
                // Null distinguishes unavailable counts from a successfully read empty evidence set.
                resolutionSummary = null;
            }
        }
        IngestionReport report = new IngestionReport(
                manifest.buildId(),
                manifest.sdkVersion(),
                manifest.toolchainFingerprint(),
                aborted,
                activationOccurred,
                stageEntries,
                failureEntries,
                resolutionSummary);

        Path reportPath = args.out().resolve(manifest.buildId()).resolve(REPORT_FILE);
        try {
            report.writeTo(reportPath);
        } catch (UncheckedIOException e) {
            // The in-memory result (already computed by the caller) is still returned; a report-write
            // failure must not mask a run's actual outcome behind an unrelated I/O exception.
            System.err.println("warning: failed to write ingestion report to " + reportPath + ": " + e.getMessage());
        }
    }

    private static boolean containsSeverity(List<StageFailure> failures, FailureSeverity severity) {
        return failures.stream().anyMatch(f -> f.severity() == severity);
    }
}
