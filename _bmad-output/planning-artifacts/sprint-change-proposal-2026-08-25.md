# Sprint Change Proposal — Structure-Aware Java Example Ingestion

- **Date:** 2026-08-25
- **Trigger source:** `change-proposal.md` ("Add Structure-Aware Java Example Ingestion")
- **Mode:** Batch
- **Scope classification:** Moderate (backlog reorganization + planning-artifact edits; no rollback; no done story reopened) — with one architecture-invariant amendment (relationship enum) that needs architect sign-off.

---

## 0. Mapping note (read first)

The incoming `change-proposal.md` was written from a stale documentation snapshot and did **not** have the authoritative repo artifacts. Two of its ID references are wrong and are corrected throughout this proposal:

| `change-proposal.md` said | Authoritative repo reality |
|---|---|
| "Epic 4 — Example and Workflow Ingestion" (parse Java examples, preserve source, extract references) | **Epic 3 — Example, Workflow & Guide Knowledge** owns this. Concretely **Story 3.1** (Parse Java examples via AST — FR-12) and **Story 3.2** (Link examples to catalog symbols — FR-13). Epic 4 is the **Hybrid Retrieval Engine** (FR-16..20) and is not an ingestion epic. |
| Story 1.3, Story 1.4 | ✅ Correct as-is. |
| Uncertain whether `USES_TYPE`/`CONSTRUCTS`/`CALLS` already exist in the enum | They do **not**. The relationship enum is a fixed set of exactly 15 types with a binding rule (`no ad-hoc additions without an AD amendment`). The example-linking type present today is the single coarse `EXAMPLE_USES`. |

All edits below use the real IDs. No existing IDs are renumbered. No source code is changed by this proposal.

### Decisions taken (from the three open design questions)

1. **Relationship enum:** *refine* the single `EXAMPLE_USES` into `USES_TYPE` / `CONSTRUCTS` / `CALLS`. Net count **15 → 17** (13 Javadoc structural types + `WORKFLOW_REQUIRES` + the 3 new example types).
2. **PRD landing:** *expand existing FRs* (FR-2, FR-3, FR-12, FR-13) in place with dated amendment notes — no new FR numbers, matching how prior sprint changes were recorded.
3. **Example record storage:** class/method-level records are *canonical SQLite rows with Lucene projections* (stable IDs, parent links, citations in SQLite; method-sized search docs in Lucene).

---

## 1. Issue Summary

The current plan preserves complete Java example source (FR-12) and extracts Luciad references (FR-13), but leaves four things underspecified for the example domain:

1. **Method/class-level retrieval records** — FR-12 mentions "secondary method/class chunks" but does not define them as first-class, citable records with source ranges and parent links.
2. **Relationship resolution evidence** — FR-13 says references link to the catalog or stay unresolved, but does not define *how* resolution is decided, what evidence is retained, or the fine-grained edge kinds (`USES_TYPE`/`CONSTRUCTS`/`CALLS`).
3. **Hash discipline for safe restart** — Story 1.4 has "stable record IDs and content hashes" but a single unspecified hash cannot distinguish changed source bytes, changed extracted record, changed embedding input, and changed tool behavior.
4. **Pipeline stage discipline for examples** — Story 1.3 has an 8-stage pipeline but does not state bounded work queues, bounded batch writes, or restart-fingerprint validation for the example-parsing stage.

**Why it matters:** conceptual questions ("how do I display a point?") match a method-sized usage sequence better than a whole sample project. The system needs to return that sequence without weakening exact-symbol validation or citations.

**Evidence:** review of two external source-analysis projects (Java2Graph, Source Atlas) surfaced the pattern (pre-scan → AST extraction → evidence-bearing relationship resolution → bounded batch persistence). The review found **no reason** to replace JavaParser, SQLite, Lucene, or ONNX. The gap is specification, not technology.

---

## 2. Impact Analysis

### 2.1 Epic impact

- **Epic 1 (Foundation) — in progress.** Story 1.3 and Story 1.4 (both backlog) get expanded acceptance criteria. Story 1.1 (done) is **not** reopened; the new domain value objects land as incremental additions in later stories (1.1 explicitly scaffolded "initial domain contracts", so additive types are in-bounds). Story 1.2 (in review) is **not** blocked — its CLI-ingest interfaces are additive to the pipeline stages and do not prevent them.
- **Epic 2 (Javadoc) — backlog.** Story 2.5 and its "15 relationship types" list change to 17 (enum refinement). No other Epic 2 story changes.
- **Epic 3 (Example/Workflow/Guide) — backlog.** Story 3.1 and 3.2 get the bulk of the new example-structure requirements. Implementation waits on the domain resolution model + Story 1.4 hash definitions being approved.
- **Epic 4 (Hybrid Retrieval) — backlog.** No requirement change. Consequential only: method-sized records become new inputs to lexical/vector indexing it already covers (FR-17/FR-20 already say "examples").
- **Epic 5 (MCP tools) — backlog.** No tool added or renamed. `get_example` keeps returning complete source; method-sized records are surfaced through existing `search_examples`/`find_examples_for_symbol`. Only additive citation fields (source range, resolution status) may appear — already permitted by the "except additive fields" clause.
- **Epics 6–9 — no change.**

### 2.2 Artifact impact

| Artifact | Sections touched |
|---|---|
| **PRD** (`prds/.../prd.md`) | FR-2, FR-3, FR-9, FR-12, FR-13 (text + Consequences); Glossary "Relationship edge" (15→17); no SM/NFR renumber |
| **Epics** (`epics.md`) | FR-9 inventory line; Story 1.3; Story 1.4; Story 2.5 (list + count); Story 3.1; Story 3.2; testing-strategy block; FR coverage map line for FR-9 |
| **Architecture Spine** (`ARCHITECTURE-SPINE.md`) | New **AD-30**; Consistency Conventions "Relationship types" row (15→17 + amendment note); Record-naming row (+ new domain types); ERD (example record hierarchy + resolution evidence); Capability map F4 row (+AD-30); light cross-refs on AD-6/AD-8/AD-22 for hash reconciliation |
| **sprint-status.yaml** | No structural change (no epics/stories added or renumbered). Statuses unchanged until edits are applied. |

### 2.3 Explicitly unaffected (confirmed against repo)

Every item on the proposal's "Explicitly Unaffected" list holds: Java 21/Gradle, JavaParser, JSoup, SQLite, Lucene, ONNX, exact-symbol priority (AD-7/AD-7a), version scoping (AD-12), complete-source preservation, local-file-only ingest (AD-9), no-SDK-JAR / no-compile (AD-10), zero egress (NFR-11), immutable builds (AD-5), MCP tool names/bounds (AD-11/AD-24). No new database, parser, language server, network dependency, or runtime process.

### 2.4 Pre-existing inconsistency (out of scope, noted)

PRD says "19 tools" at §F6 header (line ~228) and §API Contracts (line ~430), while epics and the architecture spine say 20 (`search_concepts` was the 20th). This predates this change and is unrelated to it — **not** fixed here. Flagged for a separate cleanup.

---

## 3. Recommended Approach

**Direct adjustment** (Option 1). Expand backlog Stories 1.3, 1.4, 3.1, 3.2, refine the relationship enum via a new architecture decision, and add the resolution/source-range domain types — all before those areas are implemented.

- **Effort:** Medium (planning-artifact edits only; no code).
- **Risk:** Low-to-Medium. The one Medium-risk piece is the relationship-enum refinement, because it changes the meaning of an existing type (`EXAMPLE_USES`) and touches every "15" reference across three artifacts. Mitigated by doing it as one atomic AD-30 amendment with all references updated together.
- **Timeline:** No epic resequencing. Epic 3 implementation start gains a soft dependency on AD-30 + Story 1.4 sign-off.

Rollback (Option 2) and MVP-reduction (Option 3) are **not viable / not needed** — nothing is implemented yet in the affected backlog stories, and MVP scope is unchanged.

---

## 4. Detailed Change Proposals

> Format: each edit gives the artifact, the ID/section, and OLD → NEW (or ADD). Where the repo text is long, only the changed clause is shown.

### 4.A Relationship enum refinement (15 → 17)

#### 4.A.1 Architecture — new AD-30

**ADD** after AD-29 in `ARCHITECTURE-SPINE.md`:

```
### AD-30 — Structure-aware example ingestion: refined usage edges, evidence-bearing resolution, bounded graph scope `[ADOPTED]`
- **Binds:** domain, ingest.examples, catalog.sqlite, search.lucene, retrieval
- **Prevents:** (a) the single coarse EXAMPLE_USES edge being too weak to answer method-sized conceptual queries; (b) heuristic example resolution silently manufacturing verified Luciad symbols; (c) richer example analysis expanding into an IDE/second-graph-platform.
- **Rule:** Java examples are parsed with JavaParser after a deterministic in-corpus pre-scan (packages, imports, declared types, constructors, methods, local source roots). The ingester emits example-, class-, and method-level records; the complete source is preserved once and child records carry a SourceRange plus the minimum text needed for search and citation. Example→symbol relationships use three edge types that REPLACE the former coarse EXAMPLE_USES: `USES_TYPE` (resolved Luciad type), `CONSTRUCTS` (resolved Luciad constructor/type), `CALLS` (resolved Luciad method; also a local helper method only when it is part of the Luciad usage sequence). Resolution runs in a fixed ladder — FQN literal → explicit import + exact catalog match → local/same-package declaration → unique wildcard-import match → owner+name+signature/arity match → ambiguous/unresolved — and every derived edge carries evidence (source range, original token, candidate canonical symbols, and the selected symbol only when status is EXACT). Only an exact match to the version-scoped catalog is ever returned as a verified Luciad symbol (consistent with AD-4, AD-7). Deferred until retrieval evaluation shows need: field read/write edges, control-flow, data-flow, full CPG. Storage stays SQLite (canonical example/class/method rows + typed edges); Lucene indexes method-sized projections. No LSP, graph server, SDK JAR, sample compilation, or execution is introduced (AD-10 unchanged). Cross-build incremental indexing is deferred until full-build correctness is measured.
- **Note:** this refines brief §6's 15-type set to 17 for downstream artifacts; the brief is retained as the historical contract, and AD-30 supersedes it for the example domain (same amendment pattern as FR-35/FR-38/FR-41/AD-28/AD-29).
```

#### 4.A.2 Architecture — Consistency Conventions "Relationship types" row

**OLD:**
> | Relationship types | Fixed enum, `UPPER_SNAKE_CASE`, exactly the 15 brief §6 values; no ad-hoc additions without an AD amendment. |

**NEW:**
> | Relationship types | Fixed enum, `UPPER_SNAKE_CASE`, exactly 17 values: the 13 Javadoc structural types + `WORKFLOW_REQUIRES` + `USES_TYPE`/`CONSTRUCTS`/`CALLS` (the last three refine the former coarse `EXAMPLE_USES` per AD-30); no ad-hoc additions without an AD amendment. *(15→17, 2026-08-25 Sprint Change Proposal — AD-30.)* |

#### 4.A.3 Epics — Story 2.5 relationship-type list

**OLD (line ~390):**
> The 15 required relationship types (brief §6): `EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`, `IMPLEMENTED_BY`, `DIRECT_SUBCLASS`, `RETURN_TYPE`, `PARAMETER_TYPE`, `THROWS_TYPE`, `FIELD_TYPE`, `CONSTRUCTOR_PARAMETER_TYPE`, `OVERRIDES`, `SPECIFIED_BY`, `SEE_ALSO`, `EXAMPLE_USES`, `WORKFLOW_REQUIRES`.

**NEW:**
> The 17 required relationship types (brief §6, refined by AD-30): `EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`, `IMPLEMENTED_BY`, `DIRECT_SUBCLASS`, `RETURN_TYPE`, `PARAMETER_TYPE`, `THROWS_TYPE`, `FIELD_TYPE`, `CONSTRUCTOR_PARAMETER_TYPE`, `OVERRIDES`, `SPECIFIED_BY`, `SEE_ALSO`, `WORKFLOW_REQUIRES`, `USES_TYPE`, `CONSTRUCTS`, `CALLS`. The last three refine the former coarse `EXAMPLE_USES` (AD-30). *(15→17, 2026-08-25 Sprint Change Proposal.)*

Also in Story 2.5: change "every one of the 15 relationship types" and "all 15 types are present" → **17**.

#### 4.A.4 Other "15" references → "17"

- **Epics FR-9 inventory** (line ~32): "all 15 required relationship types produced" → "all 17 required relationship types produced (AD-30)".
- **Epics FR coverage map** (FR-9 line): "across all 15 relationship types" → "17".
- **Epics testing strategy** (unit tests bullet): "all 15 relationship types" → "17 relationship types (incl. `USES_TYPE`/`CONSTRUCTS`/`CALLS`)".
- **PRD FR-9** text + first Consequence: "All 15 required relationship types are produced" / "Each of the 15 relationship types is produced" → **17**.
- **PRD Glossary "Relationship edge"**: "(one of the 15 required relationship types)" → "(one of the 17 required relationship types)".

### 4.B Story 1.3 — pipeline stage discipline (expands FR-2)

**ADD** acceptance criteria to Story 1.3 (keep existing three):

- File parsing uses bounded work queues; a completed AST is released after its records and unresolved references are emitted.
- SQLite and Lucene writes use bounded batches; batch sizes are fixed/configured and recorded in the ingestion report.
- Restart state records completed stages and validates input hashes plus the toolchain fingerprint before reusing prior work (aligns with AD-25 build-scoped staging).
- A failure in one malformed optional example is recorded with its file and stage and may allow the run to continue (existing `collect-and-continue` policy).
- A failure affecting catalog integrity, index compatibility, a required manifest, or final validation invalidates the build and blocks activation (AD-5).
- A restarted build produces the same records, edges, and manifest as an uninterrupted build from the same inputs and toolchain (NFR-13).
- No restart or fallback path performs a network request (AD-9; `ingest.trainings` exception unchanged).

**PRD FR-2 Consequences:** add "Batch sizes for catalog/index writes are bounded and recorded in the ingestion report" and "A resumed run validates input hashes + toolchain fingerprint before reusing work; a changed input set is a new build, not a resume (AD-25)."

*Note on stages:* the existing 8-stage list stands. "Discover inputs" and "pre-scan symbols" are sub-steps inside the existing `examples/workflows` stage (pre-scan is example-scoped), not new top-level pipeline stages — this keeps the corpus-wide pipeline shape intact.

### 4.C Story 1.4 — hash taxonomy (expands FR-3)

**ADD** to Story 1.4 — define and test five distinct hashes, mapped onto existing invariants so no parallel machinery is invented:

| Field | Input | Use | Maps to |
|---|---|---|---|
| `sourceHash` | Original input-file bytes | Input provenance / change detection | **New** (genuinely additive) |
| `recordHash` | Canonical extracted record | Record identity, dedup, restart validation | AD-22 identity fields |
| `relationshipHash` | SDK version + source ID + rel type + target ID + evidence | Edge identity, duplicate prevention | AD-22 identity fields |
| `semanticHash` | Exact normalized text sent to embedder | Detect whether re-embedding is required | AD-8 / AD-21 embedding fingerprint |
| `toolchainFingerprint` | Parser + schema + normalizer + relationship-rules + embedding fingerprint | Reject incompatible restart/reuse | AD-6 compatibility fingerprint (restart-scoped sibling) |

**ADD** acceptance criteria:
- Same inputs + toolchain → identical IDs and hashes (already present; keep).
- A change to parser, schema, normalization, resolution rules, or embedding config changes the relevant fingerprint and blocks unsafe reuse.
- The ingestion report includes counts for exact, ambiguous, and unresolved example relationships, grouped by relationship type and resolution kind.
- SQLite uniqueness constraints prevent duplicate records and edges even if the same source root is discovered twice.
- Cross-build incremental indexing is out of scope for this story; hashes support restart safety now and may support incremental builds later.

**PRD FR-3 Consequences:** add "Distinct hashes exist for source bytes, extracted record, relationship edge, embedding input, and toolchain; a change to one does not spuriously invalidate the others."

### 4.D Story 3.1 — three-level example records (expands FR-12)

**PRD FR-12** — extend text:
> `.java` examples are parsed with JavaParser AST; complete source is preserved **once**; the ingester emits **three levels of record — whole example, declared class/interface, and method/constructor**. Child (class/method) records are canonical SQLite rows linked to the parent example, each storing a **SourceRange** (relative path, start/end line+column) and the minimum source text needed for search and citation; Lucene holds method-sized projections. Examples are never compiled or run.

**FR-12 Consequences** — add:
- A method-sized search hit can fetch its complete parent example.
- Every class/method record and every source-derived edge carries a SourceRange and citation.

**Story 3.1** — add acceptance criteria:
- A synthetic example produces the expected whole-example, class, and method/constructor records with parent-child links.
- The complete source is stored once; child records reference it by SourceRange, not by copying the whole file.

### 4.E Story 3.2 — resolution ladder + evidence + new edges (expands FR-13)

**PRD FR-13** — extend text:
> Luciad imports and syntactically identifiable references are resolved against the catalog after Javadocs are indexed, using a **fixed resolution ladder** (FQN literal → explicit import + exact match → local/same-package declaration → unique wildcard-import match → owner+name+signature/arity match → ambiguous/unresolved). Each resolved reference emits a typed edge (`USES_TYPE`/`CONSTRUCTS`/`CALLS`, AD-30) carrying **resolution evidence** (source range, original token, candidate canonical symbols, selected symbol only when status is EXACT). Only an EXACT match becomes a verified Luciad symbol; AMBIGUOUS and UNRESOLVED references stay explicit and are never promoted (AD-4, AD-7).

**FR-13 Consequences** — add:
- Two packages exposing the same simple class name stay AMBIGUOUS unless source context selects one exactly.
- Overloaded methods stay AMBIGUOUS when parameter info is insufficient.
- An incomplete example project still produces cited partial records without an SDK JAR or build execution.

**Story 3.2** — add acceptance criteria mirroring the proposal's eight AC (constructor + chained calls produce `CONSTRUCTS`/`USES_TYPE`/`CALLS`; FQN + explicit import resolve exactly; ambiguous simple name; overload ambiguity; unresolved stays unresolved; incomplete project still cites; method hit fetches parent; method-vs-whole retrieval compared on gold queries without reducing exact-symbol accuracy).

### 4.F Domain types (Consistency Conventions record row)

**OLD:**
> Domain records are immutable Java `record` types (`ApiElement`, `MethodElement`, `Example`, `Workflow`, `Guide`, `Training`, `RelationshipEdge`, `Citation`, `IndexManifest`).

**NEW:**
> Domain records are immutable Java `record` types (`ApiElement`, `MethodElement`, `Example`, `ExampleClass`, `ExampleMethod`, `Workflow`, `Guide`, `Training`, `RelationshipEdge`, `Citation`, `IndexManifest`, `SourceRange`, `ResolutionEvidence`), plus enums `ResolutionStatus` (`EXACT`/`AMBIGUOUS`/`UNRESOLVED`) and `ResolutionKind` (`FQN_LITERAL`/`EXPLICIT_IMPORT`/`LOCAL_DECLARATION`/`SAME_PACKAGE`/`WILDCARD_IMPORT`/`OWNER_NAME_SIGNATURE`/`NONE`). *(Added 2026-08-25 Sprint Change Proposal — AD-30. Reuse existing value objects where present.)*

(These land as incremental additions in Story 1.4 / Story 3.x; Story 1.1 stays done and is not reopened.)

### 4.G ERD (architecture)

**ADD** to the core-entity ERD:
```
EXAMPLE ||--o{ EXAMPLE_CLASS : declares
EXAMPLE_CLASS ||--o{ EXAMPLE_METHOD : declares
EXAMPLE_METHOD ||--o{ EXAMPLE_SYMBOL_LINK : uses
```
and note that `EXAMPLE_SYMBOL_LINK` carries `resolution_status` + `resolution_kind` + `source_range`. `RELATIONSHIP_EDGE.type` enum now spans 17 values.

### 4.H Capability map (architecture)

**F4 row** "Defined by" — append **AD-30** to the existing `AD-4, AD-10, AD-14, AD-3, AD-29`.

### 4.I Retrieval evaluation dataset (testing strategy + B2)

**Epics testing-strategy "Retrieval evaluation" bullet** — add example-focused gold cases:
- a question answered by one method inside a large example;
- a constructor + required sequence of method calls;
- a local helper method that leads to Luciad calls;
- an ambiguous simple class name from two packages;
- an overloaded method unresolvable from arity alone;
- a sample with missing dependencies;
- whole-example-only vs method-level retrieval comparison (adopt method-level only if it improves or usefully complements top-5 without creating false verified symbols).

Ties to **B2** (gold dataset, owner: product + corpus owner). No SM renumber; these strengthen SM-3/SM-8 without changing thresholds.

---

## 5. Implementation Handoff

**Scope: Moderate** → route to **Product Owner + Developer**, with **Architect sign-off** required on 4.A (AD-30 + enum refinement).

| Recipient | Responsibility |
|---|---|
| Architect | Approve AD-30 wording; confirm the `EXAMPLE_USES → 3` refinement and the 15→17 count change across all three artifacts. |
| Product Owner / Dev | Apply the FR/Story edits (4.B–4.I); keep IDs stable; update the FR coverage map + testing strategy. |
| Dev (later) | Implement Story 1.3/1.4 expansions first, then Epic 3 example ingestion against the approved domain resolution model. Do not start Epic 3 example-ingestion code before AD-30 + Story 1.4 hashes are approved. |

**Delivery notes:**
- Obsolete work: none. Story 1.1 not reopened.
- Blocked work: Epic 3 example-ingestion implementation waits on AD-30 + Story 1.4 sign-off.
- Status changes: none applied until edits are approved and made.

**Success criteria:** all "15" references reconciled to "17"; AD-30 present; FR-2/3/12/13 amended with dated notes; five hashes defined; three-level records + resolution ladder + evidence specified; ERD + domain type list updated; consistency check passes.

---

## 6. Open items carried forward

- **[OPEN → resolved here]** Which AD owns example-resolution evidence → **new AD-30**.
- **[OPEN → resolved here]** Enum handling → **refine `EXAMPLE_USES` into 3 (15→17)**.
- **[OPEN → resolved here]** Record storage → **SQLite canonical rows + Lucene projections**.
- **[OPEN — still open, corpus owner]** Representative Luciad sample corpus + gold questions for the Epic 3 method-vs-whole comparison (ties to B2).
- **[Pre-existing, out of scope]** PRD "19 tools" strays (§F6 header, §API Contracts) vs the correct "20" in epics/architecture — fix separately.

---

## 7. Approval

- [ ] Direct-adjustment approach approved
- [ ] 4.A Relationship enum refinement (15→17) + AD-30 approved
- [ ] 4.B Story 1.3 pipeline additions approved
- [ ] 4.C Story 1.4 hash taxonomy approved
- [ ] 4.D Story 3.1 three-level records approved
- [ ] 4.E Story 3.2 resolution ladder + evidence approved
- [ ] 4.F–4.H Domain types / ERD / capability map approved
- [ ] 4.I Retrieval-evaluation additions approved

After approval, the affected planning artifacts (PRD, epics, architecture spine) are edited in place with dated Sprint-Change-Proposal notes; a short list of changed files and IDs is returned.
