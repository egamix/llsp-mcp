# Story 2.9 implementation evidence

Implementation commits: `e892082` (atomic schema/2 storage), `6932c23` (extraction and production handoff), `002a3dd` (boundary and rollback verification). The parent workflow owns final full-suite, independent complete-diff review and fresh Windows/Linux snapshot results. Earlier Story 2.8 portability evidence is historical and is not proof for this delivery.

Executed on Windows during implementation:

- `app/gradlew.bat --no-daemon :cli:test :ingest-javadoc:test :catalog-sqlite:test` passed after source-anchor integration. The extraction module executed 83 tests with its existing private-corpus scratch test skipped. The CLI's Windows symlink test remains an environment skip.
- Final boundary slice: `:cli:test --tests '*DocumentationRoundtripTest' :catalog-sqlite:test --tests '*DocumentationStoreTest' :domain:test` passed: 4 CLI roundtrip tests, 5 storage tests and 27 domain tests, no failures or skips in those tasks. These filtered tasks replace earlier XML results for their modules; they do not represent a second full-module run.
- `python tools/contracts/portable_fixture.py` passed against regenerated independent schema/runtime/recipe/capability known answers. Stable symbol, record and edge known answers are unchanged.
- `python tools/contracts/verify_contracts.py` passed 122 synthetic schema/example/adverse checks. No private-corpus or Story 10.1 acceptance claim.
- `graft build` refreshed the local ignored graph: 168 files, 1137 nodes and 3211 edges. Visible Graft savings lines during implementation total at least approximately 437,080 tokens.

| Spec matrix row | Executed evidence |
| --- | --- |
| Modern/legacy API fields survive source deletion and reopen | `DocumentationRoundtripTest.modernAndLegacyEverySemanticFieldSurvivesSourceRemoval`: JDK 8, 11 and 17 Widget fixtures; all ten IDs fetch; explicit signatures/descriptions/parameters/returns/exceptions/since/deprecation, constructor parameters and distinct overload descriptions |
| Overloads, constructors, two SDK sources | Same source-removal harness; `CatalogStageTest.constructorAndClassNamedMethodPersistWithDistinctSourceLocators`; `DocumentationRoundtripTest.typeParametersCharsetRetryContentEditRelocationAndSdkIsolation`; existing two-SDK catalog tests |
| Type parameters, actual legacy anchors and absent anchors | `DocumentationExtractionTest.nonemptyTypeAndMemberGenericDocsAndActualLegacyAnchors`: nonempty T and R documentation in both eras; source-removal harness asserts absent JDK 8 anchors; generic roundtrip asserts the actual custom anchor and nonempty generic docs |
| Identical retry, content-only change, relocated root | `DocumentationStoreTest.structuredReopenPagingScopesAndRetry`; generic roundtrip compares stable ID, independent source/content hashes, changed build IDs and equal relocated manifests; application portability test compares opposite file creation orders |
| Failure between catalog/metadata/content | `DocumentationStoreTest.rollsBackAtMetadataDocumentAndSectionBoundaries` injects SQLite abort triggers at all three boundaries; `DocumentationRoundtripTest.productionStageRollsBackFailedContentWrites` exercises actual CatalogStage, verifies all four tables empty, retries and reopens after source deletion |
| Missing ID, wrong build/source, offset/limit/continuation | `DocumentationStoreTest.structuredReopenPagingScopesAndRetry` and `exactSectionAndBlockCapsPagingAndEncodedAdmission`: missing ID, wrong SDK/build/record/limit rebinding, malformed cursor, negative/out-of-range offsets, limits 0/51, full 256-section continuation sequence |
| Oversize/corrupt content and old schema | `DocumentationStoreTest.corruptAndOversizeRejectCliAndPinnedStartup`: eleven corruptions rejected by both lifecycle paths; exact-boundary test checks 256 sections/4096 blocks, over-limit blocks/sections/text, corrupt frame counts, encoded admission and oversize persisted fetch; schema/1 test proves original file bytes unchanged |
| Unsupported content port cannot report tuple-only success | `DocumentationRoundtripTest.unsupportedContentPortCannotReportTupleOnlySuccess` proves CatalogStage never calls tuple-only put and reports a build-invalidating failure |

`ApplicationPortabilitySnapshotTest` now includes every knowledge column, document/source hashes, section counts, encoded metadata, encoded section payloads and section hashes, alongside existing catalog/edge/evidence/build/manifest/report fields. Final platform evidence must use this expanded snapshot.

API content requires one or more sections; the production assembler always emits one deterministic semantic section. Storage preserves the complete structured DTO, while extraction's rich block/link fidelity and narrative producer remain Story 2.10, and coverage instrumentation remains Story 2.11. Existing read-tools/2 useful-guidance behavior and its SDK-policy tests are preserved. Interface, evaluation and release gates remain pending beyond the scoped adoption recorded in sign-offs.json.
