---
title: 'Story 2.5: Construct the typed relationship graph in two passes'
type: 'feature'
created: '2026-09-01'
baseline_commit: c3b0adc0db01e6e456e0cba0b60b9df1173ca99d
status: 'done'
review_loop_iteration: 0
context:
  - "{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md"
  - "{project-root}/_bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md"
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The catalog (2.4) persists symbols but no relationships. The `RELATIONSHIPS` pipeline stage is still a no-op, pass-one extraction captures **no** supertype / interface / see-also / overrides / return / field-type data (it explicitly ignores them and never captures link hrefs), and no relationship store exists. Inheritance, implementation, and reference relationships are therefore not queryable as first-class typed data — they would have to be re-derived from HTML at query time.

**Approach:** Extend pass-one Javadoc extraction to capture the relationship-source references (with their link hrefs where present); add a `RelationshipStore` domain port implemented by a `SqliteRelationshipStore` that computes each edge ID via the existing `ContentHashes.relationshipHash` (AD-22); and implement a real pass-two `RelationshipsStage` that reads the persisted catalog, resolves each documented reference to a catalog symbol, emits the 13 Javadoc **structural** typed edges, and materializes the two inverse pairs. Wire the stage at the existing `RELATIONSHIPS` slot.

## Boundaries & Constraints

**Always:**
- **Two-pass, never fabricate (AD-4):** pass one is the persisted catalog (2.4); pass two resolves references against the *completed* catalog only. An edge is emitted only when both endpoints are catalog symbols in the same SDK version.
- **Store is the sole edge-ID authority (AD-22):** `SqliteRelationshipStore` computes `edge_id` via `ContentHashes.relationshipHash`; the stage submits logical edge tuples and never computes an ID or assumes a hash recipe. Idempotent upsert; a different logical edge resolving to the same identity is an explicit clash (AD-4), never a silent overwrite.
- **Version scoping (AD-12):** every edge carries `sdkVersion`; edges connect only same-version symbols.
- **Inverse reconciliation:** `DIRECT_SUBCLASS` is derived as the inverse of every `EXTENDS_CLASS` edge and `IMPLEMENTED_BY` as the inverse of every `IMPLEMENTS` edge (and vice versa) — never parsed from a second source, so the two directions can never disagree. The other 9 structural types have no named inverse in the enum and are single-direction.
- **Determinism (NFR-13):** edge IDs are pure content hashes; an interrupted-then-resumed build yields byte-identical edges and IDs; reads return a deterministic order.
- **Dependency direction (AD-1):** `catalog-sqlite` depends only on `domain` (+ `org.xerial`); the stage reaches both stores only through `domain` ports.
- Synthetic fixtures only; no proprietary Luciad documentation committed.

**Ask First:**
- **Relationship-edge SQLite schema (DDL) is a governance `[NEEDS APPROVAL]` gate (Epic 3).** I will build one concrete, clearly-labeled *provisional* edges schema now — the same "design-provisionally-then-approve" split Story 2.4 used for the catalog table — and flag it for sign-off.
- **Reference-resolution strategy.** Proposed minimal ladder for 2.5: (1) a reference carrying an href into the ingested tree resolves via the catalog's persisted `(relativePath, anchor)` → exact; (2) otherwise a plain type name resolves by FQN, else by a *unique* simple-name match; (3) otherwise it is unresolved. The full resolution ladder and the first-class *explicit unresolved-reference* model belong to Story 2.6 (and 3.2) — confirm this minimal 2.5 strategy.

**Never:**
- No `WORKFLOW_REQUIRES` (needs workflow documents — Epic 3 / Story 3.4) and no example-usage edges `USES_TYPE` / `CONSTRUCTS` / `CALLS` (Epic 3 / Story 3.2; the enum still carries the pre-AD-30 coarse `EXAMPLE_USES` — leave it untouched). This story produces **only** the 13 Javadoc structural types.
- No first-class unresolved-reference record model and no traversal-scope reporting (Story 2.6); no bounded/recursive traversal queries (Story 2.7). In 2.5, an unresolved reference is surfaced as a recoverable failure — not fabricated, not silently dropped, not yet modeled as a stored unresolved record.
- No change to the approved canonical-symbol wire grammar or the deferred edge serialization grammar beyond the provisional form; no indexing, embedding, or retrieval; no network, compilation, or symbol-solving against JARs.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| All 13 structural types present | fixture exercising every structural type | all 13 types present in the graph after both passes | N/A |
| Class extends a documented superclass | pass two | `EXTENDS_CLASS`(sub→super) **and** derived `DIRECT_SUBCLASS`(super→sub) | N/A |
| Class implements a documented interface | pass two | `IMPLEMENTS` **and** derived `IMPLEMENTED_BY` | N/A |
| Linked reference (supertype/see-also/overrides) with href | href points into the ingested tree | resolved via catalog `(relativePath, anchor)`; typed edge emitted | N/A |
| Reference target outside the ingested tree | pass two | **no edge fabricated**; recorded as `RECOVERABLE` `StageFailure` (explicit unresolved model deferred to 2.6) | mapped to `FailurePolicy` |
| Ambiguous simple name | plain name with >1 candidate catalog symbol | left unresolved; no guessed edge | recorded as `RECOVERABLE` |
| Interrupted then resumed | `RELATIONSHIPS` partially completed, rerun | identical edges/IDs as an uninterrupted run; no duplicates | resume via checkpoint |
| Duplicate edge within a build | same logical edge submitted twice | idempotent upsert — one row | real identity clash → issue (AD-4) |

</frozen-after-approval>

## Code Map

**domain (ports & recipe — already present, reuse):**
- `app/domain/.../RelationshipType.java:10` -- 13 structural values already exist (`EXTENDS_CLASS`..`SEE_ALSO`); `EXAMPLE_USES`/`WORKFLOW_REQUIRES` are out of scope. No enum change.
- `app/domain/.../RelationshipEdge.java:16` -- logical edge tuple `(sourceSymbol, targetSymbol, type, sdkVersion)`; reused as the submission tuple — `source`/`targetSymbol` are the version-scoped canonical keys. No field change.
- `app/domain/.../ContentHashes.java:92` -- `relationshipHash(sdkVersion, sourceId, type, targetId, evidence)` is the edge-ID recipe; `SqliteRelationshipStore` becomes its sole caller (structural edges pass `evidence=""`).
- `app/domain/.../CatalogStore.java:36,42` -- `findByKey` / `allRecords` feed the pass-two resolution index.
- `app/domain/.../CatalogRecord.java`, `StoredCatalogRecord.java` -- persisted fields (`recordId`, `canonicalKey`, `typeFqn`, `simpleName`, `relativePath`, `anchor`) are the resolution-index source; no relationship-source data is persisted, so pass two re-parses for source facts.
- `app/domain/.../RelationshipStore.java` (NEW) -- port mirroring `CatalogStore`: `put(RelationshipEdge)` → `PutResult`/`PutStatus`, plus deterministic read methods, `extends AutoCloseable`.

**catalog-sqlite (engine — mirror the 2.4 store):**
- `app/catalog-sqlite/.../SqliteCatalogStore.java:64,127,167,239` -- exact pattern to copy: DDL constant + `UNIQUE` identity index, ID computed in `put`, `findBy*`, deterministic `ORDER BY`, clash detection by SQLite result code (not message text).
- `app/catalog-sqlite/.../SqliteRelationshipStore.java` (NEW) -- provisional `edges` table (`edge_id TEXT PRIMARY KEY`, `sdk_version`, `source_id`, `type`, `target_id`, `evidence`; `UNIQUE` identity index); opens the same build-scoped DB file; resolves each submitted canonical key → `record_id` from the `records` table; computes `edge_id` via `relationshipHash`; idempotent upsert; clash by result code.
- `app/catalog-sqlite/build.gradle.kts` -- already has `org.xerial`; no dependency change.

**ingest-javadoc (extend pass-one extraction):**
- `app/ingest-javadoc/.../ExtractedType.java:21` -- add `superclass`, `superInterfaces`, `seeAlso` references (each carrying its link href `relativePath`+`anchor` when the Javadoc renders a link).
- `app/ingest-javadoc/.../ExtractedMember.java:28` -- add `returnType`, `fieldType`, `overrides`, `specifiedBy`, `seeAlso` (href-bearing where linked). `params`/`throwsTypes` already carry the data for `PARAMETER_TYPE`/`CONSTRUCTOR_PARAMETER_TYPE`/`THROWS_TYPE`.
- `app/ingest-javadoc/.../JavadocFieldExtractor.java:304,313,330` -- `dispatchNote` ignores See Also/Overrides/type params; `splitNameProse` reads element **text only, never href**. Extend to parse the type-header superclass / implemented-interfaces / superinterfaces, the Overrides/Specified-By/See-Also member notes, and to **capture hrefs**.
- `app/ingest-javadoc/.../SignatureNormalizer.java` -- currently yields `erasedParameterTypes`; extend to expose the erased return type / field type used for `RETURN_TYPE`/`FIELD_TYPE`.
- `app/ingest-javadoc/.../SymbolRecord.java:33` -- `erasedParameterTypes` already backs `PARAMETER_TYPE`/`CONSTRUCTOR_PARAMETER_TYPE`.
- `app/ingest-javadoc/.../CanonicalSymbolMinter.java:49`, `JavadocCatalogSource.java:31` -- pass two re-parses via the facade and re-mints per type to obtain source-element canonical keys (same call `CatalogStage` uses); extend the facade to also expose the relationship-source references per type.

**cli pipeline (the stage & wiring):**
- `app/cli/.../pipeline/PipelineStage.java:24` -- `RELATIONSHIPS` slot already exists (currently no-op), directly after `CATALOG`. No enum change.
- `app/cli/.../pipeline/RelationshipsStage.java` (NEW) -- real pass-two `Stage`: open `CatalogStore` at `catalogDbPath()`, build the resolution index from `allRecords()`; parse tree → per type re-mint source keys → collect references → resolve targets (href→`(relativePath,anchor)`, else FQN/unique-simple-name) → emit the 13 typed edges + derived inverses → persist via `RelationshipStore`; unresolved refs → `RECOVERABLE` `StageFailure.ofFile`; store-integrity/open failures → `BUILD_INVALIDATING`.
- `app/cli/.../pipeline/CatalogStage.java:39,60,118` -- structural model to copy (source + `Function<BuildContext,Store>` fields, ingest-guard→`BUILD_INVALIDATING`, per-item persist, `close()` in `finally`).
- `app/cli/.../pipeline/IngestPipelineRunner.java:114` -- `withCatalogStage` injects the real `CATALOG` via a ternary; extend to also inject the real `RELATIONSHIPS` stage (constructor requires exactly one `Stage` per enum value).
- `app/cli/.../pipeline/BuildContext.java:91` -- `catalogDbPath()` = `stagingDir/sdkVersion/catalog.sqlite`; edges live in the same build-scoped DB (reuse this path).
- `app/cli/.../pipeline/{StageFailure,FailurePolicy,FailureSeverity,CheckpointStore}.java` -- failure model + resume idiom (skip completed `RELATIONSHIPS`).

## Tasks & Acceptance

**Execution:**
- [x] `app/domain/.../RelationshipStore.java` (NEW) -- add the port mirroring `CatalogStore` (`put(RelationshipEdge)`→`PutResult`/`PutStatus`, deterministic reads, `AutoCloseable`) -- the port the stage submits edges through (AD-1/AD-22).
- [x] `app/catalog-sqlite/.../SqliteRelationshipStore.java` + schema constants (NEW) -- provisional `edges` table; resolve endpoint keys→`record_id`; compute `edge_id` via `relationshipHash`; idempotent upsert; result-code clash detection -- persists version-scoped typed edges as the sole edge-ID authority.
- [x] `app/catalog-sqlite/.../package-info.java` -- note the relationship store + provisional edges schema now live here.
- [x] `app/ingest-javadoc/.../{ExtractedType,ExtractedMember}.java` (+ `Param`/reference carriers as needed) -- add the relationship-source fields with hrefs -- gives pass two the raw references to resolve.
- [x] `app/ingest-javadoc/.../JavadocFieldExtractor.java` (+ `SignatureNormalizer.java`) -- parse superclass/interfaces/superinterfaces, Overrides/Specified-By/See-Also notes, return/field types, capturing hrefs -- makes the 8 missing categories extractable equivalently across modern/legacy layouts.
- [x] `app/ingest-javadoc/.../JavadocCatalogSource.java` -- expose the relationship-source references per type for pass two -- the AD-1-clean facade the jsoup-free `cli` calls.
- [x] `app/cli/.../pipeline/RelationshipsStage.java` (NEW) -- the real pass-two stage (resolve → emit 13 edges → derive inverses → persist; unresolved→`RECOVERABLE`) -- the end-to-end graph-construction path.
- [x] `app/cli/.../pipeline/IngestPipelineRunner.java` -- inject the real `RELATIONSHIPS` stage alongside `CATALOG` -- wires it into production.
- [x] Tests -- `SqliteRelationshipStoreTest` (upsert idempotency, clash, resume determinism, deterministic order), `RelationshipsStageTest` (all-13-types fixture, both inverse pairs reconciled, href vs name resolution, unresolved→`RECOVERABLE`, issue→policy mapping), extraction tests for the new fields/hrefs, and each I/O-matrix row; extend synthetic fixtures with modern+legacy types exercising every structural type -- prove the AC.

**Acceptance Criteria:**
- Given a fixture exercising every one of the 13 Javadoc structural relationship types, when both passes complete, then all 13 types are present in the graph, and each `EXTENDS_CLASS`/`IMPLEMENTS` forward edge has its correct derived `DIRECT_SUBCLASS`/`IMPLEMENTED_BY` inverse (and vice versa).
- Given a documented reference that resolves to a catalog symbol, when pass two runs, then exactly one typed edge is emitted with both endpoints as version-scoped catalog symbols; given a reference that does not resolve, then no edge is fabricated and the reference is surfaced as a recoverable failure.
- Given the same inputs run twice (one interrupted then resumed), when both builds finish, then the persisted edges and computed edge IDs are byte-identical.
- Given `catalog-sqlite`, when `checkDependencyDirection` runs, then it depends only on `domain` (+ `org.xerial`) and no edge ID is computed outside `catalog-sqlite`.

## Design Notes

- **Provisional-schema precedent:** mirror Story 2.4 — a concrete working `edges` table labeled *provisional / pending governance sign-off*; capture endpoint identity + `evidence` explicitly so the approved Epic-3 DDL and edge grammar map on without reshaping. Do not stamp any provisional string as the AD-18 wire grammar.
- **Why re-parse in pass two:** the persisted catalog stores only identity + `(relativePath, anchor)`, not supertype/param/see-also facts. Pass two re-parses the tree (deterministic) for the *source* references and resolves *targets* against the completed catalog — this is the AD-4 two-pass shape, not a second parse of convenience.
- **Resolution prefers links:** a reference with an href into the ingested tree resolves via the catalog's persisted `(relativePath, anchor)` — reliable and member-precise (this is how `OVERRIDES`/`SPECIFIED_BY` reach a specific supertype method). Plain type names fall back to FQN, then unique simple-name. `OVERRIDES`/`SPECIFIED_BY` targets that carry no href and cannot be matched member-precisely stay unresolved rather than guessed.
- **Inverse derivation (golden):** after emitting forward edges, for each `EXTENDS_CLASS(a→b)` also emit `DIRECT_SUBCLASS(b→a)`, and for each `IMPLEMENTS(a→b)` also emit `IMPLEMENTED_BY(b→a)`. Deriving (not re-parsing "Direct Known Subclasses") guarantees the AC's forward⇄inverse consistency.
- **Edge identity:** the store maps each submitted canonical key → `record_id` on the shared DB, then `edge_id = relationshipHash(sdkVersion, sourceId, type, targetId, "")`. `evidence` is empty for structural edges (it is an example-link concern, AD-30) but the column exists for Epic-3 reuse.

## Verification

**Commands:**
- `cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew :ingest-javadoc:test :catalog-sqlite:test :cli:test checkDependencyDirection` -- expected: BUILD SUCCESSFUL; new tests pass; dependency direction green.
- `cd app && ./gradlew check` -- expected: all module checks + egress guards (`NoNetworkClasspathTest`, `NoJsoupConnectSourceTest`) pass.
