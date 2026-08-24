---
baseline_commit: c8d174d2da82841fa858a842fccf385014bf9394
---

# Story 1.2: Local-only, egress-free `cli ingest` entrypoint

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a release operator,
I want `cli ingest` to accept only six local paths (Javadoc dir, samples/workflows dir, guides dir, training-materials dir, model dir, output dir) and nothing else,
so that an air-gapped rebuild cannot accidentally reach the network, except for the one explicitly-scoped training-materials exception owned by Epic 9.

## Acceptance Criteria

1. **Given** `cli ingest` invoked with the six local paths, excluding the training-materials dir, **when** the process runs, **then** it performs zero DNS/HTTP/socket calls, verified by a test harness that fails the build on any attempted egress.
2. **And** no crawler, portal client, login automation, or model-downloader code path exists in the runtime image, outside the training-materials path.
3. **And** any other argument or flag is rejected with a clear CLI error, not silently ignored.
4. **And** invoking `cli ingest` against the training-materials dir is the sole path permitted network access and non-Java tooling execution (AD-28), and only from the separate ingest-time image (Epic 9).

## Tasks / Subtasks

- [x] **Task 1: Add a JUnit 5 test harness capability to the build (first test framework in the repo)** (AC: 1, 3)
  - [x] Add JUnit 5 (Jupiter) version + libraries to `app/gradle/libs.versions.toml`. This is the first test dependency in the project — Story 1.1 shipped no test framework. Pick a current stable JUnit 5.x; record the pin in the catalog like every other version.
  - [x] In `app/build.gradle.kts` `subprojects` block, register `tasks.withType<Test>().configureEach { useJUnitPlatform() }` so every module can run JUnit 5 tests. Do NOT add JUnit as a dependency to modules that don't need it yet — only wire the platform runner globally and add the actual `testImplementation` in `cli` (Task 2/4).
  - [x] Confirm `checkDependencyDirection` still passes: JUnit's group is not on any module's banned list and `cli` has no banned-group entry, so `testImplementation` JUnit in `cli` is allowed by AD-1 (the check already inspects `testImplementation`/`testRuntimeOnly` — see Story 1.1 follow-up that expanded it to 9 configs). Verify, don't assume.
- [x] **Task 2: Define the `cli ingest` argument contract — exactly six named local paths** (AC: 3)
  - [x] In the `cli` module (`app/cli/src/main/java/com/luciadmcp/knowledge/cli/`), add an `ingest` subcommand entrypoint that accepts exactly the six paths from FR-1: Javadoc dir, samples/workflows dir, guides dir, training-materials dir, model dir, output dir. Use named options (e.g. `--javadoc`, `--samples`, `--guides`, `--trainings`, `--model`, `--out`) so the contract is explicit and order-independent; document the chosen names in the entrypoint.
  - [x] Reject anything else: any unknown flag, extra positional argument, unknown subcommand, or a value that is not a local filesystem path (a URL / `scheme://` / remote host form must be rejected, not resolved) fails with a clear, structured CLI error and a non-zero exit — never silently ignored (AC 3). Message names the offending argument.
  - [x] Validate each supplied path is local and a directory (exists check may be deferred to the pipeline for the output dir, which is created; decide and document). Do NOT invent `application.yaml` keys for these paths — they are one-shot invocation arguments to `cli ingest`, not long-lived serve-time config, so AD-16's "application.yaml is the sole runtime config" is not violated (config governs `serve`, not per-run ingest inputs). State this reasoning in a code comment so a later epic doesn't duplicate the paths into config.
  - [x] Keep arg parsing minimal and hand-rolled for this story. Do NOT add picocli or any CLI-parsing library — it is not in the pinned Stack table, and full `--help`/documented-exit-codes/`serve`/`validate`/`stats` surface is Story 8.3's scope (Epic 8). Adding a CLI library is a Stack decision deferred to 8.3. This story ships only the `ingest` subcommand's six-path contract.
- [x] **Task 3: Route the training-materials path to an Epic 9 seam; do NOT implement network/docling here** (AC: 4)
  - [x] Accept the training-materials dir as the sixth argument, but route its handling to a clearly-marked, not-yet-implemented seam (a stub method / interface boundary) owned by Epic 9. This story adds no docling, no Python invocation, no HTTP client, and no network code on any path — including the training path (the network exception physically lives in the *separate ingest-time image* built in Epic 8/9, never in this module's Epic 1 code). [AD-28]
  - [x] Add a code comment at the seam stating: `ingest.trainings` is the sole AD-28 network/non-Java exception, exercised only from the separate ingest-time image (Epic 9); it is deliberately unimplemented here.
- [x] **Task 4: Build the egress-blocking test harness (the core deliverable)** (AC: 1)
  - [x] Add a JUnit 5 test in `cli` that invokes `cli ingest` end-to-end over synthetic local directories covering the five non-training paths, and **fails the build** if any DNS resolution / socket connect / HTTP call is attempted during the run.
  - [x] Choose an egress-detection mechanism that will NOT silently disable itself on the final B5 JDK (see Dev Notes "Egress harness — mechanism and the SecurityManager trap"). A silently no-op guard is the specific disaster to prevent: the test would pass while providing zero protection. If the mechanism can be disabled by the JDK version, the harness MUST fail loudly (not skip) when it detects it is running without an active guard.
  - [x] Add a negative self-test: a test that performs a deliberate localhost socket/DNS attempt through the same guard and asserts the guard trips. This proves the harness actually detects egress rather than passing vacuously. (Mirror the Story 1.1 precedent: the AD-1 check was proven by a negative test that added a forbidden edge and confirmed the build failed.)
- [x] **Task 5: Assert no crawler / HTTP-client / portal / model-downloader code path is reachable from the serving-image ingest classpath** (AC: 2)
  - [x] Interpret "runtime image" at this (pre-packaging) stage as the `cli` module's runtime classpath and its transitive module graph for the five offline paths. Add a build-time or test-time assertion that no HTTP-client / crawler / login-automation / model-downloader library appears on that classpath (structural check, in the spirit of the existing `checkDependencyDirection`). This survives any JDK pin because it is a dependency-graph assertion, not a runtime interception.
  - [x] The training-materials network tooling is explicitly exempt and out of scope here — it ships only in the separate ingest-time image (Epic 8/9), never in this graph.
- [x] **Task 6: Verify the full clean build stays green** (AC: 1, 2, 3)
  - [x] Run `cd app && ./gradlew check` (see Story 1.1 local-toolchain notes for the corporate-proxy `GRADLE_OPTS` workaround if the wrapper or dependency resolution hits PKIX). Confirm: all modules still compile, `checkDependencyDirection` still passes for every module, the egress harness passes on the offline paths, and the negative self-test confirms the guard trips.
  - [x] Confirm an unknown flag / extra arg / URL-shaped value to `cli ingest` exits non-zero with a clear message (AC 3) via a test.

## Dev Notes

### Scope discipline — read this first
This story delivers exactly three things: (1) the `cli ingest` subcommand's **argument contract** (six named local paths, reject everything else); (2) an **egress-blocking test harness** that fails the build on any DNS/HTTP/socket call during offline ingest; (3) a **structural assertion** that no network-capable ingestion code is on the serving-image classpath. It does **NOT** implement the pipeline stages, deterministic IDs, the ingestion report, docling, the separate ingest-time image, `--help`, or the `serve`/`validate`/`stats` subcommands. Those are later stories/epics (see "Explicit non-goals").

### Explicit non-goals (owned by later stories — do not build here)
- The eight-stage resumable pipeline and `fail-fast | collect-and-continue` policy → **Story 1.3**. This story's `ingest` command validates args and hands off to a pipeline **seam** that is a stub/no-op placeholder for now; 1.3 fills it in. [Source: epics.md#Story-1.3]
- Deterministic record IDs, content hashes, machine-readable JSON report → **Story 1.4**. [Source: epics.md#Story-1.4]
- `serve` / `validate-index` / `stats` subcommands, `--help` text, and the full documented **exit-code** table, headless no-TTY behavior → **Story 8.3**. This story uses a non-zero exit for the reject case but does not define the full exit-code contract. [Source: epics.md#Story-8.3]
- docling, Python invocation, any network access for training decks → **Epic 9** (Stories 9.1–9.3), and it physically lives in the separate ingest-time image built in **Epic 8** (Story 8.4). [Source: epics.md#Story-9.1, #Story-8.4; ARCHITECTURE-SPINE.md#AD-28]
- The actual container image / `pull_policy: never` / non-root hardening → **Epic 8** (Story 8.1). "Runtime image" in AC 2 is interpreted at code/classpath level here. [Source: ARCHITECTURE-SPINE.md#AD-27]

### Binding architecture invariants for this story
[Source: ARCHITECTURE-SPINE.md#AD-9, #AD-28, #AD-10, #AD-17, #AD-16, #AD-1]
- **AD-9 (zero egress):** neither ingest nor serve performs DNS/HTTP/telemetry/model-download/update-check; model+tokenizer load only from a local path. This story's harness is the first concrete enforcement of AD-9. The sole exception is `ingest.trainings` per AD-28.
- **AD-28 (scoped training exception):** `ingest.trainings` is the *only* module permitted network access and non-Java tooling, only via `cli ingest` against the training-materials dir, and only from the separate ingest-time image — never the serving image. This story accepts the training path but implements no network code (Task 3).
- **AD-10 (parse-only):** no compiling, symbol-solving, Maven/Gradle invocation, or executing ingested content — again except `ingest.trainings` (AD-28). Not exercised yet (no parsing here) but sets the precedent.
- **AD-17 (`cli ingest` is the sole build coordinator):** `cli ingest` computes the one `buildId` and creates the build dir exactly once, passing an opaque `BuildContext` to ingest modules; ingest modules never mint a `buildId`. The `buildId`/`BuildContext` computation itself is Story 1.3/1.4 work — for 1.2, just place the entrypoint so that ownership sits in `cli`, and do not let any ingest module own argument parsing.
- **AD-16 (single-file config):** `application.yaml` is the sole *runtime* (serve-time) config. The six ingest paths are per-invocation arguments to a one-shot command, not runtime config — do not add them to `application.yaml`. Logs never emit full document bodies/source text by default; keep any logging in this story path-level only.
- **AD-1 (dependency direction):** unchanged. `cli` already depends on `config`, all `ingest.*`, and `mcp` (see current `app/cli/build.gradle.kts`). Adding `testImplementation` JUnit to `cli` is permitted (JUnit is not banned for `cli`). Do not add any new *project* edge or any banned external group.

### Egress harness — mechanism and the SecurityManager trap (CRITICAL)
This is the highest-risk part of the story. The "obvious" way to fail a test on a socket connect is a custom `SecurityManager` overriding `checkConnect`. **Do not build the harness on `SecurityManager` without gating it on the JDK**, for a concrete reason tied to open blocker **B5** (JDK pin: 21 vs 25 vs org LTS — see below):
- `SecurityManager` is deprecated for removal (JEP 411, JDK 17). On **Java 21** it works only if the test JVM is launched with `-Djava.security.manager=allow`; otherwise `System.setSecurityManager(...)` throws.
- On **Java 24+** (JEP 486, "permanently disable the Security Manager"), `System.setSecurityManager(...)` throws unconditionally at runtime. If B5 resolves to **Java 25**, a SecurityManager-based harness **breaks** — and the worse failure mode is that it silently stops guarding while the test still "passes."

**Verify the exact behavior against the final B5 JDK before committing to a mechanism** (this note was written without live web access — confirm the JEP 411/486 specifics for the chosen JDK). Recommended durable design, JDK-pin-independent:
1. **Structural classpath assertion (primary, Task 5):** no HTTP-client / crawler / downloader library on the `cli` offline-ingest runtime classpath. This is JDK-version-proof and catches the most likely real regression (someone adding an HTTP client dependency).
2. **DNS-layer runtime guard (durable):** install a custom `java.net.spi.InetAddressResolverProvider` / `InetAddressResolver` (Java 18+, JEP 418) that records or throws on any name resolution attempt during the ingest run. This uses a supported, non-removed SPI that works on 21/24/25 and catches any code path that tries to resolve a host — the necessary first step of almost any real egress.
3. **Optional SecurityManager path (only if B5 pins Java 21):** if and only if the JDK is fixed at 21, a `checkConnect` SecurityManager launched via `test { jvmArgs("-Djava.security.manager=allow") }` may be added for raw-IP socket coverage — but it **must be JDK-gated and must fail loudly (not skip) if it cannot install**, so a future JDK bump surfaces the gap instead of silently disabling the guard.

Whatever mechanism is chosen, Task 4's **negative self-test** must prove the guard actually trips on a deliberate egress attempt. A harness that passes without detecting a planted egress is the failure this AC exists to prevent.

### CLI argument contract details
- Six named options, one per FR-1 path. Suggested names (document whichever you pick): `--javadoc <dir>`, `--samples <dir>`, `--guides <dir>`, `--trainings <dir>`, `--model <dir>`, `--out <dir>`.
- Reject: unknown flags, unknown subcommands, extra positionals, missing required option, and any value that parses as a URL / has a `scheme://` / names a remote host. Emit a structured error (`code`, `message`, offending-arg) consistent with the Spine's "one structured error shape" convention — not free text only. [Source: ARCHITECTURE-SPINE.md#Consistency-Conventions "Tool I/O & error shape"] Exit non-zero.
- Local-path validation: reject non-local/remote forms outright; for local dirs, decide and document which must pre-exist (input dirs) vs. be created (output dir).

### Previous story intelligence (Story 1.1 — done)
[Source: _bmad-output/implementation-artifacts/1-1-scaffold-domain-model-and-module-skeleton.md]
- **Build is Gradle (Kotlin DSL), wrapper pinned to 8.10**, run from `app/` (the Gradle root moved under `app/` — all module paths are relative to `app/`). Java package root is `com.luciadmcp.knowledge.*` (never a bare `luciad` segment).
- **No test framework exists yet** — Story 1.1 shipped zero tests. This story introduces JUnit 5. Wire `useJUnitPlatform()` in the `subprojects` block and add the version to `libs.versions.toml`.
- **AD-1 is enforced, not just documented**, by `checkDependencyDirection` in `app/build.gradle.kts`. It already inspects 9 configurations including `testImplementation`/`testRuntimeOnly`/`annotationProcessor`, has a fail-closed `else`, and a `FileCollectionDependency` guard. Adding JUnit to `cli` (no banned-group entry) is fine; adding it to `domain`/`mcp`/`retrieval` would need care (those have banned lists — JUnit's group isn't banned, but don't add test deps to modules that don't need them).
- **Proven-by-negative-test precedent:** Story 1.1's AD-1 check was validated by planting a forbidden edge and confirming `gradlew check` failed. Mirror that here with the egress negative self-test.
- **JDK is pinned to 21 via `options.release=21` only** (no toolchain hard-pin); this is a **≥21** target, and the exact pin is open blocker **B5** (21 vs 25 vs org LTS). This directly affects the egress-harness mechanism choice above. If B5 has been resolved since 1.1, use that JDK and confirm the harness mechanism survives it.
- **Local corporate-proxy note:** this host's proxy CA is in the Windows trust store but not JDK `cacerts`; if the wrapper/dependency resolution hits PKIX, run with `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`. This is a dev-environment workaround only and does not touch the air-gapped runtime guarantee. **Note:** adding JUnit is the first dependency this project actually resolves from Maven Central — expect the first `check` after Task 1 to hit the network for JUnit jars, which is where the PKIX workaround may first be needed. This is build-time dependency resolution, entirely separate from the AD-9 runtime egress guarantee the harness enforces.

### Testing standards
[Source: epics.md#Testing-strategy-requirements; Story 1.1 testing notes]
- Tests use **synthetic fixtures only** — no proprietary Luciad docs/samples in the repo. The egress harness runs over synthetic local dirs (they can be empty or contain trivial placeholder files, since the pipeline is stubbed until Story 1.3).
- The meaningful tests for this story: (a) egress harness passes on offline ingest and its negative self-test trips the guard; (b) arg-rejection tests (unknown flag, extra arg, URL-shaped value) all exit non-zero with a clear message; (c) the classpath assertion passes.
- Integration-level "no network access" verification for the full tool surface is Epic 8's job; this story establishes the harness the later integration tests build on. [Source: epics.md#Testing-strategy-requirements "Integration tests"]

### Files expected to touch
- `app/gradle/libs.versions.toml` — add JUnit 5 version + libraries (UPDATE).
- `app/build.gradle.kts` — add `useJUnitPlatform()` to `subprojects`; optionally test-JVM `jvmArgs` only if a JDK-gated SecurityManager path is used (UPDATE). Do not alter the AD-1 allow-lists.
- `app/cli/build.gradle.kts` — add `testImplementation` JUnit (UPDATE).
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/` — new `ingest` entrypoint + arg parsing + local-path validation + training-path seam (NEW source files).
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/` — egress harness, negative self-test, arg-rejection tests; and the egress guard SPI implementation (e.g. an `InetAddressResolverProvider`) plus its `META-INF/services` registration under `src/test/resources` if that mechanism is chosen (NEW test files).

### Project Structure Notes
- Aligns with the Structural Seed: `cli/` owns `ingest | serve | validate` entrypoints. This story adds only `ingest`. [Source: ARCHITECTURE-SPINE.md#Structural-Seed]
- No new Gradle module and no new project dependency edge — `cli`'s existing inward edges (config, all ingest.*, mcp) are sufficient. [Source: app/cli/build.gradle.kts; ARCHITECTURE-SPINE.md#AD-1]
- Keep the training-materials network exception out of this module entirely; its home is the separate ingest-time image (Epic 8/9). [Source: ARCHITECTURE-SPINE.md#AD-28]

### References
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-1-Story-1.2]
- [Source: _bmad-output/planning-artifacts/epics.md#Story-1.3, #Story-1.4, #Story-8.3, #Story-8.4, #Story-9.1] (scope boundaries)
- [Source: _bmad-output/planning-artifacts/epics.md#Requirements-Inventory FR-1, FR-2, NFR-11, NFR-12]
- [Source: .../ARCHITECTURE-SPINE.md#AD-9] (zero egress)
- [Source: .../ARCHITECTURE-SPINE.md#AD-28] (scoped training exception; separate ingest-time image)
- [Source: .../ARCHITECTURE-SPINE.md#AD-10] (parse-only)
- [Source: .../ARCHITECTURE-SPINE.md#AD-17] (cli ingest is the sole build coordinator)
- [Source: .../ARCHITECTURE-SPINE.md#AD-16] (application.yaml is sole runtime config)
- [Source: .../ARCHITECTURE-SPINE.md#AD-1] (dependency direction, enforced by checkDependencyDirection)
- [Source: .../ARCHITECTURE-SPINE.md#AD-27] ("runtime image" / container posture — Epic 8)
- [Source: .../ARCHITECTURE-SPINE.md#Consistency-Conventions] (structured error shape, redacted logging)
- [Source: .../ARCHITECTURE-SPINE.md#Deferred] (B5 JDK pin — affects egress-harness mechanism)
- [Source: _bmad-output/implementation-artifacts/1-1-scaffold-domain-model-and-module-skeleton.md] (Gradle/wrapper, no-JUnit-yet, AD-1 enforcement, JDK ≥21/B5, proxy workaround)

## Dev Agent Record

### Agent Model Used

claude-4-8-opus (Claude Code dev-story workflow)

### Debug Log References

- `cd app && ./gradlew :cli:test` → BUILD SUCCESSFUL; 13 tests, 0 failures, 0 skipped.
- `cd app && ./gradlew check` → BUILD SUCCESSFUL; all `checkDependencyDirection` tasks green (AD-1 intact), no regressions.
- Corporate-proxy PKIX on first Maven Central fetch (JUnit) handled with `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.

### Completion Notes List

- All four ACs satisfied. AC1: `IngestEgressTest.noEgressDuringOfflineIngest` arms a DNS guard and runs an offline ingest over five synthetic dirs with zero recorded egress. AC2: `NoNetworkClasspathTest` structurally asserts no HTTP-client/crawler/browser-automation/model-downloader library on the `cli` classpath. AC3: `IngestArgumentContractTest` (10 cases) rejects unknown option/subcommand, missing subcommand, extra positional, missing required option, missing value, URL and UNC non-local paths, and non-existent dir — each with a non-zero exit and the offending token named. AC4: the training-materials path is accepted but routed to `TrainingIngestSeam`, deliberately unimplemented (AD-28, Epic 9).
- Egress guard uses `java.net.spi.InetAddressResolverProvider` (JEP 418, Java 18+) via ServiceLoader — JDK-version-proof, chosen over `SecurityManager` (disabled by JEP 486 on JDK 24+, the silent no-op trap). The negative self-test (`guardIsInstalledAndTripsOnDeliberateEgress`) fails loudly if the provider is not loaded, so the harness cannot pass vacuously.
- JUnit 5 is the first test framework in the repo; wired globally via `useJUnitPlatform()` in `subprojects`, with the actual dependency added only to `cli`. `checkDependencyDirection` confirmed still green (JUnit's group is not banned).
- No CLI-parsing library added (picocli etc. deferred to Story 8.3); arg parsing is minimal and hand-rolled. Ingest paths are one-shot invocation args, not `application.yaml` config — AD-16 not violated (documented in code).

### File List

Build files (modified):
- `app/gradle/libs.versions.toml` — added JUnit 5 Jupiter + platform-launcher versions/libraries (test-only)
- `app/build.gradle.kts` — `useJUnitPlatform()` in `subprojects` Test config
- `app/cli/build.gradle.kts` — JUnit `testImplementation` / `testRuntimeOnly`

Main sources (new):
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/CliErrorCode.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/CliError.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/CliException.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestArguments.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestArgumentParser.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestPipeline.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/TrainingIngestSeam.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/IngestCommand.java`
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/CliMain.java`

Main sources (modified):
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/package-info.java` — updated scope note

Test sources (new):
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/IngestArgumentContractTest.java`
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/IngestEgressTest.java`
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/NoNetworkClasspathTest.java`
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/testsupport/EgressGuard.java`
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/testsupport/GuardingInetAddressResolverProvider.java`
- `app/cli/src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider`

Story tracking (modified):
- `_bmad-output/implementation-artifacts/1-2-local-only-egress-free-cli-ingest-entrypoint.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

| Date       | Version | Description                                                                 | Author |
|------------|---------|-----------------------------------------------------------------------------|--------|
| 2026-08-24 | 0.1     | Implemented `cli ingest` six-path contract, egress guard harness (InetAddressResolverProvider), structural no-network classpath check, and AD-28 training seam; JUnit 5 added to build; all 13 tests + `check` green. Status → review. | claude-4-8-opus |
