---
baseline_commit: 9fb75f15d145fc982a3724553e669d288d5f07ec
---

# Story 1.4: Deterministic IDs, content hashes, and a machine-readable ingestion report

Status: done

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

- [x] Task 1: Define the five hash functions in `domain` as pure deterministic helpers (AC: 1, 2, 3)
  - [x] Add a `ContentHashes` (or equivalent) type in `com.luciadmcp.knowledge.domain` exposing `sourceHash(byte[])`, `recordHash(...)`, `relationshipHash(...)`, `semanticHash(String)`, and `toolchainFingerprint(...)`. SHA-256, lowercase hex, UTF-8 for text — reuse the exact digest idiom already in `BuildContext.sha256Hex`.
  - [x] Drive `recordHash` / `relationshipHash` from an explicit ordered `identityFields` list per entity so the recipe is declared, not implicit. Relationship identity fields are exactly: SDK version, source ID, relationship type, target ID, evidence.
  - [x] `semanticHash` hashes the already-normalized embedder input string verbatim — this type does not itself normalize text (that lives in the embedder later); it hashes what it is given.
  - [x] Field separators must be unambiguous (no delimiter that can appear in a field value collides two distinct tuples into one hash). Document the chosen separator.
- [x] Task 2: Unit-test each hash for determinism, known-answer, and locality (AC: 1, 2, 3)
  - [x] Same input twice → identical hash; a fixed input → a pinned expected hex string (known-answer, guards accidental recipe drift).
  - [x] Two records differing in one identity field → different `recordHash`; two records with identical identity fields → identical `recordHash`.
  - [x] Changing one byte of one source file changes that file's `sourceHash` and no other file's.
  - [x] Separator-collision test: tuples like `("a","bc")` and `("ab","c")` hash differently.
- [x] Task 3: Add `toolchainFingerprint` to the manifest and prove it gates reuse (AC: 4)
  - [x] Extend `IndexManifest` (currently `buildId` + `sdkVersion`) with the toolchain fingerprint field. Keep it a record; do not invent validation/activation/retention fields that belong to Epic 6.
  - [x] Compute the fingerprint from the toolchain component versions available now (parser/schema/normalizer/relationship-rules/embedding config); where a component is still a stub, use its stub version constant so the recipe is exercised end-to-end.
  - [x] Test: changing any one component version changes the fingerprint; an equal component set reproduces it.
- [x] Task 4: Replace the provisional `buildId` recipe with the content-inclusive recipe (AC: 1, 5)
  - [x] `BuildContext.forInputs` folds `sourceHash` of the input files into the sealed digest, not just normalized path strings. Preserve the existing ordered, deterministic construction and the `b-` + prefix `buildId` form.
  - [x] Same inputs → same `buildId` and same staging dir (resume); one changed input byte → different `buildId` (fresh run). Update `BuildContextTest` expectations and drop the "PROVISIONAL" Javadoc note.
  - [x] Keep it egress-free and side-effect-free beyond reading the declared input files.
- [x] Task 5: Emit the machine-readable JSON ingestion report (AC: 6, 7)
  - [x] Write a small hand-rolled JSON serializer in `cli` (no new third-party dependency — Story 1.3 deliberately used text to avoid preempting this; keep the zero-egress, minimal-dependency posture). Deterministic key order, escaped strings.
  - [x] Report shape: `buildId`, `sdkVersion`, `toolchainFingerprint`, `aborted`, `activationOccurred`, `stages` (each stage: ran | skipped-on-resume | completed), `failures` (severity + file + stage), and `relationshipCounts` grouped by relationship type then resolution kind with exact/ambiguous/unresolved sub-counts.
  - [x] Written to a fixed path under the build output (e.g. `{out}/{buildId}/ingestion-report.json`) at the end of `runToResult`, including the aborted-return paths.
  - [x] Extend `PipelineResult` (or add a report-model type) so the report is built from the run outcome already tracked; do not recompute state.
- [x] Task 6: Tests for the report and the no-egress guarantee (AC: 6, 7, 8)
  - [x] Clean run, fail-fast abort, and collect-and-continue runs each write a report; assert the JSON parses, has stable key order, and reflects the correct stage/failure/count state.
  - [x] Aborted run still writes a report with `aborted=true` and `activationOccurred=false`.
  - [x] Two identical runs produce byte-identical report content (determinism).
  - [x] Extend the pipeline egress test so the hashing + report-write + resume cycle records zero host resolutions; the trainings/network-exception path is deliberately not exercised.
- [x] Task 7: Build, verify dependency direction, finalize (all AC)
  - [x] `./gradlew cli:test domain:test checkDependencyDirection` green (run from repo root; task has no leading colon).
  - [x] Fill Dev Agent Record, File List, Change Log; mark tasks [x]; set Status review; update sprint-status.yaml 1-4 → review.

### Review Findings

**Resolved since last pass — verified against current code (2026-08-31):**

- [x] [Review][Patch] Relationship-resolution report shape doesn't match the spec's stated grouping — RESOLVED: `IngestionReport.ResolutionCell` implements the joint `(relationshipType, resolutionKind, exact, ambiguous, unresolved)` breakdown per the 2026-08-25 decision. [`IngestionReport.java:64-78`]
- [x] [Review][Patch] `IngestionReport`/`StageStatus` were `public` but package-only — RESOLVED: both are package-private records/enum (no `public` modifier). [`IngestionReport.java:30`, `StageStatus.java:15`]
- [x] [Review][Patch] No check that `--out` is disjoint from the input roots — RESOLVED: `IngestCommand.requireOutputNotNestedInInputs`/`requireNotAncestor` reject `--out` nested in any input root. [`IngestCommand.java:95-113`]
- [x] [Review][Patch] `writeReport`'s write failure discarded the already-computed `PipelineResult` — RESOLVED: `writeReport` now catches `UncheckedIOException` internally, logs a warning, and the caller still returns its already-built `PipelineResult`. [`IngestPipelineRunner.java:220-227`]
- [x] [Review][Patch] `ContentHashesTest` had no per-field locality test for `relationshipHash` — RESOLVED: `changingOneRelationshipFieldChangesOnlyThatRelationshipHash` covers it. [`ContentHashesTest.java:96-110`]
- [x] [Review][Patch] `IngestPipelineRunner` class Javadoc overclaimed report coverage on crash — RESOLVED: class Javadoc now states "a run that crashes with an uncaught exception before reaching a return point... writes no report." [`IngestPipelineRunner.java:40-41`]

**Open:**

- [x] [Review][Patch] Task 7's Debug Log Reference records a narrower `domain:checkDependencyDirection cli:checkDependencyDirection` invocation than the spec's own Task 7 command (bare `checkDependencyDirection`, the full aggregate task). Re-run the aggregate command and correct the Debug Log Reference text to match what was actually verified. [Dev Agent Record, this file] — FIXED: re-ran with the bare aggregate `checkDependencyDirection` task; Debug Log Reference below corrected to match.
- [x] [Review][Patch] `IngestArgumentParser.requireLocalPath` treats any value starting with `\\?\` or `\\.\` as local, but `\\?\UNC\server\share` is the Windows extended-length form of a genuine UNC network path — it starts with `\\?\` and is incorrectly waved through, bypassing the AD-9 zero-egress argument-contract guard this method exists to enforce. Check for the `\\?\UNC\` prefix specifically and reject it as non-local. [`IngestArgumentParser.java:156-159`] — FIXED: explicit `\\?\UNC\` rejection added before the extended-length-device-path allow; covered by new test `extendedLengthUncPathIsRejectedAsNonLocal`.
- [x] [Review][Patch] `IngestCommand.requireNotAncestor` compares `.toAbsolutePath().normalize()` paths, not `.toRealPath()` — if `--out` is (or resolves through) a symlink/junction whose real target sits inside an input root, the nested-output guard is bypassed even though the guard exists specifically to protect the buildId-determinism guarantee this story introduces. No test exercises this guard directly either (no `IngestCommandTest`). Resolve real paths for the input roots (already required to exist) before the ancestry check; `--out` may not exist yet, so only real-path it when it does. [`IngestCommand.java:95-113`] — FIXED: `requireOutputNotNestedInInputs` now resolves `--out` by walking up to its nearest existing/symlinked ancestor (following any symlinks along the way) and comparing real paths against each real input root; covered by new tests `outputNestedInsideInputDirectoryIsRejected` and `outputSymlinkedIntoInputDirectoryIsRejected` (the latter skips gracefully where symlink creation isn't permitted).
- [x] [Review][Patch] `CheckpointStore.load()` silently discards on `IllegalArgumentException` (digest mismatch or corrupt file) via a bare `catch { return; }` with no log line — a user expecting a resume gets a silent fresh run with no diagnostic trail. Add a single warning log so the behavior is observable. [`CheckpointStore.java`] — FIXED: catch branch now logs a stderr warning naming the checkpoint file and the unknown stage before starting fresh; covered by new test `unreadableCheckpointStartsFreshAndLogsAWarning`.
**Defer:**

- [x] [Review][Defer] A report-write failure (`writeReport`'s catch) is downgraded to a stderr warning with no signal in the returned `PipelineResult`, the report file, or the exit code — so the one case where the diagnostic artifact this story exists to provide is missing is itself only diagnosable by reading logs, contradicting the story's own goal ("diagnosable without reading logs by hand"). [`IngestPipelineRunner.java:220-227`] — deferred: exit-code/observability granularity for run outcomes is Story 8.3's scope; this story's ACs only require the report be written on success/abort, not that its own write-failure be surfaced distinctly.
- [x] [Review][Defer] `CliMain` catches `RuntimeException` broadly and maps every non-`CliException` failure (bad flag or internal I/O failure alike) to the same `EXIT_USAGE_ERROR` — but this is the class's own documented, deliberate scope: a single usage-error code suffices for this story, and the full exit-code table is Story 8.3. [`CliMain.java`] — deferred, matches existing documented scope boundary, revisit with Story 8.3.
- [x] [Review][Defer] `CliMain` has no `catch (Error e)` — an `Error` (e.g. `OutOfMemoryError` during hashing) escapes uncaught with a raw stack trace instead of a controlled exit. Same deliberate single-exit-code scope as above; broadly catching `Error` is also its own anti-pattern (can mask fatal JVM state) and isn't required by any AC here. [`CliMain.java`] — deferred, revisit with Story 8.3 if a documented exit-code table calls for it.
- [x] [Review][Defer] `IngestionReport`'s record constructor has no null validation on any field (contrast `BuildContext`, which does) — but its only production caller (`IngestPipelineRunner.writeReport`) always supplies non-null values today. Same reasoning as the already-deferred `ContentHashes` null-tolerance item below: no real external caller yet. [`IngestionReport.java:40-43`]
- [x] [Review][Defer] `BuildContext.hashRootFiles`/`readAll` fully buffers each input file into memory via `Files.readAllBytes` and rehashes every byte of every file on every invocation, including pure resume-checkpoint checks — no streaming digest, no caching. [`BuildContext.java:90-119`] — deferred, real for large model/javadoc trees but this story's Dev Notes scope synthetic fixtures only; revisit once input volumes become non-trivial.
- [x] [Review][Defer] `Files.walk` in `hashRootFiles` doesn't follow symlinks, extending the existing Story-1.3-deferred path-identity limitation (symlinks/junctions never resolved) to file *content* walking, not just path digesting. [`BuildContext.java:95`] — deferred, same root cause as the existing Story-1.3 deferred item on symlink/case handling.
- [x] [Review][Defer] Neither the outer path-list join in `BuildContext.forInputs` (`String.join("\n", ordered)`) nor `relativeKey`'s backslash-to-slash conversion are length-prefix framed the way `ContentHashes`' multi-field hashes are — a filename containing a literal newline (or a POSIX path segment containing a literal backslash) could theoretically shift entries and collide two distinct input sets onto the same digest. [`BuildContext.java:79,109-111`] — deferred, vanishingly unlikely for structured Javadoc/sample/model trees; same hardening technique as `ContentHashes` framing would apply if ever revisited.
- [x] [Review][Defer] `ContentHashes.relationshipHash`/`toolchainFingerprint` null-tolerance is inconsistent (only `type` is checked; other fields silently accept null) — but there are no real callers yet: records/edges don't exist per this story's own scope boundary, and `toolchainFingerprint` is only ever invoked with hardcoded non-null stub constants today. [`ContentHashes.java:92-105,138-152`] — deferred, revisit once Epic 2/3 wires real callers and the actual nullability contract is known.
- [x] [Review][Defer] No formal `identityFields`-per-entity-type registry (AD-22) — currently just a caller convention. [`ContentHashes.java:65-79`] — deferred, out of scope until real entity types exist (Epic 2+).
- [x] [Review][Defer] `IngestionReport` has no schema/report-version field, and `ResolutionSummary` has no invariant tying its totals to its group sums. [`IngestionReport.java:52-79`] — deferred, speculative hardening for a structure that's currently always zero/empty; revisit once Epic 2/3 populates real data.
- [x] [Review][Defer] TOCTOU race in `hashRootFiles`: a file deleted between being listed by `Files.walk` and read by `Files.readAllBytes` crashes the whole run via an uncaught `UncheckedIOException` rather than a graceful `StageFailure`. [`BuildContext.java:90-106`] — deferred, low-likelihood for a single local invocation over a stable input tree; consistent with the rest of the codebase not guarding against concurrent external mutation during a run.

**Dismissed as noise (5, dropped from this list):** the pipeline-restart test was re-verified to genuinely crash-then-resume within the same staging directory (Blind Hunter's claim was incorrect); the `sameFileContentProducesSameBuildId` test is weak but not a defect; the `domain/build.gradle.kts` `testImplementation` JUnit addition matches already-established Story 1.1/1.2 precedent under AD-1, not a new relaxation; `writeReport`'s `catch (UncheckedIOException)` is appropriately scoped since `IngestionReport.writeTo` never throws anything else; and a claimed `ResolutionSummary` totals/cells invariant gap duplicates the already-deferred item above.

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

claude-4-8-opus (Claude Code)

### Debug Log References

- `cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew domain:test cli:test checkDependencyDirection --console=plain` — BUILD SUCCESSFUL (bare aggregate `checkDependencyDirection`, per code review 2026-08-31; runs the task across every module, not just `domain`/`cli`).
- Test counts (forced rerun): ContentHashesTest 12, IndexManifestTest 4, IngestionReportTest 5, PipelineEgressTest 2, IngestPipelineRunnerTest 8, BuildContextTest 7 — all 0 failures / 0 errors.
- Code review 2026-08-31: added `IngestArgumentContractTest.extendedLengthUncPathIsRejectedAsNonLocal/outputNestedInsideInputDirectoryIsRejected/outputSymlinkedIntoInputDirectoryIsRejected` and `CheckpointStoreTest.unreadableCheckpointStartsFreshAndLogsAWarning`; full `domain:test cli:test checkDependencyDirection` run green.

### Completion Notes List

- **Five hashes, one idiom.** `ContentHashes` (domain) exposes the five pure SHA-256/lowercase-hex functions. Multi-field hashes use length-prefixed framing (`<utf8ByteLength>:<bytes>`) so no field value can collide two distinct tuples; a null field frames as `-1:`, distinct from empty `0:`. `sourceHash`/`semanticHash` are bare digests of the given bytes/UTF-8 text. Known-answer tests pin explicit hex for every framed hash so a recipe change fails loudly.
- **Manifest fingerprint.** `IndexManifest` is now `(buildId, sdkVersion, toolchainFingerprint)` with blank-field rejection; the fingerprint value lives here, but the refuse-on-load gate is Epic 6 (not implemented). The runner computes it from five distinct stub component versions so the recipe is exercised end-to-end.
- **Content-inclusive buildId.** `BuildContext.forInputs` now folds each input file's `sourceHash` (in sorted relative-path order, `/`-separated keys) into the sealed digest alongside the normalized input paths. The `out` destination is never folded in (it is output, not input, and folding it would break resume). Same inputs → same `buildId`/staging dir; one changed byte or an added file → new `buildId` and a fresh run.
- **Deterministic report.** Hand-rolled JSON writer in `cli` (no new dependency): fixed key order, escaped strings, ordered lists, no timestamps. Written to `{out}/{buildId}/ingestion-report.json` on every return path — clean and both aborted paths. Per-stage status is one of COMPLETED / SKIPPED_ON_RESUME / FAILED / NOT_RUN. Two fresh runs over identical inputs produce byte-identical report content.
- **Out-of-scope, as designed:** records/edges do not exist yet (`catalog-sqlite` is a stub), so `recordHash`/`relationshipHash` are proven as pure functions over synthetic identity fields — not wired to a catalog. SQLite uniqueness constraints are deferred to Epic 2. Relationship-resolution counts are structurally present and zero (`relationshipResolution` with empty `byType`/`byResolutionKind`); the shape is fixed for Epics 2–3 to populate. Cross-build incremental indexing remains out of scope.
- **Zero egress preserved.** `PipelineEgressTest` gained a case asserting the full-run hashing + report write happens under the armed guard with zero host resolutions; the trainings/AD-28 network exception is not exercised.

### File List

- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/ContentHashes.java` (NEW)
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/IndexManifest.java` (UPDATE — added `toolchainFingerprint`)
- `app/domain/src/test/java/com/luciadmcp/knowledge/domain/ContentHashesTest.java` (NEW)
- `app/domain/src/test/java/com/luciadmcp/knowledge/domain/IndexManifestTest.java` (NEW)
- `app/domain/build.gradle.kts` (UPDATE — test-only JUnit deps)
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java` (UPDATE — content-inclusive digest)
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/StageStatus.java` (NEW)
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestionReport.java` (NEW)
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunner.java` (UPDATE — fingerprint + 3-arg manifest + report emission + per-stage status)
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/BuildContextTest.java` (UPDATE — content-hash cases)
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunnerTest.java` (UPDATE — 3-arg manifest fingerprint assertion)
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/IngestionReportTest.java` (NEW)
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/PipelineEgressTest.java` (UPDATE — report-write no-egress case)

## Change Log

| Date       | Version | Description                                                                 | Author |
|------------|---------|-----------------------------------------------------------------------------|--------|
| 2026-08-25 | 0.1     | Story drafted (spec authored).                                              | eric   |
| 2026-08-25 | 1.0     | Implemented: five content hashes, manifest fingerprint, content-inclusive buildId, deterministic JSON ingestion report; all tests green. Status → review. | eric   |
