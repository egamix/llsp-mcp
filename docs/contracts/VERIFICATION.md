# Story 2.8 verification evidence

Implementation candidate dated 2026-10-21. Technical schema/identity/interface sign-offs remain pending; downstream gates remain closed. Synthetic screening and portable recipe fixtures are separate from Story 10.1 controlled acceptance/private-corpus evaluation, which was not run.

## Executed commands

- `python tools/contracts/verify_contracts.py`: PASS, 57 schema/example/adverse/semantic checks using Python jsonschema 4.26.0. Covers all six requests/responses, populated cited content, mixed record fetch outcomes, visible verdicts, typed error, exact/unknown counts, malformed locators, mismatched relationship families, absent citations and cross-build citations. Manifest/report examples also validate. Cross-field semantic rules are documented below.
- `python tools/contracts/verify_mutations.py`: PASS, all six selected mutations detected by fresh failing selected-test XML. See mutation-evidence.json. Sources restored byte-for-byte in finally. Hash, identity, scope, ambiguity, relationship resolution and activation-policy mutants are screened separately. An initial broad resolution assertion survived; the missing focused qualified-resolution regression was added and the complete screen rerun successfully. This is a selected screen, not a mutation score for the codebase.
- `python tools/contracts/portable_fixture.py`: Windows Python 3.14.7 PASS. `wsl -d Ubuntu-24.04 --cd /mnt/d/workspace/luciad-mcp --exec python3 tools/contracts/portable_fixture.py`: Ubuntu-24.04 Python 3.12.3 PASS (executed independently by the parent agent). JSON values agree exactly, ignoring only platform line endings. Symbol wire, record/edge/content/recipe/runtime/capability known answers match `fixtures/portable.properties`.
- `app/gradlew.bat --no-daemon check`: PASS. XML summary: domain 22 tests, ingest-javadoc 82, catalog-sqlite 33, CLI 86; zero failures/errors. Two environment-dependent tests skipped (real-doc scratch and Windows symlink privilege). `PortableFixtureTest.javaMatchesIndependentPortableKnownAnswers` checks the same fixture against Java domain recipes. See current XML reports for exact skip reasons.
- `git diff --check -- app docs/contracts tools/contracts`: PASS. Whole-workspace diff reports pre-existing whitespace in unrelated user documentation; those edits were preserved.
- `graft build`: refreshed after the final substantial change (deterministic graph).

Local Gradle process environment: `GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle`, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir`. The socket directory remains nonexistent. Existing cache and .git writes required approved sandbox escalation. No host-specific product setting was added.

## Frozen matrix and critical tests

| Matrix row | Covering tests / evidence |
| --- | --- |
| Portability | BuildContextTest.relocatedInputsPreserveLogicalIdentity; CanonicalSymbolMinterTest.qualifiedOverloadsAndPartialOccurrencesSurviveReordering; PortableFixtureTest.javaMatchesIndependentPortableKnownAnswers; Windows/Linux recipe runner |
| Sensitivity | ContentHashesTest pinned/sensitivity/framing tests; BuildContextTest.changedInputByteChangesBuildId and addingInputFileChangesBuildId; FreezeContractTest.structuredPayloadAndAbsentAnchorHaveIndependentHashes |
| Uncertainty | RelationshipsStageTest.ambiguousSimpleNameIsLeftUnresolved, explicitExternalDeadAndEscapingLinksNeverFallbackToLocalNames, locatorCollisionsRemainAmbiguousAndVersionScoped, qualifiedReferenceResolvesPreciselyWithoutFallback |
| Integrity | FreezeContractTest.identicalReasonsAcrossEveryLifecycleEntryAndRequiredCycles; CatalogStageTest.identicalPartialOccurrencesInvalidateContinueBuild; CatalogStageTest.completedResumeRevalidatesPersistedMetadata; SchemaCompatibilityTest.persistedMetadataCorruptionBlocksCliAndPinnedServing |
| Compatibility | SchemaCompatibilityTest.provisionalAndFutureStoresAreRejectedWithoutChangingBytes; newSchemaReopens; persistedMetadataCorruptionBlocksCliAndPinnedServing |
| Bounds | RelationshipTraversalTest.depthAndEveryWorkBoundaryAreHonestAndAtomic, continuationAuthenticatesEveryQueryScopeAndRuntimeKey, adjacencyPayloadGuardsReadOnlyColumnByteMetadata; SchemaCompatibilityTest.unrelatedOversizedCatalogRowIsRejectedBeforeTextFetch; schema adverse checks |

## Nine acceptance criteria mapping

1. Versioned coordinate/wire and digest recipes: domain classes, README, portable fixture and pinned/sensitivity tests.
2. Versioned schema and rebuild-only byte preservation: storage-schema.sql, SchemaCompatibility, payload/locator contracts and compatibility tests.
3. Independent knowledge kinds/governance/dependencies: KnowledgeRecord, StandardBuildValidator, required-cycle and candidate tests. Current SQLite capabilities reject unimplemented knowledge data instead of silently validating it.
4. Manifest/report/profile/skips/severity/fingerprints: manifest and ingestion-report schemas/examples, production stage statuses, unknown-SDK tests, model-free CLI execution regression, no fake activation.
5. Six public tools: exact schemas/examples, ReadContracts and traversal bounds/cursor tests. Technical interface approval remains pending.
6. Domain ports/shared validation: BuildValidator, StandardBuildValidator, CatalogStore metadata validation port, existing CLI bootstrap wiring and pinned serving integration. Future lifecycle operations are contract requirements, not newly implemented orchestration.
7. Selected mutations plus portable known answers: mutation-evidence.json and recipe fixture results. **Full Windows/Linux JVM/parser/SQLite/catalog/report execution comparison remains unavailable:** neither installed WSL distribution has a Java executable. The Python fixture compares pure recipes and manifest/report version/profile markers, not live Linux application output. No full cross-platform application acceptance is claimed.
8. CHANGELOG.md and sign-offs.json version impact; no reviewed exemptions or fabricated human approvers. All required technical approvals pending.
9. Cohesive commits for identity, recoverable serialization, schema/validation, public/report contracts and verification. No vectors, Docling, new database, generic multi-SDK, retrieval/MCP handlers or lifecycle orchestrator added.

## Semantic checks beyond JSON Schema

Request normalization binds operator-default SDK before lookup and one immutable build per request. Byte limits use serialized UTF-8, not JSON Schema character counts. Validate inclusive line range last>=first; table rows have header width; nesting <=32 and aggregate block/byte budgets before materialization. Citation SDK/build and record/section association must match returned content; absent source locations never imply an anchor. Exact selected IDs require exact proof; ambiguity/unresolved have no selected target. Batch output preserves each input's order and includes every admitted item, with per-item status. Count relation UNKNOWN carries null; EXACT/LOWER_BOUND carry nonnegative integers. Cursor authentication binds all normalized query/filter/order/bound/version/build fields. These runtime rules are contract obligations for future handlers; the synthetic runner exercises representative cross-field negatives, not complete handler behavior.
