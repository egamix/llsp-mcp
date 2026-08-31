---
baseline_commit: 4cdb5e79f6f0d4c36cf40b005641551eb5bb1d21
---

# Story 2.1: Discover and classify Javadoc pages

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want the ingester to use `*-search-index.js` where present, fall back to recursive `*.html` discovery, and classify each page by structure rather than filename,
so that nav/tree/help/index pages are excluded from member ingestion while overview/package pages are kept as narrative records.

## Acceptance Criteria

1. **Given** a Javadoc tree with a search index **plus** a synthetic fixture that lacks one, **when** discovery runs on each, **then** both produce the **same set of classified pages** (same relative paths, same page classification for each path).
2. Nav/tree/help/index pages (e.g. `overview-tree.html`, `package-tree.html`, `help-doc.html`, `index-all.html`, `allclasses-index.html`, `allpackages-index.html`, `deprecated-list.html`, `constant-values.html`, `serialized-form.html`, generated nav/frame pages) are **excluded from member extraction** — they are classified into an excluded/non-member bucket.
3. Overview and package pages are **retained as narrative pages** (a narrative classification), **not** member pages.
4. Type pages (class / interface / enum / annotation-type) are classified as **member-bearing** pages so later stories (2.2, 2.3) know to extract members from them.
5. Classification is decided by **HTML structure**, not by filename. A page whose filename looks like a type page but whose structure is not a type page is classified by its structure, and vice-versa.
6. Discovery and classification are **deterministic**: the returned page set is ordered by relative path (`/`-separated, OS-independent) and identical inputs yield an identical result. Nothing is random or wall-clock-derived.
7. A page that cannot be classified does **not** crash discovery; it is placed in an explicit `UNKNOWN` bucket and reported, so the run can continue and later stories decide policy.
8. No code path in this component performs a network request (AD-9). HTML is parsed only from local bytes; `*-search-index.js` is read only from local bytes. `Jsoup.connect(...)` / any remote fetch is never used.

## Tasks / Subtasks

- [ ] Task 1: Wire JSoup and JUnit into the `ingest-javadoc` module (AC: 1–8)
  - [ ] In [app/ingest-javadoc/build.gradle.kts](app/ingest-javadoc/build.gradle.kts) add `implementation(libs.jsoup)`. This is the **first** module to apply the JSoup catalog alias — the alias already exists in [app/gradle/libs.versions.toml](app/gradle/libs.versions.toml) (`jsoup = "1.23.1"`, `libs.jsoup` → `org.jsoup:jsoup`). Do not add a version string; use the catalog alias.
  - [ ] Add test deps mirroring the established pattern in [app/domain/build.gradle.kts](app/domain/build.gradle.kts): `testImplementation(libs.junit.jupiter)` and `testRuntimeOnly(libs.junit.platform.launcher)`.
  - [ ] Do **not** add JavaParser here — AST parsing of examples is Epic 3, not this story. Do **not** add SQLite/Lucene/ONNX usage — those project edges already exist as `api(project(...))` but are unused by this story.
  - [ ] Confirm the dependency-direction guard still passes: `ingest-javadoc` is allowed to depend on `org.jsoup` (it is NOT in `bannedExternalGroups`, which lists jsoup/javaparser/lucene/onnx/xerial as banned only for `domain`/`retrieval`/`mcp`). See [app/build.gradle.kts](app/build.gradle.kts).
- [ ] Task 2: Define the classification model inside `ingest.javadoc` (AC: 2, 3, 4, 7)
  - [ ] Add a `PageType` enum in `com.luciadmcp.knowledge.ingest.javadoc` with at least: `TYPE` (member-bearing: class/interface/enum/annotation-type), `PACKAGE` (narrative), `OVERVIEW` (narrative), `EXCLUDED` (nav/tree/help/index — not ingested as members or narrative), `UNKNOWN` (could not classify). Give it a helper such as `boolean isMemberBearing()` (true only for `TYPE`) and `boolean isNarrative()` (true for `PACKAGE`/`OVERVIEW`).
  - [ ] Add a `ClassifiedPage` record: `relativePath` (String, `/`-separated) + `PageType`. Keep it minimal — **no** extracted fields, **no** canonical symbol, **no** anchors. Field extraction is Story 2.2; canonical symbols are Story 2.3.
  - [ ] Do **NOT** create or extend any `domain` record type (e.g. `ApiElement`, or a new "narrative"/"page" record). The domain record field set is a **deferred Epic-2 approval gate** — the classification model lives entirely inside `ingest.javadoc` as an internal contract consumed by later stories. "Narrative record" in the AC = the `PACKAGE`/`OVERVIEW` classification, not a persisted domain type.
- [ ] Task 3: Implement discovery — search-index-assisted with recursive `*.html` fallback (AC: 1, 6, 8)
  - [ ] Add a discovery component (suggested `JavadocDiscovery`) that takes the Javadoc root `Path` and returns the ordered list of candidate `*.html` pages to classify.
  - [ ] Always enumerate candidate pages by a recursive walk of `*.html` under the root, in **sorted relative-path order** with `/` separators. Reuse the exact idiom from [BuildContext.relativeKey](app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java) (`base.relativize(file).toString().replace('\\','/')`) so paths are OS-independent — copy the idiom, do not depend on `cli` (AD-1 forbids `ingest-javadoc` → `cli`).
  - [ ] When one or more `*-search-index.js` files exist at the root (`type-search-index.js`, `member-search-index.js`, `package-search-index.js`, `tag-search-index.js`), read them from local bytes and use them as an **assist** — to confirm which discovered pages are type/package pages. The search index must not *replace* the recursive walk: it lists types/members/packages only, never nav/help/tree pages, so the recursive walk remains the authoritative candidate set. This is what makes AC 1 hold — the classified page set is a function of the walk + structural classification, and is identical whether or not a search index is present.
  - [ ] Parse `*-search-index.js` defensively: it is `var typeSearchIndex = [ {"p":"pkg","l":"Label"}, ... ]; updateSearchResults();` — extract the JSON array only; tolerate the `var name =` prefix and trailing `;`/function call. A missing or malformed search index is **not** an error (fallback path); log/collect and proceed with the recursive walk.
- [ ] Task 4: Implement structure-based classification (AC: 2, 3, 4, 5, 7)
  - [ ] Add a classifier (suggested `JavadocPageClassifier`) that parses each candidate page with `Jsoup.parse(File, charsetName)` (or parse of the already-read bytes) and assigns a `PageType` from **DOM structure**, never the filename.
  - [ ] Structural signals (support both modern and legacy generators; refine in Story 2.2, but bucket correctly here):
    - Type page → `TYPE`: modern `body` class such as `class-declaration-page` and/or a `section.class-description` / member-summary tables; legacy `div.header` + `ul.blockList`/`li.blockList` with a type declaration heading.
    - Package page → `PACKAGE`: modern `package-declaration-page` body / package summary of classes; legacy `package-summary` structure (a table listing classes with no single-type declaration).
    - Overview page → `OVERVIEW`: root overview / package-listing structure (lists packages, not members).
    - Nav/tree/help/index → `EXCLUDED`: tree structure (`ul.horizontal`/tree lists), help layout, index-of-terms listing, all-classes/all-packages index, deprecated/constant/serialized-form summaries, generated nav/frame pages with no member content.
    - Anything else → `UNKNOWN` (AC 7): never throw; record and continue.
  - [ ] Filename may be used only as a *tie-breaker/cross-check*, never as the primary decision (AC 5). If you use the search-index assist, it corroborates structure — a conflict resolves to the structural verdict.
- [ ] Task 5: Expose a single entry point returning the deterministic classified set (AC: 1, 6, 7)
  - [ ] Provide one public method (suggested `JavadocDiscovery.classify(Path root) -> DiscoveryResult`) returning the ordered `List<ClassifiedPage>` plus the `UNKNOWN`/malformed-index items surfaced (a small result holder, or the list + a separate accessor). Order by relative path.
  - [ ] Keep the component **pure and side-effect-free** beyond reading local files: no writes, no pipeline wiring, no `System.exit`. It is a library consumed later by the CATALOG stage (Story 2.4) — do **not** wire it into [PipelineStage.CATALOG](app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/PipelineStage.java) or touch `IngestPipelineRunner` in this story.
- [ ] Task 6: Synthetic fixtures + tests (AC: 1–8)
  - [ ] Create synthetic Javadoc fixtures under `app/ingest-javadoc/src/test/resources/` — hand-authored HTML only. **No proprietary Luciad content** may be committed (repo rule). Build two fixtures over the **same logical page set**: one **with** `*-search-index.js`, one **without**. Include at least one of each page type: a type page, a package page, an overview page, and several excluded pages (tree/help/index/all-classes/deprecated), plus one deliberately malformed page for the `UNKNOWN` path.
  - [ ] Test AC 1: classifying the with-index and without-index fixtures yields **equal** `List<ClassifiedPage>` (same paths, same types). This is the headline test.
  - [ ] Test AC 2/3/4: nav/tree/help/index → `EXCLUDED`; overview/package → narrative (`OVERVIEW`/`PACKAGE`); type pages → `TYPE` (member-bearing).
  - [ ] Test AC 5: a page whose filename disagrees with its structure is classified by structure (include one such page in the fixture).
  - [ ] Test AC 6: two runs over the same fixture return identical, relative-path-ordered results; use `@TempDir` where a temp tree is needed, mirroring Epic-1 test style.
  - [ ] Test AC 7: the malformed page is `UNKNOWN` and does not throw.
  - [ ] Test AC 8: classification never opens a network connection. A lightweight assertion: parse a fixture page that references an external `http(s)` resource (stylesheet/script `src`) and confirm no fetch occurs (the component only reads the local file). Do **not** reuse `cli`'s egress guard (AD-1) — assert structurally that only local file reads happen.

## Dev Notes

### What this story delivers (and what it deliberately does not)

- **Deliverable:** a pure, unit-testable discovery + classification library inside `ingest.javadoc` that, given a Javadoc root directory, returns a deterministic, relative-path-ordered set of `ClassifiedPage`. That is the whole story.
- **Out of scope — do not implement here, do not stub ahead:**
  - Field extraction (description/params/returns/throws/since/deprecated) → **Story 2.2**.
  - Canonical symbol keys and overload distinction, normalized lookup variants → **Story 2.3**.
  - SQLite persistence, path/anchor storage, version-scoped keys → **Story 2.4**.
  - Relationship graph → **Story 2.5+**.
  - Wiring into the `CATALOG` pipeline stage → happens when persistence lands (**Story 2.4**). The stage is a no-op today and stays a no-op after this story.
  - Reading the SDK version from the Javadoc (AD-12) → belongs with catalog persistence (see `STUB_SDK_VERSION` note in [IngestPipelineRunner.java](app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunner.java)); **not** this story.

### Architecture compliance (the guardrails that make this story pass review)

- **AD-1 (inward dependency direction, enforced by `checkDependencyDirection`):** `ingest-javadoc` may depend on `domain` and (unused here) `catalog-sqlite`/`search-lucene`/`embeddings-onnx`. It must **not** depend on `cli`. Consequence: the pipeline's `FailurePolicy`, `StageFailure`, and `BuildContext` types live in `cli` and are **unavailable** to this component. Do not import them. Express "couldn't classify" via the `UNKNOWN` bucket and a result holder, in this module's own terms. The fail-fast / collect-and-continue policy attaches later, at the CATALOG stage (Story 2.4), where `cli` adapts this component's output into `StageFailure`s.
- **AD-4 (two-pass, never fabricate):** classification reports only what the HTML structure supports. An unclassifiable page is `UNKNOWN`, never guessed into `TYPE`.
- **AD-9 (zero egress):** parse from local bytes only. Use `Jsoup.parse(File, charset)` or `Jsoup.parse(String html, String baseUri)`. **Never** `Jsoup.connect(url)`. JSoup's file/string parse does not fetch remote resources (no DTD/entity/network resolution) — keep it that way; do not enable any base-URI resolution that fetches.
- **AD-10 (parse-only static analysis):** HTML is parsed, never rendered or executed. `*-search-index.js` is read as **text and JSON-extracted**, never evaluated as JavaScript.
- **AD-12 (SDK-version scoping):** not exercised here (no records are minted). Do not invent a version field on `ClassifiedPage`.
- **AD-17 / AD-25:** unchanged — this story does not touch build identity, staging, or checkpoints.

### JSoup wiring — first parser in the codebase

- The catalog already declares JSoup 1.23.1; **no module applies it yet**. Applying `implementation(libs.jsoup)` in `ingest-javadoc` is expected and allowed. Verify by running the dependency-direction guard (command below) — it inspects `api`/`implementation`/`testImplementation`/etc. configurations against `allowedProjectDeps` and `bannedExternalGroups`. `ingest-javadoc` is absent from `bannedExternalGroups`, so external `org.jsoup` is permitted; the banned list only fences `domain`/`retrieval`/`mcp`.
- Prefer `implementation` over `api`: nothing downstream needs JSoup transitively (the CATALOG stage will consume `ClassifiedPage`, a plain record, not JSoup types). Keep JSoup types **out of** the public method signatures of this component.

### Source tree — files to touch

- **UPDATE** [app/ingest-javadoc/build.gradle.kts](app/ingest-javadoc/build.gradle.kts) — add `implementation(libs.jsoup)`, `testImplementation(libs.junit.jupiter)`, `testRuntimeOnly(libs.junit.platform.launcher)`. Its current comment says JSoup+JavaParser are wired in Epic 2; wire **only** JSoup now and update the comment to reflect that JavaParser is still deferred to Epic 3.
- **NEW** `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/` — `PageType`, `ClassifiedPage`, `JavadocDiscovery`, `JavadocPageClassifier` (+ a small `DiscoveryResult` holder if used). The package currently holds only [package-info.java](app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/package-info.java) (a stub stating parsing lands in Epic 2). Update the package-info prose to describe the now-real discovery/classification component.
- **NEW** `app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/` — tests.
- **NEW** `app/ingest-javadoc/src/test/resources/` — the two synthetic fixtures (with/without search index) + malformed page.
- **DO NOT TOUCH** anything under `app/cli/` (pipeline, runner, stages), `app/domain/`, or the settings/version-catalog beyond the `build.gradle.kts` edit above.

### Java package naming (hard rule)

- Package root is `com.luciadmcp.knowledge.*` — here `com.luciadmcp.knowledge.ingest.javadoc`. Never a bare `luciad` package. Directory names, Gradle project names, and file names are unaffected by this rule.

### Determinism (carried over from Epic 1)

- Order the classified page list by relative path (`/`-separated) so output is independent of filesystem walk order — same discipline as `BuildContext.hashRootFiles` sorting. Downstream reproducibility (NFR-13, deterministic IDs in Story 1.4) depends on stable ordering when this feeds catalog building in 2.4.
- No `Date`, no random, no locale-dependent casing that could change classification between machines — normalize any structural token comparison with `Locale.ROOT`.

### Testing standards

- JUnit 5 (Jupiter), `@TempDir` for temp trees, one clearly-named test per behavior — mirror the Epic-1 test style (see `domain`/`cli` test suites).
- **Synthetic fixtures only.** No proprietary Luciad Javadoc may be committed. Author minimal HTML that exhibits the structural signals above; keep pages tiny.
- The with-index vs without-index equality test (AC 1) is the story's headline proof — make it explicit and readable.

### Build / verify command (Windows, from Story 1.4)

```
cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew :ingest-javadoc:test checkDependencyDirection
```

`checkDependencyDirection` is a bare aggregate task (no leading colon). Run it to confirm the JSoup edge does not violate AD-1.

### Project Structure Notes

- The component slots into `ingest.javadoc` exactly where the architecture spine places F2 Javadoc parsing (`ingest.javadoc`, defined by AD-4/AD-10/AD-3). No new module, no settings change.
- Variance from the epics wording: the epics say "search-index-assisted discovery … with recursive fallback." The chosen design always runs the recursive walk and treats the search index as a corroborating assist rather than an alternative enumerator. Rationale: only this guarantees AC 1 (identical classified set with/without the index), because the search index never lists nav/help/tree/overview pages. This is a deliberate, review-defensible reading of FR-4, not a scope change.
- No domain record is introduced — consistent with the deferred Epic-2 domain-record approval gate. `ClassifiedPage`/`PageType` are internal `ingest.javadoc` contracts.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.1: Discover and classify Javadoc pages] — user story + ACs.
- [Source: _bmad-output/planning-artifacts/epics.md#F2] — FR-4 (search-index-assisted discovery, structure-based page typing, exclusions, narrative pages).
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md] — AD-1, AD-4, AD-9, AD-10, AD-12; F2 lives in `ingest.javadoc`; JSoup 1.23.1 stack pin.
- [Source: app/build.gradle.kts] — `allowedProjectDeps`, `bannedExternalGroups`, `checkDependencyDirection`.
- [Source: app/gradle/libs.versions.toml] — `libs.jsoup`, `libs.junit.jupiter`, `libs.junit.platform.launcher`.
- [Source: app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/BuildContext.java] — `relativeKey` `/`-separator idiom, sorted-walk determinism to reuse.
- [Source: app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/PipelineStage.java] — CATALOG stage stays no-op; wiring is Story 2.4.
- [Source: app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/package-info.java] — current stub; parsing lands in Epic 2.
- [Source: _bmad-output/implementation-artifacts/1-4-deterministic-ids-content-hashes-and-a-machine-readable-ingestion-report.md] — determinism, synthetic-fixture rule, build command, test conventions.

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
