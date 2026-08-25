---
title: LuciadLightspeed Knowledge MCP
status: final
created: 2026-08-20
updated: 2026-08-20
---

# PRD: LuciadLightspeed Knowledge MCP

## 0. Document Purpose

This is a **lean reference PRD**. The authoritative source is [`docs/luciad-knowledge-mcp-implementation-brief.md`](../../../../docs/luciad-knowledge-mcp-implementation-brief.md) (the final BMAD handoff, a locked contract). This document does **not** restate that brief. Its sole job is to convert the brief's prose into the stable, testable, individually-referenceable units the rest of the BMAD chain needs:

- **Features (F-*)** grouping **Functional Requirements (FR-*)**, each with testable *Consequences* and a `→ brief §` provenance pointer.
- **Cross-cutting Non-Functional Requirements (NFR-*)** derived from brief §14, made testable.
- **Non-goals, MVP scope, success metrics, constraints/guardrails**, and a single **Open Questions** registry (blockers B1–B5).

Downstream artifacts (architecture, epics/stories, traceability matrix) reference the IDs minted here. Architecture *how* lives in the architecture artifact and in `addendum.md`, not here. Where the brief already fixes a decision, this PRD records the requirement and points to the section — it does not reopen it.

## 1. Vision

A portable, fully offline knowledge service that lets a coding agent use the proprietary LuciadLightspeed Java SDK **accurately**, grounded only in the SDK's offline Javadocs and curated Java examples. A developer sets up the appliance, connects a compatible agent to its MCP endpoint, and asks for a LuciadLightspeed outcome (e.g. *"plot an object on a map"*); the agent discovers the right classes, retrieves exact signatures and trusted examples, validates every proposed symbol against a canonical catalog, and cites the supporting Javadoc page or example. `→ brief §1`

The system must substantially reduce invented packages, classes, methods, and overloads. It cannot guarantee semantically correct Java without compilation, but it **must never report an unknown symbol as verified**. `→ brief §1`

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

- **UJ-1. Agent builds a Luciad feature from a natural-language request.** Agent receives *"display a point object"* → `search_workflows`/`search_examples` for a relevant pattern → `resolve_symbol`/`get_class`/`get_method` for exact signatures → `find_implementations` when an interface is required → `check_symbols` on all proposed symbols → emits code citing the Javadoc page/example. Value lands when generated code compiles-conceptually against real symbols and every API choice is cited. `→ brief §1, §11`
- **UJ-2. Operator activates a new index in the air gap.** Operator runs `ingest` on a supplied local corpus → `validate-index` → atomic activation of the immutable build → `serve` on the shared internal host. Value lands when the MCP endpoint answers against the frozen index with zero external network access. `→ brief §12, §14`

## 3. Glossary

Downstream artifacts must use these terms verbatim.

- **Canonical symbol** — the unique, version-scoped key identifying a type or member, overload-distinguishing (e.g. `pkg.Type#method(ParamType,Other)`). `→ brief §7`
- **Normalized key** — auxiliary lookup variant of a canonical symbol (simple-name, dotted-member, erased-generic, camel-case). `→ brief §7`
- **Catalog** — the SQLite store of canonical symbols and their metadata for one SDK version. `→ brief §3, §6`
- **Relationship edge** — a typed, cited link between two canonical symbols (one of the 17 required relationship types). `→ brief §6` *(15→17, 2026-08-25 Sprint Change Proposal — AD-30.)*
- **Record** — a primary retrieval unit: one API element, one example, one workflow, one guide, or one training-deck section. *(Amended 2026-08-21, Sprint Change Proposal, to add training-deck sections.)* `→ brief §6`
- **Workflow (record)** — a curated Markdown/YAML task recipe (prerequisites, ordered steps, referenced symbols, linked examples, limitations, version). `→ brief §6`
- **Guide (record)** — a curated SDK tutorial/how-to article (HTML, sourced from the LuciadLightspeed doc portal) explaining a concept or use case in prose, with embedded code snippets and step-by-step instructions. Distinct from a Workflow (a discrete task recipe) in that a Guide is a narrative explainer that may span multiple concepts and does not necessarily decompose into ordered steps with explicit prerequisites. *(Added 2026-08-21, Sprint Change Proposal.)*
- **Training (record)** — a structured excerpt of a vendor/internal training slide deck (PPTX/PDF), extracted via the `docling` vision-model pipeline into a section title, ordered text blocks, and any extracted tables/figures. Distinct from a Guide (HTML tutorial article) in source format and extraction method (layout/vision-model-based, not DOM parsing); the only record type whose ingestion may reach the network (see FR-39). *(Added 2026-08-21, Sprint Change Proposal.)*
- **Quality label** — governance-assigned example trust level: `official` | `validated` | `best-practice` | `illustrative` | `legacy`. `→ brief §8`
- **Technique tag** — a curated, governance-assigned coding-technique/best-practice label (e.g. `resource-lifecycle`, `builder-pattern`) attachable to Example, Workflow, Guide, and Training records via the same sidecar mechanism as Quality label; a secondary, non-symbol join key for content not anchored to one canonical symbol. *(Added 2026-08-21, Sprint Change Proposal.)* `→ brief §8, §14`
- **Hybrid retrieval** — the required normal mode fusing exact-symbol priority + BM25 lexical + vector search. `→ brief §9`
- **Lexical mode** — fallback retrieval with no embedding model, visible in `index_status`. `→ brief §9`
- **Index build** — one immutable directory for one SDK version, identified by SDK version + build ID. `→ brief §12`
- **Manifest** — pinned build metadata: app version, schema version, Lucene version, embedding model, dimensions, tokenizer/preprocessing fingerprint, record count, SDK version. `→ brief §12`
- **Compatibility fingerprint** — the tuple whose change makes an index incompatible (schema, Lucene-major, canonical-symbol format, model, dimensions, tokenizer, preprocessing). `→ brief §12`
- **Citation** — SDK version + record type + relative source path + HTML anchor (when available) + stable record ID, attached to every primary result; canonical symbol/example ID is included when the record has one resolved, and omitted — never fabricated — for a record with no resolved canonical-symbol link (e.g. a symbol-less Guide or Training record). *(Amended 2026-08-21, Sprint Change Proposal, fixing an AD-15 defect that made canonical symbol unconditionally required.)* `→ brief §9`
- **Documented SDK universe** — the closed set of symbols present in the indexed documentation; traversal never leaves it and reports that scope. `→ brief §7`

## 4. Features

Provenance pointers cite the governing brief section. Each FR's *Consequences* are the testable conditions downstream stories must satisfy.

### F1. Offline, file-only corpus ingestion
**Description:** A one-shot ingester consumes only local directories (Javadocs, samples/workflows, guides, training materials, model, output). It never crawls, downloads, logs in, or fetches a model — except the single explicitly-scoped training-material exception (FR-39). `→ brief §2, §5, §12`

#### FR-1: File-only ingestion inputs
The ingester accepts a local Javadoc dir, local samples/workflows dir, local guides dir, local training-materials dir, local model dir, and local output dir, and nothing else — except the single, explicitly-scoped network/runtime exception in FR-39 (training-material ingestion via docling). *(Amended 2026-08-21 to add the guides dir, and again to add the training-materials dir with its scoped exception, Sprint Change Proposals.)*
**Consequences (testable):**
- Ingestion of the five non-training input types completes with no DNS/HTTP/socket egress (asserted by an offline test harness enumerating all six permitted paths).
- No HTTP crawler, portal client, or login automation exists in the runtime image; the only permitted model-fetch is docling's, confined to training-material ingestion (FR-39) and never present in the serving image.

#### FR-2: Multi-stage, resumable ingestion pipeline
Ingestion runs as ordered stages: catalog → relationships → examples/workflows → lexical index → embeddings → vector index → validation → atomic activation, with resume/checkpoint for large embedding runs. `→ brief §12`
**Consequences (testable):**
- A killed ingestion resumes from the last completed checkpoint without redoing embedded records.
- Malformed pages are handled per a configurable `fail-fast | collect-and-continue` policy.
- File parsing uses bounded work queues and releases a completed AST after its records and unresolved references are emitted. *(Added 2026-08-25 Sprint Change Proposal — AD-30.)*
- Catalog and index writes use bounded batches whose sizes are recorded in the ingestion report.
- A resumed run validates input hashes + toolchain fingerprint before reusing work; a changed input set is a new build, not a resume (AD-25). A restarted build produces the same records, edges, and manifest as an uninterrupted build from the same inputs and toolchain (NFR-13). No restart or fallback path performs a network request (AD-9; `ingest.trainings` exception unchanged).

#### FR-3: Deterministic, hashed, reportable ingestion
Record IDs are deterministic; content hashes enable repeatable updates; a machine-readable ingestion report and progress/error counts are emitted. `→ brief §12`
**Consequences (testable):**
- Re-ingesting identical input yields identical record IDs and hashes.
- The ingestion report is valid, machine-readable, and includes throughput and error summaries.
- Distinct hashes exist for source bytes (`sourceHash`, new), extracted record (`recordHash` = AD-22 identity fields), relationship edge (`relationshipHash` = AD-22 identity fields), embedding input (`semanticHash` = AD-8/AD-21 embedding fingerprint), and toolchain (`toolchainFingerprint` = AD-6 compatibility fingerprint); a change to one does not spuriously invalidate the others. *(Added 2026-08-25 Sprint Change Proposal — AD-30.)*
- The report includes counts for exact, ambiguous, and unresolved example relationships, grouped by relationship type and resolution kind.

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

#### FR-7: Canonical + normalized symbol keys
Canonical keys distinguish overloads; normalized simple-name, dotted-member, erased-generic, and camel-case keys are retained for lookup. `→ brief §7`
**Consequences (testable):**
- Canonical-symbol normalization is deterministic and round-trips through the unit suite.
- A camel-case or simple-name query resolves to the correct canonical symbol(s).

### F3. Canonical catalog & relationship graph
**Description:** A versioned SQLite catalog plus a typed relationship graph built by a mandatory two-pass process, traversable with bounded recursion. `→ brief §3, §6, §7`

#### FR-8: Versioned SQLite catalog
A version-scoped SQLite schema stores canonical symbols with unique keys and maps relative HTML paths/anchors to canonical symbols. `→ brief §3, §7`
**Consequences (testable):**
- Symbol keys are unique within an SDK version; identical keys across versions do not collide.
- Every catalog symbol resolves to a source path + anchor.

#### FR-9: Two-pass typed relationship construction
Pass one builds the complete catalog + path/anchor map; pass two resolves internal links/declarations into typed edges; forward/inverse pairs are reconciled (e.g. `IMPLEMENTS`/`IMPLEMENTED_BY`). All 17 required relationship types are produced. `→ brief §6, §7` *(15→17, 2026-08-25 Sprint Change Proposal — AD-30 refines the example edge into `USES_TYPE`/`CONSTRUCTS`/`CALLS`.)*
**Consequences (testable):**
- Each of the 17 relationship types is produced and unit-tested on fixtures.
- Method parameter/return/field/exception types are stored as linked canonical symbols when resolvable, not display strings.

#### FR-10: Explicit unresolved/external references
Unresolved external or undocumented links stay explicit and are never converted into guessed symbols; traversal is restricted to the documented SDK universe and reports that scope. `→ brief §7`
**Consequences (testable):**
- An unresolved link is retrievable as `unresolved`, never as a fabricated symbol.
- Traversal results declare that scope is limited to indexed documentation.

#### FR-11: Bounded direct + recursive traversal
Direct and recursive (CTE) traversal supports depth limits and cycle handling. `→ brief §3, §7`
**Consequences (testable):**
- Recursive subclass/implementation traversal terminates on cyclic fixtures within the depth limit.
- Direct-only queries return exactly one hop.

### F4. Example, workflow & guide ingestion and linking
**Description:** JavaParser-based ingestion of examples and workflow docs, plus HTML-article parsing of guide/tutorial pages, preserving complete source/content, extracting Luciad references, and linking to the catalog — never compiling or executing. `→ brief §2, §8`

#### FR-12: Example parsing preserves complete source (no execution)
`.java` examples are parsed with JavaParser AST; complete source is preserved **once**; the ingester emits **three levels of record — whole example, declared class/interface, and method/constructor**. Child (class/method) records are canonical SQLite rows linked to the parent example, each storing a `SourceRange` (relative path, start/end line+column) and the minimum source text needed for search and citation; Lucene holds method-sized projections. Examples are never compiled or run. `→ brief §8` *(Expanded 2026-08-25 Sprint Change Proposal — AD-30.)*
**Consequences (testable):**
- The complete original source is retrievable via `get_example`.
- A method-sized search hit can fetch its complete parent example.
- Every class/method record and every source-derived edge carries a `SourceRange` and citation.
- No build or execution of any sample occurs during ingest (asserted by code review + absence-of-build test).

#### FR-13: Luciad reference extraction & catalog linking
Luciad imports and syntactically identifiable type/member references are resolved against the catalog after Javadocs are indexed, using a **fixed resolution ladder** (FQN literal → explicit import + exact match → local/same-package declaration → unique wildcard-import match → owner+name+signature/arity match → ambiguous/unresolved). Each resolved reference emits a typed edge (`USES_TYPE`/`CONSTRUCTS`/`CALLS`, AD-30) carrying **resolution evidence** (source range, original token, candidate canonical symbols, selected symbol only when status is `EXACT`). Only an `EXACT` match becomes a verified Luciad symbol; `AMBIGUOUS` and `UNRESOLVED` references stay explicit and are never promoted (AD-4, AD-7). `→ brief §8` *(Expanded 2026-08-25 Sprint Change Proposal — AD-30.)*
**Consequences (testable):**
- A symbol can retrieve its linked examples via `find_examples_for_symbol`.
- Two packages exposing the same simple class name stay `AMBIGUOUS` unless source context selects one exactly.
- Overloaded methods stay `AMBIGUOUS` when parameter info is insufficient.
- An incomplete example project still produces cited partial records without an SDK JAR or build execution.
- An unresolvable reference is recorded as unresolved, never fabricated.

#### FR-14: Governance-supplied quality metadata
Example quality (`official`/`validated`/`best-practice`/`illustrative`/`legacy`) is supplied via sidecar metadata (e.g. `example-metadata.yaml`); filename conventions alone never determine quality; only vendor provenance establishes `official`; only an authorized corpus owner marks `validated`/`best-practice`. `→ brief §8, §14`
**Consequences (testable):**
- Quality label originates from sidecar/governance input, not the parser.
- Retrieval prefers current `official`/`validated`/`best-practice` examples.

#### FR-15: Workflow ingestion & linking
Workflow docs (Markdown/YAML) are ingested with prerequisites, ordered steps, referenced symbols, linked examples, limitations, version metadata, and linked to catalog symbols/examples. `→ brief §6, §8`
**Consequences (testable):**
- A workflow returns its required symbols and linked examples.
- `WORKFLOW_REQUIRES` edges resolve to catalog symbols where present.

#### FR-35: Guide article ingestion
*(Added 2026-08-21, Sprint Change Proposal.)* Guide HTML tutorial/how-to pages are parsed into title, ordered prose sections, and embedded code snippets; malformed/broken HTML degrades gracefully under the same policy as FR-5, never aborting the whole ingest run.
**Consequences (testable):**
- A guide article is retrievable as one record with title, prose sections, and embedded code snippets intact.
- A malformed guide-article fixture is handled per the configured `fail-fast | collect-and-continue` policy, never crashing the run silently.

#### FR-36: Guide reference extraction & catalog linking
*(Added 2026-08-21, Sprint Change Proposal.)* Luciad symbol/API references found in guide prose and embedded code snippets are resolved against the catalog using the same canonical/normalized keys as FR-7; unresolved references stay explicitly unresolved, never fabricated, per the FR-10 guarantee.
**Consequences (testable):**
- A resolvable reference in a guide's prose or code snippet links to its catalog record.
- An unresolvable reference is recorded as unresolved, never fabricated.

#### FR-37: (reserved — intentionally unassigned)
No requirement carries this number. The gap is intentional; FR numbering jumps FR-36 → FR-38 across the PRD, architecture spine, and epics, with no lost requirement.

#### FR-42: Cross-cutting technique tags
*(Added 2026-08-21, Sprint Change Proposal.)* A curated, fixed vocabulary of coding-technique/best-practice labels (e.g. `resource-lifecycle`, `builder-pattern`, `listener-chaining`) is supplied only via the same governance sidecar mechanism as FR-14, attachable to `Example`, `Workflow`, `Guide`, and `Training` records — never `ApiElement`/Javadoc records — independent of whether the record has a resolved canonical-symbol link. Filename/code-shape/prose inference never assigns a technique tag; a tag value outside the published vocabulary is rejected at ingest, not silently accepted or coerced.
**Consequences (testable):**
- A record with no sidecar-supplied technique tags is retrievable with an empty tag set, never a guessed one.
- A record whose sidecar specifies a technique tag outside the curated vocabulary fails ingest validation with a structured error naming the offending value.
- A `Guide` or `Training` record with no resolved canonical-symbol link remains discoverable via its technique tags (see FR-43), closing the symbol-less-content discoverability gap.

### F5. Hybrid retrieval (exact + lexical + vector fusion)
**Description:** A routed pipeline: detect exact signatures → exact resolution first → lexical + vector → fuse with exact-symbol priority → bounded relationship expansion → filters → concise cited results. Hybrid is the required normal mode; lexical is a visible fallback. `→ brief §9`

#### FR-16: Routed retrieval with exact-symbol priority
Exact fully-qualified signatures in the query are detected and resolved first; exact-symbol matches are heavily boosted over lexical/vector; exact validation always overrides semantic similarity. `→ brief §9`
**Consequences (testable):**
- Hybrid mode preserves 100% exact canonical lookup on indexed fixtures (no regression vs lexical).
- A query containing an exact signature returns that symbol as top result.

#### FR-17: Lexical fields & BM25
`canonicalSymbol` + normalized variants are indexed as exact/prefix/camel-case fields; descriptions, params, returns, exceptions, example/workflow/guide/training prose as analyzed text; BM25 with filters and highlighting. Applies uniformly across API elements, examples, workflows, guides, and training records. `→ brief §9`
**Consequences (testable):**
- Exact/prefix/camel-case fields return expected hits on fixtures.
- Filters apply for SDK version, record type, package, kind, deprecation, quality, and technique tag (FR-42).

#### FR-18: Local ONNX vector search with lexical fallback
Vector search runs over normalized semantic records using a locally-supplied ONNX model + tokenizer; the service never downloads a model; model-load failure is explicit; fallback to lexical occurs only when configured and is visible in `index_status`. `→ brief §9`
**Consequences (testable):**
- With no model present and fallback disabled, hybrid mode fails explicitly (does not silently degrade).
- `index_status` reports retrieval mode and model status accurately.

#### FR-19: Fusion, relationship enrichment, bounded cited results
Lexical + vector results are fused (e.g. RRF) under exact priority; the top candidates are enriched with a bounded relationship neighborhood from SQLite; results are concise, cited, and carry stable IDs. `→ brief §9`
**Consequences (testable):**
- Every primary result carries a complete citation (see Glossary).
- Relationship enrichment stays within the configured bound.

#### FR-20: Fingerprint-consistent embedding
The same pinned model, tokenizer, preprocessing, normalization, dimensions, and query instructions are used for indexing and runtime queries; navigation/duplicate/boilerplate/empty content is excluded from embedding. The embedding fingerprint is a schema-level property, not per-record-type — adding the guide record type does not itself change the fingerprint definition (see NFR-14), only what content is embedded under it. `→ brief §9`
**Consequences (testable):**
- An index whose embedding fingerprint differs from the runtime model is rejected (see FR-27).
- Embedded content excludes navigation and empty records.

### F6. MCP tool surface
**Description:** A deliberately small MCP tool surface over Streamable HTTP at `/mcp` (optional stdio), with bounded responses and never-guess guarantees. All 19 tools below are FRs. *(14 → 16 → 18 → 19, 2026-08-21, Sprint Change Proposals.)* `→ brief §3, §10`

#### FR-21: Retrieval & symbol tools
Implement `search_api`, `resolve_symbol`, `get_class`, `get_method`, `get_api_context` per §10 contracts. `→ brief §10`
**Consequences (testable):**
- `resolve_symbol` returns exact match, overload candidates, or `not_found` — never a guessed result.
- `get_method` returns one exact overload or an explicit overload list; ambiguity stays visible.

#### FR-22: Relationship tools
Implement `get_inheritance`, `get_type_relationships`, `find_implementations`, `find_subclasses` per §10, returning typed, cited edges within the documented SDK universe. `→ brief §10`
**Consequences (testable):**
- `find_implementations` marks abstract/deprecated/example-used/workflow-recommended candidates and never claims completeness beyond indexed docs.
- Transitive traversal respects `transitive`, `concreteOnly`/`includeAbstract`, and `limit`.

#### FR-23: Example & workflow tools
Implement `search_examples`, `find_examples_for_symbol`, `get_example`, `search_workflows` per §10. `→ brief §10`
**Consequences (testable):**
- `get_example` returns metadata + linked symbols + citation, and complete source only when requested.
- Example results rank by current trust metadata.

#### FR-24: Validation & status tools
Implement `check_symbols` (returns exact-valid / ambiguous-with-candidates / unknown arrays; catalog validation only, no compilation) and `index_status` (indexed SDK versions, record counts, ingestion timestamp, retrieval mode, model status, schema/index version). `→ brief §10`
**Consequences (testable):**
- `check_symbols` never marks an unknown symbol valid.
- `index_status` reflects the active build's manifest.

#### FR-38: Guide tools
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_guides` (natural-language/keyword query over the guide corpus, filterable, citation-bearing per FR-31) and `get_guide` (fetch a full guide record by stable ID with citation), both following the same structured-response conventions as the other retrieval tools.
**Consequences (testable):**
- `search_guides` returns citation-complete results ranked by relevance to a conceptual query.
- `get_guide` returns the full guide record only via this explicit tool, never embedded inline elsewhere.

#### FR-41: Training tools
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_trainings` (natural-language/keyword query over the training-deck corpus, filterable, citation-bearing per FR-31) and `get_training` (fetch a full training record by stable ID with citation), both following the same structured-response conventions as the other retrieval tools.
**Consequences (testable):**
- `search_trainings` returns citation-complete results ranked by relevance to a conceptual query.
- `get_training` returns the full training record only via this explicit tool, never embedded inline elsewhere.

#### FR-43: Concept/technique search tool
*(Added 2026-08-21, Sprint Change Proposal.)* Implement `search_concepts` (query by one or more technique tags — FR-42 — across `Example`, `Workflow`, `Guide`, and `Training` records in one federated, deduplicated, citation-complete result list, following the same fused-list convention as hybrid retrieval's FR-19). An unrecognized tag value is rejected with the valid vocabulary listed, never silently ignored.
**Consequences (testable):**
- `search_concepts` returns a single fused list spanning all four taggable record types, each entry citation-complete per FR-31.
- A query naming an unrecognized technique tag returns a structured error listing valid tag values, never an empty or guessed result.

#### FR-25: Transport, structured errors, bounded outputs
Streamable HTTP at `/mcp` (optional stdio from the same binary); structured errors, timeouts, limits; full pages/complete examples only via explicit retrieval tools. `→ brief §3, §10`
**Consequences (testable):**
- Every tool enforces bounded output (default 10, cap 50; ≤1 MiB with truncation/continuation metadata — see NFR-10).
- Errors are structured and typed, not free-text.

### F7. Index lifecycle: immutable build, validate, activate, roll back
**Description:** Every index is an immutable per-SDK-version build; validation precedes atomic activation; rollback flips only the active pointer; incompatible indexes are rejected. `→ brief §12`

#### FR-26: Immutable build + validate-before-activate + atomic activation
Builds go to a new immutable directory (SDK version + build ID); `validate-index` runs before the active pointer changes; activation is atomic; the currently-served index is never mutated. `→ brief §12`
**Consequences (testable):**
- Activation swaps a pointer atomically; a validation failure blocks activation.
- The serving index directory is never written during serve.

#### FR-27: Compatibility rejection
A schema, Lucene-major, canonical-symbol-format, embedding-model, dimension, tokenizer, or preprocessing-fingerprint change is treated as incompatible unless a tested migration exists; the server refuses to load a mismatched catalog, Lucene index, or model. `→ brief §12, §14`
**Consequences (testable):**
- The server rejects an incompatible catalog/Lucene/model at startup with a clear error.
- Manifest fingerprint mismatch prevents mixing an index with a different model/preprocessing.

#### FR-28: Rollback + retention + side-by-side versions
Rollback changes only the active pointer and reloads the service (no rebuild); at least the current + previous validated build is retained where storage allows; side-by-side SDK versions are supported with an explicit configured default. `→ brief §12`
**Consequences (testable):**
- A previously-validated build reactivates without rebuilding data.
- SDK versions remain isolated; no cross-version result unless explicitly requested.

#### FR-29: Complete-set backup
Backups cover manifest + catalog + Lucene dir + ingestion report + config + model artifacts + checksums together; a partial index is not a valid backup. `→ brief §12`
**Consequences (testable):**
- A restored backup contains all listed components and validates.

### F8. Agent guidance & citations
**Description:** A shipped agent skill file enforcing mandatory retrieval behavior, plus universal citation on primary results. `→ brief §9, §11`

#### FR-30: Agent instruction file (SKILL.md)
Ship `agent/SKILL.md` with the mandatory retrieval policy (search workflow/examples → resolve every class → exact constructors/overloads → follow linked relationships → `find_implementations` before choosing a concrete class → only MCP-returned symbols → never infer from memory → prefer trusted examples → `check_symbols` before finalizing → state unresolved rather than invent → cite) plus one worked "display a point object" sequence. `→ brief §11`
**Consequences (testable):**
- `SKILL.md` contains all 11 policy points and the worked example.
- It references tools by their exact §10 names.

#### FR-31: Universal citation on primary results
Every primary result includes SDK version, record type, relative source path, HTML anchor when available, and stable record ID; the canonical symbol/example ID is included when one is resolved for that record, and omitted — never fabricated — when it is not (e.g. a Guide or Training record with no resolved canonical-symbol link). *(Amended 2026-08-21, Sprint Change Proposal, fixing an AD-15 defect that made canonical symbol unconditionally required, which would have made any symbol-less record permanently unservable as a primary result.)* `→ brief §9`
**Consequences (testable):**
- Citation completeness is 100% of returned primary records on the gold set, including symbol-less records.
- A symbol-less Guide or Training record is servable as a primary result with a complete citation that simply omits the canonical-symbol field.

### F9. Packaging, deployment & offline/security posture
**Description:** One Docker image with `ingest`/`validate-index`/`serve` modes, deployable on a shared internal host, air-gap transferable. `→ brief §3, §5, §13, §14`

#### FR-32: One serving image, three modes, internal-host compose; separate ingest-time image for training materials
The serving image runs `ingest` (Javadoc, examples, workflows, guides), `validate-index`, and `serve`; Compose separates one-shot ingestion from read-only serving; `pull_policy: never`, non-root, read-only root FS where practical, cap-drop ALL, `no-new-privileges`, proprietary inputs mounted read-only, MCP port bound to the internal host interface reachable by the trusted team (not the public internet). Training-material ingestion (docling, FR-39) runs from a **separate, ingest-time-only image**, never deployed alongside `serve`, never included in the served appliance, and not subject to NFR-9's appliance-footprint target. *(Amended 2026-08-21, Sprint Change Proposal — training-material ingestion; amended 2026-08-24 — v1 serves a small trusted internal team from a shared host: loopback bind and "hardened appliance" framing dropped, container-hygiene defaults retained.)* `→ brief §13, §14`
**Consequences (testable):**
- The serve container runs read-only, non-root, bound to the internal host interface (not the public internet), answers MCP requests, and contains no Python/docling/network-capable tooling.
- Ingest (Javadoc/examples/workflows/guides) and serve make zero external calls during tests; only the separate training-ingest image is permitted network calls, and only while it is running.
- The separate training-ingest image is absent from the air-gap bundle's serving artifacts (FR-33).

#### FR-33: Air-gap bundle & provenance
Produce image tar (serving image only) + SHA256SUMS + SBOM + install docs + frozen index (which may already include training records ingested before transfer) + model artifacts; verify transferred artifacts by SHA-256; bundle model + tokenizer with documented licenses; support transferring a prebuilt combined index or rebuilding one combined index from approved inputs inside the air gap. Training-material ingestion, if used, must run before the air-gap boundary via the separate ingest-time image (FR-32) — it is never part of the transferred bundle. *(Amended 2026-08-21, Sprint Change Proposal.)* `→ brief §5, §14`
**Consequences (testable):**
- On a networking-disabled clean machine: image loads, fixture corpus ingests, MCP serves, smoke tests pass.
- SHA-256 verification detects a tampered artifact.
- The serving image tar contains no docling/Python training-ingest tooling.

#### FR-34: CLI surface
Provide `ingest`, `validate-index`, `serve`, `stats` with the documented options. `→ brief §12`
**Consequences (testable):**
- Each command runs headless with documented flags and exit codes.

### F10. Training-material ingestion via docling (scoped network/Python exception)
**Description:** A separate, ingest-time-only path that uses the Python `docling` library (vision/layout models) to convert PPTX/PDF training slide decks into structured content, linked to the catalog like every other record type. This is the sole ingest path permitted network access and non-Java tooling execution — an explicit, scoped exception to AD-9/AD-10, carved out only for this input type and never extended to serving. *(Added 2026-08-21, Sprint Change Proposal.)*

#### FR-39: Training-deck ingestion via docling
The ingester accepts a local training-materials dir (PPTX/PDF slide decks) as a sixth permitted input path. Training-deck ingestion is a single, explicitly-scoped exception to FR-1/AD-9 (zero egress) and AD-10 (parse-only): it invokes docling, which may reach the network to fetch/run vision-layout/table/OCR models, and executes non-Java tooling. This exception applies only within the separate training-ingest module/image (FR-32); no other ingest path or the `serve` path may perform network I/O or invoke non-Java tooling. Docling's structured output (slide/section title, ordered text blocks, tables, extracted figures/captions) is parsed into one `Training` record per logical deck section, mirroring FR-6's one-record-per-element discipline; malformed/unreadable decks degrade per the same `fail-fast | collect-and-continue` policy as FR-5.
**Consequences (testable):**
- Training-deck ingestion is the only scenario in the ingestion test suite permitted to observe network egress; every other ingestion test (Javadoc, examples, workflows, guides) still asserts zero egress.
- A malformed/unreadable slide-deck fixture is handled per the configured `fail-fast | collect-and-continue` policy, never crashing the run silently.
- A `Training` record is retrievable with its section title, ordered text blocks, and any extracted tables/figures intact.

#### FR-40: Training reference extraction & catalog linking
Luciad symbol/API references appearing in training-deck text and tables are resolved against the catalog using FR-7's canonical/normalized keys, run only after Javadocs are indexed; unresolved references stay explicitly unresolved per FR-10.
**Consequences (testable):**
- A resolvable reference in a training deck's text or table links to its catalog record.
- An unresolvable reference is recorded as unresolved, never fabricated.

## 5. Non-Goals (Explicit)

- **No compiling or executing** generated Luciad applications or ingested samples. `→ brief §2, §18`
- **No Maven/Gradle execution** against generated apps or samples (the appliance itself may use Maven/Gradle to build). `→ brief §2, §18`
- **No hosting** of Maven deps / Nexus / Artifactory; **no SDK JARs** required for ingestion or validation. `→ brief §2`
- **No running Luciad itself.** `→ brief §2`
- **No LangChain/LlamaIndex** as mandatory dependencies; **no external vector DB** (Elasticsearch/OpenSearch/Qdrant/etc.); **no separate local LLM**; **no server-side answer generation.** `→ brief §2`
- **No cloud embeddings, runtime internet, telemetry, or update checks** during serving or during ingestion of the four fully-offline record types (Javadoc, examples, workflows, guides). The sole exception is training-material ingestion via docling (FR-39), confined to its own separate ingest-time image and never extended to serving. *(Amended 2026-08-21, Sprint Change Proposal.)* `→ brief §2, §14`
- **No public, cross-org, or authenticated multi-tenant deployment in v1** (a single trusted internal team on a shared internal host is in scope); **no public-base/private-overlay index design in v1.** `→ brief §3, §5`
- **No HTTP crawler / portal client** in the production ingester, for any record type. Docling's own model-fetch (training-material ingestion only, FR-39, running in its separate ingest-time image) is the sole permitted model-downloader exception; it never runs inside the serving image. *(Amended 2026-08-21, Sprint Change Proposal.)* `→ brief §5, §12`
- **No automated inference of coding techniques or best practices** from source code, prose, or file naming. Technique tags (FR-42) are governance-supplied only, via the same sidecar mechanism as quality labels (FR-14), never derived by static analysis or ML. *(Added 2026-08-21, Sprint Change Proposal.)* `→ brief §8, §14`

## 6. MVP Scope

### 6.1 In Scope
Everything in the brief's §2 *Included* list and Definition of Done §18: file-only offline ingestion; structural Javadoc parsing; canonical catalog + relationship graph; example/workflow/guide/training-deck ingestion + linking; governance-supplied quality labels and cross-cutting technique tags (FR-42); hybrid (BM25+vector) retrieval with lexical fallback; the 20 MCP tools; immutable versioned indexes with validate/activate/rollback; shared internal-host deployment for a small trusted team (≤ ~5 developers); agent SKILL.md; air-gap packaging with SBOM/checksums/licenses. Guide-article support, training-deck (PPTX/PDF via docling) support, and technique-tag/`search_concepts` support all ship in MVP alongside the other original record types (decided 2026-08-20 and 2026-08-21, Sprint Change Proposals). `→ brief §2, §18`

### 6.2 Out of Scope for MVP
- Authenticated-TLS multi-tenant (cross-team / org-wide) profile — deferred (later deployment profile). `→ brief §3`
- Public-base/private-overlay index layering — deferred until a demonstrated need. `→ brief §5`
- Automated corpus acquisition/downloader — separate connected-environment release-engineering activity, never in the runtime image. `→ brief §5`
- Python tooling (ONNX export, model comparison, retrieval eval) ships as release-engineering utilities only — never a runtime service/dependency — for the four fully-offline record types. Training-material ingestion (FR-39) is the sole exception: docling is a genuine ingest-time Python runtime dependency, network-enabled, confined to its own separate ingest-time image (FR-32) and never present in the serving image. *(Amended 2026-08-21, Sprint Change Proposal.)* `→ brief §2, §16`

## 7. Success Metrics

Derived from brief §15 key measures and §14 targets; each validates the noted FR(s)/NFR(s).

**Primary**
- **SM-1: Exact canonical lookup = 100%** on indexed fixtures (hybrid preserves this — no regression). Validates FR-16, FR-21. `→ brief §15`
- **SM-2: Unknown-symbol safety = never marked valid.** Validates FR-10, FR-24, FR-30. `→ brief §15`
- **SM-3: Natural-language retrieval ≥ 90%** of approved conceptual gold queries return an expected workflow/example/API record in the top 5 (initial release acceptance threshold; per-query results retained; threshold frozen only after product-owner approval — see B2). Validates FR-16–FR-19. `→ brief §15, §18`
- **SM-4: Citation completeness = 100%** of returned primary records. Validates FR-31. `→ brief §15`
- **SM-5: Version isolation** — no cross-version result unless explicitly requested. Validates FR-28. `→ brief §15`

**Secondary**
- **SM-6: Overload ambiguity always explicit.** Validates FR-6, FR-21. `→ brief §15`
- **SM-7: Relationship integrity** — all resolvable synthetic links produce correct typed edges. Validates FR-9. `→ brief §15`
- **SM-8: Ranking comparison** — the selected hybrid config materially outperforms/complements the lexical baseline on approved conceptual queries. Validates FR-18, FR-19. `→ brief §15`
- **SM-9: Offline operation** — zero external calls during ingest/serve tests. Validates NFR-11, FR-1, FR-32. `→ brief §15`

**Counter-metrics (do not optimize)**
- **SM-C1: Do not raise SM-3 by weakening exact-symbol safeguards.** Counterbalances SM-3 against SM-1/SM-2 — if the candidate model misses the threshold, compare approved alternatives or improve semantic record construction; never hide the miss by relaxing exact validation. `→ brief §15`

## Cross-Cutting NFRs

From brief §14. All are *initial v1 targets*; changes require product- and technical-owner approval, not silent relaxation. **All latency/memory/throughput targets are unfalsifiable until B1 (reference hardware + representative corpus) is fixed** — see Open Questions.

| ID | Requirement | Target | Provenance |
|---|---|---|---|
| NFR-1 | Exact lookup latency | p95 ≤ 250 ms | §14 |
| NFR-2 | Hybrid search latency | p95 ≤ 2 s (warm model + index) | §14 |
| NFR-3 | Concurrency | ≥ 4 simultaneous MCP requests, no errors / unbounded queue | §14 |
| NFR-4 | Startup | ready to serve a prebuilt index ≤ 30 s | §14 |
| NFR-5 | Serving memory | ≤ 8 GiB RSS on reference corpus | §14 |
| NFR-6 | Ingestion memory | ≤ 16 GiB RSS on reference corpus | §14 |
| NFR-7 | Full rebuild | ≤ 8 h on 8-core CPU workstation for reference corpus (throughput recorded) | §14 |
| NFR-8 | Index footprint | ≤ 4× source corpus bytes (exceptions need a recorded trade-off) | §14 |
| NFR-9 | Appliance footprint | image + model ≤ 1.5 GiB (excl. corpus/indexes); applies to the serving image only — the separate training-ingest image (FR-32) is a build-time-only artifact not subject to this target. *(Note added 2026-08-21, Sprint Change Proposal.)* | §14 |
| NFR-10 | Response bounds | default 10, cap 50 results; ≤ 1 MiB serialized with truncation/continuation metadata | §14 |
| NFR-11 | Offline behavior | zero DNS/HTTP/telemetry/model-download/update-check during serve and during ingestion of the four fully-offline record types (Javadoc, examples, workflows, guides); training-material ingestion (FR-39) is the sole, explicitly-scoped exception, confined to its separate ingest-time image and never extended to serving. *(Amended 2026-08-21, Sprint Change Proposal.)* | §14 |
| NFR-12 | Security posture | non-root, read-only root FS where practical, cap-drop, internal-host bind (no public exposure), no full-doc/source logging by default | §14 |
| NFR-13 | Reproducibility | deterministic record IDs + content hashes for repeatable updates | §12 |
| NFR-14 | Compatibility enforcement | reject any index whose fingerprint (schema/Lucene-major/symbol-format/model/dims/tokenizer/preprocessing) mismatches runtime. *Note (2026-08-21): the fingerprint is independent of which record types are indexed — adding the guide record type changes index content, not the fingerprint definition; a guide-inclusive index remains compatible with prior-fingerprint validation logic.* | §12, §14 |

## Constraints and Guardrails

- **Licensing:** treat the completed index and embeddings as derivative documentation artifacts; complete legal/license review before distributing documentation-derived indexes or model artifacts (blocker B3). `→ brief §14`
- **Provenance:** public Javadocs acquired in a connected environment must record product/version + provenance and confirm redistribution/derivative-indexing permission before becoming a checksummed input dir. `→ brief §5`
- **Governance (§14):** quality labels and technique tags are governance metadata, not parser/ML inference; five named roles (product owner, technical owner, corpus owner, security/legal reviewer, release operator) own the sign-offs (blocker B4). The technique-tag vocabulary's actual content is a corpus-owner decision, deferred (see the architecture doc's Deferred section) — the tagging mechanism and governance are already ratified. *(Amended 2026-08-21, Sprint Change Proposal.)* `→ brief §14`
- **Human sign-offs (§19) — none auto-closed:** final SQLite schema · Lucene schema · canonical-symbol & relationship formats · MCP tool schemas · embedding model + license + tokenizer/preprocessing fingerprint + retrieval thresholds · NFR benchmark results · corpus provenance/license review · release activation. `→ brief §14, §19`

## API Contracts / Public Surface

The public surface is the 19 MCP tools (FR-21–FR-25, FR-38, FR-41, FR-43) whose input/output contracts are specified in brief §10 (FR-38 added 2026-08-21, FR-41 added 2026-08-21, and FR-43 added 2026-08-21, Sprint Change Proposals — contracts to be specified alongside the others at architecture sign-off). The **final tool schemas require sign-off** and are ratified in the architecture step, not here. Breaking changes to tool schemas or the canonical-symbol/relationship formats bump the compatibility fingerprint (NFR-14). `→ brief §10, §12`

## 8. Open Questions

Single registry; blockers must clear before the noted milestones (they do **not** block starting Epics 1–4).

1. **B1 — Reference hardware + representative corpus undefined.** All of NFR-1…NFR-9 are unfalsifiable until fixed. *Owner:* product + technical owner. *Blocks:* NFR sign-off, Epic 8. `→ brief §14`
2. **B2 — Gold retrieval dataset not yet built; 90% top-5 (SM-3) provisional.** *Owner:* product + corpus owner. *Blocks:* Epic 5 acceptance, Definition of Done. `→ brief §15, §18`
3. **B3 — Legal/license review for redistributing docs-derived indexes + model artifact not done.** *Owner:* security/legal reviewer. *Blocks:* Epic 8 release. `→ brief §14`
4. **B4 — Governance roles defined but not assigned to people.** *Owner:* product owner to assign. *Blocks:* every sign-off. `→ brief §14`
5. **B5 — JDK pin undecided** ("Java 21 or org's approved current LTS"). *Owner:* technical owner. *Blocks:* Epic 1 skeleton + manifest. `→ brief §3`

**Design questions to resolve in the architecture step (not blockers):**
6. ONNX embedding-session concurrency/thread-safety strategy vs NFR-3 (session pooling?). `→ brief §3, §14`
7. `get_method`/`get_class` `className` input: simple name vs FQN + ambiguity handling (folds into the MCP-schema sign-off). `→ brief §10`
8. NFR-8 (≤4×) and NFR-9 (≤1.5 GiB) feasibility risks — vector bytes scale with record count, not source size; base-image digest choice matters.

**Minor discrepancies (non-blocking):**
9. Brief §19 says store the doc as `docs/luciad-knowledge-mcp-brief.md`, but the actual file is `docs/luciad-knowledge-mcp-implementation-brief.md`. Kickoff/SKILL references should use the real path.
10. NFR-4 (30 s startup) must be read as "prebuilt index, model-load-inclusive on reference hardware," not cold-corpus.

## 9. Assumptions Index

- [ASSUMPTION §0/§2.3] The only consumer interface is the MCP tool surface (no human UI); UX artifact intentionally skipped. `[NEEDS CONFIRM]`
- [ASSUMPTION §6.2] Python evaluation tooling is release-engineering-only and excluded from the runtime image/dependency graph. (Directly from brief §2/§16 — high confidence.)
- [ASSUMPTION NFR-1…9] "Reference corpus/hardware" will be supplied by owners during Epic 5/8; targets are placeholders until B1 closes.
- [ASSUMPTION SM-3] The 90% top-5 threshold stays provisional until B2's gold set exists and the product owner approves the threshold.
- [ASSUMPTION F6] Final MCP tool JSON schemas are deferred to the architecture step's sign-off; the §10 text is the contract until then.
