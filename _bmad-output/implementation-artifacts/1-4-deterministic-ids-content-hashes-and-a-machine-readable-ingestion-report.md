# Story 1.4: Deterministic IDs, content hashes, and a machine-readable ingestion report

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a release operator,
I want stable record IDs and content hashes plus a JSON ingestion report (throughput, error summary) emitted at the end of a run,
so that re-ingesting the same inputs is verifiably reproducible and a failed run is diagnosable without reading logs by hand.

## Acceptance Criteria

1. Two ingestion runs over byte-identical inputs produce identical build identity and identical content hashes. Nothing in the hash inputs is random or wall-clock-derived.
2. Five content hashes are defined as pure, deterministic functions and unit-tested against fixed inputs and known outputs:
   - `sourceHash` — over the raw bytes of an input file (the one genuinely new hash).
   - `recordHash` — over a canonical record's identity fields.
   - `relationshipHash` — over SDK version + source ID + relationship type + target ID + evidence.
   - `semanticHash` — over the exact normalized text handed to the embedder.
   - `toolchainFingerprint` — over parser + schema + normalizer + relationship-rules + embedding config versions.
3. Changing one input byte changes only the hashes of records whose content actually includes that byte; unrelated records keep the same hash (locality — no global salt, no run-wide nonce).
4. A change to any toolchain component (parser, schema, normalization, resolution rules, embedding config) changes `toolchainFingerprint`. The manifest carries the fingerprint so a later load can refuse a build whose fingerprint does not match the runtime.
5. `buildId` is a content hash over the complete, ordered build-input set — now including input-file bytes via `sourceHash`, not only input paths. The provisional path-only digest from Story 1.3 is replaced. Same inputs still resume into the same staging directory; changed inputs still yield a new `buildId` and a fresh run.
6. A machine-readable JSON ingestion report is written to a fixed path under the build's output at the end of every run — including aborted runs. The report is deterministic given the same run outcome (stable key order, no timestamps in the hashed/compared content).
7. The report includes: per-stage throughput (which stages ran, which were skipped on resume, which completed), the collected failures with their severity and the file each is tied to, and counts for exact / ambiguous / unresolved example relationships grouped by relationship type and resolution kind. With Story-1.3 stub stages these relationship counts are structurally present and zero; the schema is fixed here so later epics populate it without changing the shape.
8. No pipeline path — hashing, report write, checkpoint, restart, or resume — performs a network request (verified under the existing egress guard).

## Tasks / Subtasks

- [ ] Task 1: Define the five hash functions in `domain` as pure deterministic helpers (AC: 1, 2, 3)
  - [ ] Add a `ContentHashes` (or equivalent) type in `com.luciadmcp.knowledge.domain` exposing `sourceHash(byte[])`, `recordHash(...)`, `relationshipHash(...)`, `semanticHash(String)`, and `toolchainFingerprint(...)`. SHA-256, lowercase hex, UTF-8 for text — reuse the exact digest idiom already in `BuildContext.sha256Hex`.
  - [ ] Drive `recordHash` / `relationshipHash` from an explicit ordered `identityFields` list per entity so the recipe is declared, not implicit. Relationship identity fields are exactly: SDK version, source ID, relationship type, target ID, evidence.
  - [ ] `semanticHash` hashes the already-normalized embedder input string verbatim — this type does not itself normalize text (that lives in the embedder later); it hashes what it is given.
  - [ ] Field separators must be unambiguous (no delimiter that can appear in a field value collides two distinct tuples into one hash). Document the chosen separator.
- [ ] Task 2: Unit-test each hash for determinism, known-answer, and locality (AC: 1, 2, 3)
  - [ ] Same input twice → identical hash; a fixed input → a pinned expected hex string (known-answer, guards accidental recipe drift).
  - [ ] Two records differing in one identity field → different `recordHash`; two records with identical identity fields → identical `recordHash`.
  - [ ] Changing one byte of one source file changes that file's `sourceHash` and no other file's.
  - [ ] Separator-collision test: tuples like `("a","bc")` and `("ab","c")` hash differently.
- [ ] Task 3: Add `toolchainFingerprint` to the manifest and prove it gates reuse (AC: 4)
  - [ ] Extend `IndexManifest` (currently `buildId` + `sdkVersion`) with the toolchain fingerprint field. Keep it a record; do not invent validation/activation/retention fields that belong to Epic 6.
  - [ ] Compute the fingerprint from the toolchain component versions available now (parser/schema/normalizer/relationship-rules/embedding config); where a component is still a stub, use its stub version constant so the recipe is exercised end-to-end.
  - [ ] Test: changing any one component version changes the fingerprint; an equal component set reproduces it.
- [ ] Task 4: Replace the provisional `buildId` recipe with the content-inclusive recipe (AC: 1, 5)
  - [ ] `BuildContext.forInputs` folds `sourceHash` of the input files into the sealed digest, not just normalized path strings. Preserve the existing ordered, deterministic construction and the `b-` + prefix `buildId` form.
  - [ ] Same inputs → same `buildId` and same staging dir (resume); one changed input byte → different `buildId` (fresh run). Update `BuildContextTest` expectations and drop the "PROVISIONAL" Javadoc note.
  - [ ] Keep it egress-free and side-effect-free beyond reading the declared input files.
- [ ] Task 5: Emit the machine-readable JSON ingestion report (AC: 6, 7)
  - [ ] Write a small hand-rolled JSON serializer in `cli` (no new third-party dependency — Story 1.3 deliberately used text to avoid preempting this; keep the zero-egress, minimal-dependency posture). Deterministic key order, escaped strings.
  - [ ] Report shape: `buildId`, `sdkVersion`, `toolchainFingerprint`, `aborted`, `activationOccurred`, `stages` (each stage: ran | skipped-on-resume | completed), `failures` (severity + file + stage), and `relationshipCounts` grouped by relationship type then resolution kind with exact/ambiguous/unresolved sub-counts.
  - [ ] Written to a fixed path under the build output (e.g. `{out}/{buildId}/ingestion-report.json`) at the end of `runToResult`, including the aborted-return paths.
  - [ ] Extend `PipelineResult` (or add a report-model type) so the report is built from the run outcome already tracked; do not recompute state.
- [ ] Task 6: Tests for the report and the no-egress guarantee (AC: 6, 7, 8)
  - [ ] Clean run, fail-fast abort, and collect-and-continue runs each write a report; assert the JSON parses, has stable key order, and reflects the correct stage/failure/count state.
  - [ ] Aborted run still writes a report with `aborted=true` and `activationOccurred=false`.
  - [ ] Two identical runs produce byte-identical report content (determinism).
  - [ ] Extend the pipeline egress test so the hashing + report-write + resume cycle records zero host resolutions; the trainings/network-exception path is deliberately not exercised.
- [ ] Task 7: Build, verify dependency direction, finalize (all AC)
  - [ ] `./gradlew cli:test domain:test checkDependencyDirection` green (run from repo root; task has no leading colon).
  - [ ] Fill Dev Agent Record, File List, Change Log; mark tasks [x]; set Status review; update sprint-status.yaml 1-4 → review.

## Dev Notes

### What this story is, precisely

Story 1.3 built the resumable pipeline skeleton and deliberately left two things for here: the formal content hashes and the JSON report. It used a path-only digest for `buildId` and a line-based text result on purpose, to avoid preempting this story. This story installs the real hash recipe and the report, still with stub stages — so the *machinery* is proven now and later epics that produce real records/edges plug into a fixed contract.

### Scope boundaries — read before implementing

- **The five hashes reconcile onto invariants that already exist; do not invent parallel machinery.** `recordHash`/`relationshipHash` are the canonical identity-field hashes; `semanticHash` is the embedding fingerprint over normalized text; `toolchainFingerprint` is the compatibility fingerprint (used here in its restart-safety role). Only `sourceHash` (input-file bytes) is genuinely new. [Source: architecture/ARCHITECTURE-SPINE.md#line-243]
- **Records and edges do not exist yet.** `catalog-sqlite` is an empty package stub and the domain record types (`ApiElement`, `RelationshipEdge`, etc.) land in Epics 2–3. So `recordHash`/`relationshipHash` are defined and unit-tested here as **pure functions over their declared identity fields** using synthetic inputs — they are not yet wired to a real catalog. Define the function and its identity-field contract; do not build a catalog to feed it.
- **SQLite uniqueness constraints are out of scope for this story.** The epics AC mentions them, but there is no schema yet. The deliverable here is the deterministic ID recipe those constraints will key on; the constraints themselves land with `catalog-sqlite` in Epic 2. State this in Completion Notes rather than building a schema.
- **Relationship-resolution counts are structurally present, not populated.** With stub stages there are zero relationships. Fix the report's grouping shape (relationship type → resolution kind → exact/ambiguous/unresolved) now so Epic 2/3 fill it without reshaping.
- **Cross-build incremental indexing stays out of scope.** These hashes support restart safety now; incremental builds are a later possibility, not this story. [Source: epics.md#Story-1.4]

### Source tree — what to touch

- `app/domain/.../ContentHashes.java` (NEW) — the five pure hash functions + identity-field specs.
- `app/domain/.../IndexManifest.java` (UPDATE) — add `toolchainFingerprint`. Currently only `buildId` + `sdkVersion`; keep it a minimal record, no Epic-6 fields. [Source: domain/IndexManifest.java]
- `app/cli/.../pipeline/BuildContext.java` (UPDATE) — fold `sourceHash` of input files into the sealed digest; remove the PROVISIONAL note. Reuse its existing `sha256Hex`. [Source: cli/pipeline/BuildContext.java:56-90]
- `app/cli/.../pipeline/IngestPipelineRunner.java` (UPDATE) — compute the manifest fingerprint; write the report on every return path including `aborted(...)`. [Source: cli/pipeline/IngestPipelineRunner.java:89-136]
- `app/cli/.../pipeline/PipelineResult.java` (UPDATE) or a new report-model type — carry what the report needs. [Source: cli/pipeline/PipelineResult.java]
- `app/cli/.../pipeline/IngestionReport*.java` (NEW) — report model + deterministic JSON writer.
- Tests alongside each, mirroring the Story-1.3 test layout under `app/cli/src/test/.../pipeline/` and a new `app/domain/src/test/...`.

### Hard constraints

- **Zero egress.** No network on any path. Reuse the Story-1.2 egress guard (`EgressGuard.arm()/disarm()/assertInstalledAndTrips()/violations()`) and extend the existing `PipelineEgressTest`. The only ever-permitted network path (training-material ingestion) is out of scope and must not be touched. [Source: architecture/ARCHITECTURE-SPINE.md#AD-9, #AD-28]
- **No new third-party dependencies.** Hand-roll the JSON writer. SHA-256 is a mandated JDK algorithm — reuse the existing digest idiom. Keep the minimal-dependency, air-gap posture.
- **Determinism is the whole point.** No `Date`, no `Random`, no `HashMap` iteration order in anything that feeds a hash or the compared report content. Use ordered structures and fixed key order. [Source: architecture/ARCHITECTURE-SPINE.md#line-252 "never random, never wall-clock-derived"]
- **Java package root is `com.luciadmcp.knowledge.*`.** No bare `luciad` package segment. Directory and Gradle module names are unaffected.
- **Dependency direction.** The build enforces `checkDependencyDirection` (registered per-module, no leading colon). `domain` depends on nothing app-internal; `cli` reaches `domain` through config's `api(project(":domain"))`. Don't add module edges — everything here uses JDK + existing `domain` types.
- **Fixtures are synthetic.** No proprietary Luciad docs/samples in the repo. Use synthetic byte arrays / temp files for hash and source tests, as Story 1.3 did.

### Testing standards

- JUnit 5, `@TempDir` for filesystem work, one behavior per test with a descriptive name (match the Story-1.3 style: `sameInputsProduceSameId...`, `changedInputByteChangesOnlyThatRecordHash`, etc.).
- Known-answer tests must pin an explicit expected hex string so a recipe change fails loudly rather than silently drifting.
- Run `./gradlew cli:test domain:test checkDependencyDirection` from the repo root. Behind the corporate proxy, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` is needed for dependency resolution.

### Project Structure Notes

- All new code lands in existing modules (`domain`, `cli`); no new module, no `settings.gradle.kts` change.
- The report path convention `{out}/{buildId}/ingestion-report.json` sits alongside the eventual immutable build directory; it does not implement activation or the active-pointer (Epic 6) — it only writes a diagnostic artifact for the run.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-1.4] — acceptance criteria, five-hash definitions, out-of-scope notes.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#AD-5] — immutable builds, validate-then-activate (why the report must not mutate a sealed build).
- [Source: .../ARCHITECTURE-SPINE.md#AD-6] — compatibility fingerprint over schema/index/embedding params; the `toolchainFingerprint` role.
- [Source: .../ARCHITECTURE-SPINE.md#AD-8] — identical embedding fingerprint at index- and query-time; the `semanticHash` basis.
- [Source: .../ARCHITECTURE-SPINE.md#AD-17] — `cli ingest` sole coordinator; single `buildId` as content hash over ordered inputs.
- [Source: .../ARCHITECTURE-SPINE.md#AD-22] — `catalog.sqlite` sole authority for IDs from a `domain`-declared `identityFields` spec per entity.
- [Source: .../ARCHITECTURE-SPINE.md#AD-25] — checkpoints only in coordinator-owned staging; resume matched by sealed input digest.
- [Source: .../ARCHITECTURE-SPINE.md#line-243, #line-252] — five-hash reconciliation; deterministic-ID convention (never random / wall-clock).
- [Source: .../ARCHITECTURE-SPINE.md#AD-9, #AD-28] — zero egress; the single scoped network exception (out of scope here).
- [Source: app/cli/.../pipeline/BuildContext.java] — provisional digest to replace; `sha256Hex` to reuse.
- [Source: app/cli/.../pipeline/IngestPipelineRunner.java, PipelineResult.java] — run loop and outcome the report is built from.
- [Source: app/domain/.../IndexManifest.java] — manifest record to extend.

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
