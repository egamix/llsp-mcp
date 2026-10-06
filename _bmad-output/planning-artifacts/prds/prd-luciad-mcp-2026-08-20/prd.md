---
title: LuciadLightspeed Knowledge MCP
status: final
created: 2026-08-20
updated: 2026-10-02
planning_application: applied
implementation_application: not-applied
---

# PRD: LuciadLightspeed Knowledge MCP

## 0. Document Purpose

This is the current requirements reference. The [approved Evidence-Backed Luciad First Proof change](../../../initiative-luciad-mcp/change-evidence-backed-first-proof/change-evidence-backed-first-proof.md), approved 2026-10-02, governs first-proof scope, interfaces, identity/evidence contracts and delivery order where it conflicts with the [historical implementation brief](../../../../docs/luciad-knowledge-mcp-implementation-brief.md). Earlier approved shared internal-host deployment remains binding. Historical text is retained for provenance, not as an instruction to reintroduce deferred work.

Planning reconciliation applied 2026-10-02; implementation is not applied by this document. Completed 1.1–1.4/2.1–2.4 and current Story 2.5 history remain intact. Story 2.5 review/status reconciliation still gates active delivery; this amendment does not close it. FR-37 remains reserved. Final wire/schema contracts and B1–B6 require their own sign-offs.

## 1. Vision

A Luciad-first offline knowledge layer gives a coding agent version-scoped SDK facts, cited examples, approved practices and recipes. The agent searches knowledge, resolves exact symbols, fetches records/relationships, generates code, batch-checks Luciad symbols, and compiles/tests in its own environment before repair. The service proves catalog membership and supplied evidence; it does not prove compilation, runtime behavior, rendering or non-Luciad dependencies. It must never report an unknown or ambiguous symbol as verified.

## 2. Target User

### 2.1 Jobs To Be Done
- **Developer** integrating LuciadLightspeed: "When I ask my coding agent for a Luciad task, give it authoritative API/context so the generated code uses real, current symbols — not hallucinations." `→ brief §1`
- **Coding agent** (the direct API consumer): "Find workflows/examples, resolve exact symbols and overloads, traverse documented type relationships, and validate all proposed symbols before finalizing code." `→ brief §1, §11`
- **Release operator / corpus owner** (offline ops): "Build, validate, activate, and roll back immutable per-SDK-version indexes inside an air gap without internet access." `→ brief §12, §14`

### 2.2 Non-Users (v1)
- Public, cross-org, or authenticated multi-tenant deployments (deferred to a later authenticated-TLS profile). A single trusted internal team (≤ ~5 developers) on a shared host is served in v1. `→ brief §3`
- Anyone expecting the appliance to generate answers itself — generation stays in the developer's agent. `→ brief §2`
- Public or cross-org consumers — v1 serves a small trusted internal team from a shared host on the internal network, not the public internet. `→ brief §3`

### 2.3 Key User Journeys
The only "UI" is the MCP tool surface consumed by an agent; journeys are tool sequences, not screens.

- **UJ-1. Agent builds a Luciad feature.** `search_knowledge` → `resolve_symbol` → `get_record` / `get_relationships` → generate → `check_symbols` → external compile/test → repair. Cite evidence, check applicability, and report unresolved or untested outcomes. `index_status` establishes build/version/coverage before relying on results.
- **UJ-2. Operator activates a new index in the air gap.** Operator runs `ingest` on a supplied local corpus → `validate-index` → atomic activation of the immutable build → `serve` on the shared internal host. Value lands when the MCP endpoint answers against the frozen index with zero external network access. `→ brief §12, §14`

## 3. Glossary

- Record type identifies content structure; knowledge kind identifies its epistemic/governance role. They are separate fields. A Workflow is the existing procedure record carrying `RECIPE` knowledge, rather than a parallel recipe runtime store.
- A symbol coordinate is a domain-owned versioned structure containing SDK/product, version, language, namespace, owner, member, parameter types and element kind. Wire serialization is frozen under 2.8; display names and normalized aliases cannot become alternate identities.
- A stable record ID identifies any content record, including symbol-less content. Symbol coordinates identify API elements and their links. Neither depends on engine row/document IDs or absolute host paths.
- Exact plus BM25 is a supported primary retrieval mode requiring no model. Vector/hybrid mode is a deferred capability with explicit compatibility/failure behavior if later enabled.
- Proof facts record exact membership, ambiguity, unresolved evidence, version and source separately; relevance scores never prove correctness.
- Four knowledge kinds are `AUTHORITATIVE_FACT`, `EXPERT_APPROVED_PRACTICE`, `RECIPE`, `CANDIDATE_LEARNING`. Candidates stay outside active builds. Provenance can establish a vendor fact, but cannot automatically establish practice approval.
- Citations use typed source locators: HTML page/anchor, source range, curated document/section, and later deck/slide. Source identity, SDK scope and stable record ID remain mandatory for primary content; canonical symbol is optional when absent. Missing anchors are represented truthfully; never synthesize a source locator and call it verified.

- **Catalog:** canonical SQLite store for version-scoped records and evidence.
- **Index build:** sealed immutable SDK/build directory; serving is read-only.
- **Manifest:** versioned profile, assets, schema, SDK/build identities, recipe/runtime fingerprints and coverage. Absent capabilities are explicit.
- **Documented SDK universe:** indexed SDK symbols; traversal reports scope and bounds.
- **Quality label:** governance-supplied official, validated, best-practice, illustrative or legacy; independent of practice approval.
- **Technique tag / Training:** deferred grouping metadata / deck-derived content; neither gates symbol-less discovery or the first proof.

## 4. Features

Provenance pointers cite the governing brief section. Each FR's *Consequences* are the testable conditions downstream stories must satisfy.

### F1. Offline, file-only corpus ingestion
**Description:** Local Javadocs and approved task-relevant source/curated inputs only; zero egress, no required model, no active training exception.

#### FR-1: File-only first-proof inputs

Consume local Javadocs and approved development-task-relevant examples, workflow recipes, guide sections and curated practices. Lexical mode requires no model. Training import is unavailable and returns a clear unsupported-capability error. No crawler, login automation, model download or external egress occurs. Story 2.8/6.5 specifies the existing CLI flag transition explicitly; completed Story 1.2 history is unchanged.

#### FR-2: Profile-aware resumable ingestion

Preserve ordered catalog → relationships → selected examples/curated records → lexical index → validation → atomic activation and staging-only checkpoints. The exact/lexical manifest and report explicitly skip deferred embedding/vector stages. Resume verifies source/content and build-recipe hashes; changed inputs start a new build. Interrupted/resumed logical output agrees with uninterrupted output. Parsing queues and catalog/index batches are bounded and reported; release each AST after emitting records/evidence. Malformed sources follow fail-fast or collect-and-continue, but BUILD_INVALIDATING always blocks activation.

#### FR-3: Portable identity, content hashes and versioned reports

Freeze distinct purposes for stable record/edge identity, source/content hashes, build-recipe fingerprint and runtime-compatibility fingerprint in Story 2.8. Relevant logical content/recipe changes affect the appropriate digest; absolute source/output locations do not affect portable build identity. Version the report and include profile, coverage, skipped stages, severity/completion, throughput, errors, batch sizes and exact/ambiguous/unresolved resolution counts grouped by type/kind. Do not equate content hashes with identity fields or build recipe with runtime compatibility.

### F2. Javadoc structural parsing & canonical symbols
**Description:** Structure-aware parsing of thousands of Javadoc HTML files across modern and legacy doclet layouts, emitting one record per API element. `→ brief §7`

#### FR-4: Search-index-assisted discovery with fallback
Discovery prefers `*-search-index.js` files and falls back to recursive `*.html` scanning; page type is detected by structure, not filename; nav/tree/help/index pages are excluded from member ingestion; overview/package pages are kept as narrative records. `→ brief §7`
**Consequences (testable):**
- Given fixtures with and without search-index files, discovery finds the same API pages.
- Navigation-only pages produce no API-member records.

#### FR-5: Modern + legacy layout parsing
Both `section.class-description`/member-summary/`section.detail` and `ul.blockList`/`li.blockList`/`h3`/`h4` layouts parse into the same extracted fields (module, package, FQ type, kind, modifiers, type params, superclass, interfaces, known subclasses, description, fields, constructors, per-overload methods, param/return/throws, since/deprecated/see-also, source path + anchor). `→ brief §7`
**Consequences (testable):**
- Modern and legacy fixtures of the same class yield equivalent catalog records.
- Broken/incomplete HTML degrades gracefully under the configured policy.

#### FR-6: One record per API element (overloads distinct)
Each semantic API element is exactly one primary retrieval record; unrelated APIs are never merged into fixed token windows; every method overload is a separate record. Long narrative may have secondary chunks that retain full symbol + source metadata. `→ brief §7`
**Consequences (testable):**
- Two overloads of the same method produce two records with distinct canonical symbols.
- No primary record spans more than one semantic API element.

#### FR-7: Domain-owned versioned symbol coordinates

Use one domain-owned versioned coordinate containing SDK/product, SDK version, language, namespace, owner/type, member, parameter types and element kind. Overloads remain distinct. Simple-name, dotted-member, erased-generic and camel-case aliases are lookup aids, never alternate identities. Freeze serialization and known-answer round trips in 2.8; preserve completed 2.3 history.

### F3. Canonical catalog & relationship graph
**Description:** A versioned SQLite catalog plus a typed relationship graph built by a mandatory two-pass process, traversable with bounded recursion. `→ brief §3, §6, §7`

#### FR-8: Versioned canonical storage and stable record identity

SQLite persists unique version-scoped symbols and stable record IDs, including symbol-less content, using domain-owned identity recipes. Record/edge IDs never depend on row IDs, search document IDs, absolute paths or output location. Preserve source locators truthfully, including absent anchors. Story 2.8 freezes schema/build metadata, uniqueness and explicit migration or rebuild-only rejection of provisional stores; completed 2.4 is not reopened.

#### FR-9: Two-pass typed relationships with explicit producer ownership

Pass one builds the catalog and source locator map; pass two resolves references with the two specified inverse pairs reconciled. The eventual 17-type SDK/example/workflow family comprises 13 Javadoc structural types (2.5), WORKFLOW_REQUIRES (3.4), and USES_TYPE/CONSTRUCTS/CALLS (3.2). Test each at its producer. Parameter/return/field/exception types link only when resolvable. Curated-knowledge dependency relations receive a separate versioned typed contract in 2.8; do not append ad-hoc SDK enum values.

#### FR-10: Explicit unresolved and ambiguous evidence

Persist original token/locator, SDK scope, candidate IDs when applicable, status/kind and reason. External/undocumented references remain unresolved; competing matches remain ambiguous with no selected exact target. Evidence remains retrievable and never becomes a guessed edge. Partial records require valid provenance. Invalid required recipe dependencies block activation; ordinary contract-permitted incomplete source evidence does not.

#### FR-11: Bounded typed traversal

Direct/recursive traversal is cycle-safe, version-isolated and deterministically ordered. Enforce finite depth, visited-node/edge, result, execution and continuation budgets frozen in 2.8. Report scope, limits and truncation; bounded results never prove graph completeness. SDK traversal and explicitly requested knowledge-dependency traversal remain distinct. Test cycles, empty graphs and below/above-limit cases.

### F4. Example, workflow & guide ingestion and linking
**Description:** JavaParser-based ingestion of examples and workflow docs, plus HTML-article parsing of guide/tutorial pages, preserving complete source/content, extracting Luciad references, and linking to the catalog — never compiling or executing. `→ brief §2, §8`

#### FR-12: Example parsing preserves complete source (no execution)
`.java` examples are parsed with JavaParser AST; complete source is preserved **once**; the ingester emits **three levels of record — whole example, declared class/interface, and method/constructor**. Child (class/method) records are canonical SQLite rows linked to the parent example, each storing a `SourceRange` (relative path, start/end line+column) and the minimum source text needed for search and citation; Lucene holds method-sized projections. Examples are never compiled or run. `→ brief §8` *(Expanded 2026-08-25 Sprint Change Proposal — AD-30.)*
**Consequences (testable):**
- The complete original source is retrievable via bounded `get_record`.
- A method-sized search hit can fetch its complete parent example.
- Every class/method record and every source-derived edge carries a `SourceRange` and citation.
- No build or execution of any sample occurs during ingest (asserted by code review + absence-of-build test).

#### FR-13: Luciad reference extraction & catalog linking
Luciad imports and syntactically identifiable type/member references are resolved against the catalog after Javadocs are indexed, using a **fixed resolution ladder** (FQN literal → explicit import + exact match → local/same-package declaration → unique wildcard-import match → owner+name+signature/arity match → ambiguous/unresolved). Each resolved reference emits a typed edge (`USES_TYPE`/`CONSTRUCTS`/`CALLS`, AD-30) carrying **resolution evidence** (source range, original token, candidate canonical symbols, selected symbol only when status is `EXACT`). Only an `EXACT` match becomes a verified Luciad symbol; `AMBIGUOUS` and `UNRESOLVED` references stay explicit and are never promoted (AD-4, AD-7). `→ brief §8` *(Expanded 2026-08-25 Sprint Change Proposal — AD-30.)*
**Consequences (testable):**
- A symbol can retrieve its linked examples via `search_knowledge` / `get_relationships`.
- Two packages exposing the same simple class name stay `AMBIGUOUS` unless source context selects one exactly.
- Overloaded methods stay `AMBIGUOUS` when parameter info is insufficient.
- An incomplete example project still produces cited partial records without an SDK JAR or build execution.
- An unresolvable reference is recorded as unresolved, never fabricated.

**Forward gate:** Story 2.8 approved contracts precede example implementation; arity uniqueness alone cannot prove an overload when parameter types cannot resolve. Development-task sources only; 10.1 reserves held-out tasks before curation.

#### FR-14: Governance-supplied quality and approval

Retain official/validated/best-practice/illustrative/legacy quality labels from governed input; filenames and parsers never infer quality. Vendor provenance alone establishes official facts, not approved practices. Practices require explicit approval/owner/provenance and SDK applicability. Missing sidecars imply neither label nor approval. Retrieval can prefer current official/validated/best-practice examples without treating quality as practice approval. Candidate and invalid approval records cannot enter active builds. Broad technique vocabulary is deferred and does not gate 3.3–3.6.

#### FR-15: Compile workflow recipes and required dependencies

Preserve prerequisites, ordered steps, limitations, linked examples and version metadata. Compile Workflow records with RECIPE kind, stable required knowledge IDs and explicit applicability. Resolve dependencies against compatible facts/approved practices; absent, incompatible or candidate required dependencies block activation. Freeze cycle policy in 2.8 and reject invalid graphs deterministically. Check applicability before recommendation; no duplicated versioned facts or second runtime recipe database.

#### FR-35: Selected local guide sections

Story 3.5 consumes an explicit allowlist of approved local guide pages/sections selected using development tasks. Preserve title, ordered selected prose/snippets, original source/section locator and content hash. Report selection/skips and coverage without claiming full-corpus ingestion. Malformed content follows the classified failure policy. Broad guide ingestion remains deferred.

#### FR-36: Guide evidence and governance

Resolve selected guide references against the version-bound catalog; retain exact/ambiguous/unresolved evidence and never guess. Vendor facts can be cited without invented quality labels; guide-derived engineering practices require separate human approval. Serve fully cited symbol-less sections through common tools.

#### FR-37: (reserved — intentionally unassigned)
No requirement carries this number. The gap is intentional; FR numbering jumps FR-36 → FR-38 across the PRD, architecture spine, and epics, with no lost requirement.

#### FR-42: Cross-cutting technique tags [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
*(Added 2026-08-21, Sprint Change Proposal.)* A curated, fixed vocabulary of coding-technique/best-practice labels (e.g. `resource-lifecycle`, `builder-pattern`, `listener-chaining`) is supplied only via the same governance sidecar mechanism as FR-14, attachable to `Example`, `Workflow`, `Guide`, and `Training` records — never `ApiElement`/Javadoc records — independent of whether the record has a resolved canonical-symbol link. Filename/code-shape/prose inference never assigns a technique tag; a tag value outside the published vocabulary is rejected at ingest, not silently accepted or coerced.
**Consequences (testable):**
- A record with no sidecar-supplied technique tags is retrievable with an empty tag set, never a guessed one.
- A record whose sidecar specifies a technique tag outside the curated vocabulary fails ingest validation with a structured error naming the offending value.
- A `Guide` or `Training` record with no resolved canonical-symbol link remains discoverable via its technique tags (see FR-43), closing the symbol-less-content discoverability gap.

### F5. Exact and lexical retrieval with gated semantic enhancement
**Description:** Version-bound exact resolution plus BM25 is the supported first-proof mode; vector search and fusion require later evaluation and approval.

#### FR-16: Version-bound exact resolution

Bind SDK version before catalog lookup. Known signatures resolve to exact proof or explicit ambiguity/unresolved state. Exact matches lead ranked candidates; BM25 relevance can never become identity or applicability proof. Canonical fixtures achieve 100% exact lookup with zero wrong-version or silently selected overloads.

#### FR-17: BM25 and scoped knowledge filters

Index canonical/normalized exact, prefix and camel-case fields plus prose, params, returns, exceptions and selected example/workflow/guide/practice text. Support highlighting and SDK version, record type, knowledge kind, applicability, governance/provenance, package/kind/deprecation/quality filters. Stable record IDs identify and deduplicate all content, including symbol-less records. No model or technique vocabulary is required.

#### FR-18: Local ONNX vector search with lexical fallback [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
Vector search runs over normalized semantic records using a locally-supplied ONNX model + tokenizer; the service never downloads a model; model-load failure is explicit; fallback to lexical occurs only when configured and is visible in `index_status`. `→ brief §9`
**Consequences (testable):**
- With no model present and fallback disabled, hybrid mode fails explicitly (does not silently degrade).
- `index_status` reports retrieval mode and model status accurately.

#### FR-19: Bounded cited lexical result assembly

Story 4.6 assembles exact/lexical candidates with governance/applicability/content-citation checks, deterministic stable-ID deduplication/order and bounded relationship enrichment. Filter-only browse is deterministic. Bound internal work as well as payload and expose exact/lower-bound/unknown counts, empty reason, truncation and continuation. Vector fusion stays deferred in 4.4, never marked completed by this capability transfer.

#### FR-20: Fingerprint-consistent embedding [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
The same pinned model, tokenizer, preprocessing, normalization, dimensions, and query instructions are used for indexing and runtime queries; navigation/duplicate/boilerplate/empty content is excluded from embedding. The embedding fingerprint is a schema-level property, not per-record-type — adding the guide record type does not itself change the fingerprint definition (see NFR-14), only what content is embedded under it. `→ brief §9`
**Consequences (testable):**
- An index whose embedding fingerprint differs from the runtime model is rejected (see FR-27).
- Embedded content excludes navigation and empty records.

### F6. MCP tool surface
**Description:** Exactly six read-only tools: `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`, `check_symbols`, `index_status`. Earlier 15 → 17 → 19 → 20 tool amendments remain historical; specialized aliases are deferred.

#### FR-21: Knowledge search, symbol resolution and record fetch

Expose search_knowledge, resolve_symbol and get_record. Search all eligible kinds with scoped filters and deterministic filter-only browse, returning stable IDs and available sections. Resolve returns exact/ambiguous/unresolved with version scope. Fetch supports bounded ID batches and optional sections with per-ID not-found/oversize outcomes. Never invent symbols or silently select an overload.

#### FR-22: Typed relationships through one tool

Expose get_relationships with edge-family/type, direction, depth and implementation/subclass filters, including concrete/abstract/deprecated/example-used/recipe-recommended context when supported. Enforce 2.7/2.8 budgets and return typed evidence/citations, scope and truncation. Distinguish SDK links from curated dependencies; no completeness claim beyond evidence or traversal budget.

#### FR-23: Example and workflow capabilities through common tools

Use search_knowledge, get_record and get_relationships for examples/workflows; expose no extra tool names. Preserve quality/provenance, linked symbols, parent/source retrieval and applicability. Full source is requested through bounded get_record; a method-sized hit can fetch its complete parent via sections/continuation within the response cap.

#### FR-24: Bounded symbol checking and index status

Expose check_symbols and index_status. Bounded batches return exact-valid, ambiguous-with-candidates or unknown for the bound SDK/build; neither unknown nor ambiguous is reported valid. Status reports active SDK/build/schema/fingerprint versions, counts/coverage, skips/errors, ingestion time, retrieval mode and available capabilities. No-model lexical operation is healthy. Neither tool compiles code.

#### FR-38: Guide access through common tools; aliases deferred

Selected guide search/fetch acceptance belongs to search_knowledge and get_record under 5.1/5.3. Return complete typed citations and bounded selected content, with not-found on unknown IDs. Dedicated search_guides/get_guide aliases (5.3b) are deferred, not exposed as placeholders.

#### FR-41: Training tools [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_trainings` (natural-language/keyword query over the training-deck corpus, filterable, citation-bearing per FR-31) and `get_training` (fetch a full training record by stable ID with citation), both following the same structured-response conventions as the other retrieval tools.
**Consequences (testable):**
- `search_trainings` returns citation-complete results ranked by relevance to a conceptual query.
- `get_training` returns the full training record only via this explicit tool, never embedded inline elsewhere.

#### FR-43: Concept/technique search tool [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_concepts` (query by one or more technique tags — FR-42 — across `Example`, `Workflow`, `Guide`, and `Training` records in one federated, deduplicated, citation-complete result list, following the same fused-list convention as hybrid retrieval's FR-19). An unrecognized tag value is rejected with the valid vocabulary listed, never silently ignored.
**Consequences (testable):**
- `search_concepts` returns a single fused list spanning all four taggable record types, each entry citation-complete per FR-31.
- A query naming an unrecognized technique tag returns a structured error listing valid tag values, never an empty or guessed result.

#### FR-25: Six read-only tools with bounded contracts

Streamable HTTP /mcp, optional stdio, shared versioned request/result/error codes and timeouts. Default page 10, hard cap 50, ≤1 MiB serialized response. Story 2.8 freezes finite query/input, ID/symbol batch, candidate, traversal, trace and time caps. Oversized content uses bounded sections/continuation or a structured size error. Cursors bind query/version/build and reject or explicitly restart stale scope. Agent inputs cannot accept credentials, host paths, endpoints, model/device settings or activation controls.

### F7. Index lifecycle: immutable build, validate, activate, roll back
**Description:** Every index is an immutable per-SDK-version build; validation precedes atomic activation; rollback flips only the active pointer; incompatible indexes are rejected. `→ brief §12`

#### FR-26: Shared validation and atomic activation

One BuildValidator checks integrity, schema/version, identities, required dependencies and capability compatibility before publishing a sealed immutable build. BUILD_INVALIDATING outcomes, unknown/stub SDK versions, corrupt data, identity collisions and invalid required recipes reject activation. Legal unresolved/ambiguous source evidence remains explicit. Serialize competing writers and test interrupted staging/activation; active data is never mutated.

#### FR-27: One compatibility policy on every load path

CLI validation/activation and MCP startup/reload/rollback/restore use the same BuildValidator and rejection reasons. Unknown schemas/profiles or mismatched declared assets fail closed. Lexical requires no model; vector assets are checked only for an approved declared capability. A tested migration produces a separately validated new build; no bypass route.

#### FR-28: Reader-safe rollback and isolated versions

Retain current and previous builds where storage allows, preserve an explicit version default and pin each request to one immutable handle. Revalidate current runtime compatibility before rollback; no deletion while readers reference a build. Invalid replacement keeps the valid current handle serving; invalid cold start fails clearly. Cross-version migration/combined queries are unsupported in the first proof, never silently merged.

#### FR-29: Manifest-complete backup and shared restore validation

Back up manifest, catalog, Lucene, versioned report, configuration, checksums and all profile-declared assets together. Lexical explicitly declares no model and needs none; missing declared assets invalidate backup. Restore uses the shared BuildValidator and never trusts historical validation alone.

### F8. Agent guidance & citations
**Description:** A shipped agent skill file enforcing mandatory retrieval behavior, plus universal citation on primary results. `→ brief §9, §11`

#### FR-30: Six-tool agent guidance and external truth boundary

Ship agent/SKILL.md using only the six actual names: retrieve → resolve → fetch context/relationships → generate → batch-check → external compile/test → repair. Preserve exact constructors/overloads, implementation lookup, cited approved examples and applicability checks, no memory-invented APIs, and honest unresolved/untested reporting. Include one worked display-a-point sequence, build on 5.8 and make guidance usable for 10.2 evaluation. The MCP service proves catalog membership/evidence, not compilation/runtime/rendering/non-Luciad dependencies.

#### FR-31: Typed citations on every primary content result

Require SDK scope, record type, stable record ID, source identity and truthful typed locator: HTML page/anchor, source range, curated document/section, and later deck/slide. Canonical symbol is present only when resolved; missing anchors are represented honestly, never fabricated. Citation completeness is 100%, including symbol-less practices/guides and dependency evidence. Do not filter exact/ambiguous/unknown verdicts for absent content citations; optional diagnostic provenance is separate.

### F9. Packaging, deployment & offline/security posture
**Description:** One Docker image with `ingest`/`validate-index`/`serve` modes, deployable on a shared internal host, air-gap transferable. `→ brief §3, §5, §13, §14`

#### FR-32: Internal-host serving image and offline deployment

One serving image provides ingest/validate-index/serve; Compose separates ingestion and read-only serving, pull_policy: never, non-root, read-only root FS where practical, cap-drop ALL, no-new-privileges and read-only proprietary inputs. Bind the shared internal host for a trusted team of about five or fewer, never public internet. Retain zero egress; no new authentication/TLS/tenant scope. First-proof assets require no ONNX model or Docling/training image; 8.4 remains deferred.

#### FR-33: Capability-complete air-gap release bundle

Bundle serving image tar, SHA256SUMS, SBOM, install documentation, frozen index and exactly the profile-declared assets with licenses. No model or training image is required for lexical. Verify tamper detection and clean networking-disabled load/ingest/serve smoke tests; document prebuilt transfer and approved in-gap rebuild. Existing legal/provenance/NFR/release approvals remain required, not passed by the proof.

#### FR-34: Headless CLI and shared diagnostics

Retain ingest, validate-index, serve and stats with documented flags and exit codes. CLI diagnostics use the same application query/proof services as MCP under operator-owned configuration and bounded output. Freeze diagnostic spelling/CLI transition in 2.8. Story 6.5 delivers non-success for build-invalidating ingest and report-write failure semantics early; 8.3 completes help/headless/release documentation.

### F10. Training-material ingestion [DEFERRED; historical design]
**Description:** A separate, ingest-time-only path that uses the Python `docling` library (vision/layout models) to convert PPTX/PDF training slide decks into structured content, linked to the catalog like every other record type. This is the sole ingest path permitted network access and non-Java tooling execution — an explicit, scoped exception to AD-9/AD-10, carved out only for this input type and never extended to serving. *(Added 2026-08-21, Sprint Change Proposal.)*

#### FR-39: Training-deck ingestion via docling [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
The ingester accepts a local training-materials dir (PPTX/PDF slide decks) as a sixth permitted input path. Training-deck ingestion is a single, explicitly-scoped exception to FR-1/AD-9 (zero egress) and AD-10 (parse-only): it invokes docling, which may reach the network to fetch/run vision-layout/table/OCR models, and executes non-Java tooling. This exception applies only within the separate training-ingest module/image (FR-32); no other ingest path or the `serve` path may perform network I/O or invoke non-Java tooling. Docling's structured output (slide/section title, ordered text blocks, tables, extracted figures/captions) is parsed into one `Training` record per logical deck section, mirroring FR-6's one-record-per-element discipline; malformed/unreadable decks degrade per the same `fail-fast | collect-and-continue` policy as FR-5.
**Consequences (testable):**
- Training-deck ingestion is the only scenario in the ingestion test suite permitted to observe network egress; every other ingestion test (Javadoc, examples, workflows, guides) still asserts zero egress.
- A malformed/unreadable slide-deck fixture is handled per the configured `fail-fast | collect-and-continue` policy, never crashing the run silently.
- A `Training` record is retrievable with its section title, ordered text blocks, and any extracted tables/figures intact.

#### FR-40: Training reference extraction & catalog linking [DEFERRED]

**Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
Luciad symbol/API references appearing in training-deck text and tables are resolved against the catalog using FR-7's canonical/normalized keys, run only after Javadocs are indexed; unresolved references stay explicitly unresolved per FR-10.
**Consequences (testable):**
- A resolvable reference in a training deck's text or table links to its catalog record.
- An unresolvable reference is recorded as unresolved, never fabricated.

### F11. Evidence contracts, governance and evaluation

#### FR-44: Structured SDK identity and frozen portable contracts

Structured SDK identity and frozen portable contracts. Versioned known-answer symbol/record/edge/build recipes; stable IDs unchanged by output-root relocation; unknown SDK version cannot activate.

**Coverage:** 2.8; AD-3/12/17/18/22/32.

#### FR-45: Governed compiled knowledge

Governed compiled knowledge. Four knowledge kinds; schema-validated deterministic authoring; candidates outside active builds; practices require approval; required recipe dependencies resolve compatibly; inapplicable recipes are not recommended.

**Coverage:** 2.8, 3.3/3.4/3.6, 6.1; AD-14/31.

#### FR-46: Shared build validation

Shared build validation. All startup/load/activation/reload/restore routes use one validation policy and return equivalent rejection reasons for the same invalid artifacts.

**Coverage:** 2.8, 5.6, 6.1–6.5; AD-5/6/23/33.

#### FR-47: Honest bounded interface

Honest bounded interface. Six read-only tools share explicit proof, route, coverage, empty reason and continuation semantics; no bounded query claims exhaustive results without proof.

**Coverage:** 2.8, 4.6, 5.1–5.7, 7.2; AD-11/15/19/20/24/35.

#### FR-48: Controlled outcome evaluation and governed improvement

Controlled outcome evaluation and governed improvement. Predeclared paired treatments, held-out protection and cost reporting; every accepted correction has human approval and a permanent regression before activation.

**Coverage:** 10.1–10.4; AD-34.

#### FR-49: Contract-change and verification gates

Contract-change and verification gates. Schema/format changes have version/changelog or reviewed exemption; Windows/Linux known answers and selected mutations demonstrate critical tests can fail.

**Coverage:** 2.8 and affected 6.x/10.x implementation; AD-32/34.

#### FR-50: Shared application services

Shared application services. CLI diagnostics and MCP produce equivalent logical proof/coverage results for identical build/query/scope, excluding transport-specific wrapping. Neither compiles generated code; evaluation does so externally.

**Coverage:** 2.8, 6.5, 7.1, 10.2; AD-1/36.

## 5. Non-Goals (Explicit)

No compilation/execution of generated apps or ingested samples, SDK JAR lookup, Maven/Gradle execution against samples, server-side generation, package hosting, separate LLM, external vector/graph database, new runtime or semantic-web platform. The evaluation harness compiles/tests externally with separately supplied SDK/build prerequisites.

No public or authenticated multi-tenant deployment, no public-base/private-overlay layering, crawler, portal client, cloud embeddings, telemetry, update checks or runtime internet. No active Docling/Python/network ingestion exception. No inferred practice approval or autonomous knowledge promotion. Proprietary Luciad inputs are never committed; tests use synthetic fixtures.

## 6. MVP Scope

### 6.1 First product proof

Contract freeze (2.8), version-bound exact/BM25 retrieval, the six tools, selected approved task-relevant content, initial guidance, immutable validation/atomic activation and controlled evaluation. Freeze 10.1 protocol and reserve held-out tasks before curation. Finish 2.5 review, then 2.6/2.7 → 2.8 → 4.1/4.2/4.6 + 6.1/6.2/6.5 + 5.7 → tools/content/guidance → 10.2/10.3/10.4. Minimum lifecycle cannot be bypassed for proof acceptance.

### 6.2 Production release

Complete lifecycle/rollback/backup, offline packaging, approved NFR evidence, corpus/legal/governance and release sign-offs. A successful product proof does not establish production release acceptance.

### 6.3 Gated scope

Vectors/fusion; full guide corpus; training/Docling/image/tools; broad technique vocabulary/concept tool; specialized aliases/migration tools; multi-SDK/plugin architecture; external-agent installer; conversation mining; autonomous promotion/self-repair; extra databases/runtimes/graph/semantic-web infrastructure. Reactivate only for a diagnosed gap, controlled benefit, preserved safeguards and owner approval. Task-relevant guide sections/practices/recipes remain in scope. Authenticated-TLS multi-tenancy and public/private overlays remain outside v1.

## 7. Success Metrics

Derived from brief §15 key measures and §14 targets; each validates the noted FR(s)/NFR(s).

**Primary**
- **SM-1: Exact canonical lookup = 100%** on indexed fixtures (exact/lexical; deferred enhancements must preserve this). Validates FR-16, FR-21. `→ brief §15`
- **SM-2: Unknown-symbol safety = never marked valid.** Validates FR-10, FR-24, FR-30. `→ brief §15`
- **SM-3: Natural-language retrieval ≥ 90%** of approved conceptual gold queries return an expected workflow/example/API record in the top 5 (provisional retrieval diagnostic; per-query results retained; B2 must explicitly reconcile and approve the threshold). Validates FR-16–FR-19. `→ brief §15, §18`
- **SM-4: Citation completeness = 100%** of returned primary records. Validates FR-31. `→ brief §15`
- **SM-5: Version isolation** — no cross-version mixing; migration/combined queries are unsupported in the first proof. Validates FR-28. `→ brief §15`

**Secondary**
- **SM-6: Overload ambiguity always explicit.** Validates FR-6, FR-21. `→ brief §15`
- **SM-7: Relationship integrity** — all resolvable synthetic links produce correct typed edges. Validates FR-9. `→ brief §15`
- **SM-8: Deferred vector-benefit gate** — a future hybrid configuration must demonstrate benefit over the lexical baseline before activation of that scope. Validates FR-18, FR-19. `→ brief §15`
- **SM-9: Offline operation** — zero external calls during ingest/serve tests. Validates NFR-11, FR-1, FR-32. `→ brief §15`

**Counter-metrics (do not optimize)**
- **SM-C1: Do not raise SM-3 by weakening exact-symbol safeguards.** Counterbalances SM-3 against SM-1/SM-2 — if retrieval misses the diagnostic target, diagnose routing/content gaps and compare controlled treatments; never hide the miss by relaxing exact validation. `→ brief §15`

- **SM-10: Agent outcomes.** Story 10.1 predeclares success rubric/improvement rule for 20–30 representative tasks, development/held-out separation, paired treatments, repeat policy and budgets. Report task completion, initial/final compile results, unknown/wrong-version/overload usage, citations/practice adherence, calls/tokens/time/repairs and build/indexing cost separately, including failures and uncertainty. Missing external SDK/build prerequisites are blocked/not-run, never passed.
- **SM-11: Trust and portability.** Zero candidate/unapproved knowledge or invalid required dependencies in active builds; Windows/Linux logical known answers agree; selected critical mutations are detected; every corrupt/incompatible build entry path fails closed. No measured success is claimed by planning approval.

## Cross-Cutting NFRs

From brief §14. All are *initial v1 targets*; changes require product- and technical-owner approval, not silent relaxation. **All latency/memory/throughput targets are unfalsifiable until B1 (reference hardware + representative corpus) is fixed** — see Open Questions.

| ID | Requirement | Target | Provenance |
|---|---|---|---|
| NFR-1 | Exact lookup latency | p95 ≤ 250 ms | §14 |
| NFR-2 | Lexical first-proof warm search latency | p95 ≤ 2 s; deferred hybrid requires separate approved hardware/corpus evidence | Approved change P07 |
| NFR-3 | Concurrency | ≥ 4 simultaneous MCP requests, no errors / unbounded queue | §14 |
| NFR-4 | Startup | ready to serve a prebuilt index ≤ 30 s including active-profile assets | Approved change P07 |
| NFR-5 | Serving memory | ≤ 8 GiB RSS on reference corpus | §14 |
| NFR-6 | Ingestion memory | ≤ 16 GiB RSS on reference corpus | §14 |
| NFR-7 | Full rebuild | ≤ 8 h on 8-core CPU workstation for reference corpus (throughput recorded) | §14 |
| NFR-8 | Index footprint | ≤ 4× source corpus bytes (exceptions need a recorded trade-off) | §14 |
| NFR-9 | Appliance footprint | serving image + required profile assets ≤ 1.5 GiB (excl. corpus/indexes); lexical requires no model; training image deferred | Approved change P07 |
| NFR-10 | Response bounds | default 10, cap 50 results; ≤ 1 MiB serialized with truncation/continuation metadata | §14 |
| NFR-11 | Offline behavior | zero DNS/HTTP/telemetry/model-download/update-check for every active ingestion and serving path; no active training exception | Approved change P07 |
| NFR-12 | Security posture | non-root, read-only root FS where practical, cap-drop, internal-host bind (no public exposure), no full-doc/source logging by default | §14 |
| NFR-13 | Reproducibility | deterministic record IDs + content hashes for repeatable updates | §12 |
| NFR-14 | Compatibility enforcement | reject unknown/mismatched schema and capability-specific fingerprints; lexical explicitly has no model, deferred vector profiles validate all declared model/dims/tokenizer/preprocessing assets | Approved change P07 |

## Constraints and Guardrails

Preserve derivative-documentation/proprietary-corpus handling and licensing review (B3), source product/version/provenance and read-only inputs. Five named governance roles remain product owner, technical owner, corpus owner, security/legal reviewer and release operator (B4). No approval is inferred from a quality label or scaffold.

Story 2.8 owns human approval of SQLite, symbol/relationship/identity, lexical Lucene and MCP schemas before dependent work. Breaking contract changes require version/changelog updates or an explicit reviewed exemption. Model/KNN/tokenizer/licensing gates stay with deferred vectors. B1 NFR evidence and release activation approval remain mandatory.

## API Contracts / Public Surface

Exactly six read-only names: `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`, `check_symbols`, `index_status`. Final schemas require separate Story 2.8 sign-off.

Keep defaults of 10 results, hard cap 50 and ≤1 MiB; finite ID/symbol batch, query, traversal work and trace limits must be frozen in Story 2.8. Too-large records return explicit bounded sections/continuation or a structured size error, never an unbounded response. Cursors bind build/version/query so stale cursors are rejected or explicitly restarted, never silently mixed with a new build.

Content results include stable ID, record type, knowledge kind, provenance/governance, citation, applicability, proof facts, `matchedBy`, scope/coverage, active build and SDK version. Empty results distinguish not-found, not-indexed, filtered/inapplicable and unavailable capability as applicable. Report total with an exact/lower-bound/unknown relation; never label a bounded ranked set exhaustive.

Do not drop verdicts because a citation is absent. Optional diagnostic provenance may accompany them, but the schema must make absent/not-applicable distinct from a missing required content citation. Public inputs cannot accept credentials, host paths, endpoints, model/device settings or activation controls.

## 8. Open Questions

- **B1 — Reference hardware/corpus.** Product + technical owners define these before NFR sign-off/release; targets are not measured or waived.
- **B2 — Retrieval gold set and provisional 90% top-5.** Product + corpus owners reconcile the diagnostic target under 10.1 before acceptance claims.
- **B3 — Corpus/license/distribution approval.** Security/legal review gates release; model licensing waits with deferred vectors.
- **B4 — Named governance owners.** Product owner assigns people; all human sign-offs remain open until recorded.
- **B5 — Java pin.** Retain Java 21 direction, but technical owner must explicitly resolve formal 21 vs organizational LTS approval; completed scaffolding does not close this gate.
- **B6 — Outcome decision protocol.** Product/technical/corpus owners approve 10.1 success rubric, improvement threshold, task partition/access controls, repeated-trial policy and budgets before trials or corpus tuning.
- **Story 2.5 review.** Story is in-review while tracker is in-progress; reviewer reconciles through existing review process. Do not promote status or expand frozen intent.
- **Story 2.8 contracts.** Exact serialization/schema/wire formats, finite budgets, recipe-cycle and lifecycle lock/recovery rules still need technical sign-offs before dependent work.
- **Deferred capabilities.** ONNX concurrency/model/KNN settings, training provenance/governance/Docling pin and broad tag vocabulary require later reactivation decisions, not first-proof work.

## 9. Assumptions Index

- The interface is agent-facing MCP; there is no human-screen UX contract.
- External evaluation tooling and compile/test are outside the serving runtime.
- Reference hardware/corpus and named owners remain pending B1/B4; no acceptance result is inferred here.
- The 90% top-5 diagnostic is provisional under B2; outcome improvement is predeclared under B6/10.1.
- Story 2.8 freezes and obtains approval for exact contracts; historical brief §10 is provenance, superseded where it conflicts with this six-tool amendment.
- Planning application does not complete Story 2.5 review, authorize immediate source changes, close technical approvals, or pass proof/release gates.


## Amendment provenance

The August 21 guide/training/tag proposals, August 24 shared-host decision and August 25 structure-aware-example amendment remain historical provenance. This approved October 2 change retains shared hosting and structure-aware examples, narrows guides and defers training/tags/vectors. Original dated proposals and completed-story records are unchanged; the approved proposal and companion retain the OLD → NEW rationale. FR-37 remains intentionally unassigned.
