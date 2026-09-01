---
title: 'Story 2.4: Persist the versioned canonical catalog'
type: 'feature'
created: '2026-09-01'
baseline_commit: 5a7c5003e06d83e4bc2cd8245098b9ba39cfd460
status: 'done'
review_loop_iteration: 0
context:
  - "{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md"
  - "{project-root}/_bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md"
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 2.3 mints canonical `SymbolRecord`s only in memory — module-internal, no SDK-version scoping, no persistence, no HTML anchor — and the pipeline `CATALOG` stage is still a no-op. Nothing stores the catalog, so it is neither queryable nor reproducible across SDK versions, and two SDK versions sharing a simple name would collide.

**Approach:** Add a versioned SQLite catalog behind the `domain` `CatalogStore` port. A real `CATALOG` stage mints records (2.3), scopes them to an SDK version, attaches each record's relative HTML path + anchor, and submits resolved logical tuples to `CatalogStore`; `catalog-sqlite` computes the record ID (AD-22) from a new `domain`-declared `identityFields` spec and persists with a version-scoped unique key. Wire the stage into the runner.

## Boundaries & Constraints

**Always:**
- `catalog-sqlite` is the **sole authority** for record IDs (AD-22): it computes them via `ContentHashes.recordHash` from the `domain` `identityFields` spec. Ingest submits logical tuples and never computes IDs.
- **Version-scoped uniqueness:** `(sdkVersion, canonicalKey)` is unique; the same simple name in two SDK versions never collides. Every persisted record stores `sdkVersion`, `relativePath`, and `anchor`.
- **Resolvable to source:** stored `relativePath` + `anchor` must reconstruct the originating Javadoc location.
- **Determinism (NFR-13):** IDs are pure content hashes — no wall-clock/random/locale; an interrupted-then-resumed build yields byte-identical rows/IDs. Builds are immutable; write only under the build-scoped directory.
- **Dependency direction (AD-1):** `catalog-sqlite` depends only on `domain`; `ingest-javadoc` and `cli` reach it through the `CatalogStore` port. No `cli` import inside `ingest-javadoc`.
- **Never fabricate (AD-4):** a record that cannot be persisted is a recorded issue mapped to the failure policy — never a silent drop or a guessed value.
- Synthetic fixtures only; no proprietary Luciad documentation committed.

**Ask First:**
- **SQLite schema (DDL) is a governance approval gate.** I will design one concrete, clearly-labeled *provisional* schema now — the same "design-provisionally-then-approve" split Story 2.3 used for the canonical-symbol grammar — and flag it for sign-off. Ownership tension to confirm: the architecture spine says Epic 3 owns the schema gate, but `epics.md` places catalog persistence in Epic 2 / Story 2.4.
- **`sdkVersion` source (RESOLVED).** An **optional** explicit build input (`--sdk-version`) threaded through `BuildContext`. When absent, the build does **not** abort — records are scoped under a default version label. Version scoping (column + `(sdkVersion, canonicalKey)` uniqueness) is retained as cheap, future-proof insurance; the mandatory-input friction was dropped per human decision (see Spec Change Log). Parsing from the Javadoc tree (AD-12 wording) remains a possible later enhancement.
- **`identityFields` spec shape** (Epic-1 retro action item: "confirm the AD-22 `identityFields` registry approach before Epic 2 catalog work"). Recommended: a small `domain` registry naming the ordered identity fields per entity, applied to the 2.3 provisional key.

**Never:**
- No relationship graph or edge/link resolution (Story 2.5+); `throwsTypes`/param types stay raw documented text.
- No embedding, indexing, or retrieval.
- No change to the approved canonical-symbol **wire** grammar (still deferred to Epic 3) — keep using Story 2.3's provisional module-internal key as the join input.
- No rollback, backups, or active-build pointer (Epic 6). No new `domain` field sets on `ApiElement`/`MethodElement` beyond what identity requires.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Cross-version, same simple name | ingest SDK v1, then v2, into the same catalog | two rows with distinct `(sdkVersion, canonicalKey)` keys; no collision | N/A |
| Resolvable to source | any persisted record | `relativePath` + `anchor` stored and reconstruct the source location | N/A |
| Duplicate computed ID within one build | two submitted tuples hash to the same record ID | idempotent upsert — one row, no duplicate | if it reflects a real identity clash, record an issue (AD-4) |
| Interrupted then resumed build | `CATALOG` partially completed, rerun | identical rows/IDs as an uninterrupted run | resume via checkpoint; no duplicate rows |
| Minting issue from 2.3 | `SymbolMintResult.issues` non-empty | mapped to `StageFailure` per `FailurePolicy` | fail-fast aborts; collect-and-continue records + proceeds |
| `sdkVersion` not supplied | no `--sdk-version` given | build proceeds; records are scoped under a default version label (no abort) | N/A — the version column + uniqueness still apply, just under the default label |
| `sdkVersion` malformed | `--sdk-version` present but not a safe path segment (`..`, separators, blank) | rejected at parse time with a clear CLI error | `INVALID_OPTION_VALUE` |

</frozen-after-approval>

## Code Map

- `app/catalog-sqlite/build.gradle.kts` -- currently only `api(project(":domain"))`; add `implementation(libs.sqlite.jdbc)` (Xerial, allowed — not banned for this module) + JUnit test deps.
- `app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/package-info.java` -- only file today; module is a bare skeleton. Add `SqliteCatalogStore` (implements the port), schema/DDL constants, and JDBC wiring here.
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/CatalogStore.java:11` -- **empty** port. Add the write/read method(s): submit a resolved logical record tuple (canonicalKey, kind, sdkVersion, relativePath, anchor, identity inputs), dedupe by computed ID.
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/ContentHashes.java:65` -- `recordHash(String entityType, List<String> identityFields)`. The **consumer** exists; `catalog-sqlite` becomes its sole caller.
- `app/domain/.../ApiElement.java:13`, `MethodElement.java:13` -- skeleton `(canonicalSymbol, sdkVersion)`; the `identityFields` spec references these entity types. Do not expand field sets beyond identity.
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/PipelineStage.java:22` -- `CATALOG` is the first stage (exec order = declaration order).
- `app/cli/.../pipeline/Stage.java:33` -- `void execute(BuildContext, Consumer<StageFailure>)`; `Stage.noOp()` is the current stub.
- `app/cli/.../pipeline/IngestPipelineRunner.java:49,87,114` -- `STUB_SDK_VERSION="0.0.0-stub"`; `withDefaultStages()` wires eight no-ops; dispatch loop + checkpoint/resume + severity handling. Add a wiring path that injects a real `CATALOG` stage while the other seven stay no-ops.
- `app/cli/.../pipeline/BuildContext.java:31,63` -- `(buildId, sealedInputDigest, stagingDir)`; no `sdkVersion`, no `{sdkVersion}/{buildId}` dir. Thread `sdkVersion` and expose the build-scoped catalog path.
- `app/cli/.../pipeline/CheckpointStore.java:32` -- resume idiom to mirror for "persist under a build dir, skip completed stage".
- `app/cli/.../IngestCommand.java:29,46,56` -- `pipelineFactory` hook for injecting a real pipeline in wiring/tests.
- `app/cli/.../IngestArguments.java:27` -- `(javadoc, samples, guides, model, out, trainings?)`; if `sdkVersion` becomes an explicit input it is added here (contract has tests).
- `app/ingest-javadoc/.../CanonicalSymbolMinter.java:49` -- `SymbolMintResult mint(ExtractedType)`; the CATALOG stage calls this per discovered type.
- `app/ingest-javadoc/.../SymbolRecord.java:33` -- `(canonicalKey, kind, typeFqn, simpleName, erasedParameterTypes, variants, relativePath, memberIndex)`; **no sdkVersion, no anchor** — 2.4 supplies both when building the logical tuple.
- `app/build.gradle.kts:14,82` -- `allowedProjectDeps` + `checkDependencyDirection`; `catalog-sqlite -> {domain}` only; `org.xerial` not banned for `catalog-sqlite`.
- `app/gradle/libs.versions.toml` -- `sqlite-jdbc = org.xerial:sqlite-jdbc:3.53.2.1` declared but unused; apply it here.

## Tasks & Acceptance

**Execution:**
- [x] `app/domain/.../IdentityFields.java` (NEW) -- declare the AD-22 per-entity `identityFields` spec (ordered identity fields for the catalog record entity), applied to the 2.3 provisional key -- gives `catalog-sqlite` the sole, canonical ID recipe.
- [x] `app/domain/.../CatalogStore.java` -- add the write method(s): accept a resolved logical record tuple; `catalog-sqlite` computes the ID and dedupes -- the port ingest submits through (AD-22).
- [x] `app/catalog-sqlite/build.gradle.kts` -- add `implementation(libs.sqlite.jdbc)` + JUnit test deps -- wire Xerial (first module to do so).
- [x] `app/catalog-sqlite/.../SqliteCatalogStore.java` + schema constants (NEW) -- provisional DDL (records table keyed by computed ID; unique index on `(sdkVersion, canonicalKey)`; columns for kind, typeFqn, simpleName, relativePath, anchor); idempotent upsert; opens/creates the build-scoped DB file -- persists version-scoped records, resolvable to source.
- [x] `app/catalog-sqlite/.../package-info.java` -- update: catalog store + provisional schema now live here (was "deferred to Epic 3").
- [x] `app/cli/.../pipeline/CatalogStage.java` (NEW) -- real `CATALOG` `Stage`: discover types (2.1) → extract (2.2) → `mint` (2.3) → build tuples with `sdkVersion` + derived anchor → submit via `CatalogStore`; map `SymbolMintResult.issues` onto `StageFailure`/`FailurePolicy` -- the end-to-end persistence path.
- [x] `app/cli/.../pipeline/BuildContext.java` -- thread `sdkVersion`; expose the `{sdkVersion}/{buildId}` build-scoped catalog path -- version scoping + immutable build layout.
- [x] `app/cli/.../pipeline/IngestPipelineRunner.java` (+ `IngestCommand`/`IngestArguments` as the `sdkVersion`-source decision requires) -- inject the real `CATALOG` stage; retire `STUB_SDK_VERSION` for the persisted path -- wires the stage into production.
- [x] Tests -- `SqliteCatalogStoreTest` (cross-version no-collision, upsert idempotency, path/anchor round-trip, resume determinism), `CatalogStageTest` (issue→policy mapping, mint→submit flow), and each I/O-matrix row; extend synthetic fixtures with a second SDK-version tree sharing a simple name -- prove the AC.

**Acceptance Criteria:**
- Given two SDK versions ingested into the same catalog and a simple name present in both, when the build completes, then each version's record has its own version-scoped unique key with no cross-version collision, and each record's relative HTML path + anchor is stored and resolves back to source.
- Given the same inputs run twice (one interrupted then resumed), when both builds finish, then the persisted rows and computed IDs are identical.
- Given `catalog-sqlite`, when `checkDependencyDirection` runs, then it depends only on `domain` (+ `org.xerial`) and no ID is computed outside `catalog-sqlite`.

## Spec Change Log

- **Post-review, human renegotiation of frozen intent (2026-09-01).** Triggered by: reconsideration of whether a mandatory SDK version is warranted given APIs change little across versions. Amended: `sdkVersion` input made **optional with a default label** instead of a hard `BUILD_INVALIDATING` abort when absent; the frozen I/O matrix row and the `sdkVersion` Ask-First item updated to match. Known-bad state avoided: forcing every offline rebuild to know/supply a version, and a real `ingest` silently aborting (exit 0) when it was omitted. KEEP: version scoping itself — the `sdkVersion` column and `(sdkVersion, canonicalKey)` uniqueness are retained (cheap, future-proof, and the guard against cross-version wrong-answers); only the mandatory-input friction was removed.

## Design Notes

- **Provisional-schema precedent:** mirror Story 2.3 — build a concrete, working schema labeled *provisional / pending governance sign-off*; capture identity inputs explicitly so a later approved DDL maps on without reshaping. Do not stamp Story 2.3's provisional key as the AD-18 wire join key.
- **ID authority (AD-22):** the CATALOG stage submits `(entityType, ordered identity field values)`; `SqliteCatalogStore` is the only code that calls `ContentHashes.recordHash` and assigns the primary key. This closes the Epic-1 retro prerequisite.
- **sdkVersion:** optional explicit ingest input → `BuildContext.sdkVersion`; absent resolves to a default label (no abort). The `CATALOG` stage reads the version from `BuildContext` (consistent with `catalogDbPath()` and the manifest), not from a separately injected value.

## Verification

**Commands:**
- `cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew :catalog-sqlite:test :cli:test checkDependencyDirection` -- expected: BUILD SUCCESSFUL; new tests pass; dependency direction green.
- `cd app && ./gradlew check` -- expected: all module checks + egress guards (`NoNetworkClasspathTest`) pass.

## Suggested Review Order

**ID authority (AD-22) — the core invariant**

- Start here: the domain spec that names the ordered identity fields per entity; unknown kinds now throw, no silent default.
  [`IdentityFields.java:51`](../../app/domain/src/main/java/com/luciadmcp/knowledge/domain/IdentityFields.java#L51)

- The port ingest submits through — logical tuple in, computed ID + outcome out; store is the sole ID authority.
  [`CatalogStore.java:33`](../../app/domain/src/main/java/com/luciadmcp/knowledge/domain/CatalogStore.java#L33)

- The only caller of `recordHash`: computes the ID from the spec and upserts idempotently.
  [`SqliteCatalogStore.java:128`](../../app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStore.java#L128)

**Version scoping & no cross-version collision (the AC)**

- Provisional DDL: `UNIQUE(sdk_version, canonical_key)` is what makes the same name in two SDKs not collide.
  [`SqliteCatalogStore.java:65`](../../app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStore.java#L65)

- `sdkVersion` folded into the sealed digest with an explicit `<absent>` marker, so a version change is a new build, not a resume.
  [`BuildContext.java:113`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java#L113)

- Build- and version-scoped catalog path, kept under coordinator-owned staging (AD-25).
  [`BuildContext.java:91`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java#L91)

**Pipeline wiring (no-op → real)**

- Real CATALOG stage at its slot; other seven stay no-ops; store opened per build from the version-scoped path.
  [`IngestPipelineRunner.java:114`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunner.java#L114)

- Stage entry point: discover→extract→mint→submit; store open/put/close failures map to `BUILD_INVALIDATING` (AD-4).
  [`CatalogStage.java:62`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/CatalogStage.java#L62)

- Production default now wires the real stage per invocation.
  [`IngestCommand.java:50`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestCommand.java#L50)

**Boundary safety**

- `sdkVersion` accepted only as a safe single path segment — rejects `..`/separators before it becomes a directory name.
  [`BuildContext.java:74`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java#L74)

- Same validation enforced at parse time; `--sdk-version` is a scalar, not a path.
  [`IngestArgumentParser.java:102`](../../app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestArgumentParser.java#L102)

- Clash detection by SQLite result code (not message text); a PK collision is never mislabeled a clash.
  [`SqliteCatalogStore.java:160`](../../app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStore.java#L160)

- AD-1-clean facade: the jsoup-free `cli` reaches discover→extract→mint through this ingest-side type.
  [`JavadocCatalogSource.java:1`](../../app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocCatalogSource.java#L1)

**Tests (proofs)**

- Cross-version no-collision at the store.
  [`SqliteCatalogStoreTest.java:47`](../../app/catalog-sqlite/src/test/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStoreTest.java#L47)

- Same version-key / different record-id drives the real SQL UNIQUE branch.
  [`SqliteCatalogStoreTest.java:168`](../../app/catalog-sqlite/src/test/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStoreTest.java#L168)

- End-to-end through the production runner: catalog persisted at the version-scoped path.
  [`CatalogStageTest.java:202`](../../app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/CatalogStageTest.java#L202)

- `--sdk-version` parsing: valid, absent, blank, and traversal-rejection.
  [`SdkVersionArgumentTest.java:22`](../../app/cli/src/test/java/com/luciadmcp/knowledge/cli/SdkVersionArgumentTest.java#L22)

- The AD-1 facade in isolation: order, issue-merge, warnings, empty tree.
  [`JavadocCatalogSourceTest.java:19`](../../app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocCatalogSourceTest.java#L19)
