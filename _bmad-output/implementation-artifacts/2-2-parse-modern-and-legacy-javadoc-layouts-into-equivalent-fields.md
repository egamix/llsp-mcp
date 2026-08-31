---
baseline_commit: 7a3b43a4f70c86ddf9d825e4f9f25e49f7e332f8
---

# Story 2.2: Parse modern and legacy Javadoc layouts into equivalent fields

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want both modern (`section.class-description`/member-summary/`section.detail`) and legacy (`ul.blockList`/`li.blockList`/`h3`/`h4`) layouts to extract equivalent fields, with broken/incomplete HTML degrading per the configured policy,
so that the catalog doesn't depend on which Javadoc generator produced the source tree.

## Acceptance Criteria

1. **Given** synthetic fixtures for both layout families (modern and legacy) covering the **same API surface**, **when** each is parsed, **then** the extracted fields — type-level `description`, `since`, `deprecated`; and per-member `description`, `params`, `returns`, `throws`, `since`, `deprecated` — are **equal** between the two layouts (same members in the same order, same field values). This is the headline test, mirroring Story 2.1's with-index/without-index equality proof.
2. Extraction covers the three modern generator eras that appear in real Luciad trees: JDK 8 legacy (`ul.blockList`/`li.blockList`/`h3`/`h4`, `dl`/`dt`/`dd` tag notes), JDK 11–16 modern (`section.class-description`, `table.memberSummary`, `section.detail`), and JDK 17+ modern (`.summary-table`, `section.detail` with `ul.member-list`, `dl.notes`). A member present in one era's fixture is extracted with the same field values as the equivalent member in the others.
3. Extraction runs **only on `TYPE` pages** (as classified by Story 2.1's `JavadocPageClassifier`). Narrative pages (`PACKAGE`/`OVERVIEW`) and `EXCLUDED`/`UNKNOWN` pages are not passed to member extraction. The type page's own class-level description, `@since`, and `@deprecated` are extracted; each documented member (method, constructor, field) is extracted as a distinct unit carrying its own doc fields.
4. **No canonical symbol is minted and no domain record is created or extended.** The output is an `ingest.javadoc`-internal extracted-fields model (a new record set alongside `ClassifiedPage`), carrying the raw extracted text/fields plus the member's structural name and signature text. Canonical symbol keys and overload distinction are **Story 2.3**; SQLite persistence and path/anchor storage are **Story 2.4**. The domain `ApiElement`/`MethodElement` field set remains the deferred Epic-2 approval gate — do not touch it.
5. A fixture with **broken/incomplete HTML** (a member missing its description, a truncated `dl` tag list, an unparseable fragment, an empty `class-description`) is handled gracefully: extraction never throws on malformed markup, extracts every field it can, and records each thing it could not extract as an **explicit, structured issue** (with the offending page's relative path and a reason) on the returned result — never a silent drop, never a fabricated value (AD-4).
6. The extractor expresses degradation **in its own module terms** and does not import any `cli` type (`FailurePolicy`, `StageFailure`, `FailureSeverity` — AD-1 forbids `ingest.javadoc` → `cli`). It surfaces per-page extraction issues so that the CATALOG stage in `cli` (Story 2.4) can later map them onto the `fail-fast | collect-and-continue` policy from Story 1.3. The extractor itself makes no fail-fast/continue decision and calls no `System.exit`.
7. Extraction is **deterministic and offline**: members are returned in a stable, structural order (source/document order, not hash/iteration order); identical input HTML yields an identical extraction result; no `Date`, random, or locale-dependent casing changes output (normalize any token comparison with `Locale.ROOT`). No network access anywhere — HTML is parsed from local bytes only, `Jsoup.connect(...)` is never used (AD-9); HTML is parsed, never rendered or executed (AD-10).
8. Extraction is a **pure library** consumed later by the CATALOG stage: it reads already-parsed pages (or local bytes), writes nothing, performs no pipeline wiring, and is **not** wired into `PipelineStage.CATALOG` or `IngestPipelineRunner` in this story (that is Story 2.4).

## Tasks / Subtasks

- [x] Task 1: Define the extracted-fields model inside `ingest.javadoc` (AC: 3, 4, 7)
  - [x] Add an `ExtractedType` record (suggested) in `com.luciadmcp.knowledge.ingest.javadoc`: `relativePath` (String, `/`-separated — reuse the value from the `ClassifiedPage`), the type's `description` (String, may be empty), `since` (String or `Optional<String>`), `deprecated` (a small holder: a boolean/flag plus the deprecation prose, since `@deprecated` carries text), and an ordered `List<ExtractedMember>`.
  - [x] Add an `ExtractedMember` record: the member's structural `name` (as it appears in the page, e.g. `doThing`), `signature` (the raw signature text, e.g. `void doThing(int count)`), a `kind` (an enum: `METHOD`/`CONSTRUCTOR`/`FIELD` — decided by structure, not filename), `description`, an **ordered** `List` of params (each: param name + prose), `returns` (String/Optional), an **ordered** `List` of throws (each: exception name/text + prose), `since`, and `deprecated`.
  - [x] Keep the model minimal and free of canonical symbols, anchors, and domain types. It is an internal contract consumed by Story 2.3 (canonical symbols) and Story 2.4 (persistence) — exactly the discipline `ClassifiedPage`/`PageType` established in Story 2.1. Do **NOT** add a canonical-symbol field, an SDK-version field, or an HTML anchor field here.
  - [x] Validate record invariants in compact canonical constructors the same way `ClassifiedPage` does (reject null `relativePath`/`kind`; default null lists to empty immutable lists via `List.copyOf`).
- [x] Task 2: Implement the layout-agnostic field extractor (AC: 1, 2, 3, 5, 7)
  - [x] Add a `JavadocFieldExtractor` (suggested) with one public method taking a JSoup `Document` (and the page's `relativePath`) and returning an `ExtractedType` plus its issues. Keep JSoup types **out of** any type the CATALOG stage will consume — mirror how `JavadocPageClassifier` keeps `Document` internal and returns plain records.
  - [x] Extract **type-level** fields:
    - Modern: type description from `section.class-description .block` (fallback `section.class-description`); `@since`/`@deprecated` from the class-description's `dl.notes` (`dt` label → following `dd`) and `div.deprecation-block`/`span.deprecated-label`.
    - Legacy: type description from `div.description .block` / the type's `div.block`; `@since` from the description `dl` (`dt` "Since:" → `dd`); `@deprecated` from `div.block > span.deprecatedLabel` + `.deprecationComment`/following text.
  - [x] Extract **members** from the detail sections (the authoritative source for full doc fields — summary tables carry only a one-line description):
    - Modern (JDK 11–16): `section.detail` blocks — member name/signature from `.member-signature` (or the section's `h3`/`h4`), description from `.block`, tag notes from `dl.notes` (`dt` text `Parameters:`/`Returns:`/`Throws:`/`Since:` mapping to the following `dd`s; a `Parameters:`/`Throws:` `dd` typically starts with a `<code>`/name span then the prose).
    - Modern (JDK 17+): same as above but members live under `ul.member-list > li` inside `section.detail`; the summary container is `.summary-table` (do **not** reuse the classifier's `hasMemberBlocks` selector — see the Review note in Dev Notes).
    - Legacy (JDK 8): `ul.blockList > li.blockList` member blocks with `h4` (member name), `pre` (signature), `div.block` (description), and a `dl` whose `dt` "Parameters:"/"Returns:"/"Throws:"/"Since:" precede `dd`s.
  - [x] Distinguish `@param`/`@throws` list items in order; keep param order and throws order stable (they are semantically ordered). Split each param `dd` into (name, prose) and each throws `dd` into (exception, prose) using the structural leading `<code>`/name element, not a text regex where the DOM already separates them.
  - [x] Normalize whitespace in extracted prose (collapse runs, trim) so modern and legacy produce byte-equal field values for the same content — this is what makes AC 1 hold. Use `Element.text()`/`wholeText()` deliberately and consistently across both layouts.
- [x] Task 3: Graceful degradation and issue reporting — module-local, no `cli` types (AC: 5, 6, 8)
  - [x] Add an issues channel on the result: either an `ExtractionResult` holder `{ ExtractedType type, List<String> issues }` or an issues accessor. Model it on Story 2.1's `DiscoveryResult` (`pages` + `warnings`) — same shape, same intent.
  - [x] Wrap per-member and per-field extraction so a malformed sub-tree records an issue (`relativePath` + what was missing/unparseable + which member when applicable) and continues to the next member/field. Never throw out of the public method on bad markup; only genuinely exceptional I/O (unreadable file if you also accept a `Path` overload) may surface, consistent with `JavadocDiscovery.walkAndClassify` catching `IOException` → recorded, not fatal.
  - [x] An absent optional field (no `@since`, no `@deprecated`, no `@return` on a void method) is **normal and silent** — not an issue. Only genuinely broken/incomplete structure (a member with no recoverable name, a truncated tag list, an empty required description block) is an issue. Do not over-report.
  - [x] Do **NOT** import `FailurePolicy`/`StageFailure`/`FailureSeverity` or any `com.luciadmcp.knowledge.cli.*` type. Do **NOT** decide fail-fast vs continue here. The mapping of these issues onto the run policy happens in `cli` at the CATALOG stage in Story 2.4; this story only produces the issue list in the module's own vocabulary.
- [x] Task 4: Synthetic fixtures over one shared API surface (AC: 1, 2, 5)
  - [x] Under `app/ingest-javadoc/src/test/resources/`, author hand-written HTML fixtures — **no proprietary Luciad content** (repo rule). Build the **same API surface** (e.g. a class with: a class description + `@since` + a `@deprecated` note; one method with `@param`×2/`@return`/`@throws`/`@since`; a deprecated overloaded method; a constructor with a `@param`; a field with a description) rendered three ways: JDK 8 legacy, JDK 11–16 modern, JDK 17+ modern.
  - [x] Extend, do not fork, the existing fixture trees where sensible (`javadoc-fixtures/modern/`, `javadoc-fixtures/legacy/`). Existing `Widget.html`/`LegacyClass.html` are classification-only stubs (description/members are placeholders) — add richer type pages for extraction rather than overloading those.
  - [x] Add at least one **broken/incomplete** fixture per family: a member missing its `.block`/`div.block` description, a `dl`/`dl.notes` truncated mid-list, an empty `section.class-description`.
- [x] Task 5: Tests (AC: 1–8)
  - [x] AC 1 headline: parse the legacy and modern (both eras) fixtures for the shared API surface and assert the resulting `ExtractedType` field values and ordered member lists are **equal** across layouts. Make this test explicit and readable — it is the story's proof.
  - [x] AC 3: assert only `TYPE` pages are extracted (a `PACKAGE`/`OVERVIEW`/`EXCLUDED` page passed in is either rejected or yields no members — pick one contract and test it); assert type-level `description`/`since`/`deprecated` are captured.
  - [x] AC 2: assert JDK 8 / JDK 11–16 / JDK 17+ member containers all extract the same member set and fields.
  - [x] AC 5/6: assert the broken/incomplete fixtures extract the recoverable fields, record explicit issues for the unrecoverable ones, and **do not throw**; assert no `cli` type is referenced (a source/import check like `NoJsoupConnectSourceTest`, or simply by the module not compiling against `cli` — AD-1 already enforces this at the Gradle level).
  - [x] AC 7: assert two runs over the same fixture return equal results and member order is stable; assert no `Jsoup.connect` (the existing `NoJsoupConnectSourceTest` source-scan already guards the whole module — confirm it still passes and covers the new class).
  - [x] Use JUnit 5 (Jupiter), `@TempDir` only where a temp tree is genuinely needed; prefer classpath-resource fixtures. Mirror `JavadocDiscoveryTest` style.

## Dev Notes

### What this story delivers (and what it deliberately does not)

- **Deliverable:** a pure, unit-testable field extractor inside `ingest.javadoc` that, given a `TYPE` page, returns a deterministic `ExtractedType` (type-level description/since/deprecated + ordered members with description/params/returns/throws/since/deprecated) plus an explicit issue list for broken/incomplete markup — identical for modern and legacy layouts over the same API surface.
- **Out of scope — do not implement here, do not stub ahead:**
  - Canonical symbol keys, overload distinction, normalized lookup variants → **Story 2.3**. Story 2.2 extracts members as raw structural units; 2.3 mints canonical symbols from them.
  - SQLite persistence, relative-path/anchor storage, version-scoped keys → **Story 2.4**.
  - Relationship graph → **Story 2.5+**.
  - Wiring into `PipelineStage.CATALOG` / `IngestPipelineRunner` → **Story 2.4** (the CATALOG stage is a no-op today and stays a no-op after this story).
  - Reading the SDK version from the Javadoc (AD-12) → belongs with catalog persistence (Story 2.4); do not invent a version field on the extracted model.
  - Narrative-prose extraction from `PACKAGE`/`OVERVIEW` pages → not required by this story's ACs; keep extraction to `TYPE` pages.

### Architecture compliance (the guardrails that make this story pass review)

- **AD-1 (inward dependency direction, enforced by `checkDependencyDirection`):** `ingest.javadoc` must **not** depend on `cli`. `FailurePolicy`, `StageFailure`, and `FailureSeverity` live in `cli` and are **unavailable** — do not import them. Express "couldn't extract X" via a module-local issue list on the result holder. The fail-fast/collect-and-continue mapping attaches later, at the CATALOG stage (Story 2.4), where `cli` adapts these issues into `StageFailure`s with the right `FailureSeverity`. [Source: app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/FailurePolicy.java, StageFailure.java, FailureSeverity.java]
- **AD-4 (two-pass, never fabricate):** extraction reports only what the HTML supports. A missing description is empty/absent + (if the structure implies it should exist) an issue — never a guessed value. Unresolved/unparseable content is recorded explicitly, never dropped silently. [Source: ARCHITECTURE-SPINE.md#AD-4]
- **AD-9 (zero egress):** parse from local bytes only. Reuse the parse idiom from Story 2.1 (`Jsoup.parse(file, null)` to honor BOM/meta charset, else operate on an already-parsed `Document`). **Never** `Jsoup.connect(url)`. [Source: app/ingest-javadoc/.../JavadocDiscovery.java:93-95]
- **AD-10 (parse-only static analysis):** HTML is parsed, never rendered or executed. No compiling, no symbol-solving, no execution. [Source: ARCHITECTURE-SPINE.md#AD-10]
- **AD-12 (SDK-version scoping):** not exercised here (no persisted records minted). Do not invent a version field on the extracted model. [Source: ARCHITECTURE-SPINE.md#AD-12]
- **Malformed-page ingestion policy is an Epic-2-owned gate:** the spine explicitly defers the malformed-page policy default and collect-and-continue report shape to Epic 2 as an `ingest.javadoc` implementation detail. This story implements the graceful-degradation half (extract-what-you-can + explicit issues); the policy *decision* is applied by `cli` in Story 2.4. [Source: ARCHITECTURE-SPINE.md#Deferred — "Malformed-page ingestion policy"]

### Reuse — build on Story 2.1, do not reinvent

- **Classification already exists.** `JavadocPageClassifier.classify(Document) -> PageType` and `PageType.isMemberBearing()` (true only for `TYPE`) tell you which pages to extract. `JavadocDiscovery.classify(Path) -> DiscoveryResult` already produces the ordered `List<ClassifiedPage>`. This story consumes `TYPE` pages; do not re-implement discovery or classification. [Source: app/ingest-javadoc/.../JavadocPageClassifier.java, PageType.java, JavadocDiscovery.java]
- **Result-holder pattern is established.** `DiscoveryResult { List<ClassifiedPage> pages; boolean searchIndexPresent; List<String> warnings }` is the exact shape to mirror for your `ExtractionResult { ExtractedType type; List<String> issues }`. Same immutability discipline (`List.copyOf`), same "warnings/issues are strings with the relative path baked in." [Source: app/ingest-javadoc/.../DiscoveryResult.java, JavadocDiscovery.java:71-79]
- **Charset + local-bytes parse idiom.** If you accept a `Path`, reuse `Jsoup.parse(file, null)` (honors BOM/meta charset, UTF-8 fallback) exactly as `JavadocDiscovery.walkAndClassify` does. Prefer accepting an already-parsed `Document` so discovery parses each page once and hands it to both classifier and extractor (avoids double-parsing large trees — real trees are ~17k pages). [Source: app/ingest-javadoc/.../JavadocDiscovery.java:93-107]
- **`Locale.ROOT` for every token comparison** (label text like "Parameters:"/"Since:", body-class tokens). The classifier already does this; matching it keeps behavior machine-independent. [Source: app/ingest-javadoc/.../JavadocPageClassifier.java:93,128]

### Review note carried over from Story 2.1 — read before extracting members

- Story 2.1's code review deferred this item **specifically to Story 2.2**: *"`hasMemberBlocks` selector misses the modern member container (JDK 17+ `.summary-table`) — classification unaffected (body-class wins) but Story 2.2 extraction should not reuse this selector as-is."* The classifier's `MEMBER_BLOCK_SELECTOR` (`section.class-description, table.memberSummary, .memberSummary, ul.blockList > li.blockList, ul.memberSummary`) is a *classification heuristic*, tuned for "does this look member-bearing," and is wrong for extraction on real JDK 17+ pages. Author extraction selectors from the actual member-**detail** structure per era (see Task 2), and validate against the JDK 17+ fixture. [Source: 2-1-discover-and-classify-javadoc-pages.md#Review Findings — Deferred]
- Story 2.1 validated the classifier against a **real 432-page `com.luciad.shape` export** (gitignored, not committed). If that local tree is still available to you, spot-check extraction against a handful of its real `TYPE` pages before finalizing selectors — but commit only synthetic fixtures.

### Modern vs legacy structure cheat-sheet (synthetic; real pages are richer)

- **Type description:** modern `section.class-description .block`; legacy `div.description .block` (or the type's `div.block`).
- **Member detail (full fields live here, not in the summary table):**
  - JDK 11–16: `section.detail` → per member: `.member-signature` (name+sig), `.block` (description), `dl.notes` (`dt`=`Parameters:`/`Returns:`/`Throws:`/`Since:`, `dd`=value).
  - JDK 17+: `section.detail ul.member-list > li` → same inner structure (`.member-signature`, `.block`, `dl.notes`).
  - JDK 8 legacy: `ul.blockList > li.blockList` → `h4` (name), `pre` (signature), `div.block` (description), `dl` (`dt`=`Parameters:`/… , `dd`=value).
- **`@param` / `@throws` `dd`:** leading `<code>`/name element = the param/exception identifier; the trailing text = the prose. Split on the DOM element, not a regex.
- **`@deprecated`:** modern `div.deprecation-block` / `span.deprecated-label` + comment; legacy `span.deprecatedLabel` + `.deprecationComment`/following text.
- The **synthetic fixtures decide correctness for this story** (AC 1's equality test); the era selectors above make the extractor robust when it later meets real trees.

### Source tree — files to touch

- **NEW** `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/` — `ExtractedType`, `ExtractedMember` (+ a `MemberKind` enum and a small `ExtractionResult` holder if used), `JavadocFieldExtractor`. Package currently holds the Story 2.1 classes: `PageType`, `ClassifiedPage`, `DiscoveryResult`, `JavadocPageClassifier`, `SearchIndex`, `JavadocDiscovery`, `package-info.java`. Update `package-info.java` prose to mention field extraction now lives here too.
- **NEW** `app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/` — extractor tests (suggested `JavadocFieldExtractorTest`).
- **NEW** `app/ingest-javadoc/src/test/resources/javadoc-fixtures/` — the shared-API-surface fixtures (JDK 8 / 11–16 / 17+) + broken/incomplete pages. Extend the existing `modern/`/`legacy/` trees.
- **NO build.gradle.kts change** — JSoup 1.23.1 is already wired as `implementation(libs.jsoup)` (Story 2.1); JUnit is already wired. Do not add JavaParser (Epic 3) or any new dependency.
- **DO NOT TOUCH** anything under `app/cli/` (pipeline, runner, stages), `app/domain/` (records stay skeleton — deferred gate), or the settings/version-catalog. **DO NOT** wire into `PipelineStage.CATALOG` or `IngestPipelineRunner`.
- **Note (not this story's job):** an untracked `app/ingest-javadoc/src/test/java/.../ScratchRealDocsTest.java` exists (Epic-1 retro action item to commit-as-fixture-or-delete). Leave it to that retro follow-up; do not build on it.

### Java package naming (hard rule)

- Package root is `com.luciadmcp.knowledge.*` — here `com.luciadmcp.knowledge.ingest.javadoc`. Never a bare `luciad` package. Directory names, Gradle project names, and file names are unaffected.

### Determinism (carried over from Epic 1 / Story 2.1)

- Return members in **document/source order** — the order they appear in the detail section — so output is independent of map/set iteration order. `@param` and `@throws` lists are ordered too (parameters are positional). No `Date`, no random, no locale-dependent casing. Downstream reproducibility (NFR-13, deterministic IDs in Story 1.4) depends on this when extraction feeds catalog building in 2.4.

### Testing standards

- JUnit 5 (Jupiter), one clearly-named test per behavior — mirror `JavadocDiscoveryTest`/`SearchIndexTest` style. Prefer classpath-resource fixtures; `@TempDir` only where a temp tree is genuinely required.
- **Synthetic fixtures only.** No proprietary Luciad Javadoc may be committed. Author minimal HTML exhibiting the structural signals above; keep pages tiny.
- The modern-vs-legacy field-equality test (AC 1) is the story's headline proof — make it explicit and readable.

### Build / verify command (Windows, from Story 1.4 / 2.1)

```
cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew :ingest-javadoc:test checkDependencyDirection
```

`checkDependencyDirection` is a bare aggregate task (no leading colon). Run it to confirm no new AD-1 violation (e.g. an accidental `cli` import) crept in. Note (from Story 1.4 retro): this environment resolves `python`, not `python3`; the `GRADLE_OPTS` trust-store flag works around the proxy PKIX issue.

### Project Structure Notes

- The extractor slots into `ingest.javadoc` exactly where the architecture spine places F2 Javadoc parsing (`ingest.javadoc`, bound by AD-4/AD-10/AD-3). No new module, no settings change, no new dependency. [Source: ARCHITECTURE-SPINE.md#F2]
- Consistent with the deferred Epic-2 domain-record approval gate: `ExtractedType`/`ExtractedMember` are internal `ingest.javadoc` contracts, exactly as `ClassifiedPage`/`PageType` were in Story 2.1. No domain record is introduced or extended.
- Variance from the epics wording: the epics phrase AC 2 as "handled per the fail-fast|collect-and-continue policy from Story 1.3." Because AD-1 forbids `ingest.javadoc` → `cli` (where that policy lives), this story implements only the graceful-degradation half (extract-what-you-can + explicit module-local issues); the policy *decision* is applied by `cli` at the CATALOG stage in Story 2.4. This is the same review-defensible split Story 2.1 used for the `UNKNOWN`/warnings channel, not a scope change.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.2: Parse modern and legacy Javadoc layouts into equivalent fields] — user story + ACs.
- [Source: _bmad-output/planning-artifacts/epics.md#F2] — FR-5 (modern + legacy layouts parse into equivalent extracted fields; broken/incomplete HTML degrades per policy).
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#AD-4] — two-pass, never fabricate; unresolved recorded explicitly.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#AD-9, #AD-10] — zero egress; parse-only static analysis.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#Deferred] — malformed-page ingestion policy is an Epic-2-owned, `ingest.javadoc` implementation detail.
- [Source: _bmad-output/implementation-artifacts/2-1-discover-and-classify-javadoc-pages.md] — classifier/discovery to consume, result-holder pattern, charset idiom, synthetic-fixture rule, build command; the deferred `.summary-table` review note that targets this story.
- [Source: app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocPageClassifier.java] — `classify`/`analyze`, `MEMBER_BLOCK_SELECTOR` (classification heuristic — not for extraction), heading/`Locale.ROOT` idioms.
- [Source: app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocDiscovery.java] — `Jsoup.parse(file, null)` local-bytes parse, `DiscoveryResult` construction, warnings channel.
- [Source: app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ClassifiedPage.java, DiscoveryResult.java, PageType.java] — internal-contract records to model the new extracted-fields model on.
- [Source: app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/FailurePolicy.java, StageFailure.java, FailureSeverity.java] — the policy types this module must NOT import; how `cli` will later map extraction issues (Story 2.4).
- [Source: app/domain/src/main/java/com/luciadmcp/knowledge/domain/ApiElement.java, MethodElement.java] — skeleton-only domain records; full field set is the deferred Epic-2 gate — do not extend.
- [Source: app/ingest-javadoc/build.gradle.kts] — JSoup + JUnit already wired; no change needed this story.

## Dev Agent Record

### Agent Model Used

claude-4-8-opus (dev-story workflow)

### Debug Log References

- `:ingest-javadoc:test checkDependencyDirection` — pass (12 new `JavadocFieldExtractorTest` cases + all Story 2.1 tests green; `NoJsoupConnectSourceTest` still passes and now also covers the new class).
- `test checkDependencyDirection` (full, all modules) — pass, no regressions. `ingest-javadoc:checkDependencyDirection` confirms no accidental `cli` import (AD-1).

### Completion Notes List

- Delivered a layout-agnostic `JavadocFieldExtractor` producing an `ingest.javadoc`-internal `ExtractedType` (type-level description/`@since`/`@deprecated` + ordered `ExtractedMember`s with description/params/returns/throws/`@since`/`@deprecated`). No domain record created or extended; no canonical symbol minted — the deferred Epic-2 domain gate is untouched (AC 4).
- **AC 1 headline proof:** `modernAndLegacyLayoutsExtractEqualFields` parses the same API surface authored three ways (JDK 8 legacy, JDK 11–16 modern, JDK 17+ modern) and asserts the whole `ExtractedType` is equal across all three eras. Signatures were authored identically across fixtures so full-record equality (not a projection) holds; equality is compared with a common `relativePath` argument since fixtures live at different disk paths.
- **Member kind is structural, never filename:** modern uses `Element.closest("[id*=constructor]/[class*=constructor]", ...field...)` on the enclosing detail section; legacy walks to the nearest ancestor `h3` section heading ("Constructor Detail"/"Field Detail"/…). Default is `METHOD`.
- **Did NOT reuse the classifier's `MEMBER_BLOCK_SELECTOR`** (the deferred Story 2.1 review note): extraction selects member *detail* blocks — modern `section.detail`, legacy `li.blockList` with a direct `h4` child — and pulls full fields from `dl.notes`/`dl` (`Parameters:`/`Returns:`/`Throws:`/`Since:`). Param/throws `dd`s are split into (name, prose) via the leading `<code>`/`<a>` element with a `" - "` text fallback.
- **Graceful degradation (AC 5/6):** never throws on malformed markup (JSoup is lenient; structural gaps are the real "broken" case). Records explicit issues for an empty type description, a member missing its description, a truncated `dl` tag (a `dt` with no following `dd`), and a member with no recoverable heading (skipped). Absent optional fields (no `@since`/`@return`/`@deprecated`) are normal and silent — not over-reported.
- **AD-1 held in the module's own terms:** the extractor imports no `cli` type; degradation is surfaced as a `List<String>` issues channel on `ExtractionResult` (modeled on `DiscoveryResult.warnings`). Mapping issues onto the `fail-fast | collect-and-continue` policy is deferred to the CATALOG stage in Story 2.4, as the story specified.
- **AD-9/AD-10:** `Jsoup.parse(file, null)` (local bytes, honors BOM/meta charset); `Jsoup.connect` never used — the existing source-scan guard covers the new class. Not wired into `PipelineStage.CATALOG`/`IngestPipelineRunner` (AC 8).
- No build/dependency change: JSoup 1.23.1 + JUnit were already wired in Story 2.1.

### File List

- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/MemberKind.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/Deprecation.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/Param.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ThrownException.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedMember.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedType.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractionResult.java
- **NEW** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractor.java
- **UPDATE** app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/package-info.java (mention field extraction)
- **NEW** app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractorTest.java
- **NEW** app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk17/Widget.html
- **NEW** app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk11/Widget.html
- **NEW** app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk8/Widget.html
- **NEW** app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/broken/modern-broken.html
- **NEW** app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/broken/legacy-broken.html

## Change Log

| Date       | Change                                                                 |
|------------|------------------------------------------------------------------------|
| 2026-08-31 | Story 2.2 implemented: layout-agnostic Javadoc field extraction (modern JDK 11–16/17+ and legacy JDK 8) into an internal `ExtractedType`/`ExtractedMember` model with equal fields across eras (AC 1) and graceful degradation with explicit issues (AC 5/6). No `cli` import (AD-1), no domain record touched. Full suite + `checkDependencyDirection` green. Status → review. |

## Review Findings

_Code review 2026-08-31 (baseline `7a3b43a`..HEAD). Three adversarial layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor. 0 decision-needed, 7 patch, 4 deferred, 3 dismissed. Blind+Edge independently agreed on the one High._

### Patch

- [ ] [Review][Patch] Member-kind self-id match misclassifies methods whose anchor id contains "field"/"constructor" (HIGH) — `block.closest("[id*=field]/[class*=field]")` includes the member's own `section.detail`, whose id is the member signature; a real method like `getField(int)`/`getConstructor()` is tagged `FIELD`/`CONSTRUCTOR`, and diverges from the legacy heading-based path (breaks AC 1 on real trees, corrupts 2.3 identity). Fix: search the enclosing details-group section only (start from `block.parent()`), never the member section's own id. [app/ingest-javadoc/.../JavadocFieldExtractor.java:175]
- [ ] [Review][Patch] `Type Parameters:` folded into the positional value-parameter list (MED) — `dispatchNote` maps both `"parameters"` and `"type parameters"` to `notes.params`, so a generic method's `<T>` lands in param slot 0, corrupting order/count for 2.3. Fix: map only `"parameters"`; let `"type parameters"` fall through (ignored this story). [app/ingest-javadoc/.../JavadocFieldExtractor.java:255]
- [ ] [Review][Patch] Notes `dl` fallback grabs an author `<dl>` nested in a description `.block` (MED) — `selectFirst("dl")` can pick a definition list inside the description, producing a spurious "truncated tag" issue and hijacking/silently dropping the real `@since`. Fix: fall back only to a `<dl>` not inside a `.block` (structural notes list). [app/ingest-javadoc/.../JavadocFieldExtractor.java:222,298]
- [ ] [Review][Patch] Signatures/prose diverge across eras on U+00A0 (MED) — modern `.member-signature` uses `&nbsp;` (U+00A0) between tokens; `norm()` (`text().trim()`) neither collapses nor strips U+00A0, so a modern signature ≠ the legacy `<pre>` plain-space one on real pages (breaks full-record equality / 2.3 `kind+name+signature` identity). Fix: normalize U+00A0 to space and collapse in `norm()`. [app/ingest-javadoc/.../JavadocFieldExtractor.java:360]
- [ ] [Review][Patch] Type-description scope misses `section.description` (MED) — `TYPE_DESC_SCOPE = "section.class-description, div.description"` matches neither a `<section class="description">` (intermediate HTML5-era doclet); type description/`@since`/`@deprecated` are all dropped and a false "no type-description section found" issue is raised. Fix: add `section.description` to the selector. [app/ingest-javadoc/.../JavadocFieldExtractor.java:41]
- [ ] [Review][Patch] Unwrapped deprecation comment (following text) is dropped; camelCase `deprecationBlock` absent from selector (MED/LOW) — legacy/modern deprecation prose that is bare following text (no `.deprecationComment` element) yields `Deprecation(present=true, comment="")`; and `DEPRECATION_BLOCK` lacks the JDK 11–15 camelCase token (currently masked by the label check). Fix: when no comment element, fall back to the deprecation container's text minus the label; add `.deprecationBlock` to the selector. [app/ingest-javadoc/.../JavadocFieldExtractor.java:46,331]
- [ ] [Review][Patch] Spurious "missing description" issue for members documented only via `@deprecated` (LOW) — a deprecated member with a deprecation comment but no separate description `.block` emits a false "missing its description" issue (noise under collect-and-continue). Fix: suppress that issue when the member is deprecated with a non-empty comment. [app/ingest-javadoc/.../JavadocFieldExtractor.java:162]

### Deferred

- [x] [Review][Defer] Multi-paragraph description split across multiple `.block` elements — only the first is kept; the rest are dropped (uncommon; concatenation risks over-grabbing). Revisit if real trees show multi-block descriptions. [app/ingest-javadoc/.../JavadocFieldExtractor.java:318]
- [x] [Review][Defer] Nameless `@param`/`@throws` (no leading `<code>`, no `" - "`) is emitted with empty name and no AD-4 issue — real Javadoc always wraps the name in `<code>`, so low real-world likelihood. [app/ingest-javadoc/.../JavadocFieldExtractor.java:269]
- [x] [Review][Defer] Enum constants classify as `METHOD` — the detail group ("Enum Constant Detail" / `constant-details`) matches neither constructor nor field; `MemberKind` has no enum-constant value. Needs an enum-constant kind or a FIELD mapping — a model decision for Story 2.3. [app/ingest-javadoc/.../JavadocFieldExtractor.java:175]
- [x] [Review][Defer] Legacy single-block deprecation+description (both in one `.block`, no wrapper) does not cleanly separate the two proses — mitigated by the deprecation-comment fallback (text lands in the deprecation comment), but the description stays empty. Revisit if real trees use this form. [app/ingest-javadoc/.../JavadocFieldExtractor.java:318]

### Dismissed (3)

- JDK 17+ `ul.member-list` container is "not load-bearing" — by design: `section.detail` is the correct per-member element in JDK 17+, shared with JDK 11–16; the classifier's `MEMBER_BLOCK_SELECTOR` was correctly avoided.
- Fixtures placed under a new `javadoc-fixtures/extract/` tree instead of extending `modern/`/`legacy/` — deliberate and cleaner; Task 4 also warned against overloading the Story 2.1 classification stubs.
- AC 5 "unparseable fragment" not represented — JSoup is lenient (never throws on bad markup); the real broken cases (empty description, truncated `dl`, nameless member) are covered by the broken fixtures.

### Independent Re-Review — Verdicts (2026-08-31, cross-model)

_Independent cross-model re-verification of the same-model review above (handover: `2-2-review-handover.md`). Every finding was re-derived from the code; the load-bearing JSoup claims were settled with a throwaway probe on JSoup 1.23.1, not asserted from memory. Outcome: 1 decision-needed, 7 patch, 5 defer, 3 dismissed. **All patches applied 2026-08-31; :ingest-javadoc:test + checkDependencyDirection green (12 cases, 0 failures).** Headline correction: the same-model U+00A0 signature finding (Patch #4) is refuted._

**Probe results (JSoup 1.23.1):** `Element.closest()` IS self-inclusive; `[attr*=val]` IS case-insensitive; `Element.text()` COLLAPSES U+00A0 to a normal space; a `<dl>` nested inside a description `.block` IS caught by `selectFirst("dl")`.

#### Decision needed — RESOLVED

- [x] [Review][Decision] Enum-constant / annotation-element members silently labeled `METHOD`. **Resolved: option (c).** Added `MemberKind.ENUM_CONSTANT`; `memberKind` now reads the kind from the enclosing details-group (constructor / enum-constant / field / method) and records an AD-4 issue for an unrecognized group (e.g. annotation-interface elements) instead of silently defaulting. New enum fixtures across all three eras + `enumConstantsClassifyConsistentlyAcrossEras` lock it. Note for Story 2.3: the `MemberKind` model was started early here; annotation-element kind is still open. [JavadocFieldExtractor.java, MemberKind.java]

#### Patch (all applied)

- [x] [Review][Patch] Member-kind self-id match (CONFIRMED HIGH) — probe proved `closest()` self-inclusive AND `[attr*=]` case-insensitive, so ANY member whose signature/anchor contains "field"/"constructor" self-misclassified; diverged from the legacy heading path. Fixed: `memberKind` searches the enclosing group from `block.parent()` (never the member's own section id); validated against jdk8/jdk11/jdk17 + regression test `methodWhoseNameContainsFieldOrConstructorIsNotMisclassified`. [JavadocFieldExtractor.java]
- [x] [Review][Patch] `Type Parameters:` folded into positional params (CONFIRMED MED) — now maps only `"parameters"`; `"type parameters"` falls through. [JavadocFieldExtractor.java]
- [x] [Review][Patch] Notes `dl` fallback grabbed an author `<dl>` inside a description `.block` (CONFIRMED MED) — new `notesList` helper falls back only to a `<dl>` whose `closest(".block") == null`. [JavadocFieldExtractor.java]
- [x] [Review][Patch] Type-desc scope missed `section.description` (LOW, UNVERIFIED) — added as a safe additive guard; still to be validated against the real 432-page export. [JavadocFieldExtractor.java]
- [x] [Review][Patch] Deprecation selector gaps (LOW-MED) — added camelCase `.deprecationBlock`; `findDeprecation` now falls back to container-text-minus-label when no `.deprecation-comment` element is present. [JavadocFieldExtractor.java]
- [x] [Review][Patch] Spurious "missing description" for `@deprecated`-only members (LOW) — suppressed when the member is deprecated with a non-empty comment. [JavadocFieldExtractor.java]
- [x] [Review][Patch] Truncated-tag issue fired for out-of-scope labels (LOW) — now only for labels the extractor consumes (`isConsumedLabel`). [JavadocFieldExtractor.java]

#### Defer

- [x] [Review][Defer] Broader signature divergence across eras (modifiers/annotations/FQN param-type rendering, and U+200B word-break that modern doclets insert and `text()` does NOT collapse) — the real cross-era signature risk that replaces the refuted U+00A0 finding. Needs real-tree validation + a normalization decision (Story 2.3 identity). [JavadocFieldExtractor.java:360]
- [x] [Review][Defer] Legacy member whose `<h4>` is not a direct child of `li.blockList` is dropped from `memberBlocks` with no issue (AD-4 silent drop). New this re-review; low likelihood (JDK 8 emits `<h4>` as a direct child). [JavadocFieldExtractor.java:142-147]
- [x] [Review][Defer] A `<dd>` with no preceding `<dt>`, or under an empty/unrecognized `<dt>` label, is silently discarded with no issue (AD-4). New this re-review. [JavadocFieldExtractor.java:241-262]
- [x] [Review][Defer] `SIGNATURE` group selector returns the document-order first match, so a `<pre>` code sample in a member with no signature block is captured as the signature. New this re-review; low likelihood. [JavadocFieldExtractor.java:50,157]
- [x] [Review][Defer] (Confirmed from same-model) Multi-paragraph `.block`, nameless `@param`/`@throws`, legacy single-block deprecation+description — remain deferred; already in `deferred-work.md`.

#### Dismissed (this re-review)

- **U+00A0 signature divergence (same-model Patch #4) — REFUTED.** The probe shows `Element.text()` collapses `&nbsp;` (U+00A0) to a normal space on JSoup 1.23.1, so modern (`&nbsp;`) and legacy (plain space) signatures normalize equal. The proposed `norm()` nbsp-normalization fixes a non-problem on this version. (The genuine cross-era signature risk is the separate defer above.)
- Multiple `Returns:`/`Since:` `dd`s overwrite (last wins) — degenerate markup; acceptable.
- Non-ASCII dash separator in `@param`/`@throws` prose — cosmetic and consistent across both eras (no AC-1 divergence).
