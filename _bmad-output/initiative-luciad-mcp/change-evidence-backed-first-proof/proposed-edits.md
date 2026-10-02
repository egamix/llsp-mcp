# Proposed edits — Evidence-Backed Luciad First Proof

Status: approved, not applied. This companion is part of [the Sprint Change Proposal](change-evidence-backed-first-proof.md).

`OLD` identifies current text or the current scope; quoted excerpts are literal excerpts, not whole-section reproductions. `NEW` supplies the replacement rule or acceptance text. Grouped edits must be propagated to the corresponding inventory, coverage map, diagram, glossary and scope statements. Preserve historical amendment notes and completed-story records, clearly distinguishing them from the new delivery scope.

## A. Product requirements

### P01 — Authority, vision and agent journey

**Target:** PRD §0, §1, §2.3 UJ-1 and §9 assumptions; original implementation brief introductory status and §19 kickoff; product brief introductory status.

**OLD:** PRD §0 calls the implementation brief “the final BMAD handoff, a locked contract.” UJ-1 names `search_workflows`/`search_examples`, `get_class`/`get_method`, and `find_implementations`. The original brief still forbids shared-host deployment and requires hybrid retrieval in the first release.

**NEW:** Add an approval-dated amendment notice to each governing brief and PRD §0: “The approved Evidence-Backed Luciad First Proof change governs first-proof scope, interfaces, identity/evidence contracts and delivery order where it conflicts with this historical baseline. Earlier approved shared internal-host deployment remains binding. Historical text is retained for provenance, not as an instruction to reintroduce deferred work.” Until approval, this notice is proposed only.

Replace the vision/journey with: “A Luciad-first offline knowledge layer gives a coding agent version-scoped SDK facts, cited examples, approved practices and recipes. The agent searches knowledge, resolves exact symbols, fetches records and relationships, generates code, batch-checks Luciad symbols, and compiles/tests in its own environment before repair. The service proves catalog membership and supplied evidence; it does not prove compilation, runtime behavior, rendering or non-Luciad dependencies.”

**Rationale:** Prevent contradictory authority and specialized tool names from being reintroduced by later BMAD runs. External compilation remains outside the service boundary.

### P02 — Glossary and model

**Target:** PRD §3; FR-7/8/10/11/14/15/31; new FR-44/45.

**OLD:** “Record” enumerates five source families; “Canonical symbol” is a version-scoped key; “Hybrid retrieval” is the required normal mode; “Lexical mode” is fallback. Workflow is already a curated Markdown/YAML task recipe.

**NEW:**

- Record type identifies content structure; knowledge kind identifies its epistemic/governance role. They are separate fields. A Workflow is the existing procedure record carrying `RECIPE` knowledge, rather than a parallel recipe runtime store.
- A symbol coordinate is a domain-owned versioned structure containing SDK/product, version, language, namespace, owner, member, parameter types and element kind. Wire serialization is frozen under 2.8; display names and normalized aliases cannot become alternate identities.
- A stable record ID identifies any content record, including symbol-less content. Symbol coordinates identify API elements and their links. Neither depends on engine row/document IDs or absolute host paths.
- Exact plus BM25 is a supported primary retrieval mode requiring no model. Vector/hybrid mode is a deferred capability with explicit compatibility/failure behavior if later enabled.
- Proof facts record exact membership, ambiguity, unresolved evidence, version and source separately; relevance scores never prove correctness.
- Four knowledge kinds are `AUTHORITATIVE_FACT`, `EXPERT_APPROVED_PRACTICE`, `RECIPE`, `CANDIDATE_LEARNING`. Candidates stay outside active builds. Provenance can establish a vendor fact, but cannot automatically establish practice approval.
- Citations use typed source locators: HTML page/anchor, source range, curated document/section, and later deck/slide. Source identity, SDK scope and stable record ID remain mandatory for primary content; canonical symbol is optional when absent. Missing anchors are represented truthfully; never synthesize a source locator and call it verified.

**Rationale:** Serves symbol-less knowledge without inventing symbols, retains existing workflow semantics, and separates source structure from trust.

### P03 — Corpus, pipeline and identifiers

**Targets / OLD → NEW:**

| Target | OLD | NEW |
|---|---|---|
| FR-1 / F1 | Six local paths with Docling exception; model included among inputs | First proof consumes local Javadocs and approved task-relevant source/curated records. Model is not required in lexical mode. Training import is unavailable in this mode and an attempted request returns a clear unsupported-capability error. Existing CLI contract transition is explicit in 2.8/6.5, not a silent reinterpretation of flags. |
| FR-2 | Ordered eight stages include embeddings and vector index | Preserve staging/resume discipline. Exact/lexical profile skips deferred stages explicitly in its manifest/report; validation/activation remain mandatory. Resume checks content and build recipe, and rejects an invalid build regardless of collect-and-continue policy. |
| FR-3 | `recordHash`/`relationshipHash` described using identity fields; toolchain fingerprint equated with compatibility | Specify separate purposes for stable identity, source/content hashes, build recipe and runtime compatibility. Logical content/toolchain changes affect the appropriate digest; absolute source/output locations do not affect portable build identity. Version the report; record skipped stages, resolution counts, severity, completion and active profile. |
| FR-9 | “All 17 required relationship types are produced” without producer distinction | All 17 remain the eventual SDK/example/workflow family: 13 Javadoc structural types (2.5), one workflow type (3.4), three example types (3.2). Curated-knowledge dependency relations have a separately frozen typed contract. |
| FR-10/11 | Explicit unresolved references and bounded SDK traversal | Preserve these; distinguish SDK-only traversal from explicitly requested knowledge-dependency traversal. Return scope, depth, visited/returned limits and truncation. No dangling reference becomes an exact edge. |
| FR-12/13 | Structure-aware source/links; named example tools | Keep parent/class/method records, source preserved once, source ranges and evidence ladder. Fetch source via `get_record`; discover links through `search_knowledge`/`get_relationships`. Arity alone cannot prove one overload. Implement after 2.8. |
| FR-14 | Sidecar-supplied labels | Retain quality-label provenance. Add explicit practice approval state/owner and applicability, independent of optional tags. Missing sidecars never imply approval. |
| FR-15 | Workflows reference symbols/examples | Compile existing workflow records as recipes with stable required knowledge IDs, version/applicability, prerequisites, ordered steps and limitations. Validate dependencies before activation and applicability before recommendation. |
| FR-35/36 | Broad HTML guide parsing and linking | First proof supports an explicit allowlist of development-task-relevant guide sections with source/locator/hash and honest links. Full corpus ingestion is deferred. This bounded extraction path is Story 3.5, not a new crawler. |
| FR-39/40 | Training ingestion/linking in MVP | Retain requirement IDs, mark deferred pending evaluation benefit, corpus/governance readiness and separate reactivation decision. |
| FR-42 | Fixed broad vocabulary across four families, prerequisite to concept discovery | Defer broad technique-tag delivery. Existing sidecar mechanism remains a future option; record IDs and lexical search already support symbol-less records. No new vocabulary approval blocks the first proof. |

**Rationale:** Delivers the selected corpus without reopening completed scaffolding or requiring deferred model/training machinery.

### P04 — Retrieval and six-tool contract

**Target:** F5/F6; FR-16–25, FR-38/41/43; API Contracts; glossary and all tool-count statements.

**OLD:** “Hybrid is the required normal mode; lexical is a visible fallback”; F6 exposes “All 20 tools”; FR-21–23 enumerate specialized retrieval/relationship/example tools.

**NEW:** F5 is “Exact and lexical retrieval, with evaluation-gated semantic enhancement.” Version binds before lookup. FR-16 routes known symbols/signatures to exact resolution; FR-17 provides BM25 and filters for SDK version, record type, knowledge kind, applicability, provenance/governance and existing API metadata. FR-18/20 are deferred; FR-19 requires bounded deterministic lexical result assembly and relationship enrichment now, with vector fusion deferred. Relevance scores cannot convert candidates into proof.

Replace the default interface with exactly these names and allocations:

| Requirement | Tools / current responsibility replaced | New responsibility |
|---|---|---|
| FR-21 | `search_api`, `resolve_symbol`, `get_class`, `get_method`, `get_api_context` | `search_knowledge`, `resolve_symbol`, `get_record`. All kinds share search; only-filter searches provide deterministic browse. Search returns stable IDs and available sections. `get_record` supports bounded ID batches and section selection. Resolve returns exact/ambiguous/unresolved and version scope. |
| FR-22 | Four specialized relationship tools | `get_relationships`: bounded typed traversal with filters for edge family/type, direction, depth and implementation/subclass needs; distinguish SDK evidence from curated dependencies. |
| FR-23 | Four example/workflow tools | Example/workflow capabilities are acceptance cases of `search_knowledge`, `get_record` and `get_relationships`; no additional exposed names. Preserve quality/source/parent retrieval behavior. |
| FR-24 | `check_symbols`, `index_status` | Retain both; bounded batch symbol verdicts never compile. Status reports active build/SDK/schema/fingerprints, corpus coverage/skips/errors, mode and available capabilities. |
| FR-38 | Dedicated guide tools | Guide capability folded into common search/fetch; aliases deferred. |
| FR-41/43 | Training and concept tools | Deferred; no empty placeholder tools advertised in the first proof. |
| FR-25 | Transport/errors/bounds | Streamable HTTP `/mcp`, optional stdio, versioned common envelope with finite request/query/batch/traversal/response/time limits. No administrative or mutable runtime settings in agent schemas. |

Keep defaults of 10 results, hard cap 50 and ≤1 MiB; finite ID/symbol batch, query, traversal work and trace limits must be frozen in Story 2.8. Too-large records return explicit bounded sections/continuation or a structured size error, never an unbounded response. Cursors bind build/version/query so stale cursors are rejected or explicitly restarted, never silently mixed with a new build.

Content results include stable ID, record type, knowledge kind, provenance/governance, citation, applicability, proof facts, `matchedBy`, scope/coverage, active build and SDK version. Empty results distinguish not-found, not-indexed, filtered/inapplicable and unavailable capability as applicable. Report total with an exact/lower-bound/unknown relation; never label a bounded ranked set exhaustive.

Do not drop verdicts because a citation is absent. Optional diagnostic provenance may accompany them, but the schema must make absent/not-applicable distinct from a missing required content citation. Public inputs cannot accept credentials, host paths, endpoints, model/device settings or activation controls.

**Rationale:** Consolidates user capabilities into six names while preserving proof and payload boundaries.

### P05 — Lifecycle, guidance and release

| Target | OLD | NEW |
|---|---|---|
| FR-26/27 | Validate-before-activate plus fingerprint check | One `BuildValidator` checks structure, versions, integrity, required dependencies and compatibility for CLI validation/activation and server startup/reload/restore. Unknown SDK/stub schema, identity clash, build-invalidating ingest failure and invalid required recipe dependency reject the build. Explicit unresolved evidence alone is not build invalidity. |
| FR-28 | Side-by-side versions, cross-version opt-in | Preserve isolated versions/default and rollback. Every request retains one immutable build handle. Migration queries remain unsupported in the first proof. Rollback revalidates current compatibility; historical validation is insufficient. |
| FR-29 | Backup always includes model artifacts | Backup includes the complete manifest-declared capability set. Lexical builds explicitly declare no model and require none; future vector builds require all declared model assets. Restore uses the shared validator. |
| FR-30 | Eleven-point policy refers to specialized tools | Preserve its safety intent using the six names. Retrieve → resolve → fetch context/relationships → generate → batch-check → external compile/test → repair; report unresolved and untested outcomes honestly. Include one worked Luciad sequence. |
| FR-31 | HTML-oriented citation tuple | Apply P02 typed citations to search/fetch/relationship content. No symbol requirement on a fully cited symbol-less record. |
| FR-32/33 | Serving image plus training image; model bundle mandatory | Preserve internal-host/offline release and baseline hygiene. First-proof profile has no training-image or model dependency; final bundle contains exactly its declared assets. Training image remains deferred as Story 8.4. |
| FR-34 | `ingest`, `validate-index`, `serve`, `stats` | Keep these CLI commands. Add shared diagnostic access to the same application services as MCP, using operator-owned configuration and documented bounded output/exit semantics. Ingestion abort cannot report success. Concrete diagnostic subcommand spelling is frozen in 2.8; no agent administration tool is added. |

**Rationale:** The proof cannot depend on a route that bypasses validation or on absent vector assets; full packaging acceptance remains a later milestone.

### P06 — New requirements and coverage

| Proposed ID | New requirement with testable consequence | Coverage |
|---|---|---|
| FR-44 | Structured SDK identity and frozen portable contracts. Versioned known-answer symbol/record/edge/build recipes; stable IDs unchanged by output-root relocation; unknown SDK version cannot activate. | 2.8; AD-3/12/17/18/22/32 |
| FR-45 | Governed compiled knowledge. Four knowledge kinds; schema-validated deterministic authoring; candidates outside active builds; practices require approval; required recipe dependencies resolve compatibly; inapplicable recipes are not recommended. | 2.8, 3.3/3.4/3.6, 6.1; AD-14/31 |
| FR-46 | Shared build validation. All startup/load/activation/reload/restore routes use one validation policy and return equivalent rejection reasons for the same invalid artifacts. | 2.8, 5.6, 6.1–6.5; AD-5/6/23/33 |
| FR-47 | Honest bounded interface. Six read-only tools share explicit proof, route, coverage, empty reason and continuation semantics; no bounded query claims exhaustive results without proof. | 2.8, 4.6, 5.1–5.7, 7.2; AD-11/15/19/20/24/35 |
| FR-48 | Controlled outcome evaluation and governed improvement. Predeclared paired treatments, held-out protection and cost reporting; every accepted correction has human approval and a permanent regression before activation. | 10.1–10.4; AD-34 |
| FR-49 | Contract-change and verification gates. Schema/format changes have version/changelog or reviewed exemption; Windows/Linux known answers and selected mutations demonstrate critical tests can fail. | 2.8 and affected 6.x/10.x implementation; AD-32/34 |
| FR-50 | Shared application services. CLI diagnostics and MCP produce equivalent logical proof/coverage results for identical build/query/scope, excluding transport-specific wrapping. Neither compiles generated code; evaluation does so externally. | 2.8, 6.5, 7.1, 10.2; AD-1/36 |

**Rationale:** New obligations get explicit IDs and acceptance owners rather than remaining prose without coverage. Keep FR-37 reserved and existing requirement numbers stable.

### P07 — Milestones, metrics and blockers

**Target:** PRD §5/6/7, NFR table, constraints/API contracts, §8/9; epics inventory and Definition of Done.

**OLD:** §6.1 says all five record families, tags, hybrid and 20 tools ship in MVP; SM-3 provisionally requires ≥90% conceptual top-5 and SM-8 compares hybrid to lexical; B1/B2 are deferred toward Epic 5/8.

**NEW:** Name two milestones: first product proof and production release. The proof includes exact/lexical, six tools, contract freeze, task-relevant approved content, initial agent policy, immutable validation/activation and evaluation. Broad corpora/vectors/aliases/migration/multi-SDK/installer/autonomous promotion remain gated. Release still requires lifecycle completion, packaging, approved NFR evidence and human sign-offs.

Retain SM-1/2/4/5/6/7 safety goals. Tighten first-proof version isolation to no cross-version mixing. Retain SM-3's 90% as a provisional retrieval diagnostic until B2 review; SM-8 becomes a later vector-benefit gate. Add task completion, initial/final compile results, wrong-version/overload/unknown usage, practice adherence, tool calls, tokens, elapsed time, repair iterations and build cost. Freeze the task success rubric and improvement criterion before evaluation; report failed tasks and uncertainty rather than selecting favorable runs.

NFR-1/3–8/10/12/13 retain their numeric targets. Apply NFR-2's ≤2 s warm-search target to lexical first-proof search as a proposed scope amendment; deferred hybrid measurement still needs its own approved hardware/corpus evidence. NFR-4 startup is measured with the active profile's assets. NFR-9's ≤1.5 GiB cap applies to the serving image plus required profile assets. NFR-11 has no active training exception in the proof; NFR-14 checks capability-specific fingerprints, including no-model lexical builds. B1 still gates measurement; no NFR is claimed achieved here.

Move SQLite/symbol/relationship/Lucene lexical/MCP contract approval gates to Story 2.8 before dependent work. Leave KNN/model/tokenizer/licensing gates with deferred vector scope. Preserve B3/B4/B5; add B6 for predeclared task-outcome acceptance under 10.1. Resolve B5's Java 21 approval explicitly; do not silently mark it closed.

## B. Architecture decisions and diagrams

| Edit | Target / OLD | NEW proposed rule | Rationale |
|---|---|---|---|
| A01 | AD-1 says engines only through ports, but module diagram draws retrieval/ingestion → engines | Domain owns contracts; application services use domain ports; CLI/bootstrap alone wires concrete SQLite/Lucene adapters. Update graph to distinguish runtime wiring from compile-time dependencies. | Removes contradictory diagram guidance. |
| A02 | AD-3/18 require canonical symbol on every join; “a candidate lacking it is not fusable” | API references use one versioned symbol coordinate. All content joins/deduplication use stable record IDs; symbol-less cited records participate normally. Normalized aliases are lookup aids. Tags do not determine identity. | P02 and symbol-less search. |
| A03 | AD-6/8/13/21/26 assume model components and fallback | Manifest explicitly declares capabilities. Lexical is primary supported mode with no embedding requirement. Shared compatibility validation checks common contracts and only declared capability fields. Embedding/session/failure rules remain deferred, retaining bounded stateless request handling now. | Removes compulsory model startup. |
| A04 | AD-7/7a/12 exact/version rules and cross-version opt-in | Bind target SDK version before every query. Exact proof uses catalog facts; relevance never creates proof. First proof rejects cross-version migration/combined scopes explicitly. | Safety preserved under new tools. |
| A05 | AD-9/10/28 active Docling exception | AD-9/10 apply to every active first-proof path. AD-28 becomes a deferred design option, requiring a later evaluation/approval decision before its network/Python exception can run. | Deferral must affect runtime capability, not just backlog labels. |
| A06 | AD-11 only bounds responses; AD-19 demands full ranked candidate set | Domain contract defines finite input, candidate, traversal, batch, payload and time budgets. Bind version/build → route/retrieve within budgets → governance/applicability/content-citation checks → deterministic order → bounded page/enrichment. Return count relation and explicit work/response truncation; no unbounded intermediate set. | P04/FR-47. |
| A07 | AD-14 quality/technique sidecars; guide governance open | Preserve source-controlled quality. Require explicit approval for practices and dependency/applicability validation for recipes. Vendor-authored guide facts can be cited without inventing a quality label; guide-derived practice needs separate approval. Broad vocabulary/training governance gates move with their deferred scope. | Closes the practice trust gap while allowing limited guides. |
| A08 | AD-15/24 HTML tuple, `citation` absent for verdicts, 20-tool enumeration | Typed citations apply to primary content in search/get_record/get_relationships. resolve_symbol/check_symbols/index_status are verdict/status paths, never filtered for absent citations; optional diagnostic source evidence is explicitly separate. Enumerate only six tools. | One applicable envelope without hiding verdicts. |
| A09 | AD-17 build inputs include embedding configuration unconditionally | Coordinator hashes source content, governance inputs, selected mode and a versioned build recipe. Host roots/output paths do not affect identity. Selected capability assets are included; absent capabilities are explicitly declared. Report/checkpoint schema is versioned. | Portable reproducibility. |
| A10 | AD-20 tie-breaks on canonical symbol | Order exact-proof tier before ranked candidates; then declared deterministic score/order and stable record ID ascending. Browse uses stable ID order. Persist consistent build scope across continuation. | Deterministic ordering includes symbol-less records. |
| A11 | AD-22 storage is sole identity authority | Domain owns versioned pure identity recipes and coordinates; SQLite invokes those recipes at persistence and enforces uniqueness. Ingest submits logical records/edges. Identity cannot depend on engine internals. Any new evidence field's effect on identity is specified by 2.8. | Keeps one writer while avoiding storage-defined semantics. |
| A12 | AD-23 leaves request-start vs reload boundary open | Validate a new immutable handle before publication; each request pins exactly one build. CLI owns atomic pointer changes and writer coordination. Reader-safe retention/GC and lock/recovery behavior are frozen in 2.8, implemented in 6.x. | No mixed-build results or validation bypass. |
| A13 | AD-29 tags described as join keys and mandatory concept tool | Deferred grouping/filtering metadata; stable record identity remains independent of tags. No `search_concepts` requirement for first-proof discovery. | Supports the requested scope reduction. |
| A14 | AD-30 three example edge types; hash identities conflated | Preserve source ranges, three-level records, static evidence ladder and exact/ambiguous/unresolved semantics after 2.8. State arity uniqueness is insufficient proof where types cannot resolve. Replace hash-equivalence note with separated identity/content/build/runtime definitions. | Keeps useful design and corrects misleading hash claims. |
| A15 | AD-31 absent | **Knowledge trust boundary:** record type and knowledge kind are independent; curated authoring compiles to canonical storage; candidates cannot enter active builds; approved practices and recipes satisfy governance/applicability/dependencies. Version dependency types separately from the 17 SDK relations. | FR-45. |
| A16 | AD-32 absent | **Contract and verification freeze:** 2.8 owns schema, symbol serialization, IDs/hashes, ingestion-report, recipe/runtime fingerprint, request/result/error/continuation and finite-bound contracts before dependent work. Breaking changes version or record a reviewed exemption. | FR-44/49. |
| A17 | AD-33 absent | **Single build-validation policy:** every validation, activation, startup, reload, rollback and restore path calls the same service. Explicit ambiguity/unresolved source evidence is allowed; corrupt/ambiguous identity and invalid required dependency graphs reject activation. | FR-46. |
| A18 | AD-34 absent | **Evaluation and governed improvement:** predeclared paired task trials, isolated gold, two benchmark tiers, selected mutation and cross-platform known answers; corrections need validation, replay, regression and human approval before a new build. | FR-48/49. |
| A19 | AD-35 absent | **Six-tool interface and progressive disclosure:** exact names from P04, deterministic browse, stable IDs/sections, bounded batch fetch, common proof/coverage envelope, read-only agent schemas. | FR-47. |
| A20 | AD-36 absent | **Shared services and external execution boundary:** CLI diagnostics/MCP share query/proof/validation services; generated-code compile/test is performed outside the MCP service; no autonomous knowledge promotion. | FR-50. |

AD-2 and AD-27 retain shared trusted internal-host deployment and baseline hygiene. AD-4 retains two-pass ingestion; AD-5 retains immutable activation; AD-16 retains operator config/redaction; AD-25 retains staging-only checkpoints. Adjust cross-references to the new contracts without weakening these rules.

Update ARCH frontmatter, module graph, dataflow, ERD, consistency conventions, capability map and Deferred section together:

- Main build path: Javadoc + approved local examples/curated sections → catalog/typed evidence → lexical index → shared validator → sealed immutable build → atomic activation.
- Curated authoring → schema/governance/dependency checks → canonical records. Candidate staging stays outside the activated store.
- Serving: six tools → shared services/domain ports → read-only SQLite/Lucene. External coding agent performs compile/test and returns candidate findings through the human-governed correction process.
- Render ONNX/vector and Docling/full training as deferred boundaries, not required first-proof paths. Preserve source modules already scaffolded.
- ERD adds stable record identity, knowledge kind, applicability, governance, dependency records, typed citations and build metadata; optional symbol links do not exclude narrative records.
- Move approval gates to 2.8 where required; retain B1–B5 and deferred capability gates. Do not claim schema approval merely by adopting the architecture amendment.

## C. Story edits and acceptance criteria

Existing IDs retain their history. New titles describe proposed future scope; tracker key renames, if later chosen, require an old-key → new-key alias record. The default proposal preserves current tracker keys and updates their description/scope.

### E01 — Epic 1 and completed Epic 2 stories

**OLD:** 1.1–1.4 and 2.1–2.4 are done; later proposals could imply rebuilding their foundations.

**NEW:** No completed-story text/status rewrite. Add a forward amendment note in the epic overview referencing 2.8 for contract gaps and 6.x for operational gaps. Existing scaffold/identity behavior may require future implementation changes, but acceptance history remains intact.

**Rationale:** New requirements are prospective, not retrospective proof that completed work was incomplete.

### E02 — Story 2.5 summary and status

**Target:** EPICS Epic 2 description and Story 2.5 summary/AC; SPRINT status discrepancy.

**OLD:** “all 17 types are present in the graph”; “Javadoc two-pass here produces the 14 structural + workflow types.” STORY25's frozen intent instead specifies 13 structural types and explicitly excludes workflow/example edges.

**NEW:** Align the epic summary to “13 Javadoc structural types with the two specified inverse pairs; workflow/example types are produced under 3.4/3.2.” Preserve the detailed story's frozen intent and review work. Record the tracker/story discrepancy for its reviewer; change no status to done without completion evidence. Apply summary correction with the coordinated planning update after the current review gate clears.

**Rationale:** Repairs a planning count/producer mismatch without expanding the in-review story.

### E03 — Story 2.6, acceptance criteria

**OLD:** “the link is recorded as unresolved/external, not silently dropped or guessed at”; traversal reports documented SDK scope.

**NEW:** Persist original token/locator, SDK scope, candidate IDs where applicable, resolution status/kind and reason. Missing/external references remain explicitly unresolved; competing matches remain ambiguous with no selected exact target. Query consumers can retrieve this evidence. A partial record remains usable only if its required provenance is valid. Required recipe dependency failures are distinguished from ordinary unresolved source references. No code outside indexed evidence resolves a guess.

**Rationale:** Establishes reusable proof terminology before new consumers.

### E04 — Story 2.7, acceptance criteria

**OLD:** Cycle-safe traversal terminates within depth; no repeated node.

**NEW:** Retain both criteria; add finite visited-node/edge, result and execution budgets, deterministic ordering, explicit direct/recursive scope, version isolation and truncation/continuation. Report that bounded traversal does not prove graph completeness. Test cycle, empty, maximum and above-maximum boundary cases. 2.8 ratifies the common limits rather than leaving per-tool variants.

**Rationale:** Depth alone does not bound a wide graph.

### E05 — NEW Story 2.8: Contract and Verification Freeze

**OLD:** No story; identity/SQLite/lexical/MCP gates scattered across later epics.

**NEW story:** As an implementer, I want domain-owned versioned contracts and critical verification gates approved before broad retrieval/corpus work, so independently built components agree on identity, evidence, trust and lifecycle semantics.

**Acceptance criteria:**

1. One structured symbol coordinate/serialization, stable record/edge ID recipes, and separate content/build-recipe/runtime-compatibility hashes have documented known-answer fixtures. Reordering unrelated inputs or relocating roots does not alter logical identities; relevant content/recipe changes do.
2. One versioned schema covers records, SDK identity, source/typed locators, unresolved evidence, relationship families, knowledge kinds, governance/applicability/dependencies and build metadata. Existing provisional stores get an explicit compatible migration or rebuild-only rejection policy.
3. Record type and knowledge kind are separate. Candidate learning is excluded from active builds; practice approval and required recipe dependencies are validated. Scope/version ambiguity is never resolved by guessing.
4. One manifest/report contract declares corpus coverage, modes, skipped stages, severity, SDK version and capability-specific fingerprints. Stub/unknown SDK versions and build-invalidating results prevent activation/success.
5. Exact request/result/error/count/pagination/trace/batch/traversal contracts and finite defaults/caps are reviewed. Cursors are bound to query/version/build. Six tool names and primary-content vs verdict behavior are enumerated.
6. Domain ports own semantics; bootstrap wires adapters. One shared `BuildValidator` contract defines CLI/startup/activation/reload/rollback/restore behavior and distinguishes legal unresolved evidence from invalid identity/dependencies.
7. Selected mutations of identity, version scope, ambiguity and activation policy are detected by critical tests; Windows/Linux fixtures agree on logical symbols/IDs/catalog/manifests/reports, excluding explicitly operational timestamps/absolute diagnostic locations.
8. Contract-impact changes have version/changelog entries or explicit reviewed exemptions. Required schema/identity/tool sign-offs are recorded; no false auto-approval.
9. Deliver reviewable sub-tasks for identity, schema, public contracts and verification. Do not add vectors, Docling, new databases or generic multi-SDK infrastructure to satisfy this story.

**Dependencies:** 2.6/2.7; evaluation protocol 10.1 informs acceptance/corpus boundaries. **Rationale:** Establishes the compatibility/trust foundation before many consumers exist.

### E06 — Stories 3.1 and 3.2, dependencies and acceptance

**OLD:** Structure-aware example/source-range parsing and evidence ladder; full parent via example tool; dependency on earlier hash approval.

**NEW:** Preserve their full extraction/linking ACs. Require 2.8 contracts, version-scoped exact proof, method/class/parent stable IDs and typed source citations. Parent source is fetched through bounded `get_record`; arity-only ambiguity remains explicit. Limit initial input selection to development-task sources and retain AD-30's synthetic comparison cases. Broad cross-build incremental indexing remains deferred.

**Rationale:** Retains the August design within the narrower proof.

### E07 — Story 3.3: Apply governance-supplied quality and approval metadata

**OLD:** “Apply governance-supplied quality labels and technique tags”; vocabulary validation across examples/workflows/guides.

**NEW acceptance:** Read quality and explicit practice approval metadata from governed input. Missing metadata produces no inferred label/approval. Record owner, source and applicable SDK version. Reject attempted candidate promotion or invalid approval records. Reuse this mechanism in 3.4–3.6. Broad technique vocabulary is deferred and does not gate this story.

**Rationale:** Governance is essential to the proof; a broad tag taxonomy is not a prerequisite.

### E08 — Story 3.4: Compile workflow recipes and required knowledge links

**OLD:** Preserve workflow steps/prerequisites/limitations and resolve symbol/example links; receive technique tags.

**NEW acceptance:** Preserve those procedural fields, compile as Workflow records with `RECIPE` kind, and reference stable knowledge IDs with explicit version/applicability. Resolve required dependencies against facts/approved practices; an absent, incompatible or candidate required dependency blocks activation of the build containing that recipe. Freeze treatment of dependency cycles in 2.8 and reject invalid graphs deterministically. Return source provenance and applicability explanation. No duplicated authoritative facts as a competing runtime store.

**Rationale:** Turns task instructions into governed, reusable knowledge consumers.

### E09 — Story 3.5: Ingest selected guide sections for the proof

**OLD:** Whole guide articles parsed into one record with mandatory tag integration.

**NEW acceptance:** Consume an explicit allowlist of approved local guide pages/sections selected using development tasks. Preserve title, selected ordered prose/snippets, original source/section locator, content hash and linked/unresolved references. Report selection/skips and coverage; never imply the full guide corpus was ingested. Vendor facts may remain without a quality label; authored practices extracted from them need explicit approval. Malformed inputs follow the classified failure policy. Broad guide ingestion is a separate deferred extension.

**Rationale:** Supplies needed explanations without making full-corpus extraction a proof dependency.

### E10 — NEW Story 3.6: Compile approved engineering practices

**OLD:** No dedicated governed practice-authoring path.

**NEW acceptance:** Define versioned Markdown/YAML authoring with stable ID, kind, SDK applicability, source citations, governance owner/approval and dependency fields. Deterministically compile approved practices into existing canonical storage/index ports. Reject malformed/unapproved/candidate content from the active-build path; preserve candidate proposals separately for human review. Verify symbol-less approved content is searchable/fetchable with complete citations. No new runtime database.

**Dependencies:** 2.8 and 3.3. **Rationale:** Provides the minimal knowledge authoring model requested by the consolidation.

### E11 — Stories 4.1/4.2 and NEW 4.6

**OLD 4.1:** Exact match ranked above lexical/vector. **NEW:** Version-bound exact resolution returns exact proof or explicit ambiguity; no lexical result is promoted to exact.

**OLD 4.2:** Canonical/camel-case and prose BM25. **NEW:** Retain these fields and add record ID, knowledge kind, governance/provenance, applicability and scope filters. Symbol-less approved records participate. No embedding/model needed.

**OLD 4.6:** Absent. **NEW Story 4.6 — Assemble bounded, cited lexical results:** Given exact and lexical candidates, enforce trust/applicability/citation rules, deduplicate by stable record ID, order deterministically, add bounded relationship context and return truthful count relation/truncation. Deterministic filter-only browse works; limits apply to internal work as well as serialized payload. Empty/not-indexed/inapplicable outcomes remain distinguishable.

**Disposition 4.3/4.4/4.5:** Defer their vector/fusion/fingerprint implementation. Move currently essential bounded enrichment from 4.4 to 4.6 with an explicit trace; do not mark 4.4 done.

**Rationale:** Supplies a complete supported lexical mode without requiring the deferred vector epic slice.

### E12 — Stories 5.1–5.4: Tool handlers

| Story / section | OLD | NEW acceptance |
|---|---|---|
| 5.1 intent/AC | Five retrieval/symbol tools | Implement `search_knowledge` and `resolve_symbol` through shared services. Search supports filters/browse and follow-up IDs/sections; resolve returns scoped exact/ambiguous/unresolved. Test unknown, wrong-version and symbol-less content paths. |
| 5.2 intent/AC | Four relationship tools | Implement only `get_relationships` with typed/family/direction/depth filters and 2.7 budgets. Preserve implementation/subclass capability and evidence. Never imply documentation or bounded traversal is exhaustive. |
| 5.3 intent/AC | Four example/workflow tools | Implement `get_record` across supported record types with bounded ID batches/sections, parent-example source access, complete content citations and per-ID not-found/oversize outcomes. Return only eligible records from the bound active build. |
| 5.4 intent/AC | `check_symbols` and `index_status` | Retain both, add batch bounds, explicit build/version/schema/coverage/fingerprints and lexical capability status. Unknown/ambiguous inputs never become valid; no-model lexical operation is healthy. |
| 5.3b | Dedicated guide tools | Defer aliases; selected-guide search/fetch acceptance moves to 5.1/5.3. |
| 5.3c / 5.3d | Training/concept tools | Defer; do not expose placeholders. |

**Rationale:** Exactly six public tools cover the essential user operations.

### E13 — Stories 5.5–5.8: Transport, load, envelope and policy

| Story / section | OLD | NEW acceptance |
|---|---|---|
| 5.5 | HTTP errors/timeouts; dedicated get tools | Retain transport `/mcp` and optional stdio. Enforce shared input/output/time limits and read-only schemas without administrative settings. Full content only through bounded `get_record`. |
| 5.6 | Fixed pointer “explicitly without atomicity or rollback guarantees” | Serve only from a validated immutable build. Shared validator runs on startup/reload; each request pins a single build; invalid replacement leaves a valid current build serving or fails clearly on cold start. Depend on minimum 6.1/6.2; no bypass demo accepted as first proof. |
| 5.7 | Common shape across 20 tools | Implement the 2.8 envelope across all six: stable codes, IDs/kinds, proof, matchedBy, governance/applicability, build/version, coverage/count relation, empty reason and continuation. Content citations required; verdicts not dropped. Run this before individual handlers are accepted. |
| 5.8 | Draft after all 20 tools incl. 5.3b/c/d | Draft guidance as six contracts stabilize, link 7.1 completion and include external compile/test/repair boundary. Only actual tool names appear. |

**Rationale:** Common contracts and lifecycle are prerequisites to a meaningful end-to-end proof.

### E14 — Stories 6.1–6.4 and NEW 6.5

| Story / section | OLD | NEW acceptance |
|---|---|---|
| 6.1 | Validate then atomic pointer swap | Use shared validator; reject invalid SDK/schema/governance/dependencies and build-invalidating outcomes. Serialize competing writers; publish only sealed builds. Interrupted staging/activation and in-flight readers follow frozen recovery rules. |
| 6.2 | Reject mismatch at activation | Apply same rejection policy at validation, activation, startup, reload, rollback and restore. Lexical profiles require no model; unknown profiles/schema fail closed. A migration produces a separately validated new build. |
| 6.3 | Swap to previously validated retained build | Revalidate runtime compatibility before rollback and pin readers safely. Retain current/previous per policy; no deletion while in use. Preserve explicit version default. |
| 6.4 | All seven components incl. model required | Validate completeness against the manifest/profile; explicitly absent model in lexical mode is valid, missing declared assets invalid. Restore runs the same validator. |
| NEW 6.5 — Shared diagnostics and service conformance | Absent; full exit codes wait for 8.3 | CLI diagnostics and MCP use same query/proof services; equivalent logical verdict/coverage for identical inputs. `BUILD_INVALIDATING` produces non-success and cannot activate. Classify report-write failure so success cannot conceal missing required evidence. Test documented outcomes and alternate entry routes using synthetic fixtures. |

**Rationale:** Brings correctness-critical operational behavior forward; 8.3 still owns final CLI/help/release documentation.

### E15 — Epic 7

**Story 7.1 OLD:** Complete the eleven-point policy using specialized §10 names. **NEW acceptance:** Ship six-tool retrieve/resolve/context/generate/check/external compile-test/repair guidance with one worked sequence, applicability checks, honest unresolved/untested reports and no invented SDK APIs. Policy is usable at first-proof evaluation.

**Story 7.2 OLD:** HTML-specific citation fields, with absent citations on verdict tools. **NEW acceptance:** Validate typed citations and stable record IDs for all content payloads, including symbol-less practices/guides and dependency evidence; exact/ambiguous/unknown verdicts remain visible independent of content-citation filtering. No fabricated anchors.

**Rationale:** Both guidance and source evidence affect measured agent outcomes immediately.

### E16 — Epics 8 and 9

**8.1 OLD → NEW:** Retain serving image/internal-network Compose acceptance; profile requires no ONNX model or Docling dependency. Keep standard container hygiene and offline operation.

**8.2 OLD → NEW:** Bundle complete manifest-declared assets with checksums/SBOM/licenses and install paths. Lexical build does not require model artifacts; no training image included. Final release still requires existing approvals.

**8.3 OLD → NEW:** Retain final CLI/help/headless/exit-code acceptance, explicitly building on 6.5's early invalid-build semantics and shared diagnostics rather than deferring those until packaging.

**8.4 / 9.1 / 9.2 / 9.3 OLD → NEW:** Move training-image/deck/link/tag delivery from MVP backlog to recorded deferred scope. Keep original intent and dependencies for future reactivation; do not mark done or silently delete.

**Rationale:** Keeps offline deployment while removing deferred training work from the critical path.

### E17 — NEW Epic 10: Agent Outcome Evaluation

**OLD:** No evaluation epic; a retrieval gold set and model comparison exist as requirements.

**NEW:** Deliver a measured first-proof decision and a governed correction loop, with these stories:

- **10.1 — Freeze the evaluation protocol and task set.** Curate 20–30 representative Luciad tasks and source evidence within existing handling rules; partition development/held-out before corpus selection. Freeze success rubric, improvement rule, treatment controls, repeat policy and budgets. Owners approve thresholds before trials. Retain retrieval diagnostics alongside outcome metrics; no invented claim of success.
- **10.2 — Build the controlled external evaluation harness.** Run unaided, exact+lexical, then relationship/example/practice treatments with the same agent/model/prompt/environment/limits except the intended treatment. Smoke-test access; isolate gold; use repeated paired trials and blind judging where practical. Compile/test occurs outside the knowledge service with separately available SDK/build prerequisites. If prerequisites are absent, record blocked/not-run, never passed. Report initial/final compile, symbol errors, citations/practice adherence, task result, calls/tokens/time/repairs and build costs separately.
- **10.3 — Evaluate outcomes and decide expansion.** Execute the frozen suite, include all failures and uncertainty, compare to the unaided baseline, and apply the predeclared decision rule. Report coverage and measurement limitations. Propose vectors, corpora or specialized tools only when a diagnosed gap and controlled comparison justify them without breaking safety gates.
- **10.4 — Govern corrections and retain regressions.** Classify failures as KNOWLEDGE_GAP, RECIPE_DEFECT, RETRIEVAL_OR_ROUTING_DEFECT, VERSION_OR_CORPUS_DEFECT, GENUINE_AMBIGUITY or NON_LUCIAD_FAILURE. Keep correction as CANDIDATE_LEARNING until minimal patch, structural/symbol/citation checks, targeted replay, affected regressions, independent review where practical and human/domain-owner approval pass. Add a permanent regression before activating a new immutable build. Never auto-promote or repeatedly tune against held-out scoring material.

**Rationale:** Replaces unmeasured architectural expansion with an explicit product decision process.

## D. Tracker, deferred register and traceability

### T01 — Sprint tracking

**OLD:** Development statuses allow backlog/ready-for-dev/in-progress/review/done; no `deferred` value; stories include 20-tool/vector/training work and no 2.8/Epic 10.

**NEW proposed edit:** Preserve all current statuses, including the unresolved 2.5 discrepancy, until their owners reconcile them. Preserve existing YAML keys to avoid breaking links. Add new stories as `backlog` and new Epic 10 as `backlog`, with retrospective optional. Add proposed names in comments where story scopes change.

Do not write unsupported `deferred` values into `development_status`. Add an additive `deferred_scope` mapping that records reason, reactivation gate and replacement capability owners; original execution statuses remain backlog. State clearly that scheduling must exclude these keys until an approved gate clears. If transitioning to `bmad-ticket`'s ticket tree, import these dispositions deliberately rather than running two unsynchronized trackers. Migration itself is a separate follow-on task.

Deferred story keys:

| Existing key | Reason / retained capability |
|---|---|
| `4-3-local-onnx-vector-search-with-explicit-failure-semantics` | Vector benefit must be demonstrated after lexical baseline. |
| `4-4-fuse-lexical-and-vector-results-under-exact-priority` | Vector fusion deferred; required lexical enrichment moves to 4.6. |
| `4-5-enforce-a-consistent-embedding-fingerprint` | Deferred vector capability; general compatibility remains 2.8/6.2. |
| `5-3b-guide-tools` | Dedicated aliases deferred; selected-guide capability covered by 5.1/5.3. |
| `5-3c-training-tools` | Training and specialized aliases deferred. |
| `5-3d-concept-technique-search-tool` | Broad tag/concept tool deferred; symbol-less search covered by common tools. |
| `8-4-separate-ingest-time-only-image-for-training-material-ingestion` | Training deferred, no image needed for proof. |
| `epic-9` and all `9-1`, `9-2`, `9-3` story keys | Full training corpus/governance need evaluated first. |

New proposed keys: `2-8-contract-and-verification-freeze`, `3-6-compile-approved-engineering-practices`, `4-6-assemble-bounded-cited-lexical-results`, `6-5-shared-diagnostics-and-service-conformance`, `epic-10`, `10-1-freeze-evaluation-protocol-and-task-set`, `10-2-build-controlled-external-evaluation-harness`, `10-3-evaluate-outcomes-and-decide-expansion`, `10-4-govern-corrections-and-retain-regressions`, `epic-10-retrospective`.

### T02 — Deferred register and blocker ownership

**OLD:** Deferred-work entries are recorded implementation/review findings with no mapping to the new proof sequence.

**NEW:** Append a course-correction section; keep every historical finding. Link, without claiming fixed:

| Existing finding | New owner/gate |
|---|---|
| Build identity includes path strings; delimiter/case/symlink issues | 2.8 portable source/identity contract; distinguish diagnostic absolute paths from hashed logical source identity. |
| No identityFields registry; hash nullability/meaning issues | 2.8 identity recipes and schema validation. |
| Report lacks schema version/invariants | 2.8 report contract; 6.5 diagnostic failure behavior. |
| Provisional catalog lacks schema/build marker | 2.8 approved metadata and rebuild/migration policy; 6.2 rejection. |
| Reconstructed member anchors | 2.8 provenance contract and necessary forward extraction work; never rewrite 2.4's completion. |
| Build-invalidating ingest still reports success | 6.5 non-success outcome plus 6.1 activation guard. |
| Checkpoint torn writes / competing writers | 6.1/6.2 transition tests and lifecycle coordination. |
| Catalog per-record autocommit / large-input buffering | Evaluate under B1; bounded batches remain required, but do not invent a performance result. |

Also record full guide-corpus expansion, broad tags/vocabulary, vectors/fusion, training/image, specialized aliases, migration tools, multi-SDK framework, external-agent installer, conversation mining, autonomous promotion/self-repair and new infrastructure as gated scope. Reactivation requires a diagnosed task gap, a controlled benefit result, preserved safety metrics and owner approval; training additionally needs its existing provenance/governance/version decisions.

### T03 — Full requirement disposition

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

Update EPICS Requirements Inventory, Additional Requirements, FR Coverage Map, Epic List, story bodies and Definition of Done from this disposition in the same planning changeset. Update ARCH capability map/Deferred and PRD cross-references in parallel with that changeset, not as independently conflicting specifications. Dates and statuses must reflect actual approval/application events; neither “final” frontmatter nor an existing implemented scaffold auto-closes a governance gate.

### T04 — Secondary documentation

The original and product briefs get explicit amendment precedence (P01). The product brief's current headline tool count, corpus scope, relationship count, workflow and diagrams should either be updated to the approved first-proof direction or explicitly labeled historical in that same amendment. Do not leave it advertised as the live six-tool plan while retaining unqualified 20-tool promises.

After implementation changes, owners update operational docs, CLI examples, agent/SKILL.md, packaging and CI evidence through their stories. Existing dated readiness/review reports remain historical; rerun readiness once the approved planning set agrees. No source-code, deployment-script or test-suite change is authorized merely by saving this proposal.
