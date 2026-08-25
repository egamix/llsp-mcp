---
baseline_commit: aca5a782a19d0a0d9034947677dcdccf1f2e6f90
---

# Story 1.3: Stub the multi-stage resumable pipeline with a configurable failure policy

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a release operator,
I want the eight-stage ingestion pipeline (catalog → relationships → examples/workflows → lexical index → embeddings → vector index → validation → atomic activation) stubbed with checkpoint/resume support and a `fail-fast | collect-and-continue` failure policy,
so that Epic 2 onward can fill in real stage logic without redesigning orchestration, and a long run can resume after interruption instead of restarting from scratch.

## Acceptance Criteria

1. **Given** the pipeline skeleton with stubbed stages, **When** a run is interrupted after an earlier stage and restarted, **Then** it resumes from the last completed stage rather than restarting from scratch.
2. **And** setting the policy to `collect-and-continue` lets a stubbed stage record a recoverable failure and continue rather than aborting.
3. **And** setting `fail-fast` aborts the run on the first recoverable stage failure.
4. **And** restart state records the completed stages plus the sealed input digest, and validates that digest (toolchain-fingerprint validation is a seam here, formalized in Story 1.4) before reusing prior work; a changed input set produces a new `buildId` and a fresh run rather than a resume (AD-25).
5. **And** a failure in one malformed optional example is recorded with its file and stage, and may allow the run to continue under `collect-and-continue`.
6. **And** a failure affecting catalog integrity, index compatibility, a required manifest, or final validation invalidates the build and blocks activation regardless of policy (AD-5).
7. **And** a restarted build produces the same completed-stage set and manifest as an uninterrupted build from the same inputs and toolchain (NFR-13).
8. **And** no restart or fallback path performs a network request (AD-9; the `ingest.trainings` AD-28 exception is unchanged and is NOT exercised here).

> Scope note (from epics.md, AD-30 expansion): the bounded-work-queue and bounded-batch-size disciplines apply *once the examples/workflows stage is filled in* (Epic 2+). This story only stubs the stages, so it does NOT implement AST work queues or SQLite/Lucene batch writes. Leave those as documented seams. "discover inputs" and "pre-scan symbols" are example-scoped sub-steps inside the examples/workflows stage, not new top-level stages — the eight-stage shape is fixed.

## Tasks / Subtasks

- [x] **Task 1: Define the pipeline domain types in `cli`** (AC: 1, 2, 3, 5, 6)
  - [x] Add `PipelineStage` enum in a new `com.luciadmcp.knowledge.cli.pipeline` package, with the eight values in fixed order: `CATALOG`, `RELATIONSHIPS`, `EXAMPLES_WORKFLOWS`, `LEXICAL_INDEX`, `EMBEDDINGS`, `VECTOR_INDEX`, `VALIDATION`, `ACTIVATION`. Enum declaration order IS the execution order.
  - [x] Add `FailurePolicy` enum: `FAIL_FAST`, `COLLECT_AND_CONTINUE`. Default is `FAIL_FAST`.
  - [x] Add `FailureSeverity` enum: `RECOVERABLE`, `BUILD_INVALIDATING`.
  - [x] Add `StageFailure` record: `PipelineStage stage`, `FailureSeverity severity`, `Optional<Path> file`, `String message`.
  - [x] Document with Javadoc that these live in `cli` because AD-17 makes `cli ingest` the sole build coordinator.
- [x] **Task 2: Define `BuildContext` and the provisional build identity** (AC: 4, 7)
  - [x] Add `BuildContext` record: `String buildId`, `String sealedInputDigest`, `Path stagingDir`. Javadoc it as the opaque handle the coordinator passes to stages (AD-17).
  - [x] Add a coordinator-owned function that computes `sealedInputDigest` and `buildId` deterministically from the ordered input set (the six `IngestArguments` paths, in fixed order). Use a plain, stable digest (e.g. SHA-256 over the normalized absolute path strings in fixed order via `java.security.MessageDigest`). Mark it clearly `// PROVISIONAL — Story 1.4 replaces this with the formal five-hash content-hash recipe (sourceHash/recordHash/relationshipHash/semanticHash/toolchainFingerprint).` Do NOT implement the five hashes here.
  - [x] Compute `buildId` and `stagingDir` exactly once per invocation, inside the `cli` coordinator. Stages never mint a `buildId` (AD-17).
  - [x] `stagingDir` = a build-scoped staging directory under `out`, keyed by `buildId` (e.g. `{out}/.staging/{buildId}/`). Do NOT use the final immutable `{sdkVersion}/{buildId}` layout — that is Epic 6 (AD-5), and `sdkVersion` is not known until Javadoc parsing (Epic 2).
- [x] **Task 3: Implement the checkpoint store (AD-25)** (AC: 1, 4, 7, 8)
  - [x] Add `CheckpointStore` that writes/reads a checkpoint file inside `BuildContext.stagingDir` only. Never write checkpoints anywhere a future serve path could read (AD-25: checkpoint state is invisible to serve).
  - [x] Checkpoint content: the `sealedInputDigest` header plus the set/ordered list of completed `PipelineStage` values. Use a minimal line-based text format — do NOT add a JSON dependency; the machine-readable JSON report is Story 1.4.
  - [x] After each stage completes successfully, append/rewrite the checkpoint. Writes and reads touch local disk only (no network — AD-9).
  - [x] On restart: read the checkpoint if present; if its digest matches the current invocation's `sealedInputDigest`, resume by skipping already-completed stages; if it is absent or its digest differs, start fresh (a changed input set means a different `buildId`, so its staging dir differs anyway).
- [x] **Task 4: Implement the pipeline orchestrator** (AC: 1, 2, 3, 5, 6, 7)
  - [x] Add a `Stage` seam (functional interface) representing one unit of stubbed work; a stage may record `StageFailure`s via a collector passed in. Production stages are no-ops (they do no parsing/storage — that is Epics 2+).
  - [x] Add `IngestPipelineRunner implements IngestPipeline`, constructed with an ordered `List<Stage>` (one per `PipelineStage`) and a `FailurePolicy`. Provide a factory that wires the eight production no-op stubs with a supplied policy (default `FAIL_FAST`).
  - [x] `run(IngestArguments)`: compute `BuildContext`, load checkpoint, then run only the not-yet-completed stages in order. After each stage, persist the checkpoint.
  - [x] Failure handling — the core nuance, get this exactly right:
    - `RECOVERABLE` failures are governed by policy: under `FAIL_FAST`, abort the run on the first one; under `COLLECT_AND_CONTINUE`, record it (with file + stage) and continue.
    - `BUILD_INVALIDATING` failures ALWAYS abort the run and block activation, regardless of policy (AD-5). The `ACTIVATION` stage must not run and no manifest is activated when the build is invalidated.
  - [x] Expose an in-memory result to callers/tests: completed stages, collected `StageFailure`s, and whether activation occurred. (No JSON report — Story 1.4.)
- [x] **Task 5: Wire the real pipeline into `IngestCommand` and expose the policy** (AC: 2, 3)
  - [x] Replace the Story-1.2 `IngestPipeline.noOp()` default in `IngestCommand` with the real `IngestPipelineRunner` (default policy `FAIL_FAST`). Keep `IngestPipeline.noOp()` available for tests that don't need staging.
  - [x] Let a release operator set the policy per run via an optional `--failure-policy fail-fast|collect-and-continue` flag parsed by `IngestArgumentParser` (default `fail-fast`).
  - [x] The policy is orchestration configuration, NOT one of the six FR-1 input paths: do NOT add it to the `IngestArguments` six-path record, and do NOT put it in `application.yaml` (AD-16 keeps `application.yaml` serve-time only). Carry it alongside `IngestArguments` (e.g. a small parse result or sibling value) and hand it to the runner factory.
  - [x] Update the `IngestPipeline` seam Javadoc: the multi-stage resumable pipeline + policy now exist (this story); deterministic IDs/content hashes + JSON report remain Story 1.4.
- [x] **Task 6: Tests** (AC: 1–8)
  - [x] Resume: run stages, interrupt (simulate) after stage N, restart with same inputs → assert stages 1..N are skipped and N+1..8 run (AC 1).
  - [x] `collect-and-continue`: a stubbed stage records a `RECOVERABLE` failure (e.g. a malformed optional example carrying its file path) → run continues, failure is recorded with file + stage (AC 2, 5).
  - [x] `fail-fast`: same recoverable failure → run aborts on the first failure, later stages do not run (AC 3).
  - [x] Build-invalidating: a `BUILD_INVALIDATING` failure in a catalog/index/validation stage under `COLLECT_AND_CONTINUE` still aborts and `ACTIVATION` never runs / manifest not activated (AC 6).
  - [x] Changed inputs: a checkpoint from a different `sealedInputDigest` is NOT reused; a new `buildId` + fresh run results (AC 4).
  - [x] Determinism: an uninterrupted run and an interrupted-then-resumed run over identical inputs produce the same completed-stage set and the same manifest identity (AC 7).
  - [x] No egress: run a full pipeline including the resume path under the existing egress guard test support (`testsupport/EgressGuard`, `GuardingInetAddressResolverProvider`) and assert zero name resolution (AC 8). Do NOT exercise the `trainings`/AD-28 path.

## Dev Notes

### Scope discipline (read first)
- This is a **stub/orchestration** story. Stages do NO real parsing, storage, embedding, or indexing — those are Epics 2+. Production stages are no-ops; tests inject stages that fail on purpose to exercise the policy and resume logic.
- **Do NOT implement Story 1.4 work here.** No formal content hashes (the five hashes), no JSON ingestion report, no SQLite uniqueness constraints. The `sealedInputDigest`/`buildId` in this story is a deliberately provisional deterministic value good enough for staging-dir naming and resume matching; Story 1.4 replaces the recipe. [Source: epics.md#Story-1.4]
- **Do NOT implement Epic 6 immutable-build/atomic-activation semantics.** The `VALIDATION` and `ACTIVATION` stages are stubs; "block activation" here means the orchestrator does not run the `ACTIVATION` stub and reports the build as not activated. Real validate-then-atomic-activate is Epic 6. [Source: ARCHITECTURE-SPINE.md AD-5]
- **Do NOT touch the trainings/AD-28 path.** `TrainingIngestSeam` stays untouched; it is the separate ingest-time image's concern (Epic 9). This story's pipeline runs only over the offline inputs. [Source: 1-2 story; IngestArguments Javadoc]

### Governing architecture invariants
- **AD-17** — `cli ingest` is the sole build coordinator. It computes the single `buildId` over the complete ordered input set, creates the build directory once, and passes an opaque `BuildContext` to stages. Ingest modules never mint a `buildId`. All new pipeline types therefore live in the `cli` module (new `com.luciadmcp.knowledge.cli.pipeline` package), not in an ingest module. [Source: ARCHITECTURE-SPINE.md AD-17]
- **AD-25** — checkpoints are written only inside the coordinator-owned `BuildContext` staging area for the in-progress build. A resumed run re-attaches to the same `buildId`'s staging, matched by the sealed input digest; if the input set changed, that is a new `buildId`, not a resume. Checkpoint state is invisible to serve. [Source: ARCHITECTURE-SPINE.md AD-25]
- **AD-5** — builds are immutable and follow validate-then-atomic-activate. A failure affecting catalog integrity, index compatibility, a required manifest, or final validation invalidates the build and blocks activation. This is why `BUILD_INVALIDATING` failures ignore the policy. [Source: ARCHITECTURE-SPINE.md AD-5]
- **AD-9** — zero egress. No stage, restart, or fallback path may perform a network request. Reuse the Story-1.2 egress harness. The only ever-permitted network exception is `ingest.trainings` (AD-28), which is out of scope here. [Source: ARCHITECTURE-SPINE.md AD-9, AD-28]
- **AD-16** — `application.yaml` is serve-time config only. Per-run ingest inputs and the failure policy are NOT serve-time config, so they do not go there. [Source: IngestArguments Javadoc; ARCHITECTURE-SPINE.md AD-16]
- **NFR-13** — a restarted build must produce the same records, edges, and manifest as an uninterrupted build from the same inputs and toolchain. Stub stages must be deterministic so this holds. [Source: epics.md#Story-1.3]

### Existing code to build on (read before writing)
- [IngestPipeline.java](app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestPipeline.java) — the seam Story 1.2 left for this story. `@FunctionalInterface void run(IngestArguments)` with a `noOp()` default. The runner implements this interface. Update its Javadoc to reflect that 1.3 is now done.
- [IngestCommand.java](app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestCommand.java) — validates the six paths then calls `pipeline.run(args)`. Its no-arg constructor currently defaults to `IngestPipeline.noOp()`; switch the production default to the real runner. Preserve the injected-pipeline constructor used by tests.
- [IngestArguments.java](app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestArguments.java) — the six-path record. **Do not add fields to it.** Its Javadoc explicitly forbids growing it beyond the six FR-1 paths.
- [IngestArgumentParser.java](app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestArgumentParser.java) — extend to accept the optional `--failure-policy` flag; return the policy alongside the `IngestArguments` (a small parse-result holder or sibling), not inside the record.
- [TrainingIngestSeam.java](app/cli/src/main/java/com/luciadmcp/knowledge/cli/TrainingIngestSeam.java) — leave untouched (AD-28, Epic 9).
- [IndexManifest.java](app/domain/src/main/java/com/luciadmcp/knowledge/domain/IndexManifest.java) — skeleton record `(String buildId, String sdkVersion)`. Use it for the stub manifest identity in the determinism test. Do not extend it (Epic 6 owns the full manifest). `sdkVersion` is unknown at stub time — use a fixed placeholder and note it.
- Egress test support already exists: [EgressGuard.java](app/cli/src/test/java/com/luciadmcp/knowledge/cli/testsupport/EgressGuard.java), [GuardingInetAddressResolverProvider.java](app/cli/src/test/java/com/luciadmcp/knowledge/cli/testsupport/GuardingInetAddressResolverProvider.java), and patterns in [IngestEgressTest.java](app/cli/src/test/java/com/luciadmcp/knowledge/cli/IngestEgressTest.java). Reuse them; do not reinvent an egress harness.

### Previous-story learnings (Story 1.1, 1.2)
- Build runs from `app/`; Gradle wrapper pinned 8.10; Java 21 (`options.release = 21`); root package `com.luciadmcp.knowledge.*` — never a bare `luciad` segment. [Source: 1-1 story]
- Corporate-proxy PKIX workaround for Gradle: `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`. Only needed for dependency resolution, not at runtime.
- JUnit 5 is the test framework, already wired in `cli` (Story 1.2). Follow the existing test package layout under `app/cli/src/test/java/com/luciadmcp/knowledge/cli/`.
- Story 1.2 established the egress guard via `java.net.spi.InetAddressResolverProvider` (JEP 418), chosen over `SecurityManager` (disabled on JDK 24+). [Source: 1-2 story]
- On Windows dev shells, use `python` (not `python3`) for the BMAD resolver scripts.
- No proprietary Luciad docs/samples in the repo — tests use synthetic fixtures / injected stub stages only.

### Suggested new files (all under `app/cli`)
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/PipelineStage.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/FailurePolicy.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/FailureSeverity.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/StageFailure.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/CheckpointStore.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/Stage.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunner.java`
- `src/main/java/com/luciadmcp/knowledge/cli/pipeline/package-info.java`
- Tests under `src/test/java/com/luciadmcp/knowledge/cli/pipeline/` (resume, policy, build-invalidating, changed-inputs, determinism) plus a pipeline egress test reusing existing `testsupport`.

### Testing standards
- JUnit 5, tests colocated in `app/cli/src/test/java`. Use JUnit `@TempDir` for `out`/staging so checkpoint I/O is real but sandboxed.
- Drive failure scenarios by injecting `Stage` stubs that record specific `StageFailure`s — do not rely on real parsing.
- The egress test must assert the guarded resolver was never asked to resolve a host across the full run including resume.

### Project Structure Notes
- New code stays in the `cli` module — this is required by AD-17 (coordinator ownership), not a convenience. Do not place pipeline orchestration or `buildId` logic in an `ingest-*` module.
- New subpackage `com.luciadmcp.knowledge.cli.pipeline` keeps the growing pipeline surface out of the top-level `cli` package. Confirm no module dependency changes are needed (the pipeline uses only JDK + existing `domain` types).
- No new external dependencies. `MessageDigest` (JDK) covers the provisional digest; line-based text covers the checkpoint. Introducing a JSON library now would preempt Story 1.4.

### References
- [Source: _bmad-output/planning-artifacts/epics.md#Story-1.3] — user story + full acceptance criteria (incl. AD-30 expansion and the eight-stage list).
- [Source: _bmad-output/planning-artifacts/epics.md#Story-1.4] — boundary: deterministic IDs, five content hashes, JSON report — NOT this story.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md] — AD-5, AD-9, AD-16, AD-17, AD-25, AD-28.
- [Source: app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestPipeline.java] — the seam to implement.
- [Source: _bmad-output/implementation-artifacts/1-2-local-only-egress-free-cli-ingest-entrypoint.md] — egress harness, arg parsing, six-path contract, non-goals handing the pipeline to this story.

## Dev Agent Record

### Agent Model Used

claude-4-8-opus (Claude Code, dev-story workflow)

### Debug Log References

- `./gradlew cli:test checkDependencyDirection` — BUILD SUCCESSFUL. 34 tests, 0 failures/errors/skips.
- New pipeline tests: IngestPipelineRunnerTest (8), BuildContextTest (4), CheckpointStoreTest (4), PipelineEgressTest (1), IngestFailurePolicyTest (4). Existing cli tests still green (Contract 10, Egress 2, NoNetworkClasspath 1).
- AD-1 `checkDependencyDirection` green across all 12 modules — no new module edges; the pipeline uses only JDK + the `domain` types already reachable through `config`'s `api(project(":domain"))`.

### Completion Notes List

- All eight ACs implemented as an orchestration skeleton in the new `com.luciadmcp.knowledge.cli.pipeline` package; stages are no-ops, and failure/resume behavior is exercised by injected stub stages, never real parsing.
- Pipeline lives in `cli` (not an ingest module) because AD-17 makes `cli ingest` the sole build coordinator that owns the single `buildId` / `BuildContext`.
- `buildId`/`sealedInputDigest` are a **provisional** SHA-256 over the six ordered input paths — enough for staging-dir naming and resume matching. Story 1.4 replaces this with the formal five-hash recipe + JSON report; both are explicitly out of scope here and marked as such in `BuildContext`.
- Failure handling: `RECOVERABLE` obeys the policy (fail-fast aborts on the first; collect-and-continue records with file+stage and continues); `BUILD_INVALIDATING` always aborts and blocks `ACTIVATION` regardless of policy (AD-5). The failing/invalidating stage is never checkpointed, so a resume re-runs it.
- Resume is simulated by a stage that throws mid-run; stages completed before the throw are checkpointed under `{out}/.staging/{buildId}/checkpoint.txt` and skipped on restart. Checkpoint is line-based text (no JSON dependency) written in fixed pipeline order so it is byte-stable for a given completed-stage set (NFR-13).
- `--failure-policy fail-fast|collect-and-continue` (default fail-fast) is parsed by `IngestArgumentParser` and carried in a new `IngestInvocation` alongside `IngestArguments` — not added to the six-path record and not put in `application.yaml` (AD-16). `IngestCommand` builds the runner per run from a `Function<FailurePolicy, IngestPipeline>` factory keyed on the parsed policy.
- No egress on any path including resume: `PipelineEgressTest` runs a full crash-and-resume cycle under the Story-1.2 `EgressGuard` and asserts zero host resolution. The `trainings`/AD-28 path is untouched.

### File List

New (main):
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/PipelineStage.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/FailurePolicy.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/FailureSeverity.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/StageFailure.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/CheckpointStore.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/Stage.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/PipelineResult.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunner.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/package-info.java
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestInvocation.java

New (test):
- app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/PipelineTestSupport.java
- app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunnerTest.java
- app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/BuildContextTest.java
- app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/CheckpointStoreTest.java
- app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/PipelineEgressTest.java
- app/cli/src/test/java/com/luciadmcp/knowledge/cli/IngestFailurePolicyTest.java

Modified (main):
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestArgumentParser.java (parse `--failure-policy`, return `IngestInvocation`)
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestCommand.java (per-run pipeline factory keyed on policy; production default is the real runner)
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestPipeline.java (Javadoc: 1.3 pipeline now exists; noOp retained for tests)
- app/cli/src/main/java/com/luciadmcp/knowledge/cli/CliErrorCode.java (add `INVALID_OPTION_VALUE`)

## Change Log

| Date       | Change                                                                 |
|------------|------------------------------------------------------------------------|
| 2026-08-25 | Implemented Story 1.3: stubbed eight-stage resumable pipeline, `fail-fast`/`collect-and-continue` policy, checkpoint/resume, provisional build identity. All 8 ACs; 34 cli tests green; AD-1 check green. Status → review. |
