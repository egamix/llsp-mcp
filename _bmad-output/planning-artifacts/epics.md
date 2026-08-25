---
stepsCompleted: ["step-01-validate-prerequisites", "step-02-design-epics", "step-03-create-stories"]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-luciad-mcp-2026-08-20/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md
  - docs/luciad-knowledge-mcp-implementation-brief.md
---

# LuciadLightspeed Knowledge MCP - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the LuciadLightspeed Knowledge MCP, decomposing the requirements from the PRD and Architecture Spine into implementable stories. There is no UX design contract — the only consumer interface is the MCP tool surface (no human UI), per PRD assumption §9.

## Requirements Inventory

### Functional Requirements

**F1. Offline, file-only corpus ingestion**
- FR-1: Ingester accepts only a local Javadoc dir, local samples/workflows dir, local guides dir, local training-materials dir, local model dir, and local output dir — nothing else. The five non-training input paths assert zero DNS/HTTP/socket egress; the sixth path (training-materials) carries the sole scoped exception below. No crawler/portal client/login automation/model downloader in the runtime image. The sole exception is FR-39's docling model-fetch, confined to training-material ingestion in a separate ingest-time image, never in the serving image. *(Amended 2026-08-21 to add the guides dir, Sprint Change Proposal; amended again 2026-08-21 to add the training-materials dir with a scoped exception to FR-39, Sprint Change Proposal.)*
- FR-2: Multi-stage, resumable ingestion pipeline (catalog → relationships → examples/workflows → lexical index → embeddings → vector index → validation → atomic activation) with checkpoint/resume for large embedding runs; configurable `fail-fast | collect-and-continue` policy for malformed pages.
- FR-3: Deterministic, hashed, reportable ingestion — stable record IDs and content hashes for repeatable re-ingestion; machine-readable ingestion report with throughput and error summaries.

**F2. Javadoc structural parsing & canonical symbols**
- FR-4: Search-index-assisted discovery (`*-search-index.js`) with recursive `*.html` fallback; page type detected by structure, not filename; nav/tree/help/index pages excluded from member ingestion; overview/package pages kept as narrative records.
- FR-5: Modern (`section.class-description`/member-summary/`section.detail`) and legacy (`ul.blockList`/`li.blockList`/`h3`/`h4`) layouts parse into equivalent extracted fields; broken/incomplete HTML degrades gracefully per policy.
- FR-6: One record per API element; overloads are always distinct records; no primary record spans more than one semantic API element.
- FR-7: Canonical symbol keys distinguish overloads; normalized simple-name, dotted-member, erased-generic, and camel-case keys retained for lookup.

**F3. Canonical catalog & relationship graph**
- FR-8: Versioned SQLite catalog stores canonical symbols with unique, version-scoped keys and maps relative HTML paths/anchors to symbols.
- FR-9: Two-pass typed relationship construction — pass one builds the catalog + path/anchor map, pass two resolves links/declarations into typed edges, forward/inverse pairs reconciled; all 17 required relationship types produced (AD-30; the 3 example edges emitted in Epic 3).
- FR-10: Unresolved external/undocumented links stay explicit, never guessed; traversal restricted to the documented SDK universe and reports that scope.
- FR-11: Bounded direct and recursive (CTE) traversal with depth limits and cycle handling.

**F4. Example, workflow & guide ingestion and linking**
- FR-12: `.java` examples parsed via JavaParser AST; complete source preserved; large files get secondary method/class chunks; examples are never compiled or executed.
- FR-13: Luciad imports and syntactically identifiable references extracted and linked to catalog entries after Javadocs are indexed; unresolved references stay unresolved.
- FR-14: Example quality (`official`/`validated`/`best-practice`/`illustrative`/`legacy`) supplied only via governance sidecar metadata, never filename or parser inference.
- FR-15: Workflow docs (Markdown/YAML) ingested with prerequisites, ordered steps, referenced symbols, linked examples, limitations, version metadata; linked to catalog symbols/examples.
- FR-35: Guide HTML tutorial/how-to pages parsed into title, ordered prose sections, and embedded code snippets; malformed/broken HTML degrades gracefully per the same policy as FR-5, never aborting the whole ingest run. *(Added 2026-08-21, Sprint Change Proposal.)*
- FR-36: Luciad symbol/API references in guide prose and code snippets resolved against the catalog using FR-7's canonical/normalized keys; unresolved references stay explicitly unresolved per FR-10. *(Added 2026-08-21, Sprint Change Proposal.)*
- FR-42: Cross-cutting technique tags — a curated, fixed vocabulary of coding-technique/best-practice labels supplied only via the same governance sidecar mechanism as FR-14, attachable to `Example`, `Workflow`, `Guide`, and `Training` records (never `ApiElement`) independent of any resolved canonical-symbol link; an out-of-vocabulary tag value is rejected at ingest. *(Added 2026-08-21, Sprint Change Proposal.)*

**F5. Hybrid retrieval (exact + lexical + vector fusion)**
- FR-16: Routed retrieval detects exact fully-qualified signatures and resolves them first; exact-symbol matches heavily boosted over lexical/vector; exact validation always overrides semantic similarity.
- FR-17: `canonicalSymbol` + normalized variants indexed as exact/prefix/camel-case fields; descriptions/params/returns/exceptions/example-workflow prose as analyzed text; BM25 with filters and highlighting.
- FR-18: Local ONNX vector search over normalized semantic records; never downloads a model; explicit failure on model-load error; lexical fallback only when configured, visible in `index_status`.
- FR-19: Lexical + vector fusion (e.g. RRF) under exact priority; top candidates enriched with bounded relationship neighborhood; results concise, cited, with stable IDs.
- FR-20: Same pinned model/tokenizer/preprocessing/normalization/dimensions/query instructions used for indexing and runtime queries; navigation/duplicate/boilerplate/empty content excluded from embedding.

**F6. MCP tool surface (20 tools)** *(15 → 17 → 19 → 20, 2026-08-21, Sprint Change Proposal.)*
- FR-21: Retrieval & symbol tools — `search_api`, `resolve_symbol`, `get_class`, `get_method`, `get_api_context`.
- FR-22: Relationship tools — `get_inheritance`, `get_type_relationships`, `find_implementations`, `find_subclasses` — typed, cited edges within the documented SDK universe.
- FR-23: Example & workflow tools — `search_examples`, `find_examples_for_symbol`, `get_example`, `search_workflows`.
- FR-24: Validation & status tools — `check_symbols` (exact-valid / ambiguous-with-candidates / unknown; catalog-only, no compilation) and `index_status` (versions, record counts, ingestion timestamp, retrieval mode, model status, schema/index version).
- FR-38: Guide tools — `search_guides` and `get_guide`, both citation-bearing per FR-31. *(Added 2026-08-21, Sprint Change Proposal.)*
- FR-41: Training tools — `search_trainings` and `get_training`, both citation-bearing per FR-31. *(Added 2026-08-21, Sprint Change Proposal.)*
- FR-43: Concept/technique search tool — `search_concepts`, federating technique-tag matches (FR-42) across `Example`/`Workflow`/`Guide`/`Training` into one fused, citation-complete list; rejects unrecognized tag values. *(Added 2026-08-21, Sprint Change Proposal.)*
- FR-25: Streamable HTTP transport at `/mcp` (optional stdio); structured errors, timeouts, limits; full pages/complete examples only via explicit retrieval tools.

**F7. Index lifecycle: immutable build, validate, activate, roll back**
- FR-26: Builds go to a new immutable directory (SDK version + build ID); `validate-index` runs before activation; activation is atomic; serving index is never mutated.
- FR-27: Schema/Lucene-major/canonical-symbol-format/embedding-model/dimension/tokenizer/preprocessing-fingerprint mismatch is rejected unless a tested migration exists.
- FR-28: Rollback changes only the active pointer (no rebuild); at least current + previous validated build retained where storage allows; side-by-side SDK versions supported with an explicit configured default.
- FR-29: Backups cover manifest + catalog + Lucene dir + ingestion report + config + model artifacts + checksums together; a partial index is not a valid backup.

**F8. Agent guidance & citations**
- FR-30: Ship `agent/SKILL.md` with the mandatory 11-point retrieval policy plus one worked "display a point object" sequence, referencing tools by their exact §10 names.
- FR-31: Every primary result includes SDK version, record type, relative source path, HTML anchor when available, and stable record ID; canonical symbol/example ID is included when resolved and omitted — never fabricated — otherwise, so a symbol-less Guide/Training record remains servable. *(Amended 2026-08-21, Sprint Change Proposal — fixes an AD-15 defect that previously required canonical symbol unconditionally.)*

**F9. Packaging, deployment & offline/security posture**
- FR-32: One serving image runs `ingest` (four offline record types)/`validate-index`/`serve`; Compose separates one-shot ingestion from read-only serving; `pull_policy: never`, non-root, read-only root FS where practical, cap-drop ALL, `no-new-privileges`, proprietary inputs read-only, MCP bound to the internal host interface reachable by the trusted team (not the public internet). Training-material ingestion (FR-39) runs from a **separate, ingest-time-only image**, never in the served appliance, exempt from NFR-9. *(Amended 2026-08-21, Sprint Change Proposal; amended 2026-08-24 — shared internal-host deployment, loopback bind and "hardened" framing dropped, container-hygiene defaults retained.)*
- FR-33: Air-gap bundle = image tar (serving image only) + SHA256SUMS + SBOM + install docs + frozen index + model artifacts; SHA-256 verification; documented licenses; supports transferring a prebuilt combined index or rebuilding one inside the air gap. A frozen index may already include training records ingested pre-transfer via the separate image; the serving image tar contains no docling/Python tooling. *(Amended 2026-08-21, Sprint Change Proposal.)*
- FR-34: CLI provides `ingest`, `validate-index`, `serve`, `stats` with documented options, headless, with exit codes.

**F10. Training-material ingestion via docling (scoped network/Python exception)** *(Added 2026-08-21, Sprint Change Proposal.)*
- FR-39: Training-deck (PPTX/PDF) ingestion via docling's vision/layout-model pipeline, extracting each slide-deck section into a title, ordered text blocks, and any extracted tables/figures; runs only inside `ingest.trainings`, invoked by `cli ingest` against the local training-materials dir; the sole path in this system permitted network access (to fetch/run docling's models) and non-Java tooling execution; ships only in a separate, ingest-time-only image, never in the serving image.
- FR-40: Training reference extraction & catalog linking — Luciad symbol/API references in extracted training text resolved against the catalog using FR-7's canonical/normalized keys, run only after Javadocs are indexed; unresolved references stay explicitly unresolved per FR-10.
- FR-42 (also covered here): `Training` records are a taggable target for cross-cutting technique tags — see FR-42 under F4. Ingestion of the tag itself is via the same sidecar mechanism, applied to training decks by `ingest.trainings`.

### NonFunctional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-1 | Exact lookup latency | p95 ≤ 250 ms |
| NFR-2 | Hybrid search latency | p95 ≤ 2 s (warm model + index) |
| NFR-3 | Concurrency | ≥ 4 simultaneous MCP requests, no errors / unbounded queue |
| NFR-4 | Startup | ready to serve a prebuilt index ≤ 30 s |
| NFR-5 | Serving memory | ≤ 8 GiB RSS on reference corpus |
| NFR-6 | Ingestion memory | ≤ 16 GiB RSS on reference corpus |
| NFR-7 | Full rebuild | ≤ 8 h on 8-core CPU workstation for reference corpus (throughput recorded) |
| NFR-8 | Index footprint | ≤ 4× source corpus bytes (exceptions need a recorded trade-off) |
| NFR-9 | Appliance footprint | image + model ≤ 1.5 GiB (excl. corpus/indexes) — **serving image only**; the separate training-ingest image is exempt and intentionally left unbounded, since it is ingest-time-only and never deployed to or alongside the appliance *(2026-08-21, Sprint Change Proposal)* |
| NFR-10 | Response bounds | default 10, cap 50 results; ≤ 1 MiB serialized with truncation/continuation metadata |
| NFR-11 | Offline behavior | zero DNS/HTTP/telemetry/model-download/update-check during serve and during ingest of the four offline record types; training-material ingestion (FR-39) is the sole explicitly-scoped exception *(2026-08-21, Sprint Change Proposal)* |
| NFR-12 | Security posture | non-root, read-only root FS where practical, cap-drop, internal-host bind (no public exposure), no full-doc/source logging by default |
| NFR-13 | Reproducibility | deterministic record IDs + content hashes for repeatable updates |
| NFR-14 | Compatibility enforcement | reject any index whose fingerprint (schema/Lucene-major/symbol-format/model/dims/tokenizer/preprocessing) mismatches runtime |

NFR-1..9 are **unfalsifiable until B1** (reference hardware + representative corpus) is fixed by the product/technical owner — see Open Questions/Blockers below. This does not block starting Epics 1–4.

### Additional Requirements

**Architecture invariants (AD-1..AD-29, binding on implementation — see ARCHITECTURE-SPINE.md for full rule text):**
- AD-1: Dependencies point inward to `domain`; no module cycles; engines reached only through domain-defined ports.
- AD-2: Embedded engines only (SQLite/Lucene/ONNX in-process); single internal-host MCP listener; no other server socket.
- AD-3 / AD-18: Canonical symbol is the universal, primary join key; exactly one canonical-symbol variant is the cross-module wire/join key — all other normalized variants are module-internal only. AD-29 adds technique tags as a secondary, non-symbol join key alongside this — never displacing it.
- AD-4: Two-pass ingestion; unresolved references are recorded explicitly, never fabricated.
- AD-5 / AD-23: Immutable index builds keyed `{sdkVersion}/{buildId}`; validate-then-atomic-activate; only `cli` writes the active pointer via atomic rename; serving process re-resolves the pointer at a defined boundary; a build directory is never deleted while referenced.
- AD-6: Compatibility fingerprint gate on load (schema/Lucene-major/symbol-format/model/dims/tokenizer/preprocessing) — NFR-14.
- AD-7 / AD-7a: Exact-symbol priority is inviolable, but only after SDK version scope is bound; `check_symbols` never marks unknown as valid.
- AD-8 / AD-21: Identical embedding fingerprint at index-time and query-time; all text preprocessing lives once, behind the `Embedder` port.
- AD-9: Zero egress — no DNS/HTTP/telemetry/model-download/update-check anywhere except `ingest.trainings`, which carries the sole, explicitly-scoped exception defined by AD-28; model/tokenizer loaded only from local filesystem path.
- AD-10: Parse-only — JSoup/JavaParser static analysis; no compiling, symbol-solving against JARs, Maven/Gradle invocation, or executing ingested/generated code — except `ingest.trainings`, which carries the sole, explicitly-scoped exception defined by AD-28.
- AD-28: Training-material ingestion is a scoped, explicit exception to AD-9/AD-10 — `ingest.trainings` is the sole module permitted network access (docling's vision-layout models) and non-Java tooling execution (Python/docling), only via `cli ingest`. Ships as a separate, ingest-time-only image, never in the serving image that runs `cli serve`. The served appliance remains fully air-gapped and zero-egress (Epic 8, NFR-11) unchanged. *(Added 2026-08-21, Sprint Change Proposal.)*
- AD-11: Bounded responses everywhere — default page 10, hard cap 50, ≤1 MiB response ceiling, explicit truncation metadata; full source only via dedicated `get_*` tools.
- AD-12: SDK-version scoping is mandatory on every record/edge/index segment/query; no cross-version results unless explicitly requested.
- AD-13: Bounded, thread-safe ONNX session pool sized for ≥4 concurrent requests (NFR-3); retrieval handlers are stateless per request over the read-only active index.
- AD-14: Trust/quality metadata is governance input only, never inferred by parsers. Whether `Guide` records get a governance quality-tag sidecar under this rule (like examples/workflows) or ship untagged is an open `[NEEDS APPROVAL]` design question — see Deferred, must resolve before Story 3.5 is sized. The same open question applies to `Training` records — user explicitly chose to leave it open/deferred; see Deferred. Amended (2026-08-21, Sprint Change Proposal) to extend this same sidecar mechanism to technique tags (AD-29) — a second, independent metadata field, orthogonal to the still-open quality-tag questions above.
- AD-15 / AD-24: Full citation tuple required on every primary-result payload; each of the 20 tools (per the Architecture Spine's own tool-count convention) is enumerated as primary-result (citation-complete filter applies) or verdict/navigation (`check_symbols`, `resolve_symbol`, `index_status` — never dropped for missing citation). Primary-result tools include `search_guides`/`get_guide`, `search_trainings`/`get_training`, and `search_concepts`. *(Amended 2026-08-21, Sprint Change Proposal: AD-15's citation tuple no longer requires canonical symbol unconditionally — it is included when resolved, omitted when not — fixing a defect that would have made symbol-less records permanently unservable.)*
- AD-29: Technique tags are a secondary, non-symbol join key — a curated, fixed, sidecar-supplied vocabulary (governed like AD-14's quality labels) attachable to `Example`/`Workflow`/`Guide`/`Training` records, indexed as a filterable Lucene field, and federated by the new `search_concepts` tool (FR-43). Closes the gap where content with no resolved canonical-symbol link was undiscoverable. *(Added 2026-08-21, Sprint Change Proposal.)*
- AD-16: `application.yaml` is the sole runtime config; logs never emit full document bodies/source text by default.
- AD-17: `cli ingest` is the sole build coordinator; computes the one `buildId` as a content hash over the complete ordered input set; ingest modules never mint their own build ID or directory.
- AD-19: Fixed retrieval pipeline order — bind version → exact-first fusion → citation-completeness filter → deterministic ranking → bounded pagination; `total` = citation-complete/version-scoped/fused candidates before pagination.
- AD-20: Total deterministic result order — primary key fused score, secondary tie-break canonical wire/join key ascending; no ordering dependent on iteration/thread/wall-clock.
- AD-22: `catalog.sqlite` is the sole authority for record/edge identity, computed from a domain-declared `identityFields` spec.
- AD-25: Checkpoints write only to build-scoped staging inside the in-progress `{sdkVersion}/{buildId}`; a changed input set is a new `buildId`, not a resume.
- AD-26: Model-load failure and lexical fallback are always explicit; hybrid never silently degrades to lexical-only without config opt-in + `index_status` signal.
- AD-27: Container hardening (non-root, read-only root FS, cap-drop, `no-new-privileges`, read-only proprietary mounts) is a fixed build/packaging invariant, not per-deployment discretion.

**Stack pins (from Architecture Spine "Stack" table):**
- Java 21 LTS (pin pending blocker B5), Gradle multi-project (Kotlin DSL — `settings.gradle.kts` + per-module `build.gradle.kts`; wrapper pinned to 8.10), Apache Lucene 10.5.1, MCP Java SDK 2.0.1, ONNX Runtime (Java) 1.29.0, SQLite JDBC (Xerial) 3.53.2.1, JSoup 1.23.1, JavaParser 3.28.2.
- Embedding model candidate: BAAI/bge-small-en-v1.5 (384-dim) — subject to the model-selection gate; not locked.
- Python permitted only for offline model prep/eval tooling under `tools/evaluation/`, never a runtime dependency.
- docling (training-deck ingestion, `ingest.trainings` only) — network-enabled, AD-28 exception, ships only in the separate ingest-time image; version pin `[NEEDS APPROVAL]`, distinct from and never mixed with the offline-eval-only Python tooling above. *(Added 2026-08-21, Sprint Change Proposal.)*

**Module/source-tree seed (informs Epic 1 scaffolding — no external starter template used):**
- Gradle multi-project aggregator (`settings.gradle.kts`); root package `com.luciadmcp.knowledge` with modules `domain`, `config`, `ingest.javadoc`, `ingest.examples`, `ingest.guides`, `ingest.trainings`, `catalog.sqlite`, `search.lucene`, `embeddings.onnx`, `retrieval`, `mcp`, `cli`. *(`ingest.guides` added 2026-08-21, Sprint Change Proposal; `ingest.trainings` added 2026-08-21, Sprint Change Proposal — packaged/shipped separately per AD-28, never in the serving image.)*
- `packaging` (compose.yaml, Dockerfile, scripts/) is a build/deploy layer, not a Java module.
- Repository layout per brief §4 and spine "Structural Seed": `agent/SKILL.md`, `tools/evaluation/` (Python, release-engineering only), `scripts/` (build-bundle.sh, ingest.sh, start.sh, smoke-test.sh), `models/`, `indexes/`.
- No proprietary Luciad documentation/samples may be committed to the repo; tests use synthetic fixtures reproducing the required HTML/source structures.

**Deferred approval gates (each blocks its noted epic's completion, not its start — see ARCHITECTURE-SPINE.md "Deferred"):**
- MCP tool request/response JSON schemas beyond the fixed envelope — gate owned by Epic 6.
- SQLite catalog schema (DDL, adjacency + recursive-CTE representation) — gate owned by Epic 3.
- Lucene schema (fields, analyzers, BM25 params, KNN/HNSW config) — gate owned by Epic 5.
- Canonical-symbol & relationship serialization format (string grammar, on-disk edge format, join variant per AD-18) — gate owned by Epic 3.
- Embedding model selection (final model + dimension + license + fingerprint + retrieval thresholds) — ties to blockers B2/B3.
- Malformed-page ingestion policy default and collect-and-continue report shape — owned by Epic 2.
- Build retention count and backup/rollback ops procedure — owned by Epic 7 (AD-23 already fixes GC-safety).
- Guide governance/quality-tagging model (sidecar under AD-14 like examples/workflows, or untagged since vendor-authored) — must resolve before Epic 3 Story 3.5 is sized. *(Added 2026-08-21, Sprint Change Proposal.)*
- Training governance/quality-tagging model (same AD-14 question as Guide, above) — user explicitly chose to leave this open/deferred; must resolve before Epic 9's stories are sized. *(Added 2026-08-21, Sprint Change Proposal.)*
- docling version pin for `ingest.trainings` — deferred alongside the JDK pin (B5); owned by Epic 9.
- Initial technique-tag vocabulary content (e.g. the actual list of values like `resource-lifecycle`, `builder-pattern`) — AD-29 fixes the mechanism/governance, not the content; a corpus-owner decision, independent of the still-open Guide/Training quality-tag questions above. Must resolve before Story 3.3 (amended), Story 5.3d, and Story 9.3 are sized. *(Added 2026-08-21, Sprint Change Proposal.)*

**Open questions / blockers (registry from PRD §8 — do not block starting Epics 1–4, but gate the noted milestones):**
- B1: Reference hardware + representative corpus undefined — blocks NFR sign-off, Epic 8.
- B2: Gold retrieval dataset not yet built; 90% top-5 (SM-3) is provisional — blocks Epic 5 acceptance, Definition of Done.
- B3: Legal/license review for redistributing docs-derived indexes + model artifact not done — blocks Epic 8 release.
- B4: Governance roles (product owner, technical owner, corpus owner, security/legal reviewer, release operator) defined but not assigned to people — blocks every sign-off.
- B5: JDK pin undecided (Java 21 vs org's approved current LTS) — blocks Epic 1 skeleton + manifest.

**Testing strategy requirements (brief §15 — inform per-epic acceptance criteria):**
- Synthetic Javadoc fixtures covering modern/legacy layouts, interfaces/abstract classes, constructors, overloaded methods, generics/arrays, deprecated elements, since/params/returns/throws, inherited-method sections, broken/incomplete HTML.
- Unit tests: canonical symbol normalization, overload distinction, Javadoc extraction, example AST parsing, example-to-symbol linking, link/anchor resolution, all 17 relationship types (incl. `USES_TYPE`/`CONSTRUCTS`/`CALLS`), example resolution ladder + evidence, transitive/recursive traversal with cycles and depth limits, SDK-version filtering, exact resolution + ambiguity/not-found behavior, ranking/citation completeness, embedding fingerprint compatibility, hybrid fusion preserving exact priority.
- Integration tests: ingest synthetic corpus → start MCP server → call every tool → confirm bounded outputs/structured errors/no network access/read-only serving/rejection of incompatible index.
- Retrieval evaluation: maintain a gold dataset (exact lookup, ambiguous overloads, conceptual queries, deprecated APIs, example-preferred requests, nonexistent classes, interface-to-implementation navigation, example-preferred-implementation queries) against the SM-1..SM-9 / SM-C1 measures in the PRD. *(Added 2026-08-25 Sprint Change Proposal — AD-30, ties to B2:)* example-focused gold cases — a question answered by one method inside a large example; a constructor + required sequence of calls; a local helper method leading to Luciad calls; an ambiguous simple class name from two packages; an overloaded method unresolvable from arity alone; a sample with missing dependencies; whole-example-only vs method-level retrieval comparison (adopt method-level only if it improves or usefully complements top-5 without creating false verified symbols).

**Governance & Definition of Done requirements (brief §14, §18 — inform release-readiness stories, likely Epic 8):**
- Five named roles (product owner, technical owner, corpus owner, security/legal reviewer, release operator) each own explicit sign-offs; quality labels are governance metadata only.
- Human sign-offs required, none auto-closed: final SQLite schema, Lucene schema, canonical-symbol & relationship formats, MCP tool schemas, embedding model + license + tokenizer/preprocessing fingerprint + retrieval thresholds, NFR benchmark results, corpus provenance/license review, release activation.
- Full Definition of Done per brief §18 (offline build/serve, exact + relationship traversal, not_found on unknown symbols, 100% citation, version isolation, hybrid default with lexical fallback, ≥90% top-5 gold-query recall, transferable air-gap release, validated-build reactivation without rebuild, approved NFR targets, zero forbidden compilation/execution/external-DB usage).

### UX Design Requirements

None. The PRD (§2.2, §9 assumptions) confirms the only consumer interface is the MCP tool surface consumed by a coding agent — there is no human UI, and a UX design contract was intentionally skipped `[NEEDS CONFIRM in PRD, treated as accepted for this breakdown]`.

### FR Coverage Map

FR-1: Epic 1 - Local-only ingester inputs, zero egress, enforced at pipeline scaffold level
FR-2: Epic 1 - Multi-stage resumable pipeline skeleton with configurable malformed-input policy
FR-3: Epic 1 - Deterministic hashed record IDs and machine-readable ingestion report
FR-4: Epic 2 - Search-index-assisted + recursive-fallback Javadoc page discovery and typing
FR-5: Epic 2 - Modern and legacy Javadoc layout parsing into equivalent extracted fields
FR-6: Epic 2 - One record per API element; overloads always distinct
FR-7: Epic 2 - Canonical symbol keys and normalized lookup variants
FR-8: Epic 2 - Versioned SQLite catalog with unique version-scoped symbol keys
FR-9: Epic 2 - Two-pass typed relationship construction across all 17 relationship types (AD-30; `USES_TYPE`/`CONSTRUCTS`/`CALLS` emitted by Epic 3 example linking)
FR-10: Epic 2 - Unresolved external/undocumented links stay explicit, never guessed
FR-11: Epic 2 - Bounded direct and recursive traversal with depth limits and cycle handling
FR-12: Epic 3 - Java example ingestion via JavaParser AST, never compiled or executed
FR-13: Epic 3 - Example-to-catalog linking via imports and syntactic references
FR-14: Epic 3 - Example quality labels from governance sidecar metadata only
FR-15: Epic 3 - Workflow doc ingestion linked to catalog symbols and examples
FR-35: Epic 3 - Guide article HTML parsing into title, prose sections, and code snippets
FR-36: Epic 3 - Guide reference extraction and catalog linking
FR-37: (intentionally unassigned — reserved gap; no requirement carries this number across the PRD, architecture spine, or epics)
FR-42: Epic 3 & Epic 9 - Cross-cutting technique tags via governance sidecar metadata (examples/workflows/guides in Epic 3's Story 3.3; training decks in Epic 9's Story 9.3)
FR-16: Epic 4 - Exact fully-qualified signature detection and priority resolution
FR-17: Epic 4 - Exact/prefix/camel-case symbol fields plus analyzed BM25 text fields
FR-18: Epic 4 - Local ONNX vector search, explicit failure on model-load error
FR-19: Epic 4 - Lexical + vector fusion under exact priority, relationship-enriched results
FR-20: Epic 4 - Consistent embedding fingerprint between indexing and query time
FR-21: Epic 5 - Retrieval and symbol tools (search_api, resolve_symbol, get_class, get_method, get_api_context)
FR-22: Epic 5 - Relationship tools (get_inheritance, get_type_relationships, find_implementations, find_subclasses)
FR-23: Epic 5 - Example and workflow tools (search_examples, find_examples_for_symbol, get_example, search_workflows)
FR-24: Epic 5 - Validation and status tools (check_symbols, index_status)
FR-38: Epic 5 - Guide tools (search_guides, get_guide)
FR-41: Epic 5 - Training tools (search_trainings, get_training)
FR-43: Epic 5 - Concept/technique search tool (search_concepts) federating technique-tag matches across examples/workflows/guides/trainings
FR-25: Epic 5 - Streamable HTTP transport at /mcp with structured errors, timeouts, limits
FR-26: Epic 6 - Immutable versioned builds with pre-activation validation and atomic activation
FR-27: Epic 6 - Compatibility fingerprint mismatch rejected unless a tested migration exists
FR-28: Epic 6 - Rollback via pointer swap only; retained builds; side-by-side SDK versions
FR-29: Epic 6 - Full-set backups (manifest, catalog, Lucene dir, report, config, model, checksums)
FR-30: Epic 7 - agent/SKILL.md with the mandatory retrieval policy and one worked example
FR-31: Epic 7 - Full citation tuple on every primary result
FR-32: Epic 8 - Single serving image for ingest/validate-index/serve; internal-host Compose deployment; separate ingest-time image for training materials
FR-33: Epic 8 - Air-gap bundle with checksums, SBOM, licenses, transferable/rebuildable index (serving image only)
FR-34: Epic 8 - CLI with ingest, validate-index, serve, stats; headless with exit codes
FR-39: Epic 9 - Training-deck (PPTX/PDF) ingestion via docling's vision/layout-model pipeline
FR-40: Epic 9 - Training reference extraction and catalog linking

## Epic List

### Epic 1: Foundation & Domain Contracts
Establishes the domain model (API element/Example/Workflow/Relationship records), module skeleton, and ports so a developer can run the ingestion pipeline scaffold end-to-end against a synthetic corpus — deterministic IDs and hashes, zero egress, machine-readable report — before any real Javadoc parsing exists.
**FRs covered:** FR-1, FR-2, FR-3

### Epic 2: Javadoc Knowledge Extraction
Ingests a real Javadoc tree into canonical, version-scoped symbol records with a typed relationship graph across the 14 Javadoc-derived relationship types (of the 17 total; the 3 example edges are emitted in Epic 3, AD-30), covering both modern and legacy layouts. Merges what the source brief split into two epics (structural parsing; catalog + relationship graph) — both touch the same catalog/relationship modules with no standalone value between them, so they ship as one epic with ordered stories.
**FRs covered:** FR-4, FR-5, FR-6, FR-7, FR-8, FR-9, FR-10, FR-11

### Epic 3: Example, Workflow & Guide Knowledge
Ingests `.java` examples, workflow docs, and guide tutorial articles; links them all to catalog symbols after Javadocs are indexed; and tags examples (and, per the technique-tag amendment, workflows and guides too) with governance-supplied quality labels and cross-cutting technique tags — never filename or parser inference. *(Renamed 2026-08-21, Sprint Change Proposal, to add guide articles as a fourth ingestion input; technique tags added 2026-08-21, Sprint Change Proposal.)*
**FRs covered:** FR-12, FR-13, FR-14, FR-15, FR-35, FR-36, FR-42

### Epic 4: Hybrid Retrieval Engine
Builds the Lucene lexical index, ONNX embedding pipeline, and fusion ranking so queries resolve exact symbols first and fall back to deterministic hybrid BM25+vector search with relationship-enriched results. Lexical indexing (FR-17) and embedding (FR-20) cover all five content record families — API elements (`ApiElement` + `MethodElement`), examples, workflows, guides, and training-deck sections — uniformly, once each type's records exist in the catalog. Training-deck coverage is a forward dependency on Epic 9 — exercised only after Epic 9 produces `Training` records; until then Epic 4 validates the other four families and the empty/no-op path for training. *(Guide coverage noted 2026-08-21, Sprint Change Proposal; training coverage noted 2026-08-21, Sprint Change Proposal.)*
**FRs covered:** FR-16, FR-17, FR-18, FR-19, FR-20

### Epic 5: MCP Tool Surface
Exposes all 20 tools over Streamable HTTP so a coding agent can query, resolve, and traverse SDK knowledge end-to-end. *(15 → 17 → 19 → 20, 2026-08-21, Sprint Change Proposal.)*
**FRs covered:** FR-21, FR-22, FR-23, FR-24, FR-25, FR-38, FR-41, FR-43

Implementation notes (from epic-design review):
- Scoped to read from a fixed active-build pointer with no atomicity/rollback guarantee — full immutable-build/atomic-activation semantics belong to Epic 6, not this epic. State this explicitly in the epic's stories so it isn't mistaken for the finished lifecycle.
- Includes its own story for a structured error/ambiguity/truncation contract applied consistently across all 20 tools, rather than left to be decided differently per tool.
- A retrieval-policy skeleton for the agent instruction file is drafted here, while tool contracts are fresh; Epic 7 completes it.

### Epic 6: Index Lifecycle
Gives a release operator immutable versioned builds, pre-activation validation, atomic activation, and rollback without rebuild — upgrades Epic 5's fixed-pointer resolver to the full lifecycle guarantee.
**FRs covered:** FR-26, FR-27, FR-28, FR-29

### Epic 7: Agent Guidance & Usability
Ships the agent-facing `agent/SKILL.md` with the mandatory retrieval policy and a worked example, and guarantees every primary result carries a complete citation tuple.
**FRs covered:** FR-30, FR-31

Implementation notes: completes the retrieval-policy skeleton drafted in Epic 5 rather than starting it from a blank page.

### Epic 8: Air-Gap Packaging & Release
Produces an air-gap-transferable release — image, CLI, Compose layout, SBOM, checksums — that a release operator can install and run with zero egress on a shared internal host. Also produces the separate, ingest-time-only image that runs training-material ingestion (FR-39) — a distinct artifact from the serving image, never deployed as or alongside it. *(Amended 2026-08-21, Sprint Change Proposal.)*
**FRs covered:** FR-32, FR-33, FR-34

### Epic 9: Training Material Knowledge
Ingests PowerPoint training-deck PPTX/PDF exports via docling's vision/layout-model pipeline into structured `Training` records (section title, ordered text blocks, extracted tables/figures), and links their symbol/API references to the catalog after Javadocs are indexed — the only ingestion path in this system permitted network access and non-Java tooling, confined to a separate, ingest-time-only image (AD-28). Standalone epic rather than an Epic 3 extension because its ingestion mechanics (network, Python/docling) are architecturally distinct from Epic 1–4's fully offline, Java-only pipeline. Also tags `Training` records with cross-cutting technique tags via the same governance sidecar mechanism as Epic 3. *(Added 2026-08-21, Sprint Change Proposal; technique-tagging scope added 2026-08-21, Sprint Change Proposal.)*
**FRs covered:** FR-39, FR-40, FR-42

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

## Epic 2: Javadoc Knowledge Extraction

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

As a developer,
I want unresolved external/undocumented links recorded explicitly rather than guessed, and traversal restricted to the documented SDK universe,
So that the system never fabricates a relationship it can't back with a citation (AD-4).

**Acceptance Criteria:**
**Given** a page linking to a class outside the ingested Javadoc tree
**When** relationship resolution runs
**Then** the link is recorded as unresolved/external, not silently dropped or guessed at
**And** a traversal request reports it operated within the documented SDK scope

### Story 2.7: Bounded, cycle-safe relationship traversal

As a developer,
I want direct and recursive (CTE) traversal with configurable depth limits and cycle handling,
So that a query over a graph containing cycles (e.g. mutually referencing interfaces) terminates and returns a bounded result instead of hanging.

**Acceptance Criteria:**
**Given** a fixture graph containing a cycle
**When** a recursive traversal is requested
**Then** it terminates within the configured depth limit
**And** no node is visited more than once in a single traversal result

## Epic 3: Example, Workflow & Guide Knowledge

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
*(Expanded 2026-08-25 Sprint Change Proposal — AD-30. Depends on the domain resolution model + Story 1.4 hashes being approved before implementation.)*

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

### Story 3.3: Apply governance-supplied quality labels and technique tags

As a corpus owner,
I want example quality (`official`/`validated`/`best-practice`/`illustrative`/`legacy`) supplied only via governance sidecar metadata, and cross-cutting technique tags from the same sidecar contract attachable to examples, workflow docs, and guide articles alike,
So that quality and technique metadata are never inferred from filename, code shape, or parser heuristics. *(Technique tags added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** an example with no sidecar metadata
**When** ingestion runs
**Then** it receives no quality label and no technique tags rather than guessed ones
**And** an example with sidecar metadata receives exactly the quality label and technique tags the sidecar specifies
**And** a technique tag value outside the curated vocabulary is rejected at ingest with a structured error, never silently accepted
**And** a workflow doc or guide article with sidecar-supplied technique tags (Story 3.4/3.5) receives them via this same mechanism

### Story 3.4: Ingest and link workflow documents

As a developer,
I want workflow docs (Markdown/YAML) ingested with prerequisites, ordered steps, referenced symbols, linked examples, limitations, and version metadata, linked to catalog symbols and examples,
So that a query for "how do I do X" surfaces a complete, cited procedure rather than a scattered set of facts.

**Acceptance Criteria:**
**Given** a workflow doc referencing catalog symbols and an example
**When** ingested
**Then** its ordered steps, prerequisites, and limitations are preserved
**And** its symbol and example references resolve to the corresponding catalog/example records
**And** a workflow doc with sidecar-supplied technique tags (Story 3.3) receives them attached to its record

### Story 3.5: Parse and link guide tutorial articles

As a developer,
I want guide tutorial/how-to HTML articles parsed into title, ordered prose sections, and embedded code snippets, with Luciad symbol/API references in both prose and snippets resolved against the catalog using the same canonical/normalized keys as other record types, run only after Javadocs are indexed,
So that a query for SDK how-to guidance surfaces a cited, structured article instead of raw unlinked HTML. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a guide article HTML page with prose sections and embedded code snippets
**When** it is parsed
**Then** its title, ordered prose sections, and code snippets are preserved as a single `Guide` record
**And** a fixture with malformed/broken HTML degrades per the same `fail-fast | collect-and-continue` policy as Story 2.2, never aborting the whole ingest run
**Given** a guide referencing catalog symbols in its prose or code snippets, run after the catalog exists
**When** linking runs
**Then** each resolvable reference is linked to its catalog record
**And** an unresolvable reference stays explicitly unresolved rather than guessed
**And** a guide article with sidecar-supplied technique tags (Story 3.3) receives them attached to its record

## Epic 4: Hybrid Retrieval Engine

### Story 4.1: Detect and prioritize exact-symbol queries

As a coding agent,
I want a query containing a fully-qualified signature detected and resolved first, heavily boosted over lexical/vector matches,
So that asking for a specific known symbol never returns a semantically-similar but wrong result.

**Acceptance Criteria:**
**Given** a query containing an exact fully-qualified signature that exists in the catalog
**When** the query is routed
**Then** the exact match is returned and ranked above any lexical/vector candidate
**And** exact validation overrides semantic similarity whenever both apply

### Story 4.2: Index canonical and lexical fields for BM25

As a developer,
I want canonical symbol + normalized variants indexed as exact/prefix/camel-case fields, and descriptions/params/returns/exceptions/example prose indexed as analyzed BM25 text with filters and highlighting,
So that lexical search covers both precise symbol lookup and natural-language description matching.

**Acceptance Criteria:**
**Given** a corpus indexed by this scheme
**When** a query uses a partial camel-case symbol fragment
**Then** it matches via the camel-case field
**And** a query using descriptive prose matches via the analyzed text fields with highlighting

### Story 4.3: Local ONNX vector search with explicit failure semantics

As a developer,
I want vector search to run entirely against a local ONNX model, fail explicitly on model-load error, and only fall back to lexical-only when configured,
So that a broken or missing local model is never silently masked as "no results."

**Acceptance Criteria:**
**Given** a corrupted or missing local model path
**When** the server starts
**Then** it fails explicitly with a clear error rather than starting in a degraded silent state
**And** with lexical fallback explicitly configured, `index_status` reports the fallback is active

### Story 4.4: Fuse lexical and vector results under exact priority

As a coding agent,
I want lexical and vector results fused (e.g. RRF) beneath exact-match priority, with top candidates enriched with a bounded relationship neighborhood,
So that hybrid search returns a concise, cited, relationship-aware result set rather than two disjoint candidate lists.

**Acceptance Criteria:**
**Given** a query with both lexical and vector candidates and no exact match
**When** fusion runs
**Then** results are merged into one ranked, deduplicated list
**And** each top result includes its bounded relationship neighborhood

### Story 4.5: Enforce a consistent embedding fingerprint

As a developer,
I want the same pinned model/tokenizer/preprocessing/normalization/dimensions/query-instructions used at index-time and query-time, excluding navigation/duplicate/boilerplate/empty content from embedding,
So that vector search never silently compares embeddings produced under different settings.

**Acceptance Criteria:**
**Given** an index built with fingerprint F
**When** a query arrives at a server configured with a different fingerprint
**Then** vector search is rejected/disabled per AD-6, not silently run with mismatched vectors
**And** navigation/boilerplate/empty content is confirmed absent from the embedded record set

## Epic 5: MCP Tool Surface

### Story 5.1: Retrieval and symbol tools

As a coding agent,
I want `search_api`, `resolve_symbol`, `get_class`, `get_method`, and `get_api_context` exposed as MCP tools,
So that I can look up and read Luciad SDK API knowledge directly instead of guessing from training data.

**Acceptance Criteria:**
**Given** a running MCP server over a built index
**When** each tool is called with a valid input
**Then** each primary-result tool (`search_api`, `get_class`, `get_method`, `get_api_context`) returns a citation-complete result per AD-15, while `resolve_symbol` returns a verdict/navigation payload per AD-24 (no citation-completeness drop; `citation` absent, not empty)
**And** an unknown symbol returns a structured not-found response, never a fabricated result

### Story 5.2: Relationship tools

As a coding agent,
I want `get_inheritance`, `get_type_relationships`, `find_implementations`, and `find_subclasses` exposed as MCP tools,
So that I can navigate typed, cited relationships within the documented SDK universe.

**Acceptance Criteria:**
**Given** a symbol with known relationship edges
**When** each relationship tool is called
**Then** it returns only edges within the documented SDK scope, each carrying its citation tuple
**And** results respect the bounded traversal limits from Story 2.7

### Story 5.3: Example and workflow tools

As a coding agent,
I want `search_examples`, `find_examples_for_symbol`, `get_example`, and `search_workflows` exposed as MCP tools,
So that I can retrieve validated usage examples and procedures instead of inventing example code.

**Acceptance Criteria:**
**Given** a symbol with linked examples and a workflow referencing it
**When** each tool is called
**Then** results are ranked with governance quality label visible
**And** `get_example` returns complete example source only via this explicit tool, never embedded inline elsewhere

### Story 5.3b: Guide tools

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

### Story 5.4: Validation and status tools

As a coding agent,
I want `check_symbols` (exact-valid / ambiguous-with-candidates / unknown, catalog-only, no compilation) and `index_status` (versions, record counts, ingestion timestamp, retrieval mode, model status, schema/index version) exposed as MCP tools,
So that I can verify a symbol exists before using it and check the server's operating mode before relying on results.

**Acceptance Criteria:**
**Given** a mix of valid, ambiguous, and nonexistent symbols
**When** `check_symbols` is called with all three
**Then** each is classified correctly and unknown is never marked valid
**And** `index_status` reports current retrieval mode (hybrid/lexical-fallback) and model status accurately

### Story 5.5: Streamable HTTP transport

As a coding agent,
I want to reach the MCP server over Streamable HTTP at `/mcp` (with optional stdio), with structured errors, timeouts, and limits,
So that tool calls fail predictably instead of hanging or returning unstructured errors.

**Acceptance Criteria:**
**Given** a malformed request or a request exceeding configured limits
**When** it is sent to `/mcp`
**Then** a structured error response is returned within the configured timeout
**And** full pages/complete examples are never embedded inline outside their dedicated `get_*` tools

### Story 5.6: Serve from a fixed active-build pointer

As a developer,
I want the retrieval layer to read from a single fixed active-build pointer file, explicitly without atomicity or rollback guarantees,
So that Epic 5 can ship and be tested before Epic 6's full immutable-build/atomic-activation lifecycle exists, without either epic mistaking the other's guarantees for its own.

**Acceptance Criteria:**
**Given** a fixed pointer file naming the active build directory
**When** the server starts
**Then** it serves from that build and documents (in its own story text and `index_status`) that no atomic swap or rollback is implemented yet
**And** Epic 6 can later replace this resolver without changing any tool's request/response contract

### Story 5.7: Structured error, ambiguity, and truncation contract

As a coding agent,
I want every one of the 20 tools to use the same structured contract for errors, ambiguous matches, and truncated results,
So that I can write one handling path instead of learning 20 different shapes for "something didn't fully resolve."

**Acceptance Criteria:**
**Given** an ambiguous query, a truncated result set, and an invalid input, each sent to a different tool
**When** each is handled
**Then** all three use the same structured shape (error/ambiguity/truncation) regardless of which tool was called
**And** truncation always includes the pagination/continuation metadata from NFR-10

### Story 5.8: Draft the agent retrieval-policy skeleton

As a developer,
I want a first-draft skeleton of the agent retrieval policy written while the 20 tool contracts are still fresh from this epic,
So that Epic 7 completes a policy grounded in the actual shipped contracts instead of reconstructing decisions from memory later.

**Acceptance Criteria:**
**Given** all tools from Stories 5.1–5.5 (including 5.3b, 5.3c, and 5.3d) are implemented
**When** the skeleton is drafted
**Then** it lists all 20 tools by their exact names with a one-line policy note each
**And** it is saved as a working draft that Epic 7 Story 7.1 explicitly builds on

## Epic 6: Index Lifecycle

### Story 6.1: Immutable builds with pre-activation validation and atomic activation

As a release operator,
I want each build written to a new immutable `{sdkVersion}/{buildId}` directory, validated via `validate-index` before activation, and activated atomically,
So that the serving index is never mutated in place and a bad build never goes live.

**Acceptance Criteria:**
**Given** a newly built index directory
**When** `validate-index` passes and activation is triggered
**Then** the active pointer is swapped atomically to the new build
**And** the previously serving build directory is left untouched on disk

### Story 6.2: Reject incompatible builds

As a release operator,
I want a build whose schema/Lucene-major/canonical-symbol-format/embedding-model/dimension/tokenizer/preprocessing fingerprint mismatches the runtime to be rejected unless a tested migration exists,
So that an incompatible index can never silently start serving wrong or corrupt results.

**Acceptance Criteria:**
**Given** a build with a deliberately mismatched fingerprint field
**When** activation is attempted
**Then** activation is rejected with a clear reason naming the mismatched field
**And** a build with a fingerprint mismatch that has a registered tested migration is allowed through only via that migration path

### Story 6.3: Rollback via pointer swap and build retention

As a release operator,
I want rollback to only change the active pointer (no rebuild), with at least the current and previous validated builds retained where storage allows, and side-by-side SDK versions supported with an explicit default,
So that a bad release can be undone in seconds, not hours.

**Acceptance Criteria:**
**Given** an active build and a previously validated build still retained
**When** rollback is triggered
**Then** the active pointer swaps to the previous build with no reprocessing
**And** with two SDK versions both retained, the configured default version is the one served when unspecified

### Story 6.4: Full-set backups

As a release operator,
I want a backup to cover the manifest, catalog, Lucene directory, ingestion report, config, model artifacts, and checksums together,
So that a partial backup is never mistaken for a restorable one.

**Acceptance Criteria:**
**Given** a backup operation over an active build
**When** it completes
**Then** all seven listed components are present in the backup with checksums
**And** a backup missing any one component is flagged as invalid, not silently accepted

## Epic 7: Agent Guidance & Usability

### Story 7.1: Ship the agent SKILL.md

As a coding agent,
I want `agent/SKILL.md` shipped with the mandatory 11-point retrieval policy plus one worked "display a point object" sequence, referencing tools by their exact §10 names, completing the skeleton drafted in Epic 5 Story 5.8,
So that I know how to use this MCP server correctly before I make my first tool call.

**Acceptance Criteria:**
**Given** the Epic 5 policy skeleton
**When** Epic 7 completes it
**Then** all 11 policy points are present and each references an exact, currently-shipped tool name
**And** the worked example runs through a realistic multi-tool sequence end-to-end

### Story 7.2: Guarantee complete citations on every primary result

As a coding agent,
I want every primary-result payload to always include SDK version, record type, relative source path, and stable record ID; HTML anchor when available; and canonical symbol/example ID only when resolved (omitted — never fabricated — otherwise, per FR-31/AD-15),
So that I can always cite exactly where an answer came from, and a symbol-less Guide/Training record is still citation-complete.

**Acceptance Criteria:**
**Given** a call to any primary-result tool (per AD-24's classification)
**When** it returns a result
**Then** the four always-required fields (SDK version, record type, relative source path, stable record ID) are present, anchor is explicitly null (not omitted) when unavailable, and canonical symbol/example ID is present when resolved and omitted — never fabricated — when not, per FR-31/AD-15
**And** verdict/navigation tools (`check_symbols`, `resolve_symbol`, `index_status`) are never dropped for missing citation, per AD-15

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

### Story 8.2: Air-gap transfer bundle

As a release operator,
I want a bundle of image tar + SHA256SUMS + SBOM + install docs + frozen index + model artifacts, with SHA-256 verification and documented licenses, supporting either transferring a prebuilt combined index or rebuilding one inside the air gap,
So that the appliance can be verified and installed with no network access at any point.

**Acceptance Criteria:**
**Given** a generated bundle
**When** SHA256SUMS is checked against the bundle contents
**Then** every file verifies
**And** the install docs describe both the prebuilt-transfer path and the in-gap-rebuild path

### Story 8.3: Headless CLI with documented exit codes

As a release operator,
I want `ingest`, `validate-index`, `serve`, and `stats` as documented CLI commands with defined exit codes, running fully headless,
So that the appliance can be driven from scripts/CI without any interactive prompt.

**Acceptance Criteria:**
**Given** each of the four commands run with no TTY attached
**When** invoked with valid and invalid arguments
**Then** each exits with its documented code (success, validation failure, runtime failure, etc.) and no interactive prompt ever appears
**And** `--help` output documents every option for all four commands

### Story 8.4: Separate ingest-time-only image for training-material ingestion

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

## Epic 9: Training Material Knowledge

### Story 9.1: Ingest training decks via docling

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

As a developer,
I want Luciad symbol/API references in extracted training text resolved against the catalog using the same canonical/normalized keys as other record types, run only after Javadocs are indexed,
So that a symbol lookup or training query surfaces cited training-deck content instead of raw unlinked text. *(Added 2026-08-21, Sprint Change Proposal.)*

**Acceptance Criteria:**
**Given** a training record referencing catalog symbols, run after the catalog exists
**When** linking runs
**Then** each resolvable reference is linked to its catalog record
**And** an unresolvable reference stays explicitly unresolved rather than guessed, per FR-10

### Story 9.3: Apply governance-supplied technique tags to training records

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
