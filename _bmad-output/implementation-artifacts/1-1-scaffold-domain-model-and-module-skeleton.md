---
baseline_commit: 72393b7f4f0c66a9a07d65cfbf23181819ed705b
---

# Story 1.1: Scaffold domain model and module skeleton

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want the domain records (API element, Example, Workflow, Guide, Training, Relationship) and the Gradle multi-project tree (`domain`, `config`, `ingest.javadoc`, `ingest.examples`, `ingest.guides`, `ingest.trainings`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx`, `retrieval`, `mcp`, `cli`) scaffolded with dependencies pointing inward per AD-1,
so that later epics implement real logic against stable ports instead of inventing types and module boundaries as they go.

## Acceptance Criteria

1. **Given** an empty repository, **when** the skeleton is scaffolded, **then** all twelve modules exist with the dependency direction enforced (no module reaches an engine except through a domain-defined port).
2. **And** the project builds with no implementation code beyond empty port interfaces and record definitions.
3. **And** no external starter template is used (brief confirms none applies).
4. **And** `ingest.trainings` is scaffolded like every other module at the source level, but its packaging (Epic 8/9) ships it only in a separate, ingest-time-only image, never the serving image (AD-28).

## Tasks / Subtasks

- [x] **Task 1: Create Gradle multi-project aggregator and 12 module build scripts** (AC: 1, 2, 3)
  - [x] Create root `settings.gradle.kts` (`rootProject.name` + `include(...)` of all 12 modules) and root `build.gradle.kts` (shared `java-library` config); no external parent/starter (AC 3).
  - [x] Pin `options.release`/`sourceCompatibility`/`targetCompatibility` to **21** and record dependency versions in `gradle/libs.versions.toml` from the Stack table (Lucene 10.5.1, MCP Java SDK 2.0.1, ONNX Runtime 1.29.0, SQLite JDBC 3.53.2.1, JSoup 1.23.1, JavaParser 3.28.2). Do NOT add these as actual dependencies to any module yet — record versions in the catalog only.
  - [x] Create one Gradle subproject per package: `domain`, `config`, `ingest.javadoc`, `ingest.examples`, `ingest.guides`, `ingest.trainings`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx`, `retrieval`, `mcp`, `cli`. Use project paths matching the module names (e.g. `:ingest-javadoc`, `:catalog-sqlite`).
  - [x] Each module's `build.gradle.kts` declares only its inward dependencies (`api(project(":..."))`) per the AD-1 dependency graph (see Dev Notes "Dependency edges" table). `domain` declares NO project dependencies.
- [x] **Task 2: Create the Java package tree** (AC: 1)
  - [x] Under each module create `src/main/java/com/luciad/knowledge/<module-path>/` matching the Structural Seed source tree (e.g. `ingest/javadoc/`, `catalog/sqlite/`, `search/lucene/`, `embeddings/onnx/`).
  - [x] Add `src/main/resources/application.yaml` (empty/placeholder, sole runtime config per AD-16) in the module that owns it (`config` or `cli` — see Dev Notes).
- [x] **Task 3: Define domain records as immutable Java `record` types** (AC: 2)
  - [x] In `domain`, create records: `ApiElement`, `MethodElement`, `Example`, `Workflow`, `Guide`, `Training`, `RelationshipEdge`, `Citation`, `IndexManifest`. Minimal fields only — enough to compile and express identity; full schemas are deferred approval gates (do NOT invent DDL/JSON schema).
  - [x] Every record carries an SDK-version string field (AD-12) and API-element-bearing records carry a canonical-symbol string field (AD-3).
  - [x] Create the fixed relationship-type enum with exactly the 15 `UPPER_SNAKE_CASE` values (see Dev Notes); no ad-hoc extras.
- [x] **Task 4: Define engine ports (interfaces) in `domain`** (AC: 1, 2)
  - [x] Create empty interfaces `CatalogStore`, `LexicalIndex`, `VectorIndex`, `Embedder` in `domain`. Method bodies/signatures may be minimal or empty — this story defines the boundary, not behavior.
  - [x] The `Embedder` interface signature must reflect AD-21: accepts raw text + a role (`QUERY` | `PASSAGE`), returns a vector. Add a `Role` enum (or nested enum) for this.
- [x] **Task 5: Enforce dependency direction so violations fail the build** (AC: 1)
  - [x] Add a build-time check that fails if `domain` gains any project/engine dependency, or if `mcp`/`retrieval` import `ingest.*` or parser/engine types. Implemented as a custom `checkDependencyDirection` verification task (wired into `check`) that inspects each module's declared project + external dependencies against an inward-only allow-list and a banned-engine-group map; document how the rule maps to AD-1. (See Dev Notes for the exact forbidden edges.)
  - [x] Verify `gradlew check` passes on the clean skeleton and would fail if a forbidden edge were added.
- [x] **Task 6: Verify full clean build** (AC: 2)
  - [x] Run the multi-project build from the root (`gradlew check`); confirm all 12 modules compile with zero implementation logic beyond records + empty interfaces.

### Review Follow-ups (AI)

- [x] [AI-Review][Med] Add engine groups (`org.apache.lucene`, `com.microsoft.onnxruntime`, `org.xerial`) to `retrieval`'s banned-external list — AD-1 forbidden edges explicitly ban `retrieval → engine`, but only the parser groups were listed. (Fixed in-review.)
- [x] [AI-Review][Med] Expand `checkDependencyDirection` to inspect test + annotation-processor configurations — a forbidden edge declared in `testImplementation`/`annotationProcessor` escaped the 4-config loop. (Fixed in-review: now covers 9 configs.)
- [x] [AI-Review][Low] Add fail-closed `else` + `FileCollectionDependency` guard to the `when(dep)` — a raw jar (`files("lucene.jar")`) or unrecognized dependency type on a banned-group module slipped through silently. (Fixed in-review.)
- [ ] [AI-Review][Med][DECISION] JDK is pinned as **≥21**, not **==21**: `options.release=21` sets the bytecode target only; the build runs on any JDK ≥ 21 and no toolchain enforces exact 21, yet `gradle.properties` implies a hard "must run on JDK 21". Resolution is tied to open blocker **B5** (21 vs 25 vs org LTS). Owner decides: add a `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` (hard-pin, fails loudly when absent) **or** soften the comment to "≥ 21". Deferred pending B5.
- [ ] [AI-Review][Low] `config`/`cli`/`ingest.*` have no banned-external entry (empty ban). This matches the spec's explicit forbidden-edge list (only `domain`/`mcp`/`retrieval` named) and adapters legitimately need engines — revisit tightening `ingest.*`/`config` when engine libs are actually wired (Epic 2+).
- [ ] [AI-Review][Low] Mandatory `Citation` fields (`sourceDoc`/`anchor`/`sdkVersion`) accept null; records carry no null-guards generally. Skeleton is intentionally minimal (no validation behavior specified) — revisit when construction/validation lands.
- [ ] [AI-Review][Low] `checkDependencyDirection` aborts on the first violation (no aggregate report) — ergonomics only; consider collecting all violations before throwing.
- [ ] [AI-Review][Low] Adapters/`retrieval`/`cli` use blanket `api(project(...))`, republishing transitively — tighten to `implementation` where public re-exposure isn't required, once real types exist.

## Dev Notes

### Scope discipline — read this first
This is a **skeleton-only** story. Produce a compiling module tree, domain records, engine port interfaces, and an enforced dependency direction. Do **NOT** implement parsing, storage, retrieval, MCP tools, or wire real engine libraries (SQLite/Lucene/ONNX/JSoup/JavaParser) into module dependencies — those belong to Epics 2–9. Several concrete schemas are explicit **deferred approval gates** and must not be invented here (see "Do not invent" below).

### Target source tree (authoritative — from Architecture Spine "Structural Seed")
[Source: architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#Structural-Seed]
```text
<repo root>/                      # planning + BMAD artifacts (_bmad/, _bmad-output/, docs/) live here, beside app/
app/                              # Gradle build root (all paths below are relative to app/)
  settings.gradle.kts             # Gradle multi-project aggregator (includes all modules)
  build.gradle.kts                # root build: shared Java 21 config + AD-1 checkDependencyDirection
  gradle/libs.versions.toml       # version catalog (Stack pins)
  compose.yaml, Dockerfile        # packaging (Epic 8) — do NOT create here
  scripts/                        # release-engineering — do NOT create here
  agent/SKILL.md                  # Epic 7 — do NOT create here
  tools/evaluation/               # offline Python eval — do NOT create here
  src/main/java/com/luciad/knowledge/
    cli/                          # ingest | serve | validate entrypoints
    config/                       # application.yaml binding
    domain/                       # records, canonical symbol, relationship enum, interfaces, manifest
    ingest/javadoc/               # JSoup + JavaParser, parse-only
    ingest/examples/              # sample & workflow extraction + governance sidecar
    ingest/guides/                # JSoup guide/tutorial HTML parsing + catalog linking
    ingest/trainings/             # docling PPTX/PDF parsing (separate ingest-time image only)
    catalog/sqlite/               # CatalogStore adapter
    search/lucene/                # LexicalIndex (BM25) + VectorIndex (KNN) adapters
    embeddings/onnx/              # Embedder adapter, ONNX session pool
    retrieval/                    # fusion, ranking, citation assembly
    mcp/                          # 20 MCP tools + Streamable HTTP (/mcp)
  src/main/resources/
    application.yaml              # sole runtime config (AD-16)
  models/                         # (Epic 4+) — do NOT create here
  indexes/                        # (Epic 6) — do NOT create here
```
**Note on layout:** the Spine shows a single `src/main/java/...` tree, but AC 1 and AD-1 require **twelve Gradle subprojects** with enforceable dependency edges — a single flat source tree cannot enforce inward-only dependencies. Resolve this by making each package above its own Gradle subproject (module dir contains its own `build.gradle.kts` + `src/main/java/com/luciad/knowledge/<pkg>/`). Root `com.luciad.knowledge`; one package per module. [Source: ARCHITECTURE-SPINE.md#Consistency-Conventions]

### Dependency edges (AD-1 — dependencies point inward to `domain`)
[Source: ARCHITECTURE-SPINE.md#System-Structure (module→package mermaid) and #AD-1]
Each module's `build.gradle.kts` declares exactly these project dependencies (inward only, via `api(project(":..."))`):

| Module | Depends on (project modules) |
| --- | --- |
| `domain` | **nothing** (zero project/engine deps — enforced) |
| `config` | `domain` |
| `catalog.sqlite` | `domain` |
| `search.lucene` | `domain` |
| `embeddings.onnx` | `domain` |
| `retrieval` | `domain`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx` |
| `ingest.javadoc` | `domain`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx` |
| `ingest.examples` | `domain`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx` |
| `ingest.guides` | `domain`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx` |
| `ingest.trainings` | `domain`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx` |
| `mcp` | `domain`, `retrieval` |
| `cli` | `config`, `ingest.javadoc`, `ingest.examples`, `ingest.guides`, `ingest.trainings`, `mcp` |

**Forbidden edges (must fail the build if introduced) [AD-1]:**
- `domain` → any other project module or any SQLite/Lucene/ONNX type.
- `mcp` or `retrieval` → any `ingest.*` module, or any parser/engine concrete type.
- Any import cycle between modules.
Engines are reached ONLY through the `domain` interfaces (`CatalogStore`, `LexicalIndex`, `VectorIndex`, `Embedder`). [Source: ARCHITECTURE-SPINE.md#AD-1]

### Domain records to define (minimal, immutable `record` types)
[Source: ARCHITECTURE-SPINE.md#Consistency-Conventions "Record & entity naming"; Core-entity ERD]
`ApiElement`, `MethodElement`, `Example`, `Workflow`, `Guide`, `Training`, `RelationshipEdge`, `Citation`, `IndexManifest`.
- Keep fields minimal — just enough to compile and carry identity. The ERD only fixes a few fields; the rest is deferred.
- `ApiElement` / `MethodElement`: include `canonicalSymbol` (String) + `sdkVersion` (String). [AD-3, AD-12]
- `RelationshipEdge`: include `type` (the enum below) + `sdkVersion`. [ERD]
- `Citation`: include `canonicalSymbol` (nullable/Optional — omitted when unresolved per AD-15), `sourceDoc`, `anchor`. **Do not make canonical symbol mandatory** — AD-15 was amended so symbol-less Guide/Training/Workflow records stay servable. [Source: ARCHITECTURE-SPINE.md#AD-15]
- `Guide`, `Training`, `Example`, `Workflow`: these are the four technique-taggable record types (AD-29). A technique-tag field (e.g. `List<String>`/`Set<String>`) is acceptable to include as a placeholder, but the **vocabulary content is a deferred gate** — do not hardcode tag values. [Source: ARCHITECTURE-SPINE.md#AD-29]

### Relationship enum — exactly these 15 values, `UPPER_SNAKE_CASE`
[Source: epics.md#Story-2.5 (brief §6); ARCHITECTURE-SPINE.md#Consistency-Conventions "Relationship types"]
`EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`, `IMPLEMENTED_BY`, `DIRECT_SUBCLASS`, `RETURN_TYPE`, `PARAMETER_TYPE`, `THROWS_TYPE`, `FIELD_TYPE`, `CONSTRUCTOR_PARAMETER_TYPE`, `OVERRIDES`, `SPECIFIED_BY`, `SEE_ALSO`, `EXAMPLE_USES`, `WORKFLOW_REQUIRES`. No ad-hoc additions without an AD amendment.

### Engine ports (interfaces in `domain`)
[Source: ARCHITECTURE-SPINE.md#AD-1, #AD-21]
- `CatalogStore`, `LexicalIndex`, `VectorIndex`, `Embedder` — declare as interfaces in `domain`; empty or minimal signatures are fine (boundary, not behavior).
- `Embedder` must model AD-21: `embed(String text, Role role)` returning a vector, where `Role` ∈ {`QUERY`, `PASSAGE`}. Preprocessing lives behind this interface only.

### Do not invent (deferred approval gates — leave as TODO/stubs)
[Source: ARCHITECTURE-SPINE.md#Deferred]
- SQLite catalog schema/DDL (Epic 3 gate).
- Lucene field set / analyzers / BM25 / KNN config (Epic 5 gate).
- Canonical-symbol & relationship serialization string grammar + the single AD-18 wire/join variant (Epic 3 gate). Fields can exist; the *format* is not decided here.
- MCP tool request/response JSON schemas (Epic 6 gate).
- Embedding model/dimension (candidate `bge-small-en-v1.5`/384-dim only — not locked).
- **JDK pin is blocker B5** (21 vs org LTS vs 25). This story pins **21 (LTS)** as the working default per the Stack table; if the owner has resolved B5 differently, use that. Flag in Completion Notes.

### AD-28 packaging note (source vs. shipping)
`ingest.trainings` is scaffolded as a normal module at the **source level** exactly like the others. Its *separateness* is a packaging concern for Epic 8/9 (it ships only in a distinct ingest-time image, never the serving image). Do NOT build any Docker/packaging separation in this story — just the module. [Source: ARCHITECTURE-SPINE.md#AD-28, epics.md#Story-1.1 AC4]

### Testing standards
[Source: epics.md#Testing-strategy-requirements]
- No behavior exists yet, so no unit-test coverage of logic is expected. The meaningful test for this story is the **build itself**: `gradlew check` compiles all 12 modules, and the `checkDependencyDirection` task fails the build on a forbidden dependency edge (AC 1). Consider adding a trivial compile-time assertion or a rule self-test if quick.
- Tests throughout the project use **synthetic fixtures only** — no proprietary Luciad docs/samples may be committed to the repo. Not directly applicable here but sets the precedent for Epic 2+. [Source: epics.md#Module/source-tree-seed]

### Project Structure Notes
- Aligns with the Structural Seed and the AD-1 module→package map. The only deliberate variance from the Spine's literal single-`src` diagram is promoting each package to a Gradle subproject, which is required to *enforce* (not merely document) the inward dependency direction AC 1 demands. This is consistent with the "one package per module" convention and `packaging` being explicitly a non-module build layer.
- `packaging` (`compose.yaml`, `Dockerfile`, `scripts/`) is NOT a Java module and is out of scope for this story (Epic 8). [Source: ARCHITECTURE-SPINE.md#System-Structure]

### References
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-1-Story-1.1]
- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.5 (15 relationship types)]
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#System-Structure]
- [Source: .../ARCHITECTURE-SPINE.md#AD-1] (dependency direction, engine ports)
- [Source: .../ARCHITECTURE-SPINE.md#AD-3, #AD-12] (canonical symbol join key, SDK-version scoping)
- [Source: .../ARCHITECTURE-SPINE.md#AD-15] (citation tuple; canonical symbol conditional)
- [Source: .../ARCHITECTURE-SPINE.md#AD-21] (Embedder interface + Role)
- [Source: .../ARCHITECTURE-SPINE.md#AD-28] (ingest.trainings packaging separation)
- [Source: .../ARCHITECTURE-SPINE.md#AD-29] (technique tags, taggable record types)
- [Source: .../ARCHITECTURE-SPINE.md#Consistency-Conventions, #Stack, #Structural-Seed, #Deferred]

## Dev Agent Record

### Agent Model Used

claude-4-8-opus (Claude Code)

### Debug Log References

- Full `gradlew check` across the root + 12 subprojects: BUILD SUCCESSFUL (36 tasks); `checkDependencyDirection` passed for every module.
- Negative test: temporarily added forbidden edge `mcp -> ingest-javadoc`; `gradlew check` → BUILD FAILED with `AD-1 violation: ':mcp' must not depend on project ':ingest-javadoc' (allowed inward edges: [domain, retrieval]).`. Edge reverted; tree restored to the green state.

### Completion Notes List

- **All 6 tasks complete; AC 1–4 satisfied.** 12 Gradle subprojects build clean; dependency direction is enforced (not merely documented) via a custom `checkDependencyDirection` verification task (wired into `check`) that inspects each module's declared project + external dependencies against an inward-only allow-list and a banned-engine-group map (covering `domain`, `retrieval`, and `mcp`). Proven to fail on a forbidden edge and pass on the clean tree.
- **Module layout:** each Structural-Seed package is promoted to its own Gradle subproject (flat dirs named by project path, e.g. `ingest-javadoc`, `catalog-sqlite`); Java packages still match the Spine tree (`com.luciad.knowledge.ingest.javadoc`, etc.). This is the deliberate, documented variance from the Spine's single-`src` diagram required to *enforce* AD-1.
- **Records:** all 9 are immutable `record` types with an `sdkVersion` field (AD-12). `ApiElement`/`MethodElement` carry `canonicalSymbol` (AD-3). `Citation.canonicalSymbol` is `Optional<String>` (AD-15 amended — symbol-less records stay servable). The four taggable types (`Example`, `Workflow`, `Guide`, `Training`) carry a `techniqueTags` placeholder list (AD-29), defensively copied via `List.copyOf`; tag *vocabulary* left as a deferred gate. Relationship enum has exactly the 15 `UPPER_SNAKE_CASE` values.
- **Ports:** `CatalogStore`/`LexicalIndex`/`VectorIndex` are boundary-only (no methods — schema/config are deferred gates). `Embedder` carries the one AD-21 signature `float[] embed(String text, Role role)` with nested `Role{QUERY,PASSAGE}`.
- **No deferred gates invented:** no SQLite DDL, no Lucene field set, no canonical-symbol/relationship wire grammar, no MCP JSON schemas, no embedding model/dimension. Stack versions are recorded in `gradle/libs.versions.toml` only — no engine library is a real dependency of any module yet.
- **AD-28:** `ingest-trainings` is scaffolded exactly like the other ingest modules at the source level; packaging separation is left to Epic 8/9.
- **Blocker B5 (JDK pin):** pinned to **21 (LTS)** per the Stack table, as the story instructed. If the owner resolves B5 to a different JDK, update the `options.release` / `sourceCompatibility` / `targetCompatibility` values in the root `build.gradle.kts`.
- **Local toolchain notes (environment, not deliverables):**
  - Repo had no Gradle install; used a locally-cached `gradle-8.10` distribution to generate the committed **Gradle wrapper** (pinned to 8.10) as the story's build entrypoint. `gradle.properties` sets `org.gradle.java.installations.auto-download=false` (no toolchain downloads).
  - This host sits behind a corporate SSL proxy whose CA is in the **Windows** trust store but **not** in the JDK `cacerts`. If the wrapper needs to fetch its distribution or Gradle resolves dependencies over the proxy, PKIX failures are worked around by running with `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` so the JVM uses the Windows trust store. This is a **local dev-environment** setting only; it does not affect the air-gapped runtime (AD egress/offline constraints unchanged) and nothing was written to machine `cacerts`. A CI/other-host build should either import the proxy CA into `cacerts` or set the same `GRADLE_OPTS`. Consider documenting in a future build-setup doc.
  - The version catalog records Stack pins only; it is not yet `implementation`/`api`-applied by any module (mirrors the earlier `<dependencyManagement>`-only intent). No test framework (e.g. JUnit) is wired in — no tests ship in this skeleton.

### File List

*All paths below are relative to `app/` — the Gradle build root at the repo root (moved under `app/` on 2026-08-24; BMAD/planning artifacts sit beside it at the repo root).*

**Build / wrapper**
- `settings.gradle.kts` (root aggregator; `rootProject.name` + `include(...)` of all 12 modules)
- `build.gradle.kts` (root: shared `java-library` + Java 21 config + inward-only allow-list, banned-engine-group map, and the `checkDependencyDirection` task wired into `check`)
- `gradle/libs.versions.toml` (version catalog: Stack pins)
- `gradle.properties` (`org.gradle.java.installations.auto-download=false`)
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (wrapper pinned to Gradle 8.10)

**Module build scripts**
- `domain/build.gradle.kts` (no project deps — AD-1 no outward deps)
- `config/build.gradle.kts`
- `catalog-sqlite/build.gradle.kts`
- `search-lucene/build.gradle.kts`
- `embeddings-onnx/build.gradle.kts`
- `retrieval/build.gradle.kts` (no ingest.*/parser edges)
- `ingest-javadoc/build.gradle.kts`
- `ingest-examples/build.gradle.kts`
- `ingest-guides/build.gradle.kts`
- `ingest-trainings/build.gradle.kts`
- `mcp/build.gradle.kts` (no ingest.*/engine edges)
- `cli/build.gradle.kts`

**Domain sources (`domain/src/main/java/com/luciad/knowledge/domain/`)**
- `RelationshipType.java` (15-value enum)
- `ApiElement.java`, `MethodElement.java`, `Example.java`, `Workflow.java`, `Guide.java`, `Training.java`, `RelationshipEdge.java`, `Citation.java`, `IndexManifest.java`
- `CatalogStore.java`, `LexicalIndex.java`, `VectorIndex.java`, `Embedder.java` (+ nested `Role`)
- `package-info.java`

**Package tree + config (one `package-info.java` per module; application.yaml in config)**
- `config/src/main/java/com/luciad/knowledge/config/package-info.java`
- `config/src/main/resources/application.yaml`
- `catalog-sqlite/.../catalog/sqlite/package-info.java`
- `search-lucene/.../search/lucene/package-info.java`
- `embeddings-onnx/.../embeddings/onnx/package-info.java`
- `retrieval/.../retrieval/package-info.java`
- `ingest-javadoc/.../ingest/javadoc/package-info.java`
- `ingest-examples/.../ingest/examples/package-info.java`
- `ingest-guides/.../ingest/guides/package-info.java`
- `ingest-trainings/.../ingest/trainings/package-info.java`
- `mcp/.../mcp/package-info.java`
- `cli/.../cli/package-info.java`

### Change Log

| Date       | Change                                                                 |
| ---------- | ---------------------------------------------------------------------- |
| 2026-08-24 | Story 1.1 implemented: 12-module skeleton, 9 domain records + 15-value relationship enum, 4 engine ports (Embedder+Role), enforced AD-1 dependency direction. Full clean build green; dependency-direction check negative-tested. Status → review. |
| 2026-08-24 | Build tool converted Maven → **Gradle** (Kotlin DSL, wrapper pinned to 8.10). POMs/`mvnw`/`.mvn` removed; added `settings.gradle.kts`, root + per-module `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, wrapper. AD-1 enforcement re-implemented as the `checkDependencyDirection` task. `gradlew check` green; forbidden-edge negative test fails as expected. Story + ARCHITECTURE-SPINE Stack/Structural-Seed updated to name Gradle. Domain sources unchanged (build-agnostic). |
| 2026-08-24 | Senior Developer Review (AI) — adversarial 3-reviewer pass. Outcome: **Approve with follow-ups**. 3 AD-1 enforcement gaps fixed in-review in root `build.gradle.kts` (retrieval engine-ban, test/annotation-proc config coverage, fail-closed `when(dep)` + `FileCollectionDependency` guard); `gradlew check` re-run green. 1 open decision (JDK ≥21 vs ==21, blocked on B5) + 4 low deferrals recorded as Review Follow-ups. |
| 2026-08-24 | Gradle build moved one layer deeper into `app/` (via `git mv`, history preserved) so BMAD/planning artifacts own the repo root — IntelliJ-style project/modules layout. No build-script changes needed (`include(...)` uses default dir mapping); `.gitignore` wrapper negation de-anchored to `**/gradle/wrapper/gradle-wrapper.jar`. `cd app && gradlew check` green. Structural-Seed + this File List re-rooted to `app/`. |

## Senior Developer Review (AI)

**Reviewer:** eric (via Claude Code, adversarial 3-reviewer pass — blind hunter + edge-case hunter + acceptance auditor)
**Date:** 2026-08-24
**Outcome:** ✅ **Approve with follow-ups** — ACs 1–4 satisfied; build green after fixes. No blocking defects.

### Summary

The 12-module Gradle skeleton meets all four acceptance criteria: every module exists, the tree builds with no logic beyond records + empty ports, no external starter is used, and `ingest-trainings` is scaffolded like its peers with packaging deferred (AD-28). The AD-1 dependency direction is genuinely *enforced* (via `checkDependencyDirection`), not just documented. The review found no correctness defect in the domain model or module graph, but did find that the enforcement task under-covered its own mandate in three ways — all now fixed in-review — plus one design decision that is genuinely the owner's (JDK pin) and blocked on B5.

### Findings applied in-review (root `build.gradle.kts`)

1. **[Med] `retrieval` banned-list omitted the engine groups.** The AD-1 forbidden-edge list (Dev Notes) explicitly bans `retrieval → any parser/engine concrete type`, but `retrieval` banned only `org.jsoup` + `com.github.javaparser`; a direct `org.apache.lucene`/`com.microsoft.onnxruntime`/`org.xerial` dependency would have passed. **Fixed** — the three engine groups added.
2. **[Med] Enforcement inspected only 4 configurations.** The loop covered `api`/`implementation`/`compileOnly`/`runtimeOnly`; a forbidden edge declared in `testImplementation` or `annotationProcessor` escaped silently. **Fixed** — expanded to 9 configs (adds test + annotation-processor scopes).
3. **[Low] `when(dep)` had no `else`.** A raw-jar `FileCollectionDependency` (`files("lucene.jar")`) or any non-standard dependency type fell through unchecked. **Fixed** — added a `FileCollectionDependency` guard (rejected for banned-group modules) and a fail-closed `else`.

Re-ran `gradlew check` after the fixes: **BUILD SUCCESSFUL**, all `checkDependencyDirection` tasks pass.

### Open decision (owner)

- **[Med] JDK pinned as ≥21, not ==21.** `options.release=21` sets the bytecode target only; the build runs on any JDK ≥ 21 and no toolchain enforces exact 21 — yet `gradle.properties` reads as a hard "must run on JDK 21". This is tied to open blocker **B5** (21 vs 25 vs org LTS), so it is deliberately *not* silently pinned. Owner picks: add a `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` (hard-pin, fails loudly when a 21 toolchain is absent) **or** soften the comment to "≥ 21". Recorded as a Review Follow-up.

### Deferred (low — not required by this skeleton)

- `config`/`cli`/`ingest.*` carry no banned-external entry. This *matches* the spec's explicit forbidden-edge list (only `domain`/`mcp`/`retrieval` are named) and adapters legitimately need engines — tighten `ingest.*`/`config` bans when engine libs are actually wired (Epic 2+).
- Mandatory `Citation` fields accept null; no null-guards on record components generally. Skeleton is intentionally minimal — revisit when construction/validation lands.
- `checkDependencyDirection` aborts on the first violation (ergonomics only).
- Blanket `api(project(...))` republishes transitively — tighten to `implementation` where re-exposure isn't needed, once real types exist.

### Dismissed

- **`Optional<String>` as a `Citation` record component** — mandated by AD-15 (amended). Per spec, not a defect.
- **`List.copyOf` NPE on a null element** — acceptable fail-fast; the null-*list* case is already handled by the ternary.
- **"Gradle wrapper not delivered"** — false positive. The wrapper files (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`, pinned to 8.10) are present on disk; the finding was an artifact of the source-only review diff excluding `gradle/wrapper/*`.
- **Redundant `sourceCompatibility`/`targetCompatibility` alongside `options.release`** — cosmetic, no consequence.
- **"Maven → Gradle swap unsanctioned"** — the conversion was explicitly requested by the owner and is documented in the Change Log.
