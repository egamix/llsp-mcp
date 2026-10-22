---
stepsCompleted: ["step-01-validate-prerequisites", "step-02-design-epics", "step-03-create-stories"]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-luciad-mcp-2026-08-20/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md
  - docs/luciad-knowledge-mcp-implementation-brief.md
---

# LuciadLightspeed Knowledge MCP - Epic Breakdown

## Overview

Coordinated planning amendment applied 2026-10-02 under the [approved proposal](../initiative-luciad-mcp/change-evidence-backed-first-proof/change-evidence-backed-first-proof.md) and its P01–P07/A01–A20/E01–E17/T01–T04 edits. No source implementation is applied. Completed stories and current Story 2.5 history/status remain intact; active delivery still awaits its review gate. Planning approval is distinct from contract sign-off, implemented proof and production release acceptance.

Stable tracker keys are preserved even when a displayed story title changes. Deferred stories remain backlog and are excluded by the additive deferred_scope map; do not interpret them as done. Ticket-tree migration is a separate task. The interface is agent-facing MCP without human screens.

## Requirements Inventory

### Functional Requirements

Current PRD definitions and dispositions govern; completed story acceptance remains historical. FR-37 is reserved.

- **FR-1 — File-only first-proof inputs:** Consume local Javadocs and approved development-task-relevant examples, workflow recipes, guide sections and curated practices. Lexical mode requires no model. Training import is unavailable and returns a clear unsupported-capability error. No crawler, login automation, model download or external egress occurs. Story 2.8/6.5 specifies the existing CLI flag transition explicitly; completed Story 1.2 history is unchanged.
- **FR-2 — Profile-aware resumable ingestion:** Preserve ordered catalog → relationships → selected examples/curated records → lexical index → validation → atomic activation and staging-only checkpoints. The exact/lexical manifest and report explicitly skip deferred embedding/vector stages. Resume verifies source/content and build-recipe hashes; changed inputs start a new build. Interrupted/resumed logical output agrees with uninterrupted output. Parsing queues and catalog/index batches are bounded and reported; release each AST after emitting records/evidence. Malformed sources follow fail-fast or collect-and-continue, but BUILD_INVALIDATING always blocks activation.
- **FR-3 — Portable identity, content hashes and versioned reports:** Freeze distinct purposes for stable record/edge identity, source/content hashes, build-recipe fingerprint and runtime-compatibility fingerprint in Story 2.8. Relevant logical content/recipe changes affect the appropriate digest; absolute source/output locations do not affect portable build identity. Version the report and include profile, coverage, skipped stages, severity/completion, throughput, errors, batch sizes and exact/ambiguous/unresolved resolution counts grouped by type/kind. Do not equate content hashes with identity fields or build recipe with runtime compatibility.
- **FR-4 — Search-index-assisted discovery with fallback:** Discovery prefers `*-search-index.js` files and falls back to recursive `*.html` scanning; page type is detected by structure, not filename; nav/tree/help/index pages are excluded from member ingestion; overview/package pages are kept as narrative records. `→ brief §7`
**Consequences (testable):**
- Given fixtures with and without search-index files, discovery finds the same API pages.
- Navigation-only pages produce no API-member records.
- **FR-5 — Modern + legacy layout parsing:** Both `section.class-description`/member-summary/`section.detail` and `ul.blockList`/`li.blockList`/`h3`/`h4` layouts parse into the same extracted fields (module, package, FQ type, kind, modifiers, type params, superclass, interfaces, known subclasses, description, fields, constructors, per-overload methods, param/return/throws, since/deprecated/see-also, source path + anchor). `→ brief §7`
**Consequences (testable):**
- Modern and legacy fixtures of the same class yield equivalent catalog records.
- Broken/incomplete HTML degrades gracefully under the configured policy.
- **FR-6 — One record per API element (overloads distinct):** Each semantic API element is exactly one primary retrieval record; unrelated APIs are never merged into fixed token windows; every method overload is a separate record. Long narrative may have secondary chunks that retain full symbol + source metadata. `→ brief §7`
**Consequences (testable):**
- Two overloads of the same method produce two records with distinct canonical symbols.
- No primary record spans more than one semantic API element.
- **FR-7 — Domain-owned versioned symbol coordinates:** Use one domain-owned versioned coordinate containing SDK/product, SDK version, language, namespace, owner/type, member, parameter types and element kind. Overloads remain distinct. Simple-name, dotted-member, erased-generic and camel-case aliases are lookup aids, never alternate identities. Freeze serialization and known-answer round trips in 2.8; preserve completed 2.3 history.
- **FR-8 — Versioned canonical storage and stable record identity:** SQLite persists unique version-scoped symbols and stable record IDs, including symbol-less content, using domain-owned identity recipes. Record/edge IDs never depend on row IDs, search document IDs, absolute paths or output location. Preserve source locators truthfully, including absent anchors. Story 2.8 freezes schema/build metadata, uniqueness and explicit migration or rebuild-only rejection of provisional stores; completed 2.4 is not reopened.
- **FR-9 — Two-pass typed relationships with explicit producer ownership:** Pass one builds the catalog and source locator map; pass two resolves references with the two specified inverse pairs reconciled. The eventual 17-type SDK/example/workflow family comprises 13 Javadoc structural types (2.5), WORKFLOW_REQUIRES (3.4), and USES_TYPE/CONSTRUCTS/CALLS (3.2). Test each at its producer. Parameter/return/field/exception types link only when resolvable. Curated-knowledge dependency relations receive a separate versioned typed contract in 2.8; do not append ad-hoc SDK enum values.
- **FR-10 — Explicit unresolved and ambiguous evidence:** Persist original token/locator, SDK scope, candidate IDs when applicable, status/kind and reason. External/undocumented references remain unresolved; competing matches remain ambiguous with no selected exact target. Evidence remains retrievable and never becomes a guessed edge. Partial records require valid provenance. Invalid required recipe dependencies block activation; ordinary contract-permitted incomplete source evidence does not.
- **FR-11 — Bounded typed traversal:** Direct/recursive traversal is cycle-safe, version-isolated and deterministically ordered. Enforce finite depth, visited-node/edge, result, execution and continuation budgets frozen in 2.8. Report scope, limits and truncation; bounded results never prove graph completeness. SDK traversal and explicitly requested knowledge-dependency traversal remain distinct. Test cycles, empty graphs and below/above-limit cases.
- **FR-12 — Example parsing preserves complete source (no execution):** `.java` examples are parsed with JavaParser AST; complete source is preserved **once**; the ingester emits **three levels of record — whole example, declared class/interface, and method/constructor**. Child (class/method) records are canonical SQLite rows linked to the parent example, each storing a `SourceRange` (relative path, start/end line+column) and the minimum source text needed for search and citation; Lucene holds method-sized projections. Examples are never compiled or run. `→ brief §8` *(Expanded 2026-08-25 Sprint Change Proposal — AD-30.)*
**Consequences (testable):**
- The complete original source is retrievable via bounded `get_record`.
- A method-sized search hit can fetch its complete parent example.
- Every class/method record and every source-derived edge carries a `SourceRange` and citation.
- No build or execution of any sample occurs during ingest (asserted by code review + absence-of-build test).
- **FR-13 — Luciad reference extraction & catalog linking:** Luciad imports and syntactically identifiable type/member references are resolved against the catalog after Javadocs are indexed, using a **fixed resolution ladder** (FQN literal → explicit import + exact match → local/same-package declaration → unique wildcard-import match → owner+name+signature/arity match → ambiguous/unresolved). Each resolved reference emits a typed edge (`USES_TYPE`/`CONSTRUCTS`/`CALLS`, AD-30) carrying **resolution evidence** (source range, original token, candidate canonical symbols, selected symbol only when status is `EXACT`). Only an `EXACT` match becomes a verified Luciad symbol; `AMBIGUOUS` and `UNRESOLVED` references stay explicit and are never promoted (AD-4, AD-7). `→ brief §8` *(Expanded 2026-08-25 Sprint Change Proposal — AD-30.)*
**Consequences (testable):**
- A symbol can retrieve its linked examples via `search_knowledge` / `get_relationships`.
- Two packages exposing the same simple class name stay `AMBIGUOUS` unless source context selects one exactly.
- Overloaded methods stay `AMBIGUOUS` when parameter info is insufficient.
- An incomplete example project still produces cited partial records without an SDK JAR or build execution.
- An unresolvable reference is recorded as unresolved, never fabricated.
- **FR-14 — Governance-supplied quality and approval:** Retain official/validated/best-practice/illustrative/legacy quality labels from governed input; filenames and parsers never infer quality. Vendor provenance alone establishes official facts, not approved practices. Practices require explicit approval/owner/provenance and SDK applicability. Missing sidecars imply neither label nor approval. Candidate and invalid approval records cannot enter active builds. Broad technique vocabulary is deferred and does not gate 3.3–3.6.
- **FR-15 — Compile workflow recipes and required dependencies:** Preserve prerequisites, ordered steps, limitations, linked examples and version metadata. Compile Workflow records with RECIPE kind, stable required knowledge IDs and explicit applicability. Resolve dependencies against compatible facts/approved practices; absent, incompatible or candidate required dependencies block activation. Freeze cycle policy in 2.8 and reject invalid graphs deterministically. Check applicability before recommendation; no duplicated versioned facts or second runtime recipe database.
- **FR-35 — Selected local guide sections:** Story 3.5 consumes an explicit allowlist of approved local guide pages/sections selected using development tasks. Preserve title, ordered selected prose/snippets, original source/section locator and content hash. Report selection/skips and coverage without claiming full-corpus ingestion. Malformed content follows the classified failure policy. Broad guide ingestion remains deferred.
- **FR-36 — Guide evidence and governance:** Resolve selected guide references against the version-bound catalog; retain exact/ambiguous/unresolved evidence and never guess. Vendor facts can be cited without invented quality labels; guide-derived engineering practices require separate human approval. Serve fully cited symbol-less sections through common tools.
- **FR-37 — (reserved — intentionally unassigned):** No requirement carries this number. The gap is intentional; FR numbering jumps FR-36 → FR-38 across the PRD, architecture spine, and epics, with no lost requirement.
- **FR-42 — Cross-cutting technique tags [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
*(Added 2026-08-21, Sprint Change Proposal.)* A curated, fixed vocabulary of coding-technique/best-practice labels (e.g. `resource-lifecycle`, `builder-pattern`, `listener-chaining`) is supplied only via the same governance sidecar mechanism as FR-14, attachable to `Example`, `Workflow`, `Guide`, and `Training` records — never `ApiElement`/Javadoc records — independent of whether the record has a resolved canonical-symbol link. Filename/code-shape/prose inference never assigns a technique tag; a tag value outside the published vocabulary is rejected at ingest, not silently accepted or coerced.
**Consequences (testable):**
- A record with no sidecar-supplied technique tags is retrievable with an empty tag set, never a guessed one.
- A record whose sidecar specifies a technique tag outside the curated vocabulary fails ingest validation with a structured error naming the offending value.
- A `Guide` or `Training` record with no resolved canonical-symbol link remains discoverable via its technique tags (see FR-43), closing the symbol-less-content discoverability gap.
- **FR-16 - Source-bound exact resolution:** Separate optional target SDK context from actual pinned source SDK/build. Missing target documentation does not block available-source retrieval, resolution or symbol checks. Exact proof establishes identity in the cited source only; ambiguity remains explicit and relevance never proves compatibility. Preserve exact lookup with no foreign-source attribution or silently selected overloads.
- **FR-17 — BM25 and scoped knowledge filters:** Index canonical/normalized exact, prefix and camel-case fields plus prose, params, returns, exceptions and selected example/workflow/guide/practice text. Support highlighting and SDK version, record type, knowledge kind, applicability, governance/provenance, package/kind/deprecation/quality filters. Stable record IDs identify and deduplicate all content, including symbol-less records. No model or technique vocabulary is required.
- **FR-18 — Local ONNX vector search with lexical fallback [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
Vector search runs over normalized semantic records using a locally-supplied ONNX model + tokenizer; the service never downloads a model; model-load failure is explicit; fallback to lexical occurs only when configured and is visible in `index_status`. `→ brief §9`
**Consequences (testable):**
- With no model present and fallback disabled, hybrid mode fails explicitly (does not silently degrade).
- `index_status` reports retrieval mode and model status accurately.
- **FR-19 — Bounded cited lexical result assembly:** Story 4.6 assembles exact/lexical candidates with governance/applicability/content-citation checks, deterministic stable-ID deduplication/order and bounded relationship enrichment. Filter-only browse is deterministic. Bound internal work as well as payload and expose exact/lower-bound/unknown counts, empty reason, truncation and continuation. Vector fusion stays deferred in 4.4, never marked completed by this capability transfer.
- **FR-20 — Fingerprint-consistent embedding [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
The same pinned model, tokenizer, preprocessing, normalization, dimensions, and query instructions are used for indexing and runtime queries; navigation/duplicate/boilerplate/empty content is excluded from embedding. The embedding fingerprint is a schema-level property, not per-record-type — adding the guide record type does not itself change the fingerprint definition (see NFR-14), only what content is embedded under it. `→ brief §9`
**Consequences (testable):**
- An index whose embedding fingerprint differs from the runtime model is rejected (see FR-27).
- Embedded content excludes navigation and empty records.
- **FR-21 — Knowledge search, symbol resolution and record fetch:** Expose search_knowledge, resolve_symbol and get_record. Search all eligible kinds with scoped filters and deterministic filter-only browse, returning stable IDs and available sections. Resolve returns exact/ambiguous/unresolved with version scope. Fetch supports bounded ID batches and optional sections with per-ID not-found/oversize outcomes. Never invent symbols or silently select an overload.
- **FR-22 — Typed relationships through one tool:** Expose get_relationships with edge-family/type, direction, depth and implementation/subclass filters, including concrete/abstract/deprecated/example-used/recipe-recommended context when supported. Enforce 2.7/2.8 budgets and return typed evidence/citations, scope and truncation. Distinguish SDK links from curated dependencies; no completeness claim beyond evidence or traversal budget.
- **FR-23 — Example and workflow capabilities through common tools:** Use search_knowledge, get_record and get_relationships for examples/workflows; expose no extra tool names. Preserve quality/provenance, linked symbols, parent/source retrieval and applicability. Full source is requested through bounded get_record; a method-sized hit can fetch its complete parent via sections/continuation within the response cap.
- **FR-24 — Bounded symbol checking and index status:** Expose check_symbols and index_status. Bounded batches return exact-valid, ambiguous-with-candidates or unknown for the bound SDK/build; neither unknown nor ambiguous is reported valid. Status reports active SDK/build/schema/fingerprint versions, counts/coverage, skips/errors, ingestion time, retrieval mode and available capabilities. No-model lexical operation is healthy. Neither tool compiles code.
- **FR-38 — Guide access through common tools; aliases deferred:** Selected guide search/fetch acceptance belongs to search_knowledge and get_record under 5.1/5.3. Return complete typed citations and bounded selected content, with not-found on unknown IDs. Dedicated search_guides/get_guide aliases (5.3b) are deferred, not exposed as placeholders.
- **FR-41 — Training tools [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_trainings` (natural-language/keyword query over the training-deck corpus, filterable, citation-bearing per FR-31) and `get_training` (fetch a full training record by stable ID with citation), both following the same structured-response conventions as the other retrieval tools.
**Consequences (testable):**
- `search_trainings` returns citation-complete results ranked by relevance to a conceptual query.
- `get_training` returns the full training record only via this explicit tool, never embedded inline elsewhere.
- **FR-43 — Concept/technique search tool [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_concepts` (query by one or more technique tags — FR-42 — across `Example`, `Workflow`, `Guide`, and `Training` records in one federated, deduplicated, citation-complete result list, following the same fused-list convention as hybrid retrieval's FR-19). An unrecognized tag value is rejected with the valid vocabulary listed, never silently ignored.
**Consequences (testable):**
- `search_concepts` returns a single fused list spanning all four taggable record types, each entry citation-complete per FR-31.
- A query naming an unrecognized technique tag returns a structured error listing valid tag values, never an empty or guessed result.
- **FR-25 — Six read-only tools with bounded contracts:** Streamable HTTP /mcp, optional stdio, shared versioned request/result/error codes and timeouts. Default page 10, hard cap 50, ≤1 MiB serialized response. Story 2.8 freezes finite query/input, ID/symbol batch, candidate, traversal, trace and time caps. Oversized content uses bounded sections/continuation or a structured size error. Cursors bind query/version/build and reject or explicitly restart stale scope. Agent inputs cannot accept credentials, host paths, endpoints, model/device settings or activation controls.
- **FR-26 — Shared validation and atomic activation:** One BuildValidator checks integrity, schema/version, identities, required dependencies and capability compatibility before publishing a sealed immutable build. BUILD_INVALIDATING outcomes, unknown/stub SDK versions, corrupt data, identity collisions and invalid required recipes reject activation. Legal unresolved/ambiguous source evidence remains explicit. Serialize competing writers and test interrupted staging/activation; active data is never mutated.
- **FR-27 — One compatibility policy on every load path:** CLI validation/activation and MCP startup/reload/rollback/restore use the same BuildValidator and rejection reasons. Unknown schemas/profiles or mismatched declared assets fail closed. Lexical requires no model; vector assets are checked only for an approved declared capability. A tested migration produces a separately validated new build; no bypass route.
- **FR-28 — Reader-safe rollback and isolated versions:** Retain current and previous builds where storage allows, preserve an explicit source default and pin each request to one immutable source/build handle. Revalidate current runtime compatibility before rollback; no deletion while readers reference a build. Invalid replacement keeps the valid current handle serving; invalid cold start fails clearly. Optional target context may differ from the pinned source and never implies a restrictive source filter. Explicit source filters remain possible; combined-source engines and cross-source edges remain deferred.
- **FR-29 — Manifest-complete backup and shared restore validation:** Back up manifest, catalog, Lucene, versioned report, configuration, checksums and all profile-declared assets together. Lexical explicitly declares no model and needs none; missing declared assets invalidate backup. Restore uses the shared BuildValidator and never trusts historical validation alone.
- **FR-30 — Six-tool agent guidance and external truth boundary:** Ship agent/SKILL.md using only the six actual names: retrieve → resolve → fetch context/relationships → generate → batch-check → external compile/test → repair. Preserve exact constructors/overloads, implementation lookup, cited approved examples and applicability checks, no memory-invented APIs, and honest unresolved/untested reporting. Include one worked display-a-point sequence, build on 5.8 and make guidance usable for 10.2 evaluation. The MCP service proves catalog membership/evidence, not compilation/runtime/rendering/non-Luciad dependencies.
- **FR-31 — Typed citations on every primary content result:** Require SDK scope, record type, stable record ID, source identity and truthful typed locator: HTML page/anchor, source range, curated document/section, and later deck/slide. Canonical symbol is present only when resolved; missing anchors are represented honestly, never fabricated. Citation completeness is 100%, including symbol-less practices/guides and dependency evidence. Do not filter exact/ambiguous/unknown verdicts for absent content citations; optional diagnostic provenance is separate.
- **FR-32 — Internal-host serving image and offline deployment:** One serving image provides ingest/validate-index/serve; Compose separates ingestion and read-only serving, pull_policy: never, non-root, read-only root FS where practical, cap-drop ALL, no-new-privileges and read-only proprietary inputs. Bind the shared internal host for a trusted team of about five or fewer, never public internet. Retain zero egress; no new authentication/TLS/tenant scope. First-proof assets require no ONNX model or Docling/training image; 8.4 remains deferred.
- **FR-33 — Capability-complete air-gap release bundle:** Bundle serving image tar, SHA256SUMS, SBOM, install documentation, frozen index and exactly the profile-declared assets with licenses. No model or training image is required for lexical. Verify tamper detection and clean networking-disabled load/ingest/serve smoke tests; document prebuilt transfer and approved in-gap rebuild. Existing legal/provenance/NFR/release approvals remain required, not passed by the proof.
- **FR-34 — Headless CLI and shared diagnostics:** Retain ingest, validate-index, serve and stats with documented flags and exit codes. CLI diagnostics use the same application query/proof services as MCP under operator-owned configuration and bounded output. Freeze diagnostic spelling/CLI transition in 2.8. Story 6.5 delivers non-success for build-invalidating ingest and report-write failure semantics early; 8.3 completes help/headless/release documentation.
- **FR-39 — Training-deck ingestion via docling [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
The ingester accepts a local training-materials dir (PPTX/PDF slide decks) as a sixth permitted input path. Training-deck ingestion is a single, explicitly-scoped exception to FR-1/AD-9 (zero egress) and AD-10 (parse-only): it invokes docling, which may reach the network to fetch/run vision-layout/table/OCR models, and executes non-Java tooling. This exception applies only within the separate training-ingest module/image (FR-32); no other ingest path or the `serve` path may perform network I/O or invoke non-Java tooling. Docling's structured output (slide/section title, ordered text blocks, tables, extracted figures/captions) is parsed into one `Training` record per logical deck section, mirroring FR-6's one-record-per-element discipline; malformed/unreadable decks degrade per the same `fail-fast | collect-and-continue` policy as FR-5.
**Consequences (testable):**
- Training-deck ingestion is the only scenario in the ingestion test suite permitted to observe network egress; every other ingestion test (Javadoc, examples, workflows, guides) still asserts zero egress.
- A malformed/unreadable slide-deck fixture is handled per the configured `fail-fast | collect-and-continue` policy, never crashing the run silently.
- A `Training` record is retrievable with its section title, ordered text blocks, and any extracted tables/figures intact.
- **FR-40 — Training reference extraction & catalog linking [DEFERRED]:** **Disposition (2026-10-02):** Historical intent below is retained for reactivation only, not first-proof acceptance. No placeholder capability is exposed. Reactivation requires a diagnosed task gap, controlled benefit, preserved safety metrics and owner approval; training also requires provenance/governance/version decisions.
Luciad symbol/API references appearing in training-deck text and tables are resolved against the catalog using FR-7's canonical/normalized keys, run only after Javadocs are indexed; unresolved references stay explicitly unresolved per FR-10.
**Consequences (testable):**
- A resolvable reference in a training deck's text or table links to its catalog record.
- An unresolvable reference is recorded as unresolved, never fabricated.
- **FR-44 — Structured SDK identity and frozen portable contracts:** Structured SDK identity and frozen portable contracts. Versioned known-answer symbol/record/edge/build recipes; stable IDs unchanged by output-root relocation; unknown SDK version cannot activate.
- **FR-45 — Governed compiled knowledge:** Governed compiled knowledge. Four knowledge kinds; schema-validated deterministic authoring; candidates outside active builds; practices require approval; required recipe dependencies resolve compatibly; inapplicable recipes are not recommended.
- **FR-46 — Shared build validation:** Shared build validation. All startup/load/activation/reload/restore routes use one validation policy and return equivalent rejection reasons for the same invalid artifacts.
- **FR-47 — Honest bounded interface:** Honest bounded interface. Six read-only tools share explicit proof, route, coverage, empty reason and continuation semantics; no bounded query claims exhaustive results without proof.
- **FR-48 — Controlled outcome evaluation and governed improvement:** Controlled outcome evaluation and governed improvement. Predeclared paired treatments, held-out protection and cost reporting; every accepted correction has human approval and a permanent regression before activation.
- **FR-49 — Contract-change and verification gates:** Contract-change and verification gates. Schema/format changes have version/changelog or reviewed exemption; Windows/Linux known answers and selected mutations demonstrate critical tests can fail.
- **FR-50 — Shared application services:** Shared application services. CLI diagnostics and MCP produce equivalent logical proof/coverage results for identical build/query/scope, excluding transport-specific wrapping. Neither compiles generated code; evaluation does so externally.

### NonFunctional Requirements

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

### Additional Requirements

- **AD-1:** Dependencies point inward through domain ports. See the Architecture Spine for the binding rule and capability disposition.
- **AD-2:** Embedded engines only; single internal-host listener `[ADOPTED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-3:** Versioned symbols and stable record IDs have distinct roles. See the Architecture Spine for the binding rule and capability disposition.
- **AD-4:** Two-pass ingestion; never fabricate `[ADOPTED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-5:** Immutable index builds; validate-then-atomic-activate `[ADOPTED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-6:** Capability-specific compatibility on every load. See the Architecture Spine for the binding rule and capability disposition.
- **AD-7:** Exact evidence cannot be replaced by relevance. See the Architecture Spine for the binding rule and capability disposition.
- **AD-7a:** Separate optional target SDK context from one actual pinned source/build. See the Architecture Spine for the binding rule and capability disposition.
- **AD-8:** Identical embedding fingerprint at index-time and query-time `[DEFERRED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-9:** Zero external egress on all active paths. See the Architecture Spine for the binding rule and capability disposition.
- **AD-10:** Parse-only knowledge service; external compile/test. See the Architecture Spine for the binding rule and capability disposition.
- **AD-11:** Bound requests, internal work and responses. See the Architecture Spine for the binding rule and capability disposition.
- **AD-12:** SDK and build scope are mandatory. See the Architecture Spine for the binding rule and capability disposition.
- **AD-13:** Bounded concurrent stateless serving. See the Architecture Spine for the binding rule and capability disposition.
- **AD-14:** Governance is explicit input. See the Architecture Spine for the binding rule and capability disposition.
- **AD-15:** Typed citations on all primary content. See the Architecture Spine for the binding rule and capability disposition.
- **AD-16:** Single-file config; redacted logging. See the Architecture Spine for the binding rule and capability disposition.
- **AD-17:** One coordinator and portable build recipe. See the Architecture Spine for the binding rule and capability disposition.
- **AD-18:** Stable record IDs join content; symbols link APIs. See the Architecture Spine for the binding rule and capability disposition.
- **AD-19:** Bounded retrieval pipeline and truthful counts. See the Architecture Spine for the binding rule and capability disposition.
- **AD-20:** Deterministic ordering and continuation. See the Architecture Spine for the binding rule and capability disposition.
- **AD-21:** Text preprocessing lives once, behind the Embedder interface. See the Architecture Spine for the binding rule and capability disposition.
- **AD-22:** Domain owns identity; storage enforces uniqueness. See the Architecture Spine for the binding rule and capability disposition.
- **AD-23:** Validate handles before atomic publication. See the Architecture Spine for the binding rule and capability disposition.
- **AD-24:** Six tools distinguish content from verdicts. See the Architecture Spine for the binding rule and capability disposition.
- **AD-25:** Checkpointed ingestion writes to build-scoped staging only. See the Architecture Spine for the binding rule and capability disposition.
- **AD-26:** Lexical is healthy primary operation. See the Architecture Spine for the binding rule and capability disposition.
- **AD-27:** Container baseline posture is a build/packaging invariant `[ADOPTED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-28:** Training-material ingestion is a scoped, explicit exception to AD-9/AD-10 `[DEFERRED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-29:** Technique vocabulary is deferred metadata. See the Architecture Spine for the binding rule and capability disposition.
- **AD-30:** Structure-aware example ingestion: refined usage edges, evidence-bearing resolution, bounded graph scope `[ADOPTED]`. See the Architecture Spine for the binding rule and capability disposition.
- **AD-31:** Knowledge trust boundary. See the Architecture Spine for the binding rule and capability disposition.
- **AD-32:** Contract and verification freeze. See the Architecture Spine for the binding rule and capability disposition.
- **AD-33:** Single build-validation policy. See the Architecture Spine for the binding rule and capability disposition.
- **AD-34:** Evaluation and governed improvement. See the Architecture Spine for the binding rule and capability disposition.
- **AD-35:** Six-tool interface and progressive disclosure. See the Architecture Spine for the binding rule and capability disposition.
- **AD-36:** Shared services and external execution boundary. See the Architecture Spine for the binding rule and capability disposition.

**Delivery gates and ordering:** Finish/reconcile Story 2.5 review without changing its frozen intent; 2.6/2.7 then 2.8. Define 10.1 and protect held-out tasks before corpus curation/tuning. Next exact/lexical 4.1/4.2/4.6, minimum shared lifecycle 6.1/6.2/6.5 and common envelope 5.7; then individual handlers/transport 5.1–5.6, selected corpus 3.1–3.6 and guidance/citations 5.8/7.1/7.2. Fixtures may unblock parallel contract development; they cannot establish lifecycle or outcome acceptance.

**Open approvals:** B1 hardware/corpus before NFR sign-off; B2 provisional 90% retrieval diagnostic and B6 task success/improvement/repeat/access policy frozen by 10.1; B3 corpus/legal/distribution; B4 named owners; B5 formal Java 21 pin. SQLite, identities, lexical Lucene, MCP schemas and finite budgets require 2.8 approval before dependent work. Model/KNN/license/training/vocabulary gates stay deferred. Retention/recovery contracts belong to 2.8/6.x.

**Verification:** Synthetic modern/legacy Javadoc, generics/arrays/overloads, source ranges and broken HTML; exact/ambiguous/unresolved evidence; typed producer-owned relationships; cycles and wide-graph limits; symbol-less citations; source isolation and differing-target/unknown verdicts; deterministic IDs/order/cursors. Keep AD-30 method-inside-large-example, constructor/call sequence, local-helper, ambiguous-name/overload and missing-dependency comparison cases. Selective hashing/identity/version/ambiguity/relationship-resolution/activation mutations must fail tests. Windows/Linux known answers agree on logical symbols/IDs/catalog/manifests/reports. Test interruption, torn writes, competing writers, reload during requests and just-below/above-limit values. Contract/schema changes require version/changelog or reviewed exemption; separate contract/parser/storage/resolver/wiring/docs changes where practical. Proprietary source data is never committed.

**First-proof Definition of Done:** 100% exact fixtures/cited primary content; zero unknown-as-valid, silent overload selection, foreign-source leakage, candidate/unapproved active knowledge or invalid required recipe dependencies. Shared validation rejects corrupt/incompatible builds on all entry routes; bounded work/output and honest incomplete evidence. External controlled agent-outcome improvement meets the 10.1 predeclared decision rule, with failures/uncertainty/costs reported. No result is claimed here.

**Release Definition of Done:** Proof evidence plus complete rollback/backup lifecycle, offline/internal-host packaging, licenses/SBOM/checksums, approved NFR results and human release activation sign-off. Preserve product/technical/corpus/security-legal/release roles; no gate auto-closes. Deferred vector/training/alias scope is not a release prerequisite unless separately reactivated.

### UX Design Requirements

None. The PRD (§2.2, §9 assumptions) confirms the only consumer interface is the MCP tool surface consumed by a coding agent — there is no human UI, and a UX design contract was intentionally skipped `[NEEDS CONFIRM in PRD, treated as accepted for this breakdown]`.

### FR Coverage Map

| Existing/new requirements | Disposition and backlog owner |
|---|---|
| FR-1–3 | Amend proof profile/identity/report; completed Epic 1 history preserved, forward work 2.8/6.x. |
| FR-4–6 | Retain Javadoc parsing/overload commitments and completed history; 2.8 may add forward provenance/contract changes. |
| FR-7–8 | Extend/freeze identity and storage via 2.8. |
| FR-9 | Retain total relation family; correct producer ownership under 2.5/3.2/3.4. |
| FR-10–11 | Extend explicit evidence and bounded traversal under 2.6/2.7/2.8. |
| FR-12–15 | Retain structure-aware examples/governance; extend compiled recipes under 3.1–3.4. |
| FR-16–17 | Required exact/BM25 core, 4.1/4.2. |
| FR-18/20 | Deferred vector capability, 4.3/4.5. |
| FR-19 | Split required bounded lexical/enrichment 4.6 from deferred vector fusion 4.4. |
| FR-21–25 | Six-tool contract and common capabilities, 5.1–5.7. |
| FR-26–29 | Retain/strengthen lifecycle, 6.1–6.4. |
| FR-30–31 | Update policy/typed citations, 5.8/7.1/7.2. |
| FR-32–34 | Retain internal-host release, remove mandatory deferred assets; 6.5 and 8.1–8.3. Training image under 8.4 deferred. |
| FR-35–36 | Narrow to selected guide sections, 3.5; full corpus deferred. |
| FR-37 | Reserved, never assigned. |
| FR-38 | Guide capability in common tools; dedicated aliases 5.3b deferred. |
| FR-39–40 | Training 9.1/9.2 deferred. |
| FR-41 | Training tools 5.3c deferred. |
| FR-42–43 | Broad tags/concept tools deferred; retain symbol-less discovery via common tools. |
| FR-44–50 | New contracts/governance/validation/evaluation/engineering/services; exact coverage in P06. |

| Requirement | New requirement with testable consequence | Coverage |
|---|---|---|
| FR-44 | Structured SDK identity and frozen portable contracts. Versioned known-answer symbol/record/edge/build recipes; stable IDs unchanged by output-root relocation; unknown SDK version cannot activate. | 2.8; AD-3/12/17/18/22/32 |
| FR-45 | Governed compiled knowledge. Four knowledge kinds; schema-validated deterministic authoring; candidates outside active builds; practices require approval; required recipe dependencies resolve compatibly; inapplicable recipes are not recommended. | 2.8, 3.3/3.4/3.6, 6.1; AD-14/31 |
| FR-46 | Shared build validation. All startup/load/activation/reload/restore routes use one validation policy and return equivalent rejection reasons for the same invalid artifacts. | 2.8, 5.6, 6.1–6.5; AD-5/6/23/33 |
| FR-47 | Honest bounded interface. Six read-only tools share explicit proof, route, coverage, empty reason and continuation semantics; no bounded query claims exhaustive results without proof. | 2.8, 4.6, 5.1–5.7, 7.2; AD-11/15/19/20/24/35 |
| FR-48 | Controlled outcome evaluation and governed improvement. Predeclared paired treatments, held-out protection and cost reporting; every accepted correction has human approval and a permanent regression before activation. | 10.1–10.4; AD-34 |
| FR-49 | Contract-change and verification gates. Schema/format changes have version/changelog or reviewed exemption; Windows/Linux known answers and selected mutations demonstrate critical tests can fail. | 2.8 and affected 6.x/10.x implementation; AD-32/34 |
| FR-50 | Shared application services. CLI diagnostics and MCP produce equivalent logical proof/coverage results for identical build/query/scope, excluding transport-specific wrapping. Neither compiles generated code; evaluation does so externally. | 2.8, 6.5, 7.1, 10.2; AD-1/36 |



### Requirement-to-decision coverage

| Requirements | Components | Decisions / acceptance owners |
|---|---|---|
| FR-1–3 | cli / domain / ingest | AD-4/5/9/10/17/25/32; forward 2.8/6.x, completed Epic 1 preserved |
| FR-4–8 | Javadoc / catalog / domain | AD-3/4/12/18/22/32; 2.1–2.4 history, 2.8 forward contracts |
| FR-9–11 | relationship ports / SQLite | AD-4/11/12/30/32; 2.5–2.8, producers 3.2/3.4 |
| FR-12–15/35/36 | examples / selected guides / curated authoring | AD-10/14/15/30/31; 3.1–3.6 |
| FR-16/17/19 | retrieval / Lucene / domain | AD-7/7a/11/18/19/20/26; 4.1/4.2/4.6 |
| FR-18/20 | deferred ONNX/vector | AD-8/21/26; deferred 4.3–4.5 |
| FR-21–25/38 | six-tool MCP / shared services | AD-11/15/19/24/35; 5.1–5.8, guide aliases deferred |
| FR-26–29 | cli / shared validator / immutable handles | AD-5/6/12/17/22/23/25/33; 6.1–6.4 / 5.6 |
| FR-30/31 | guidance / content assembly | AD-15/24/35/36; 5.8/7.1/7.2 |
| FR-32–34 | packaging / cli / config | AD-2/9/16/27/36; 6.5/8.1–8.3, 8.4 deferred |
| FR-39–43 | deferred training / broad tags / aliases | AD-28/29; 9.x, 5.3c/d; FR-37 reserved |
| FR-44 | identity/contracts | AD-3/12/17/18/22/32; 2.8 |
| FR-45 | governed knowledge | AD-14/31; 2.8/3.3/3.4/3.6/6.1 |
| FR-46 | one BuildValidator | AD-5/6/23/33; 2.8/5.6/6.1–6.5 |
| FR-47 | honest bounded interface | AD-11/15/19/20/24/35; 2.8/4.6/5.1–5.7/7.2 |
| FR-48/49 | evaluation / critical verification | AD-32/34; 2.8 / 10.1–10.4 / affected 6.x |
| FR-50 | shared services / external execution | AD-1/36; 2.8/6.5/7.1/10.2 |
| NFR-1–14 | active-profile services / packaging | Unchanged numeric targets except approved scope amendments in PRD P07; B1 evidence remains open. NFR-3 bounded queues, NFR-13 portable logical results, NFR-14 profile compatibility. |

## Epic List

## Approved SDK guidance amendment — 2026-10-21

Applies prospectively to Stories 2.9–2.11, 4.1/4.2/4.6, the six 5.x read tools and 6.x source/build selection. read-tools/2 policy/contracts are implemented; retrieval/MCP integration remains downstream. Optional user target SDK is guidance context, never an automatic restrictive source filter. Choose one configured usable source deterministically within finite request budgets, respecting explicit source/build filters and immutable pinning. Default guidance is useful normal content with actual source-version citations, including target 2023.1/source 2023.5. No mismatch-only UNKNOWN_SDK, INAPPLICABLE, negative existence claim or automatic UNVERIFIED warning. Only concrete, cited target-relevant introduction/removal/behavior-change evidence can support a specific notice; labels/proximity/absence cannot. GENERAL applicability is explicit and retains governance; exact source proof does not certify tested target compatibility. Preserve ambiguity and honest source limitations, source-specific IDs, same-source graph edges and corrupt-build rejection. The user approved this policy; all other technical sign-offs remain pending. Completed-story frozen intent remains historical and unchanged.

### Epic 1: Foundation & Domain Contracts

Completed-story text below is historical acceptance, preserved verbatim. Forward profile/contract changes belong to 2.8/6.x.

Completed 1.1–1.4 remain unchanged; epic status remains in-progress. New profile/identity/report gaps are forward work in 2.8/6.x.

### Epic 2: Javadoc Knowledge Extraction and Contract Freeze

Stories 2.1–2.4 and the existing 2.5 summary remain unchanged. The detailed 2.5 story freezes 13 structural types and two inverse pairs; its review/status reconciliation remains open. The prospective producer correction in the overview does not expand or complete it.

Preserve 2.1–2.4 completion and current 2.5 intent/status. Producer accounting is 13 Javadoc structural types + WORKFLOW_REQUIRES (3.4) + three example types (3.2), 17 total. Add explicit evidence, bounds and 2.8 freeze. The old 2.5 summary below remains historical pending its reviewer; do not apply its obsolete 17-type wording to current delivery.

### Epic 3: Selected Examples, Recipes, Guides and Practices

After 2.8/10.1, retain structure-aware examples, narrow guides to allowed sections and compile governed Workflow recipes/practices. Broad guide/tag scope deferred.

### Epic 4: Exact and Lexical Retrieval; Gated Semantic Enhancement

4.1/4.2/4.6 deliver exact/BM25 plus bounded cited enrichment; 4.3–4.5 remain deferred backlog.

### Epic 5: Six-Tool MCP Surface

Exactly search_knowledge, resolve_symbol, get_record, get_relationships, check_symbols, index_status. 5.7 precedes handlers; 5.6 depends on shared validator and minimum 6.1/6.2/6.5. No fixed-pointer bypass proof. 5.3b/c/d deferred; guidance starts in 5.8.

### Epic 6: Shared Validation and Index Lifecycle

Minimum validation/atomic activation/conformance precedes proof; rollback/backup completion remains release work. Shared BuildValidator on every load path.

### Epic 7: Agent Guidance & Usability

Complete six-tool guidance and typed citations before outcome evaluation, including external compile/test/repair.

### Epic 8: Air-Gap Packaging & Release

Retain shared trusted internal host, zero egress and hygiene; manifest-complete lexical release assets. 8.4 training image deferred. All NFR/legal/release sign-offs remain.

### Epic 9: Training Material Knowledge [DEFERRED]

Epic and all three stories excluded from scheduling until training benefit/governance/version gates clear; original backlog statuses preserved.

### Epic 10: Agent Outcome Evaluation

10.1 protocol before curation; 10.2 external controlled harness; 10.3 outcome decision; 10.4 human-governed corrections and permanent regressions.

## Epic 1: Foundation & Domain Contracts

### Story 1.1: Scaffold domain model and module skeleton

As a developer,
I want the domain records (API element, Example, Workflow, Guide, Training, Relationship) and the Gradle multi-project tree (`domain`, `config`, `ingest.javadoc`, `ingest.examples`, `ingest.guides`, `ingest.trainings`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx`, `retrieval`, `mcp`, `cli`) scaffolded with dependencies pointing inward per AD-1,
So that later epics implement real logic against stable ports instead of inventing types and module boundaries as they go.

**Acceptance Criteria:**
**Given** an empty repository
**When** the skeleton is scaffolded
**Then** all twelve modules exist with the dependency direction enforced (no module reaches an engine except through a domain-defined port)
**And** the project builds with no implementation code beyond empty port interfaces and record definitions
**And** no external starter template is used (brief confirms none applies)
**And** `ingest.trainings` is scaffolded like every other module at the source level, but its packaging (Epic 8/9) ships it only in a separate, ingest-time-only image, never the serving image (AD-28)

### Story 1.2: Local-only, egress-free `cli ingest` entrypoint

As a release operator,
I want `cli ingest` to accept only six local paths (Javadoc dir, samples/workflows dir, guides dir, training-materials dir, model dir, output dir) and nothing else,
So that an air-gapped rebuild cannot accidentally reach the network, except for the one explicitly-scoped training-materials exception owned by Epic 9.

**Acceptance Criteria:**
**Given** `cli ingest` invoked with the six local paths, excluding the training-materials dir
**When** the process runs
**Then** it performs zero DNS/HTTP/socket calls, verified by a test harness that fails the build on any attempted egress
**And** no crawler, portal client, login automation, or model-downloader code path exists in the runtime image, outside the training-materials path
**And** any other argument or flag is rejected with a clear CLI error, not silently ignored
**And** invoking `cli ingest` against the training-materials dir is the sole path permitted network access and non-Java tooling execution (AD-28), and only from the separate ingest-time image (Epic 9)

### Story 1.3: Stub the multi-stage resumable pipeline with a configurable failure policy

As a release operator,
I want the eight-stage pipeline (catalog → relationships → examples/workflows → lexical index → embeddings → vector index → validation → atomic activation) stubbed with checkpoint/resume support and a `fail-fast | collect-and-continue` policy,
So that Epic 2 onward fill in real stage logic without redesigning orchestration, and long embedding runs can resume after interruption.

**Acceptance Criteria:**
**Given** the pipeline skeleton with stubbed stages
**When** a run is interrupted after an earlier stage and restarted
**Then** it resumes from the last completed stage rather than restarting from scratch
**And** setting the policy to `collect-and-continue` lets a stubbed stage record a failure and continue rather than aborting
**And** setting `fail-fast` aborts the run on the first stage failure

*(Expanded 2026-08-25 Sprint Change Proposal — AD-30; the discipline below applies once the examples/workflows stage is filled in.)*
**And** file parsing uses bounded work queues; a completed AST is released after its records and unresolved references are emitted
**And** SQLite and Lucene writes use bounded batches whose sizes are fixed/configured and recorded in the ingestion report
**And** restart state records completed stages and validates input hashes plus the toolchain fingerprint before reusing prior work (AD-25)
**And** a failure in one malformed optional example is recorded with its file and stage and may allow the run to continue under `collect-and-continue`
**And** a failure affecting catalog integrity, index compatibility, a required manifest, or final validation invalidates the build and blocks activation (AD-5)
**And** a restarted build produces the same records, edges, and manifest as an uninterrupted build from the same inputs and toolchain (NFR-13)
**And** no restart or fallback path performs a network request (AD-9; `ingest.trainings` exception unchanged)
*(Note: "discover inputs" and "pre-scan symbols" are example-scoped sub-steps inside the existing examples/workflows stage, not new top-level pipeline stages — the eight-stage shape is unchanged.)*

### Story 1.4: Deterministic IDs, content hashes, and a machine-readable ingestion report

As a release operator,
I want stable record IDs and content hashes plus a JSON ingestion report (throughput, error summary) emitted at the end of a run,
So that re-ingesting the same inputs is verifiably reproducible and a failed run is diagnosable without reading logs by hand.

**Acceptance Criteria:**
**Given** two ingestion runs over identical input content
**When** both complete
**Then** every record ID and content hash is identical between runs
**And** a JSON report is written summarizing stage throughput and any collected errors
**And** changing one input byte changes only the hashes of records that content actually touches, not unrelated records

*(Expanded 2026-08-25 Sprint Change Proposal — AD-30; five distinct hashes mapped onto existing invariants, no parallel machinery invented.)*
**And** five hashes are defined and tested: `sourceHash` (input-file bytes, new), `recordHash` (canonical record = AD-22 identity fields), `relationshipHash` (SDK version + source ID + rel type + target ID + evidence = AD-22 identity fields), `semanticHash` (exact normalized embedder input = AD-8/AD-21 embedding fingerprint), `toolchainFingerprint` (parser + schema + normalizer + relationship-rules + embedding fingerprint = AD-6 compatibility fingerprint)
**And** a change to parser, schema, normalization, resolution rules, or embedding config changes the relevant fingerprint and blocks unsafe reuse
**And** the ingestion report includes counts for exact, ambiguous, and unresolved example relationships, grouped by relationship type and resolution kind
**And** SQLite uniqueness constraints prevent duplicate records and edges even if the same source root is discovered twice
**And** cross-build incremental indexing is out of scope for this story; hashes support restart safety now and may support incremental builds later

## Epic 2: Javadoc Knowledge Extraction and Contract Freeze

### Story 2.1: Discover and classify Javadoc pages

As a developer,
I want the ingester to use `*-search-index.js` where present, fall back to recursive `*.html` discovery, and classify each page by structure rather than filename,
So that nav/tree/help/index pages are excluded from member ingestion while overview/package pages are kept as narrative records.

**Acceptance Criteria:**
**Given** a Javadoc tree with a search index plus a synthetic fixture that lacks one
**When** discovery runs on each
**Then** both produce the same set of classified pages
**And** nav/tree/help/index pages are excluded from member extraction
**And** overview/package pages are retained as narrative records, not member records

### Story 2.2: Parse modern and legacy Javadoc layouts into equivalent fields

As a developer,
I want both modern (`section.class-description`/member-summary/`section.detail`) and legacy (`ul.blockList`/`li.blockList`/`h3`/`h4`) layouts to extract equivalent fields, with broken/incomplete HTML degrading per the configured policy,
So that the catalog doesn't depend on which Javadoc generator produced the source tree.

**Acceptance Criteria:**
**Given** synthetic fixtures for both layout families covering the same API surface
**When** each is parsed
**Then** the extracted fields (description, params, returns, throws, since, deprecated) match between layouts
**And** a fixture with broken/incomplete HTML is handled per the `fail-fast | collect-and-continue` policy from Story 1.3, never crashing the run silently

### Story 2.3: Build canonical symbol records with overload distinction

As a developer,
I want one record per API element — never merging overloads — with canonical symbol keys plus normalized simple-name, dotted-member, erased-generic, and camel-case lookup variants,
So that a query for one overload never silently returns another.

**Acceptance Criteria:**
**Given** a class with multiple overloaded methods
**When** parsing completes
**Then** each overload is a distinct record with a distinct canonical symbol key
**And** all four normalized variants are retained per record for lookup
**And** no primary record spans more than one semantic API element

### Story 2.4: Persist the versioned canonical catalog

As a developer,
I want canonical symbols stored in a versioned SQLite catalog with unique version-scoped keys, mapped to their relative HTML paths and anchors,
So that the catalog is queryable and reproducible across SDK versions without collisions.

**Acceptance Criteria:**
**Given** two SDK versions ingested into the same catalog
**When** the same simple name exists in both
**Then** each version's record has its own version-scoped unique key with no cross-version collision
**And** each record's relative HTML path/anchor is stored and resolvable back to source

### Story 2.5: Construct the typed relationship graph in two passes

As a developer,
I want pass one to build the catalog and path/anchor map, and pass two to resolve links/declarations into all 17 required typed relationship edges with forward/inverse pairs reconciled,
So that inheritance, implementation, and reference relationships are queryable as first-class typed data, not re-derived from HTML at query time.

**Acceptance Criteria:**
**Given** a fixture exercising every one of the 17 relationship types
**When** both passes complete
**Then** all 17 types are present in the graph
**And** every forward edge has its correct inverse edge, and vice versa

The 17 required relationship types (brief §6, refined by AD-30): `EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`, `IMPLEMENTED_BY`, `DIRECT_SUBCLASS`, `RETURN_TYPE`, `PARAMETER_TYPE`, `THROWS_TYPE`, `FIELD_TYPE`, `CONSTRUCTOR_PARAMETER_TYPE`, `OVERRIDES`, `SPECIFIED_BY`, `SEE_ALSO`, `WORKFLOW_REQUIRES`, `USES_TYPE`, `CONSTRUCTS`, `CALLS`. The last three refine the former coarse `EXAMPLE_USES` (AD-30); the Javadoc two-pass here produces the 14 structural + workflow types, while `USES_TYPE`/`CONSTRUCTS`/`CALLS` are emitted by example ingestion (Story 3.2). *(15→17, 2026-08-25 Sprint Change Proposal.)*

### Story 2.6: Keep unresolved references explicit, scope traversal to the documented SDK

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Persist original token/locator, SDK scope, candidate IDs where applicable, resolution status/kind and reason. Missing/external references remain explicitly unresolved; competing matches remain ambiguous with no selected exact target. Query consumers can retrieve this evidence. A partial record remains usable only if its required provenance is valid. Required recipe dependency failures are distinguished from ordinary unresolved source references. No code outside indexed evidence resolves a guess.

**Dependencies / gates:** 2.5 review completion/status reconciliation; 2.8 ratifies common contracts.

**Coverage:** FR-10/44/47; AD-4/12/32.

### Story 2.7: Bounded, cycle-safe relationship traversal

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Retain both criteria; add finite visited-node/edge, result and execution budgets, deterministic ordering, explicit direct/recursive scope, version isolation and truncation/continuation. Report that bounded traversal does not prove graph completeness. Test cycle, empty, maximum and above-maximum boundary cases. 2.8 ratifies the common limits rather than leaving per-tool variants.



Cycle-safe recursive traversal terminates within depth and visits no node more than once. Direct queries are one hop.

**Dependencies / gates:** 2.5 review; 2.6 evidence; limits ratified by 2.8.

**Coverage:** FR-11/47; AD-11/12/19/32.

### Story 2.8: Contract and Verification Freeze

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

1. One structured symbol coordinate/serialization, stable record/edge ID recipes, and separate content/build-recipe/runtime-compatibility hashes have documented known-answer fixtures. Reordering unrelated inputs or relocating roots does not alter logical identities; relevant content/recipe changes do.
2. One versioned schema covers records, SDK identity, source/typed locators, unresolved evidence, relationship families, knowledge kinds, governance/applicability/dependencies and build metadata. Existing provisional stores get an explicit compatible migration or rebuild-only rejection policy.
3. Record type and knowledge kind are separate. Candidate learning is excluded from active builds; practice approval and required recipe dependencies are validated. Scope/version ambiguity is never resolved by guessing.
4. One manifest/report contract declares corpus coverage, modes, skipped stages, severity, SDK version and capability-specific fingerprints. Stub/unknown SDK versions and build-invalidating results prevent activation/success.
5. Exact request/result/error/count/pagination/trace/batch/traversal contracts and finite defaults/caps are reviewed. Cursors are bound to query/version/build. Six tool names and primary-content vs verdict behavior are enumerated.
6. Domain ports own semantics; bootstrap wires adapters. One shared `BuildValidator` contract defines CLI/startup/activation/reload/rollback/restore behavior and distinguishes legal unresolved evidence from invalid identity/dependencies.
7. Selected mutations of hashing, identity, version scope, ambiguity, relationship resolution and activation policy are detected by critical tests; use fast synthetic screening and a controlled task suite for architecture claims. Windows/Linux fixtures agree on logical symbols/IDs/catalog/manifests/reports, excluding explicitly operational timestamps/absolute diagnostic locations.
8. Contract-impact changes have version/changelog entries or explicit reviewed exemptions. Required schema/identity/tool sign-offs are recorded; no false auto-approval.
9. Deliver reviewable sub-tasks for identity, schema, public contracts and verification. Do not add vectors, Docling, new databases or generic multi-SDK infrastructure to satisfy this story.

**Dependencies / gates:** 2.6/2.7; 10.1 informs acceptance/corpus boundaries; technical schema/identity/interface approvals required.

**Coverage:** FR-1–3/7–11/44–47/49/50; AD-31–36.

### Source-content delivery amendment (2026-10-21)

Decision: preserve the existing epics/story IDs and legacy file-backed tracker. The user authorized folding the product-quality proposal into current work; no second ticket tree, store migration, new epic or runtime redesign is introduced. Completed 2.1–2.7 acceptance remains historical. Story 2.8 defines the contracts below; 2.9–2.11 implement them. Technical sign-offs remain explicit and pending.

Story 2.8 must define the durable documentation payload and its ordered sections/blocks, original links, actual/absent anchors, narrative identity, content hashes, bounded storage/read operations and page/section coverage dispositions. Define searchable text as a projection of retained content, not its replacement. Do not implement all ingestion/search functionality inside the freeze. See [contract input](../implementation-artifacts/story-2-8-source-content-contract-input.md).

### Story 2.9: Persist source-faithful API documentation

As a user, I want extracted API explanations and constraints to survive ingestion and restart, so answers can use the documentation rather than only symbol names.

**Outcome:** A thin type/member fixture passes through extraction, domain ports, SQLite and record fetch with its documentation attached to the correct stable ID. The same harness is reused by 2.10/2.11.

**Acceptance:** Given modern/legacy types and overloads, when ingested and the store reopened without the original input directory, then signatures, descriptions, params/type params, returns, exceptions, since/deprecation and source locators are recoverable without cross-version leakage. Content and its record association commit atomically. Prose-only edits change content hashes rather than unaffected symbol IDs; relocated roots preserve logical identity. Unsupported schema opens fail before mutation. Tests include rollback and partial-record handling.

**Dependencies / gates:** Ratified 2.8 payload/identity/schema/locator/limits; no implicit technical approval. **Owner:** implementation via `bmad-build`; schema decisions remain with 2.8's named technical approvers.

**Coverage:** FR-5/6/8/44/47; AD-15/20/22/26. [Build intent](../implementation-artifacts/2-9-persist-source-faithful-api-documentation.md).

### Story 2.10: Preserve structured Javadoc and narrative content

As a user, I want code, ordered instructions, caveats and package introductions preserved, so retrieved content retains the meaning needed to use an API correctly.

**Acceptance:** Given multiline code, lists/tables, several owned description blocks, API/implementation notes and inline links, when extracted/stored/reopened, then code whitespace, order/nesting, table headers/cells, note labels and original destinations survive. Package/overview pages become cited symbol-less narrative records, not fabricated API types. Navigation and duplicated member summaries are excluded. External/escaping hrefs remain evidence without fetching or verified local edges; absent anchors remain absent. Add representative modern/legacy golden fixtures to 2.9's roundtrip harness.

**Dependencies / gates:** 2.9; consume the same approved content/locator contracts rather than define a second format. **Owner:** ingestion implementation via `bmad-build`.

**Coverage:** FR-4/5/6/8/47; AD-4/15/26. [Build intent](../implementation-artifacts/2-10-preserve-structured-javadoc-and-narrative-content.md).

### Story 2.11: Account for ingestion coverage and fidelity

As a user, I want an honest account of what was ingested or lost, so an apparently successful build cannot conceal missing documentation.

**Acceptance:** Given complete/partial/unsupported/malformed/oversized inputs, when ingestion ends, then discovered-page counts reconcile with explicitly excluded, complete, partial and failed dispositions; emitted/persisted records/sections reconcile under documented counting rules. Do not equate a member count with a page count or claim completeness from bounded inspection. Limits are enforced before unbounded materialization. End-to-end cases prove restart/source-directory independence, rollback/retry determinism and preserved structure/citations. A local optional private-corpus audit checks an independently sampled inventory without committing proprietary content; ordinary CI uses synthetic fixtures. Corpus selection/tuning uses development material under 10.1, never held-out gold. No answer-quality improvement is claimed by ingestion checks alone.

**Dependencies / gates:** 2.10 and 2.8 report/outcome/limit contracts. Private-corpus selection/tuning additionally requires 10.1/B2/B6; synthetic implementation does not wait for gold access. **Owner:** ingestion/report implementation via `bmad-build`, with the corpus owner supplying any private audit inventory.

**Coverage:** FR-1/2/3/5/8/49; AD-4/20/32/34. [Build intent](../implementation-artifacts/2-11-account-for-ingestion-coverage-and-fidelity.md).

**Forward handoffs:** 4.1 retains its 2.8 exact-proof dependency. 4.2 consumes 2.9–2.11 stored content through bounded enumeration and cannot build its production index by re-parsing original HTML. 4.6 depends on 4.1/4.2 and 2.7, joining by stable ID to authoritative content. 5.1/5.3 consume shared services and 5.7 envelopes; a Javadoc-only first connection does not complete their broader supported-content requirements. Live serving still needs 5.5/5.6 and minimum 6.1/6.2/6.5. Define 10.1 alongside 2.8, before private corpus curation/tuning; trials remain 10.2/10.3. See the tracker's `delivery_dependencies` for exact scheduling keys.

Decision: tests and necessary cleanup stay in each bounded story; 2.11 owns the closing ingestion end-to-end check. An unrelated sweeping refactor is not added to this product-quality amendment.

## Epic 3: Selected Examples, Recipes, Guides and Practices

### Story 3.1: Parse Java examples via AST

As a developer,
I want `.java` examples parsed via JavaParser AST with complete source preserved once, and three levels of record emitted — whole example, declared class/interface, and method/constructor — each child carrying a `SourceRange` and parent link,
So that a method-sized usage sequence is retrievable without ever compiling or executing the sample.

**Acceptance Criteria:**
**Given** a large multi-class example file
**When** it is parsed
**Then** the complete original source is preserved once as the whole-example record
**And** class/interface and method/constructor records are produced with parent-child links
**And** each child record stores a `SourceRange` (relative path, start/end line+column) and the minimum text needed for search and citation, referencing the source rather than copying the whole file
**And** child records are canonical SQLite rows and Lucene holds method-sized projections
**And** no compilation, symbol-solving, or execution occurs anywhere in the pipeline
*(Expanded 2026-08-25 Sprint Change Proposal — AD-30. Depends on Story 2.8 contract approval; completed Story 1.4 is not reopened.)*

**Approved forward amendment (2026-10-02):** Preserve all extraction/linking criteria above. Require Story 2.8 approved identity/evidence/hash contracts, version-scoped exact proof, stable parent/class/method IDs and typed source citations. Fetch complete parent source through bounded get_record. Arity alone cannot select an exact overload when types are unresolved. Use development-task sources only; retain AD-30 synthetic comparisons. Broad cross-build incremental indexing stays deferred. 10.1 protocol/held-out reservation precedes source selection.

### Story 3.2: Link examples to catalog symbols

As a developer,
I want Luciad references in an example resolved against the catalog via a fixed resolution ladder, run only after Javadocs are indexed, emitting typed `USES_TYPE`/`CONSTRUCTS`/`CALLS` edges with resolution evidence,
So that a symbol lookup can surface examples that actually use it, without ever guessing a verified symbol.

**Acceptance Criteria:**
**Given** an example importing and referencing catalog symbols
**When** linking runs after the catalog exists
**Then** each resolvable reference is linked to its catalog record via a typed edge (`USES_TYPE`/`CONSTRUCTS`/`CALLS`) carrying evidence (source range, original token, candidate canonical symbols, selected symbol only when `EXACT`)
**And** a constructor + chained calls produce `CONSTRUCTS`, `USES_TYPE`, and `CALLS` edges as appropriate
**And** an FQN literal and an explicit-import reference each resolve exactly (`EXACT`)
**And** the same simple name declared in two packages stays `AMBIGUOUS` unless source context selects one
**And** an overloaded method unresolvable from arity/signature alone stays `AMBIGUOUS`
**And** an unresolvable reference stays explicitly `UNRESOLVED` rather than guessed
**And** an example project with missing dependencies still produces cited partial records without an SDK JAR or build execution
**And** a method-sized hit can fetch its complete parent example
**And** method-level vs whole-example retrieval is compared on gold queries without reducing exact-symbol accuracy
*(Expanded 2026-08-25 Sprint Change Proposal — AD-30. Resolution ladder: FQN literal → explicit import + exact match → local/same-package declaration → unique wildcard-import match → owner+name+signature/arity → ambiguous/unresolved. Only `EXACT` becomes a verified Luciad symbol, per AD-4/AD-7.)*

**Approved forward amendment (2026-10-02):** Preserve all extraction/linking criteria above. Require Story 2.8 approved identity/evidence/hash contracts, version-scoped exact proof, stable parent/class/method IDs and typed source citations. Fetch complete parent source through bounded get_record. Arity alone cannot select an exact overload when types are unresolved. Use development-task sources only; retain AD-30 synthetic comparisons. Broad cross-build incremental indexing stays deferred. 10.1 protocol/held-out reservation precedes source selection.

### Story 3.3: Apply governance-supplied quality and approval metadata

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Read quality and explicit practice approval metadata from governed input. Missing metadata produces no inferred label/approval. Record owner, source and applicable SDK version. Reject attempted candidate promotion or invalid approval records. Reuse this mechanism in 3.4–3.6. Broad technique vocabulary is deferred and does not gate this story.

**Dependencies / gates:** 2.8; B4 named approvals.

**Coverage:** FR-14/45; AD-14/31.

### Story 3.4: Compile workflow recipes and required knowledge links

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Preserve those procedural fields, compile as Workflow records with `RECIPE` kind, and reference stable knowledge IDs with explicit version/applicability. Resolve required dependencies against facts/approved practices; an absent, incompatible or candidate required dependency blocks activation of the build containing that recipe. Freeze treatment of dependency cycles in 2.8 and reject invalid graphs deterministically. Return source provenance and applicability explanation. No duplicated authoritative facts as a competing runtime store.

**Dependencies / gates:** 2.8; 3.3; 10.1 before curation.

**Coverage:** FR-9/15/45; AD-14/31.

### Story 3.5: Ingest selected guide sections for the proof

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Consume an explicit allowlist of approved local guide pages/sections selected using development tasks. Preserve title, selected ordered prose/snippets, original source/section locator, content hash and linked/unresolved references. Report selection/skips and coverage; never imply the full guide corpus was ingested. Vendor facts may remain without a quality label; authored practices extracted from them need explicit approval. Malformed inputs follow the classified failure policy. Broad guide ingestion is a separate deferred extension.

**Dependencies / gates:** 2.8; 3.3; 10.1 before curation.

**Coverage:** FR-35/36/38/45; AD-14/15/31.

### Story 3.6: Compile approved engineering practices

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Define versioned Markdown/YAML authoring with stable ID, kind, SDK applicability, source citations, governance owner/approval and dependency fields. Deterministically compile approved practices into existing canonical storage/index ports. Reject malformed/unapproved/candidate content from the active-build path; preserve candidate proposals separately for human review. Verify symbol-less approved content is searchable/fetchable with complete citations. No new runtime database.

**Dependencies / gates:** 2.8; 3.3; 10.1 before curation.

**Coverage:** FR-45; AD-14/31.

## Epic 4: Exact and Lexical Retrieval; Gated Semantic Enhancement

### Story 4.1: Detect and prioritize exact-symbol queries

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Pin one available source/build before lookup, independently of optional target SDK context. Missing exact-target docs do not block useful evidence. Fully qualified signatures produce exact cited-source identity or explicit ambiguity/unresolved state; relevance never establishes compatibility. Test target 2023.1/source 2023.5 with normal useful results and source citations, omitted target, source filters, unknown/ambiguous inputs and same-source isolation; canonical exact fixtures remain 100%.

**Dependencies / gates:** 2.8; shared query/proof contracts.

**Coverage:** FR-16/44/47; AD-7/7a/12/32.

### Story 4.2: Index canonical and lexical fields for BM25

As a developer,
I want canonical symbol + normalized variants indexed as exact/prefix/camel-case fields, and descriptions/params/returns/exceptions/example prose indexed as analyzed BM25 text with filters and highlighting,
So that lexical search covers both precise symbol lookup and natural-language description matching.

**Acceptance Criteria:**
**Given** a corpus indexed by this scheme
**When** a query uses a partial camel-case symbol fragment
**Then** it matches via the camel-case field
**And** a query using descriptive prose matches via the analyzed text fields with highlighting

**Approved forward amendment (2026-10-02):** Require 2.8. Retain exact/prefix/camel-case and analyzed prose/highlighting; add stable record ID, knowledge kind, governance/provenance, applicability and scope filters. Index selected supported content including symbol-less approved records; no embedding model or broad tag vocabulary is required. Coverage: FR-17/45/47; AD-18/19/26/31.

**2026-10-21 source-content handoff:** Production indexing additionally waits for 2.9–2.11. Read approved stored payloads in bounded batches; index description, parameter/return/exception prose, notes, signatures and narrative text through stable record IDs. Do not reparse original HTML or use Lucene document IDs as public identity. Verify prose-only discovery, restart/source-directory independence, version exclusion and catalog/index compatibility.

### Story 4.3: Local ONNX vector search with explicit failure semantics

**DEFERRED from first proof (2026-10-02):** Vector benefit requires evaluation after lexical baseline. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a developer,
I want vector search to run entirely against a local ONNX model, fail explicitly on model-load error, and only fall back to lexical-only when configured,
So that a broken or missing local model is never silently masked as "no results."

**Acceptance Criteria:**
**Given** a corrupted or missing local model path
**When** the server starts
**Then** it fails explicitly with a clear error rather than starting in a degraded silent state
**And** with lexical fallback explicitly configured, `index_status` reports the fallback is active

### Story 4.4: Fuse lexical and vector results under exact priority

**DEFERRED from first proof (2026-10-02):** Vector fusion deferred; required bounded lexical enrichment transfers to 4.6, not completion of this story. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a coding agent,
I want lexical and vector results fused (e.g. RRF) beneath exact-match priority, with top candidates enriched with a bounded relationship neighborhood,
So that hybrid search returns a concise, cited, relationship-aware result set rather than two disjoint candidate lists.

**Acceptance Criteria:**
**Given** a query with both lexical and vector candidates and no exact match
**When** fusion runs
**Then** results are merged into one ranked, deduplicated list
**And** each top result includes its bounded relationship neighborhood

### Story 4.5: Enforce a consistent embedding fingerprint

**DEFERRED from first proof (2026-10-02):** Vector-specific fingerprints deferred; general identity/compatibility remains 2.8/6.2. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a developer,
I want the same pinned model/tokenizer/preprocessing/normalization/dimensions/query-instructions used at index-time and query-time, excluding navigation/duplicate/boilerplate/empty content from embedding,
So that vector search never silently compares embeddings produced under different settings.

**Acceptance Criteria:**
**Given** an index built with fingerprint F
**When** a query arrives at a server configured with a different fingerprint
**Then** vector search is rejected/disabled per AD-6, not silently run with mismatched vectors
**And** navigation/boilerplate/empty content is confirmed absent from the embedded record set

### Story 4.6: Assemble bounded, cited lexical results

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Given exact and lexical candidates, enforce trust/applicability/citation rules, deduplicate by stable record ID, order deterministically and add bounded relationship context. Return truthful exact/lower-bound/unknown count relation, coverage, truncation and build-bound continuation. Filter-only browse is deterministic. Limits apply to internal candidate/traversal/time work and serialized payload. Distinguish not-found/not-indexed/filtered-inapplicable/unavailable-capability outcomes. Test below/above-limit cases and stale cursors. Required lexical enrichment moves from deferred 4.4 here; 4.4 remains backlog and is not done.

**Dependencies / gates:** 2.8; 4.1/4.2; 2.7 bounded traversal.

**Coverage:** FR-19/47; AD-11/15/18/19/20/35.

**2026-10-21 source-content handoff:** Requires 4.1/4.2 and 2.7. Fetch authoritative sections by stable ID, retain code/note context and truthful locators, and apply request budgets before enrichment. Verify that citations resolve to the returned section and that bounded context is never presented as complete-corpus proof.

## Epic 5: Six-Tool MCP Surface

### Story 5.1: Knowledge search and symbol resolution

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Implement `search_knowledge` and `resolve_symbol` through shared services. Search supports filters/browse and follow-up IDs/sections; resolve returns scoped exact/ambiguous/unresolved. Test unknown/ambiguous evidence, target 2023.1 with useful 2023.5 source results, explicit source filters and symbol-less content paths.

**Dependencies / gates:** 2.8, 5.7, 4.1/4.2/4.6.

**Coverage:** FR-21/23/38/47; AD-11/24/32/33/35 as applicable.

**2026-10-21 delivery handoff:** Depends on the shared 4.6 retrieval service and 5.7 envelope. A first Javadoc search/resolve connection consumes the production persisted-content path from 2.9–2.11; it does not mark examples/guides or the broader story complete. CLI and MCP must use equivalent proof/ranking policy.

### Story 5.2: Bounded typed relationship tool

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Implement only `get_relationships` with typed/family/direction/depth filters and 2.7 budgets. Preserve implementation/subclass capability and evidence. Never imply documentation or bounded traversal is exhaustive.

**Dependencies / gates:** 2.8, 5.7, 2.7.

**Coverage:** FR-22/47; AD-11/24/32/33/35 as applicable.

### Story 5.3: Bounded record fetch across supported content

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Implement `get_record` across supported record types with bounded ID batches/sections, parent-example source access, complete content citations and per-ID not-found/oversize outcomes. Return only eligible records from the bound active build.

**Dependencies / gates:** 2.8, 5.7, canonical record ports; synthetic eligible records may precede each corpus producer.

**Coverage:** FR-21/23/38/47; AD-11/24/32/33/35 as applicable.

**2026-10-21 delivery handoff:** The first real Javadoc fetch consumes 2.9–2.11 payloads through bounded sections/IDs. Preserve code/notes and per-ID outcomes after input source removal. Shared DTO/schema work belongs to 2.8, envelope enforcement to 5.7; later producer-specific integrations remain required before claiming all supported content complete.

### Story 5.3b: Guide tools

**DEFERRED from first proof (2026-10-02):** Guide aliases deferred; selected-guide capability belongs to 5.1/5.3. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a coding agent,
I want `search_guides` and `get_guide` exposed as MCP tools,
So that I can retrieve cited SDK tutorial/how-to guidance instead of inventing procedures. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a running MCP server over a built index containing guide records
**When** `search_guides` is called with a query matching a guide's title or prose
**Then** it returns a citation-complete result per AD-15
**And** `get_guide` returns the complete guide (title, ordered prose sections, code snippets) only via this explicit tool, never embedded inline elsewhere
**And** an unknown guide identifier returns a structured not-found response, never a fabricated result

### Story 5.3c: Training tools

**DEFERRED from first proof (2026-10-02):** Training corpus and specialized tools deferred; no placeholder tools. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a coding agent,
I want `search_trainings` and `get_training` exposed as MCP tools,
So that I can retrieve cited training-deck content instead of inventing procedures or facts the training material actually covers. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a running MCP server over a built index containing training records (ingested by Epic 9)
**When** `search_trainings` is called with a query matching a training section's title or text
**Then** it returns a citation-complete result per AD-15
**And** `get_training` returns the complete training-deck section (title, ordered text blocks, extracted tables/figures) only via this explicit tool, never embedded inline elsewhere
**And** an unknown training identifier returns a structured not-found response, never a fabricated result

*Cross-epic dependency: `Training` records are produced by Epic 9, which is sequenced after this epic. Until Epic 9 lands, this story is validated only for its structured not-found / empty-result behavior; full retrieval is exercised once Training records exist.*

### Story 5.3d: Concept/technique search tool

**DEFERRED from first proof (2026-10-02):** Broad tags/concept tool deferred; symbol-less common search remains 4.2/4.6/5.1/5.3. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a coding agent,
I want `search_concepts` exposed as an MCP tool, querying by one or more curated technique tags (FR-42) across examples, workflow docs, guide articles, and training-deck sections,
So that I can find every citation-complete record demonstrating a coding technique or best practice, even one with no resolved canonical-symbol link. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a running MCP server over a built index containing records tagged with technique tags across multiple record types
**When** `search_concepts` is called with one or more technique tag values
**Then** it returns a federated, deduplicated, citation-complete result set spanning `Example`, `Workflow`, `Guide`, and `Training` records per AD-15
**And** a record with no resolved canonical-symbol link is still returned, since technique tag is a secondary, non-symbol join key (AD-29)
**And** a tag value outside the curated vocabulary returns a structured error, never a silently empty result

*Cross-epic dependency: the `Training` slice of this federation depends on Epic 9. Before Epic 9, `search_concepts` federates only `Example`/`Workflow`/`Guide`; the `Training` slice is exercised once Epic 9 produces records.*

### Story 5.4: Symbol validation and index status

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Retain both, add batch bounds, explicit build/version/schema/coverage/fingerprints and lexical capability status. Unknown/ambiguous inputs never become valid; no-model lexical operation is healthy.

**Dependencies / gates:** 2.8, 5.7, shared catalog/manifest services.

**Coverage:** FR-24/47; AD-11/24/32/33/35 as applicable.

### Story 5.5: Streamable HTTP transport

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Retain transport `/mcp` and optional stdio. Enforce shared input/output/time limits and read-only schemas without administrative settings. Full content only through bounded `get_record`.

**Dependencies / gates:** 2.8, 5.7; handlers for end-to-end acceptance.

**Coverage:** FR-25/47; AD-11/24/32/33/35 as applicable.

### Story 5.6: Serve validated immutable build handles

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Serve only from a validated immutable build. Shared validator runs on startup/reload; each request pins a single build; invalid replacement leaves a valid current build serving or fails clearly on cold start. Depend on minimum 6.1/6.2; no bypass demo accepted as first proof.

**Dependencies / gates:** 2.8, minimum 6.1/6.2 and 6.5; fixture-only handlers do not pass proof lifecycle acceptance.

**Coverage:** FR-26/27/46; AD-11/24/32/33/35 as applicable.

### Story 5.7: Shared six-tool result and error envelope

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Implement the 2.8 envelope across all six: stable codes, IDs/kinds, proof, matchedBy, governance/applicability, build/version, coverage/count relation, empty reason and continuation. Content citations required; verdicts not dropped. Run this before individual handlers are accepted. Defaults 10, cap 50 and ≤1 MiB remain; finite batch/query/traversal/time/trace caps are frozen in 2.8. Stale build/version/query cursors reject or explicitly restart, never mix.

**Dependencies / gates:** 2.8; accepted before individual handlers.

**Coverage:** FR-25/47; AD-11/24/32/33/35 as applicable.

### Story 5.8: Draft six-tool agent guidance

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Draft guidance as six contracts stabilize, link 7.1 completion and include external compile/test/repair boundary. Only actual tool names appear.

**Dependencies / gates:** 2.8/5.7 as six schemas stabilize; completed by 7.1.

**Coverage:** FR-30; AD-11/24/32/33/35 as applicable.

## Epic 6: Shared Validation and Index Lifecycle

### Story 6.1: Immutable builds with shared validation and atomic activation

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Use shared validator; reject invalid SDK/schema/governance/dependencies and build-invalidating outcomes. Serialize competing writers; publish only sealed builds. Interrupted staging/activation and in-flight readers follow frozen recovery rules.

**Dependencies / gates:** 2.8 shared validator/lock/recovery contracts.

**Coverage:** FR-26/45/46; AD-11/24/32/33/35 as applicable.

### Story 6.2: Reject incompatible builds on every entry path

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Apply same rejection policy at validation, activation, startup, reload, rollback and restore. Lexical profiles require no model; unknown profiles/schema fail closed. A migration produces a separately validated new build.

**Dependencies / gates:** 2.8; validation shared with 6.1 and all load paths.

**Coverage:** FR-27/46; AD-11/24/32/33/35 as applicable.

### Story 6.3: Reader-safe rollback and retention

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Revalidate runtime compatibility before rollback and pin readers safely. Retain current/previous per policy; no deletion while in use. Preserve explicit version default. Retain at least current and previous compatible builds where storage allows; side-by-side versions use an explicit default and never mixed query scope.

**Dependencies / gates:** 2.8, 6.1/6.2; release acceptance.

**Coverage:** FR-28/46; AD-11/24/32/33/35 as applicable.

### Story 6.4: Manifest-complete backups and validated restore

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Validate completeness against the manifest/profile; explicitly absent model in lexical mode is valid, missing declared assets invalid. Restore runs the same validator. Include manifest, catalog, Lucene directory, report, configuration, checksums and all declared assets; tampered or incomplete backup cannot restore.

**Dependencies / gates:** 2.8, 6.1/6.2; release acceptance.

**Coverage:** FR-29/46; AD-11/24/32/33/35 as applicable.

### Story 6.5: Shared diagnostics and service conformance

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

CLI diagnostics and MCP use the same query/proof/validation services and produce equivalent logical verdicts/coverage for identical build/query/scope, excluding transport wrapping. BUILD_INVALIDATING yields non-success and cannot activate. Classify report-write failure so success cannot conceal missing required evidence. Test synthetic fixtures across validation, activation, startup/reload, rollback/restore and alternate entry routes. Operator configuration stays outside agent schemas; diagnostic command spelling is frozen in 2.8. No compilation is performed by the service.

**Dependencies / gates:** 2.8; 6.1/6.2 shared policy; transport conformance integrated before proof acceptance.

**Coverage:** FR-34/46/50; AD-1/33/36.

## Epic 7: Agent Guidance & Usability

### Story 7.1: Ship the agent SKILL.md

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Complete 5.8 with six-tool retrieve → resolve → context/relationships → generate → check → external compile/test → repair guidance. Include one worked display-a-point sequence, exact constructors/overloads, concrete implementation navigation, approved examples and recipe applicability/dependency checks. Use only actual tool names and MCP-returned SDK APIs; report unresolved and untested outcomes honestly. Policy is usable in first-proof evaluation.

**Dependencies / gates:** 5.8; accepted six-tool contracts; available before 10.2 trials.

**Coverage:** FR-30/50; AD-35/36.

### Story 7.2: Guarantee typed citations on primary content

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Validate SDK scope, record type, stable record ID and truthful typed source locator for every primary content payload, including symbol-less practices/guides and dependency evidence. Canonical symbol is optional when absent, anchors are never fabricated. Distinguish absent/not-applicable from missing required content evidence. Exact/ambiguous/unknown verdicts remain visible without content-citation filtering. Achieve 100% citation completeness on primary fixtures.

**Dependencies / gates:** 2.8; 5.7; content paths in 5.1/5.2/5.3.

**Coverage:** FR-31/47; AD-15/24/35.

## Epic 8: Air-Gap Packaging & Release

### Story 8.1: Single serving image and Compose layout

As a release operator,
I want one image that runs `ingest`/`validate-index`/`serve`, with Compose separating one-shot ingestion from read-only serving, `pull_policy: never`, non-root, read-only root FS where practical, cap-drop ALL, `no-new-privileges`, proprietary inputs mounted read-only, and MCP bound to the internal host interface reachable by the trusted team,
So that the deployed image can't be pulled from a registry, escalate privileges, or be exposed to the public internet.

**Acceptance Criteria:**
**Given** the Compose file deployed in an air-gapped environment
**When** the stack starts
**Then** the serving container runs as non-root with the listed hardening flags active
**And** the MCP endpoint is reachable only on the internal network, never from the public internet

**Approved forward amendment (2026-10-02):** Retain all internal-host/container-hygiene criteria. The lexical profile requires no ONNX model or Docling/training image; zero egress on all active paths. Final release approvals remain open. Coverage FR-32; AD-2/9/27.

### Story 8.2: Air-gap transfer bundle

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Bundle serving image tar + SHA256SUMS + SBOM + install docs + frozen index and all manifest-declared profile assets with licenses. Lexical needs no model; omit training image. Verify every checksum and reject tampering. On a networking-disabled clean machine verify load, approved fixture ingestion, MCP serving and smoke tests. Document both prebuilt transfer and in-gap rebuild paths. Preserve proprietary-corpus handling and existing release approvals.

**Dependencies / gates:** 6.3/6.4 lifecycle completion; 8.1; B1/B3/B4/B5 and NFR/release approvals.

**Coverage:** FR-33; AD-9/27/33.

### Story 8.3: Headless CLI with documented exit codes

As a release operator,
I want `ingest`, `validate-index`, `serve`, and `stats` as documented CLI commands with defined exit codes, running fully headless,
So that the appliance can be driven from scripts/CI without any interactive prompt.

**Acceptance Criteria:**
**Given** each of the four commands run with no TTY attached
**When** invoked with valid and invalid arguments
**Then** each exits with its documented code (success, validation failure, runtime failure, etc.) and no interactive prompt ever appears
**And** `--help` output documents every option for all four commands

**Approved forward amendment (2026-10-02):** Build on 6.5 early invalid-build/report-write and shared-diagnostic semantics; do not defer those correctness requirements until packaging. Final CLI/help documents 2.8-approved diagnostic spelling, profile-aware inputs, output bounds and exit codes. Coverage FR-34/50; AD-36.

### Story 8.4: Separate ingest-time-only image for training-material ingestion

**DEFERRED from first proof (2026-10-02):** Training image deferred; lexical serving needs neither model nor Docling. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a release operator,
I want a distinct container image that runs `cli ingest` against the training-materials dir only (docling/Python/network tooling included), built and shipped separately from the serving image,
So that the served MCP appliance never contains, and can never reach, network-capable or non-Java tooling. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** the serving image (Story 8.1) and the training-ingest image built independently
**When** their contents are inspected
**Then** the serving image contains no docling, Python, or network-capable tooling
**And** the training-ingest image is never referenced by, or composed alongside, the Compose layout that runs `serve`
**And** the air-gap bundle (Story 8.2) ships only the serving image tar; any training records ingested via the separate image must already be present in the frozen index before the bundle is built

*Cross-epic dependency: a frozen index containing Training records requires Epic 9 (ingestion) and Epic 6 (lifecycle) to have completed. If Epic 8 ships before Epic 9, the initial bundle simply contains no Training records; a later rebundle adds them.*

## Epic 9: Training Material Knowledge [DEFERRED]

### Story 9.1: Ingest training decks via docling

**DEFERRED from first proof (2026-10-02):** Training ingestion deferred pending benefit, provenance/governance/version decisions. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a developer,
I want PPTX/PDF training-deck exports parsed via docling's vision/layout-model pipeline into a title, ordered text blocks, and any extracted tables/figures per section, running only inside `ingest.trainings` from the separate ingest-time image,
So that training-deck content becomes indexable structured records without ever granting network access or non-Java tooling to the served appliance. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a training-deck PPTX/PDF fixture invoked via `cli ingest` against the training-materials dir
**When** it is parsed
**Then** each section is preserved as a `Training` record with its title, ordered text blocks, and any extracted tables/figures
**And** the process runs only inside `ingest.trainings`, the sole module permitted network access (docling's models) and non-Java tooling execution (AD-28)
**And** a fixture with malformed/broken deck content degrades per the same `fail-fast | collect-and-continue` policy as Story 2.2, never aborting the whole ingest run
**And** docling's parsed output (section titles, ordered text blocks, extracted tables/figures) is captured and frozen as the source-of-record ingestion input, so downstream record IDs and content hashes (NFR-13, AD-22) are computed deterministically from that frozen output rather than re-derived by re-running docling's network-fetched vision model

### Story 9.2: Link training references to the catalog

**DEFERRED from first proof (2026-10-02):** Training links deferred with corpus. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a developer,
I want Luciad symbol/API references in extracted training text resolved against the catalog using the same canonical/normalized keys as other record types, run only after Javadocs are indexed,
So that a symbol lookup or training query surfaces cited training-deck content instead of raw unlinked text. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a training record referencing catalog symbols, run after the catalog exists
**When** linking runs
**Then** each resolvable reference is linked to its catalog record
**And** an unresolvable reference stays explicitly unresolved rather than guessed, per FR-10

### Story 9.3: Apply governance-supplied technique tags to training records

**DEFERRED from first proof (2026-10-02):** Broad training tags deferred with corpus/vocabulary. Existing execution status remains backlog. Original intent/criteria below are retained for reactivation only and do not override current contracts. Scheduling excludes this story until a diagnosed gap, controlled benefit, preserved safety and owner approval clear the gate.

As a corpus owner,
I want cross-cutting technique tags (FR-42), from the same curated vocabulary and sidecar-metadata mechanism as Story 3.3, attachable to training records,
So that a training-deck section with no resolved canonical-symbol link is still discoverable via `search_concepts` instead of being permanently unjoinable. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a training record with no sidecar metadata
**When** ingestion runs
**Then** it receives no technique tags rather than guessed ones
**And** a training record with sidecar-supplied technique tags receives exactly the technique tags the sidecar specifies, via the same mechanism as Story 3.3
**And** a technique tag value outside the curated vocabulary is rejected at ingest with a structured error, never silently accepted
**And** a training record with no resolved canonical-symbol link remains discoverable via `search_concepts` (Story 5.3d) once tagged, per AD-29

## Epic 10: Agent Outcome Evaluation

First-proof decision and human-governed correction loop; no measured outcome is claimed by this plan.

### Story 10.1: Freeze the evaluation protocol and task set

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Curate 20–30 representative Luciad tasks and source evidence within existing handling rules; partition development/held-out before corpus selection. Freeze success rubric, improvement rule, treatment controls, repeat policy and budgets. Keep held-out gold answers/scoring inaccessible to the agent and corpus authors; development tasks alone guide curation/tuning. Owners approve thresholds before trials. Retain retrieval diagnostics alongside outcome metrics; no invented claim of success.

**Dependencies / gates:** B2/B6 product/technical/corpus owner protocol approval; held-out reservation before curation/tuning; can be defined early.

**Coverage:** FR-48/49/50; AD-34/36.

**2026-10-21 sequencing:** Run protocol definition alongside 2.8, not after retrieval tuning. Reserve held-out sources/questions before private-corpus curation/tuning or comparative claims; synthetic fidelity implementation in 2.9–2.11 can proceed independently. Distinguish absent source, extraction loss, retrieval miss, insufficient result context and answer-generation failure in the rubric.

### Story 10.2: Build the controlled external evaluation harness

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Run unaided, exact+lexical, then relationship/example/practice treatments with the same agent/model/prompt/environment/limits except the intended treatment. Smoke-test access; isolate gold; use repeated paired trials and blind judging where practical. Compile/test occurs outside the knowledge service with separately available SDK/build prerequisites. If prerequisites are absent, record blocked/not-run, never passed. Report initial/final compile, symbol errors, citations/practice adherence, task result, calls/tokens/time/repairs and build costs separately.

**Dependencies / gates:** 10.1; 2.8; accepted six tools, minimum 6.1/6.2/6.5, selected corpus and 7.1/7.2; external SDK/build prerequisites.

**Coverage:** FR-48/49/50; AD-34/36.

**2026-10-21 evaluation handoff:** Include ordinary source-search, exact/BM25 and relationship/example context treatments with identical task/model/budgets except the treatment. Verify the source-to-store-to-result chain and classify failures by stage; ingestion roundtrip success alone is not evidence of improved answers.

### Story 10.3: Evaluate outcomes and decide expansion

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Execute the frozen suite, include all failures and uncertainty, compare to the unaided baseline, and apply the predeclared decision rule. Report coverage and measurement limitations. Propose vectors, corpora or specialized tools only when a diagnosed gap and controlled comparison justify them without breaking safety gates.

**Dependencies / gates:** 10.1/10.2 frozen suite and paired trials.

**Coverage:** FR-48/49/50; AD-34/36.

### Story 10.4: Govern corrections and retain regressions

**Planning disposition:** Backlog; approved prospective scope, implementation not applied.

**Acceptance criteria:**

Classify failures as KNOWLEDGE_GAP, RECIPE_DEFECT, RETRIEVAL_OR_ROUTING_DEFECT, VERSION_OR_CORPUS_DEFECT, GENUINE_AMBIGUITY or NON_LUCIAD_FAILURE. Keep correction as CANDIDATE_LEARNING until minimal patch, structural/symbol/citation checks, targeted replay, affected regressions, independent review where practical and human/domain-owner approval pass. Add a permanent regression before activating a new immutable build. Never auto-promote or repeatedly tune against held-out scoring material.

**Dependencies / gates:** 10.1 protocol; 10.2/10.3 findings; human/domain-owner approval and regression before activation.

**Coverage:** FR-48/49/50; AD-34/36.
