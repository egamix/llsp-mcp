# Story 2.8 verification evidence (historical read-tools/1)

Current SDK guidance amendment uses read-tools/2. See SDK-VERSION-GUIDANCE-VERIFICATION.md for current executed checks. All original digests, counts, Windows/Linux parity and mutation evidence below belong to the earlier read-tools/1 candidate; they do not certify the amended interface.

Implementation candidate dated 2026-10-21. Technical schema/identity/interface sign-offs remain pending; downstream gates remain closed. Synthetic screening and portable recipe fixtures are separate from Story 10.1 controlled acceptance/private-corpus evaluation, which was not run.

## Executed commands

- `python tools/contracts/verify_contracts.py`: PASS, 87 schema/example/adverse/semantic checks using Python jsonschema 4.26.0. Covers all six requests/responses, populated cited content, mixed record fetch outcomes, visible verdicts, pre-binding errors, filter-only browsing, exact/unknown counts, malformed locators, mismatched relationship families, absent citations and cross-build citations. Manifest/report examples also validate. Cross-field semantic rules are documented below.
- `python tools/contracts/verify_mutations.py`: PASS, all six selected mutations detected by fresh failing selected-test XML. See mutation-evidence.json. Sources restored byte-for-byte in finally. Hash, identity, scope, ambiguity, relationship resolution and activation-policy mutants are screened separately. An initial broad resolution assertion survived; the missing focused qualified-resolution regression was added and the complete screen rerun successfully. This is a selected screen, not a mutation score for the codebase.
- `python tools/contracts/portable_fixture.py`: Windows Python 3.14.7 PASS. `wsl -d Ubuntu-24.04 --cd /mnt/d/workspace/luciad-mcp --exec python3 tools/contracts/portable_fixture.py`: Ubuntu-24.04 Python 3.12.3 PASS (executed independently by the parent agent). JSON values agree exactly, ignoring only platform line endings. Symbol wire, record/edge/content/recipe/runtime/capability known answers match `fixtures/portable.properties`.
- `app/gradlew.bat --no-daemon check`: PASS. The final application portability regression adds one CLI test to the initial 223-test baseline. `PortableFixtureTest.javaMatchesIndependentPortableKnownAnswers` checks the same fixture against Java domain recipes. See the final platform results below for actual execution and skips.
- Final Linux Java contract suites: PASS, 225 tests, zero failures/errors/skips, including both review regressions and the real application snapshot. Used a clean `git archive` of review-fix commit `f80d41c` on Ubuntu-24.04 native temporary storage, Temurin Java 21.0.12.1 and Gradle 8.10. Invoked the committed wrapper JAR directly because the archived shell launcher has CRLF; no product source was transformed. A preliminary mounted-drive attempt hit project-cache I/O errors and was superseded by successful native-filesystem runs. Local log: `build/story-2-8-linux-check.log`; XML archive: `build/contracts/linux-test-xml-final.tar`.
- Real application portability: PASS, Windows/Linux snapshot bytes identical, SHA-256 `2d13d1705cadb127196380dd747129183501d20bb6ffdf15907694ed7ff070d9`. Includes four complete catalog rows/symbols, two relationships, three exact/unresolved evidence entries, manifest, persisted schema/build metadata and the complete actual report. Build ID `b-9e8c5ca2656e0ff0bddec356ce0df4f0524871d4c52104455374c46609a025da`. No logical field was removed. See `APPLICATION-PORTABILITY.md` for the synthetic fixture and export contract.
- Final independent Windows `app/gradlew.bat --no-daemon check --rerun-tasks`: PASS, 226 tests, zero failures/errors, two existing environment skips (`ScratchRealDocsTest.classifyRealTree`, `IngestArgumentContractTest.outputSymlinkedIntoInputDirectoryIsRejected`). All 16 specifically named matrix tests ran and passed. Log: `build/story-2-8-parent-windows-check.log`.
- `git diff --check -- app docs/contracts tools/contracts`: PASS. Whole-workspace diff reports pre-existing whitespace in unrelated user documentation; those edits were preserved.
- `graft build`: refreshed after the final substantial change (deterministic graph).

Local Gradle process environment: `GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle`, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir`. The socket directory remains nonexistent. Existing cache and .git writes required approved sandbox escalation. No host-specific product setting was added.

## Frozen matrix and critical tests

| Matrix row | Covering tests / evidence |
| --- | --- |
| Portability | BuildContextTest.relocatedInputsPreserveLogicalIdentity; CanonicalSymbolMinterTest.qualifiedOverloadsAndPartialOccurrencesSurviveReordering; PortableFixtureTest.javaMatchesIndependentPortableKnownAnswers; ApplicationPortabilitySnapshotTest.realPipelineProducesPortableLogicalSnapshot; byte-identical Windows/Linux application snapshots |
| Sensitivity | ContentHashesTest pinned/sensitivity/framing tests; BuildContextTest.changedInputByteChangesBuildId and addingInputFileChangesBuildId; FreezeContractTest.structuredPayloadAndAbsentAnchorHaveIndependentHashes |
| Uncertainty | RelationshipsStageTest.ambiguousSimpleNameIsLeftUnresolved, explicitExternalDeadAndEscapingLinksNeverFallbackToLocalNames, locatorCollisionsRemainAmbiguousAndVersionScoped, qualifiedReferenceResolvesPreciselyWithoutFallback |
| Integrity | FreezeContractTest.identicalReasonsAcrossEveryLifecycleEntryAndRequiredCycles; FreezeContractTest.independentPracticeAndRecipeTypesRequireGovernanceEvenWhenMarkedAsFacts; CatalogStageTest.identicalPartialOccurrencesInvalidateContinueBuild; CatalogStageTest.completedResumeRevalidatesPersistedMetadata; SchemaCompatibilityTest.persistedMetadataCorruptionBlocksCliAndPinnedServing; SchemaCompatibilityTest.mixedScopeCatalogAndForeignEdgesRejectCliAndPinnedStartupIdentically |
| Compatibility | SchemaCompatibilityTest.provisionalAndFutureStoresAreRejectedWithoutChangingBytes; newSchemaReopens; persistedMetadataCorruptionBlocksCliAndPinnedServing |
| Bounds | RelationshipTraversalTest.depthAndEveryWorkBoundaryAreHonestAndAtomic, continuationAuthenticatesEveryQueryScopeAndRuntimeKey, adjacencyPayloadGuardsReadOnlyColumnByteMetadata; SchemaCompatibilityTest.unrelatedOversizedCatalogRowIsRejectedBeforeTextFetch; schema adverse checks |

## Nine acceptance criteria mapping

1. Versioned coordinate/wire and digest recipes: domain classes, README, portable fixture and pinned/sensitivity tests.
2. Versioned schema and rebuild-only byte preservation: storage-schema.sql, SchemaCompatibility, payload/locator contracts and compatibility tests.
3. Independent knowledge kinds/governance/dependencies: KnowledgeRecord, StandardBuildValidator, required-cycle and candidate tests. Current SQLite capabilities reject unimplemented knowledge data instead of silently validating it.
4. Manifest/report/profile/skips/severity/fingerprints: manifest and ingestion-report schemas/examples, production stage statuses, unknown-SDK tests, model-free CLI execution regression, no fake activation.
5. Six public tools: exact schemas/examples, ReadContracts and traversal bounds/cursor tests. Technical interface approval remains pending.
6. Domain ports/shared validation: BuildValidator, StandardBuildValidator, CatalogStore metadata validation port, existing CLI bootstrap wiring and pinned serving integration. Future lifecycle operations are contract requirements, not newly implemented orchestration.
7. Selected mutations plus portable known answers: mutation-evidence.json, recipe fixture results and real Windows/Linux JVM/parser/SQLite/catalog/manifest/report snapshots. A temporary Linux JDK closed the original missing-Java limitation. This synthetic cross-platform proof does not establish private-corpus or Story 10.1 evaluation acceptance.
8. CHANGELOG.md and sign-offs.json version impact; no reviewed exemptions or fabricated human approvers. All required technical approvals pending.
9. Cohesive commits for identity, recoverable serialization, schema/validation, public/report contracts and verification. No vectors, Docling, new database, generic multi-SDK, retrieval/MCP handlers or lifecycle orchestrator added.

## Semantic checks beyond JSON Schema

Historical v1 normalization bound an operator-default SDK. Current read-tools/2 separates optional target context from actual pinned source selection; see README.md. Byte limits use serialized UTF-8, not JSON Schema character counts. Validate inclusive line range last>=first; table rows have header width; nesting <=32 and aggregate block/byte budgets before materialization. Citation SDK/build and record/section association must match returned content; absent source locations never imply an anchor. Exact selected IDs require exact proof; ambiguity/unresolved have no selected target. Batch output preserves each input's order and includes every admitted item, with per-item status. Count relation UNKNOWN carries null; EXACT/LOWER_BOUND carry nonnegative integers. Cursor authentication binds all normalized query/filter/order/bound/version/build fields. These runtime rules are contract obligations for future handlers; the synthetic runner exercises representative cross-field negatives, not complete handler behavior.

## Independent review and final rerun

The focused independent review reported two concrete defects: persisted CLI/startup scope parity and independent practice/recipe governance. Both were patched in `f80d41c` and exercised on both platforms. The parent reran all six mutations successfully after the patches, confirmed source restoration, reran 87 schema checks, and verified the final application snapshot bytes again. No review finding was deferred. Technical human ratification and Story 10.1 acceptance remain separate, pending gates.
