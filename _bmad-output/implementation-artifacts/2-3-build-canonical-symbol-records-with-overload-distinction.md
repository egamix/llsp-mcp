---
baseline_commit: 896fc9b
context:
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedType.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedMember.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/MemberKind.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractionResult.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/Param.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ThrownException.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/Deprecation.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/DiscoveryResult.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractor.java
  - app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/package-info.java
  - app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractorTest.java
  - app/domain/src/main/java/com/luciadmcp/knowledge/domain/ApiElement.java
  - app/domain/src/main/java/com/luciadmcp/knowledge/domain/MethodElement.java
  - app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk8/Widget.html
  - app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk11/Widget.html
  - app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk17/Widget.html
---

# Story 2.3: Build canonical symbol records with overload distinction

Status: in-progress

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer,
I want one record per API element — never merging overloads — with a canonical symbol key plus normalized simple-name, dotted-member, erased-generic, and camel-case lookup variants,
so that a query for one overload never silently returns another.

## Acceptance Criteria

1. **Given** a `TYPE` page whose `ExtractedType` (Story 2.2) contains multiple overloaded methods (e.g. `contains(ILcdPoint)` and `contains(double, double)`), **when** canonical-symbol minting runs, **then** each overload becomes a **distinct** symbol record with a **distinct** canonical symbol key. Two records for the same class+member-name but different parameter-type lists must never collapse into one, and two genuinely-identical elements must never split into two. This is the headline test — mirror Story 2.1's index-equality and 2.2's layout-equality proofs in explicitness.
2. Every symbol record retains **all four** normalized lookup variants, each defined precisely and computed deterministically:
   - **simple-name** — the element's bare simple name (type: `ILcdShape`; member: `contains`). Locale-independent (`Locale.ROOT`), no package, no signature.
   - **dotted-member** — the `SimpleType.member` dotted form for members (`ILcdShape.contains`); for a type it is the simple name itself.
   - **erased-generic** — the identity form with generics erased and parameter types normalized (see AC 5): `contains(ILcdPoint)`, `map(Function)` from `map(Function<A,B>)`. Type-level erased-generic is the raw simple type name (types carry no signature).
   - **camel-case** — the CamelHumps abbreviation of the simple name from its capital-letter run (`ILcdShape` → `ILS`, `getFooBar` → `gFB`). Pin the exact algorithm with a known-answer test; it is deterministic and `Locale.ROOT`-cased.
3. **One record per semantic API element, never spanning more than one.** The type itself yields one record; each `ExtractedMember` (method, constructor, field, enum constant) yields exactly one record carrying its `MemberKind`. Overloaded methods/constructors yield one record each. No record represents two members; no two records represent the same member. Field and enum-constant records need no parameter list (name is unique within the type) but are still distinct records.
4. **No `domain` record is minted or extended, and no persisted/wire canonical-symbol string is produced.** The output is an `ingest.javadoc`-internal symbol-record model (a new record set alongside `ExtractedType`), exactly as Story 2.1's `ClassifiedPage` and Story 2.2's `ExtractedType` were internal contracts. The canonical key minted here is an explicitly-labeled **provisional, module-internal** identity form — it is **not** the AD-18 wire/join variant and does **not** resolve the deferred `[NEEDS APPROVAL]` canonical-symbol serialization grammar (Epic 3). `domain.ApiElement`/`MethodElement` stay skeleton records (the deferred Epic-2 domain field-set gate) — do **not** add fields or a minting method to them.
5. **Signature normalization for identity is deterministic and era-independent** (this resolves the signature-divergence item deferred from Story 2.2's re-review). The parameter-type list used for overload distinction is derived from `ExtractedMember.signature` by: stripping leading modifiers/annotations and the return type, stripping type-parameter declarations (`<T>`, `<T extends X>`) and generic type arguments (`List<String>` → `List`), stripping array/varargs consistently (`int...` and `int[]` normalized to one agreed form), removing zero-width characters (U+200B) and collapsing whitespace, and comparing simple type names with `Locale.ROOT`. The **same** logical method rendered as JDK 8 legacy `<pre>` (plain spaces, simple types) and as a JDK 11/17 modern `.member-signature` (`&nbsp;`, possibly FQN param types, U+200B word-breaks) must yield the **same** erased parameter-type list and therefore the **same** canonical key.
6. **Overload keys are stable and order-independent.** Symbol records are returned in a deterministic order (source/member order from `ExtractedType.members`, which is already document order). The canonical key for a given element is a pure function of (type FQN, member kind, member name, erased parameter types) — independent of how many other overloads exist, map/set iteration order, or which page-era produced the input. Identical input yields identical keys and identical record ordering across runs (NFR-13).
7. **The type's fully-qualified name is derived deterministically from `ExtractedType.relativePath`** (the only identity source the 2.2 model carries — there is no FQN field): `com/luciad/shape/ILcdShape.html` → package `com.luciad.shape`, simple name `ILcdShape`, FQN `com.luciad.shape.ILcdShape`. Nested types (`.../Outer.Inner.html`) map to `com...Outer.Inner` using one agreed, documented, tested nested-type separator convention. Derivation is offline and `Locale.ROOT`-independent; no network, no page-title guessing (the title is at most a cross-check, never the authority).
8. **Graceful degradation, module-local, no `cli` types.** A member from which no parameter-type list can be recovered (an empty/unparseable signature on a method/constructor) records an **explicit, structured issue** (relative path + member name + reason) on the returned result and either mints a best-effort name-only key or is skipped — pick one contract and test it — never a fabricated parameter list, never a silent drop (AD-4). The minter imports **no** `com.luciadmcp.knowledge.cli.*` type (`FailurePolicy`/`StageFailure`/`FailureSeverity` — AD-1), makes no fail-fast/continue decision, calls no `System.exit`, and is **not** wired into `PipelineStage.CATALOG`/`IngestPipelineRunner` (that is Story 2.4). It is a pure library: reads the already-extracted model, writes nothing, opens no socket.

## Tasks / Subtasks

- [ ] Task 1: Define the canonical-symbol record model inside `ingest.javadoc` (AC: 2, 3, 4, 6)
  - [ ] Add a `SymbolKind` enum (or reuse/extend the concept): `TYPE`, `METHOD`, `CONSTRUCTOR`, `FIELD`, `ENUM_CONSTANT`. It mirrors `MemberKind` plus a `TYPE` value for the type-level record. Prefer a small dedicated enum over overloading `MemberKind` so the type-level record isn't forced to carry a member kind — decide and document.
  - [ ] Add a `LookupVariants` record (suggested): `simpleName`, `dottedMember`, `erasedGeneric`, `camelCase` — all `String`, non-null, validated in the compact canonical constructor exactly as `ClassifiedPage`/`ExtractedType` validate (reject/normalize null). These are the four AC-2 variants; keep them plain strings.
  - [ ] Add a `SymbolRecord` record (suggested): the **provisional canonical key** (String), `SymbolKind`, the type FQN, the element simple name, the ordered erased parameter-type list (`List<String>`, empty for type/field/enum-constant), the `LookupVariants`, and a back-reference to the source (`relativePath`; and enough to tie back to the `ExtractedMember` — e.g. its index or name+signature). Do **NOT** add an SDK-version field (AD-12 scoping is Story 2.4), an HTML anchor field (Story 2.4), or any `domain` type. Validate invariants in the compact constructor (`List.copyOf`, reject null key/kind/fqn).
  - [ ] Add a result holder `SymbolMintResult` (suggested) `{ List<SymbolRecord> records, List<String> issues }`, modeled exactly on Story 2.2's `ExtractionResult` and Story 2.1's `DiscoveryResult` — same immutability discipline (`List.copyOf`), same "issues are strings with the relative path baked in."
  - [ ] Document in every record's Javadoc that the canonical key is **provisional and module-internal** (not the AD-18 wire/join key, not the Epic-3 approved grammar), the same way `ExtractedType` documents "no canonical symbol minted here."
- [ ] Task 2: Derive the type FQN from `relativePath` (AC: 7)
  - [ ] Add a small, pure helper (a static method or a tiny `TypeName` value): strip a trailing `.html`, split the `/`-separated `relativePath` into package segments + final type segment, join package with `.`, and handle a nested-type final segment (`Outer.Inner`) with one documented separator convention (keep `.` for readability, or `$` for binary form — decide, document, and test). Return package, simple name, FQN.
  - [ ] Guard the malformed/degenerate cases: a `relativePath` with no `/` (default package), an empty or non-`.html` path — record an issue (AC 8) rather than throwing. `relativePath` is already validated non-blank by `ExtractedType`.
  - [ ] `Locale.ROOT` for any casing; no `Date`, no locale-dependent transforms — the FQN is a deterministic function of the path (AC 6, 7).
- [ ] Task 3: Normalize signatures into an erased parameter-type list (AC: 5, 6)
  - [ ] Add a pure signature-normalizer that takes `ExtractedMember.signature` and returns an ordered `List<String>` of erased parameter type names. Steps (all deterministic): remove U+200B (zero-width) and collapse whitespace; drop leading modifiers/annotations and the return type; isolate the parenthesized parameter list; for each parameter, drop the parameter *name* and any generic arguments (`List<String>` → `List`), erase declared type parameters, normalize array/varargs (`T...`/`T[]`) to one agreed form; reduce each type to its simple name with `Locale.ROOT`.
  - [ ] A no-arg method/constructor → empty list. A field/enum-constant → no signature parsing (empty list, name-unique).
  - [ ] This is the crux of AC 1 and AC 5: author it so the JDK 8 `<pre>` and JDK 11/17 `.member-signature` renderings of the *same* method produce byte-equal lists. Test it directly with the divergent-rendering strings called out in the Story 2.2 re-review deferral (modifiers/annotations, FQN vs simple param types, U+200B).
- [ ] Task 4: Mint canonical symbol records (AC: 1, 2, 3, 6, 8)
  - [ ] Add a `CanonicalSymbolMinter` (suggested) with one public method taking an `ExtractedType` (and optionally its `relativePath`, already on the type) and returning a `SymbolMintResult`. Keep the class free of JSoup and `domain` and `cli` types — it operates purely on the 2.2 model.
  - [ ] Emit one `TYPE` record for the type, then one record per `ExtractedMember` in `members` order. Compute the provisional canonical key as a pure function of (FQN, kind, name, erased param types) — suggested form `com.luciad.shape.ILcdShape#contains(ILcdPoint)` for methods/ctors, `com.luciad.shape.ILcdShape#SIZE` for fields/enum-constants, `com.luciad.shape.ILcdShape` for the type. Document the grammar as provisional. Overloads differ only in the `(...)` erased list, so they get distinct keys automatically (AC 1).
  - [ ] Compute the four `LookupVariants` per AC 2. Keep the camel-case algorithm tiny and pinned by a known-answer test.
  - [ ] Degradation (AC 8): a method/constructor whose signature yields no recoverable parameter list records an issue and follows the chosen name-only-key-or-skip contract; never fabricate a parameter list. No `cli` import; issues are module-local strings.
- [ ] Task 5: Fixtures — reuse the Story 2.2 `extract/` tree, add overloads (AC: 1, 5)
  - [ ] Reuse the existing shared-API-surface fixtures under `app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/{jdk8,jdk11,jdk17}/Widget.html` and `Direction.html`. Add **overloaded methods** (same name, different parameter-type lists — including one generic method like `map(Function<A,B>)` and one array/varargs pair) to the shared `Widget` surface across all three eras so AC 1 and AC 5 have a cross-era overload proof. **Synthetic only — no proprietary Luciad content** (repo rule).
  - [ ] Ensure the three eras render the overloaded signatures in their era-authentic way (JDK 8 `<pre>` plain-space/simple-type; modern `.member-signature` with `&nbsp;` and, for at least one param, an FQN type + a U+200B word-break) so the AC-5 normalizer is genuinely exercised, not trivially satisfied.
  - [ ] Do not fork the fixtures into a new tree; extend the `extract/` pages (Story 2.2's Task-4 discipline).
- [ ] Task 6: Tests (AC: 1–8) — mirror `JavadocFieldExtractorTest`/`JavadocDiscoveryTest` style
  - [ ] AC 1 headline: from a `Widget` `ExtractedType` with overloaded `contains(...)`, assert each overload is a distinct `SymbolRecord` with a distinct canonical key, and non-overloaded members are single records. Make it the explicit, readable proof.
  - [ ] AC 5 cross-era: mint from the jdk8, jdk11, and jdk17 `Widget` `ExtractedType`s (obtained via `JavadocFieldExtractor`) and assert the overloads' canonical keys and erased param lists are **equal** across all three eras. Add a direct unit test of the signature normalizer with the divergent-rendering strings.
  - [ ] AC 2: assert all four variants are present and correct per record; pin camel-case with known-answer values (`ILcdShape`→`ILS`, `getFooBar`→`gFB` or your documented rule).
  - [ ] AC 3: assert record count == 1 (type) + N (members), kinds correct, fields/enum-constants have empty param lists and distinct records; assert no two records share a key unless they are the same element.
  - [ ] AC 7: assert FQN derivation for a normal path, a default-package path, and a nested-type path; assert the documented nested separator.
  - [ ] AC 6: assert two runs over the same `ExtractedType` return equal records in equal order; keys independent of overload count.
  - [ ] AC 8: assert a member with an empty/garbage signature records an explicit issue and does not throw; assert (source/import scan, or simply the module still compiling clean against AD-1) that no `cli` type is referenced. Confirm `NoJsoupConnectSourceTest` still passes and covers the new class.
  - [ ] JUnit 5 (Jupiter); prefer classpath-resource fixtures; `@TempDir` only if a temp tree is genuinely needed.

## Dev Notes

### What this story delivers (and what it deliberately does not)

- **Deliverable:** a pure, unit-testable canonical-symbol minter inside `ingest.javadoc` that turns a Story-2.2 `ExtractedType` into a deterministic list of `SymbolRecord`s — one per API element (type + each member), overloads never merged — each carrying a **provisional module-internal** canonical key and the four normalized lookup variants, plus an explicit issue list for signatures it can't parse. It consumes the 2.2 model; it does not re-parse HTML.
- **Out of scope — do not implement here, do not stub ahead:**
  - The **approved** canonical-symbol string grammar and the AD-18 **wire/join** variant → `[NEEDS APPROVAL]` gate, **Epic 3**. Mint a clearly-labeled provisional internal key now; the approved grammar maps on later. Do not "temporarily" invent the wire format.
  - `domain` record field sets / a `domain` minting method → **deferred Epic-2 approval gate**. `ApiElement`/`MethodElement` stay skeleton `(canonicalSymbol, sdkVersion)` records. Do not touch them.
  - SDK-version scoping of the key, version-scoped uniqueness, SQLite persistence, relative-path/anchor storage → **Story 2.4**. No `sdkVersion` field, no anchor field on `SymbolRecord`.
  - Record/edge **ID computation** → `catalog.sqlite` is the sole authority (AD-22), from a `domain` `identityFields` spec that does not exist yet. This story mints logical symbol records; it does **not** hash IDs.
  - Relationship graph / resolving `throwsTypes`/param types to catalog symbols → **Story 2.5+**. `ThrownException.type` and param types stay raw documented text here.
  - Wiring into `PipelineStage.CATALOG` / `IngestPipelineRunner` → **Story 2.4** (CATALOG stays a no-op after this story).

### Architecture tension to respect (read before choosing the key format)

- **AD-3 says the canonical symbol is "minted in `domain`" and "no module derives its own symbol string"**, yet the **exact string format is a `[NEEDS APPROVAL]` gate deferred to Epic 3**, and `domain` today has only skeleton records with no minting logic. This story cannot both (a) honor the deferral and (b) mint the final domain wire string. The **review-defensible resolution** — consistent with how Story 2.1 (`ClassifiedPage`) and Story 2.2 (`ExtractedType`, `ExtractionResult`) handled deferred domain/`cli` concerns — is: build an **`ingest.javadoc`-internal** symbol-record model with a **provisional** canonical key, explicitly documented as *not* the AD-18 wire/join variant and *not* the Epic-3 grammar. Overload distinction and the four variants (the story's actual value) are fully deliverable this way and provable by test. When Epic 3 approves the grammar, a later story maps these internal records onto the `domain` canonical symbol; nothing here needs reshaping because the identity inputs (FQN, kind, name, erased params) are captured explicitly. **This split is the same shape as Story 2.2's cli/policy split — not a scope change.** [Source: ARCHITECTURE-SPINE.md#AD-3, #Deferred — canonical-symbol serialization gate; 2-2-...md#Variance from the epics wording]
- **AD-18 (one wire/join variant):** whichever of the four variants becomes the cross-boundary join key is decided by the deferred gate, not here. Keep **all four** variants module-internal for now; do not stamp one as "the" wire key. [Source: ARCHITECTURE-SPINE.md#AD-18]
- **AD-22 (catalog owns identity):** do not compute record IDs or assume a hash recipe. The Epic-1 retro action item ("confirm the AD-22 `identityFields` registry approach before Epic 2 catalog work") is a **Story 2.4** prerequisite, not this story's — this story produces logical records, 2.4 submits them through `CatalogStore` for ID computation. [Source: ARCHITECTURE-SPINE.md#AD-22; sprint-status.yaml action_items]

### Architecture compliance (the guardrails that make this story pass review)

- **AD-1 (inward dependency direction, enforced by `checkDependencyDirection`):** `ingest.javadoc` must **not** depend on `cli`. Express "couldn't parse this signature" via a module-local issue list on `SymbolMintResult`; do not import `FailurePolicy`/`StageFailure`/`FailureSeverity`. [Source: app/cli/.../pipeline/FailurePolicy.java]
- **AD-4 (never fabricate):** an unparseable signature is a recorded issue + (name-only key or skip), never a guessed parameter list, never a silent drop. A member is either represented faithfully or explicitly flagged. [Source: ARCHITECTURE-SPINE.md#AD-4]
- **AD-9 / AD-10:** this story touches no HTML and no network at all — it consumes the already-parsed 2.2 model. No `Jsoup.connect`, nothing to render. The existing `NoJsoupConnectSourceTest` source-scan already guards the module; confirm it still covers the new class. [Source: ARCHITECTURE-SPINE.md#AD-9,#AD-10]
- **AD-12 (SDK-version scoping):** not exercised here — no persisted, version-scoped record is minted. Do not invent a `sdkVersion` field on `SymbolRecord`; version scoping is Story 2.4. [Source: ARCHITECTURE-SPINE.md#AD-12]
- **NFR-13 (determinism):** keys are pure functions of identity inputs; records come out in `ExtractedType.members` order (already document order); no `Date`/random/locale-dependent casing (`Locale.ROOT` everywhere). [Source: ARCHITECTURE-SPINE.md#Cross-cutting — IDs; 2-2-...md#Determinism]

### Reuse — build on Stories 2.1 / 2.2, do not reinvent

- **Consume `ExtractedType`/`ExtractedMember`; do not re-extract.** `JavadocFieldExtractor.extract(Path, relativePath) -> ExtractionResult` already produces the layout-agnostic model with `MemberKind` set structurally (METHOD/CONSTRUCTOR/FIELD/ENUM_CONSTANT), ordered members, and raw `signature` text. This story's minter takes an `ExtractedType` and mints symbol records from it. [Source: app/ingest-javadoc/.../JavadocFieldExtractor.java, ExtractedType.java, ExtractedMember.java, MemberKind.java]
- **Result-holder pattern is established twice.** `DiscoveryResult { pages; warnings }` (2.1) and `ExtractionResult { type; issues }` (2.2) are the exact shape for `SymbolMintResult { records; issues }`. Same `List.copyOf` immutability, same "issues are strings with the relative path baked in." [Source: app/ingest-javadoc/.../DiscoveryResult.java, ExtractionResult.java]
- **Record-invariant discipline.** Every existing record (`ExtractedType`, `ExtractedMember`, `Param`, `ThrownException`, `Deprecation`, `ClassifiedPage`) validates/normalizes in a compact canonical constructor (reject null kind/path, default null lists to `List.of()` via `List.copyOf`, default null strings to `""`). New records match this exactly. [Source: app/ingest-javadoc/.../ExtractedMember.java:39-51]
- **`MemberKind` already carries the structural kind** including `ENUM_CONSTANT` (added in 2.2's re-review). Reuse it; the only new value needed is a type-level `TYPE` (hence a small `SymbolKind` enum, or a documented convention). [Source: app/ingest-javadoc/.../MemberKind.java]
- **`Locale.ROOT` for every casing/token op** — the classifier and extractor already do this; matching it keeps behavior machine-independent. [Source: app/ingest-javadoc/.../JavadocPageClassifier.java]

### Signature normalization — the item Story 2.2 explicitly deferred to *this* story

- Story 2.2's independent re-review deferred exactly this: *"Broader signature divergence across eras (modifiers/annotations, FQN vs simple param-type rendering, and U+200B word-break that modern doclets insert and `Element.text()` does NOT collapse) … needs a signature-normalization decision that belongs with Story 2.3 identity."* AC 5 / Task 3 are that decision. Note the refuted red herring: U+00A0 (`&nbsp;`) **is** collapsed to a normal space by JSoup 1.23.1 `Element.text()`, so it is *not* a divergence source; **U+200B (zero-width space) is not collapsed** and must be stripped. [Source: deferred-work.md#story-2.2 independent re-review; 2-2-...md#Dismissed — U+00A0 REFUTED]
- The `signature` field is not one of AC-1's equality-tested fields in 2.2, so the fixtures may currently render signatures loosely. Task 5 tightens them so the AC-5 normalizer is genuinely exercised. Because overload identity depends on the *erased parameter-type list*, normalize to **simple type names with generics erased** — an FQN param type and a simple param type for the same logical parameter must reduce to the same token. Decide the FQN→simple reduction rule (last dotted segment) and document it.

### Nested types and the FQN-from-path rule (AC 7)

- `ExtractedType` carries **only** `relativePath` — there is no FQN or simple-name field. Derive both from the path. Standard Javadoc layout: `pkg/segments/TypeName.html`; nested types render as `pkg/Outer.Inner.html` (dots in the final segment). Pick one nested separator for the FQN/key (`.` reads naturally and matches Javadoc; `$` is the binary form) and pin it with a test — downstream 2.4/2.5 will rely on it being stable. Default-package (`Foo.html`, no `/`) and malformed paths degrade to an issue, not an exception (AC 8).

### Source tree — files to touch

- **NEW** under `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/`: `SymbolKind` (enum), `LookupVariants` (record), `SymbolRecord` (record), `SymbolMintResult` (record), `CanonicalSymbolMinter` (the minter), and a small signature-normalizer + FQN helper (either private methods on the minter or tiny package-private value types — keep them testable). Package currently holds the 2.1/2.2 classes: `PageType`, `ClassifiedPage`, `DiscoveryResult`, `JavadocPageClassifier`, `SearchIndex`, `JavadocDiscovery`, `ExtractedType`, `ExtractedMember`, `Param`, `ThrownException`, `Deprecation`, `MemberKind`, `ExtractionResult`, `JavadocFieldExtractor`, `package-info.java`.
- **UPDATE** `package-info.java` — add a paragraph noting canonical-symbol minting (Story 2.3) now lives here, consuming the 2.2 model, minting a provisional internal key (not the domain wire key).
- **NEW** `app/ingest-javadoc/src/test/java/.../CanonicalSymbolMinterTest.java` (+ a focused signature-normalizer test if you split it out).
- **EXTEND** `app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/{jdk8,jdk11,jdk17}/Widget.html` with overloaded methods (do not add a new fixture tree).
- **NO** `build.gradle.kts` change — no new dependency (JSoup/JUnit already wired; this story may not even need JSoup). **DO NOT TOUCH** `app/cli/` (pipeline/runner/stages), `app/domain/` (skeleton records — deferred gate), the settings/version-catalog. **DO NOT** wire into `PipelineStage.CATALOG`/`IngestPipelineRunner`.
- **Note (not this story's job):** the untracked `ScratchRealDocsTest.java` (Epic-1 retro item) — leave it alone.

### Java package naming (hard rule)

- Package root is `com.luciadmcp.knowledge.*` — here `com.luciadmcp.knowledge.ingest.javadoc`. Never a bare `luciad` package. Directory names, Gradle project names, and file names are unaffected.

### Testing standards

- JUnit 5 (Jupiter), one clearly-named test per behavior — mirror `JavadocFieldExtractorTest`/`JavadocDiscoveryTest`. Prefer classpath-resource fixtures; `@TempDir` only where a temp tree is genuinely required.
- **Synthetic fixtures only.** No proprietary Luciad Javadoc may be committed.
- The overload-distinction test (AC 1) and the cross-era key-equality test (AC 5) are the story's headline proofs — make them explicit and readable.
- Write assertions **adversarially up front** — pin known-answer canonical keys and camel-case values; no vacuously-passing tests (Epic-1 retro action item, owner: dev). [Source: sprint-status.yaml action_items]

### Build / verify command (Windows, from Story 1.4 / 2.1 / 2.2)

```
cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew :ingest-javadoc:test checkDependencyDirection
```

`checkDependencyDirection` is a bare aggregate task (no leading colon) — run it to confirm no accidental `cli` import (AD-1) crept in. This environment resolves `python`, not `python3`; the `GRADLE_OPTS` trust-store flag works around the proxy PKIX issue. (Both notes carried from the Epic-1 retro / Stories 1.4, 2.1, 2.2.)

### Project Structure Notes

- The minter slots into `ingest.javadoc` where the architecture spine places F2 Javadoc parsing (AD-4/AD-10/AD-3). No new module, no settings change, no new dependency. [Source: ARCHITECTURE-SPINE.md#F2 table]
- Consistent with the deferred Epic-2 domain-record approval gate: `SymbolRecord`/`LookupVariants`/`SymbolMintResult` are internal `ingest.javadoc` contracts, exactly as `ClassifiedPage`/`PageType` (2.1) and `ExtractedType`/`ExtractionResult` (2.2) were. No `domain` record introduced or extended.
- **Variance from the epics wording:** the epics AC says "one record per API element … with canonical symbol keys." Because the canonical-symbol *serialization grammar* and the AD-18 wire/join variant are a `[NEEDS APPROVAL]` gate deferred to Epic 3, and `domain` minting logic does not yet exist, this story mints a **provisional module-internal** key (fully overload-distinguishing) plus the four variants, and defers the approved-grammar mapping to a post-approval story. Same review-defensible deferral split Stories 2.1 and 2.2 used. See the "Architecture tension" note above.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.3: Build canonical symbol records with overload distinction] — user story + ACs (one record per element, four normalized variants, no overload merging).
- [Source: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md#AD-3] — canonical symbol is the universal join key; overloads distinguished by canonical form; **exact string format is a `[NEEDS APPROVAL]` gate**.
- [Source: .../ARCHITECTURE-SPINE.md#AD-18] — `domain` designates exactly one wire/join variant; other variants are module-internal; the deferred gate must specify the join variant.
- [Source: .../ARCHITECTURE-SPINE.md#AD-22] — `catalog.sqlite` is the sole authority for record/edge IDs from a `domain` `identityFields` spec; ingest modules never compute IDs.
- [Source: .../ARCHITECTURE-SPINE.md#AD-4, #AD-12, #Deferred] — never fabricate; SDK-version scoping is later; canonical-symbol serialization gate is Epic 3.
- [Source: _bmad-output/implementation-artifacts/2-2-parse-modern-and-legacy-javadoc-layouts-into-equivalent-fields.md] — the `ExtractedType`/`ExtractedMember` model this story consumes; the cli/policy deferral pattern; the fixtures under `extract/`; the signature-divergence item deferred to this story.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#story-2.2 independent re-review] — signature divergence (modifiers/annotations, FQN vs simple param types, U+200B) is Story 2.3 identity work; U+00A0 is a refuted non-issue.
- [Source: app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedType.java, ExtractedMember.java, MemberKind.java, ExtractionResult.java, JavadocFieldExtractor.java] — the 2.2 model + minter API to consume; record-invariant and result-holder patterns to mirror.
- [Source: app/domain/src/main/java/com/luciadmcp/knowledge/domain/ApiElement.java, MethodElement.java, CatalogStore.java] — skeleton `(canonicalSymbol, sdkVersion)` records and empty `CatalogStore`; the deferred domain gate — do not extend.

## Dev Agent Record

### Agent Model Used

claude-opus (Claude Code)

### Debug Log References

- Initial `SignatureNormalizer` split parameters on commas before erasing generics, so `Function<A,B>`
  split into two params (`[Function, B]`). Fixed by erasing generics on the whole parameter-list
  string before the top-level comma split.

### Completion Notes List

- New `ingest.javadoc`-internal model: `SymbolKind`, `LookupVariants`, `SymbolRecord`,
  `SymbolMintResult`, plus helpers `TypeName` (FQN-from-path) and `SignatureNormalizer` (erased
  param list), and the `CanonicalSymbolMinter`. No `domain`/`cli` types touched (AD-1); `domain`
  skeleton records untouched.
- Decisions documented in code: dedicated `SymbolKind` (adds `TYPE`); nested-type separator is the
  readable dotted form (`pkg.Outer.Inner`, innermost `simpleName`); array/varargs normalize to `[]`;
  FQN→simple param reduction takes the last dotted segment; degradation contract = record an issue +
  mint a best-effort name-only key (never fabricate, never drop, AD-4). Provisional key grammar
  (`fqn`, `fqn#name(erased,params)`, `fqn#NAME`) explicitly labeled not-the-AD-18/Epic-3 wire key.
- Fixtures: extended shared `extract/{jdk8,jdk11,jdk17}/Widget.html` with overloaded `contains(Point)`
  / `contains(double,double)`, generic `map(Function<A,B>)`, varargs `merge(int...)`, array
  `scale(double[])`. Modern eras render era-authentically (FQN param types, `&nbsp;`, U+200B word
  breaks) so the AC-5 normalizer is genuinely exercised, not trivially satisfied.
- Story 2.2's `JavadocFieldExtractorTest` updated: raw signatures now legitimately diverge across
  eras (that divergence is exactly what 2.3 normalizes), so the cross-era equality assertion compares
  every field except the raw signature (`withoutSignatures` helper), and the member-order/kind
  assertion was updated to the 9 members. All other 2.2 assertions unchanged.
- Verify: `./gradlew :ingest-javadoc:test checkDependencyDirection` — BUILD SUCCESSFUL. Per-class:
  CanonicalSymbolMinterTest 14, SignatureNormalizerTest 8, JavadocFieldExtractorTest 12, all pass;
  `NoJsoupConnectSourceTest` (scans all main sources, now covers the new class) passes;
  `checkDependencyDirection` green for every module.

### File List

New:
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/SymbolKind.java
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/LookupVariants.java
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/SymbolRecord.java
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/SymbolMintResult.java
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/TypeName.java
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/SignatureNormalizer.java
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/CanonicalSymbolMinter.java
- app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/CanonicalSymbolMinterTest.java
- app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/SignatureNormalizerTest.java

Modified:
- app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/package-info.java
- app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractorTest.java
- app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk8/Widget.html
- app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk11/Widget.html
- app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/jdk17/Widget.html
