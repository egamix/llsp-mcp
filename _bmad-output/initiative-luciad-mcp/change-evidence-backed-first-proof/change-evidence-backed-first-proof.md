---
title: "Sprint Change Proposal: Evidence-Backed Luciad First Proof"
type: sprint-change-proposal
date: 2026-10-02
status: approved
mode: batch
scope_classification: major
initiative: initiative-luciad-mcp
decision_owner: product owner
trigger_source: ../../planning-artifacts/sprint-change-proposal-2026-10-02-all-proposals-consolidated.md
companions:
  - proposed-edits.md
  - evidence-snapshot.json
approval: approved
approved_at: 2026-10-02
---

# Sprint Change Proposal: Evidence-Backed Luciad First Proof

## 1. Issue summary

The requested October 2 consolidation changes the delivery strategy from a broad five-corpus, 20-tool hybrid retrieval MVP to a smaller first product proof: exact SDK identity, BM25 retrieval, six read-only MCP tools, a small governed knowledge corpus, and controlled agent-outcome evaluation. Existing requirements and architecture still mandate the broader scope. Implementing the consolidation without reconciling those documents would give later stories contradictory instructions.

This is a product-scope and contract change during Epic 2, not evidence that completed implementation should be rolled back. Story 2.5 is the current delivery boundary. Its story file says `in-review`; the sprint tracker says `in-progress`. Neither proves review completion. Its frozen intent covers 13 Javadoc structural relationships, while the epic summary incorrectly expects 17 in Story 2.5 and attributes workflow relationships to Javadoc ingestion.

The October 2 proposal is the input to this assessment, not an approved replacement specification. Its statement that September 22 already superseded September 7 overstates the earlier document: September 22 says `supersedes_if_approved`. Neither proposal is treated here as already approved.

### Evidence and source authority

The following live files were consulted; their hashes are recorded in `evidence-snapshot.json` so a later application can detect intervening edits:

| Alias | Source | Relevant evidence |
|---|---|---|
| INPUT | [October 2 consolidation](../../planning-artifacts/sprint-change-proposal-2026-10-02-all-proposals-consolidated.md) | Six tools, contract freeze, narrower corpus, evaluation and deferrals. |
| PRD | [PRD](../../planning-artifacts/prds/prd-luciad-mcp-2026-08-20/prd.md) | F5 requires hybrid as normal mode; F6 specifies 20 tools; §6.1 includes guides, training and tags in MVP. |
| ARCH | [Architecture Spine](../../planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md) | AD-18 rejects candidates without a canonical-symbol join key; AD-19 returns a full candidate set; schema/identity approval gates remain deferred to later epics. |
| EPICS | [Epic breakdown](../../planning-artifacts/epics.md) | Story 5.6 explicitly lacks atomicity; Story 2.8 and Epic 10 do not exist; vector and training work remain planned. |
| SPRINT | [Sprint tracker](../../implementation-artifacts/sprint-status.yaml) | Stories 1.1–1.4 and 2.1–2.4 are done; 2.5 is in-progress; remaining stories backlog. |
| STORY25 | [Story 2.5](../../implementation-artifacts/2-5-construct-the-typed-relationship-graph-in-two-passes.md) | In-review; frozen 13-type intent; unresolved persistence and traversal explicitly reserved for 2.6/2.7. |
| DEFERRED | [Deferred work](../../implementation-artifacts/deferred-work.md) | Path-dependent build identity, provisional schema, derived anchors, report versioning, and abort/exit-code gaps. These are recorded findings, not newly reproduced failures. |
| BRIEF | [Original implementation brief](../../../docs/luciad-knowledge-mcp-implementation-brief.md) | Described as locked by PRD §0; still says loopback, mandatory vectors and specialized tools. Needs explicit amendment precedence. |
| PRODUCT | [August 24 product brief](../../planning-artifacts/briefs/brief-luciad-mcp-2026-08-24/brief.md) | Shared internal host, but still advertises 20 tools and 15 relationships. |

Repository `AGENTS.md` was read and the Graft orientation command was used. It contains no BMAD context block. No standalone UX specification or spec kernel was discovered in the planning inventory; the current PRD/epics explicitly describe an agent-facing MCP interface without human screens. Existing documents remain in their current locations; creating the initiative does not migrate them.

## 2. Impact analysis

### Epic and delivery impact

| Epic | Current position | Proposed disposition |
|---|---|---|
| 1 Foundation | All four stories done; epic itself still in-progress | Preserve history and status. Address identity/report/pipeline gaps prospectively in 2.8 and 6.x. |
| 2 Javadoc | 2.1–2.4 done; 2.5 has a status discrepancy; 2.6/2.7 backlog | Finish 2.5 review; retain explicit evidence/traversal work; add 2.8 contract and verification freeze. |
| 3 Examples/workflows/guides | Backlog | Keep structure-aware examples; narrow guides to selected sources; replace broad tagging dependency with governed practice/recipe compilation. Add 3.6. |
| 4 Retrieval | Backlog | Exact resolution and BM25 are the first supported mode. Defer 4.3–4.5 vector work; add 4.6 for bounded lexical result assembly/enrichment. |
| 5 MCP | Backlog | Rescope stories around six tools. Retire specialized-tool stories into the deferred register; pull common contracts ahead of handlers. |
| 6 Lifecycle | Backlog | Bring minimum validation and atomic activation forward; use one validator everywhere. Add shared diagnostic/conformance work as 6.5. |
| 7 Agent guidance | Backlog | Ship initial usable policy with the proof, including external compile/test and repair. Reuse 5.8 draft. |
| 8 Release | Backlog | Retain shared internal-host and offline-release target. Defer 8.4 training image. Pull invalid-build exit semantics into 6.5; finish packaging after proof. |
| 9 Training | Backlog | Defer epic and all three stories until outcome evidence justifies the corpus. |
| 10 Agent Outcome Evaluation | Absent | Add 10.1 protocol, 10.2 harness, 10.3 controlled evaluation, 10.4 governed corrections. Define protocol before selecting the proof corpus. |

This is a first product proof, not production release acceptance. Packaging, rollback/backup completion, NFR evidence, and existing release approvals remain required for a release. The proposal does not silently declare those gates passed.

### Conflicts that must be resolved in the edits

1. **Stable record identity and symbol identity are different.** AD-18 currently makes a canonical symbol mandatory for fusion, contradicting AD-15's servable symbol-less records. Use stable record IDs to identify/deduplicate content; use versioned symbol coordinates to link API identities. Tags are filters/grouping attributes, never record identity.
2. **Bounded output also needs bounded internal work.** AD-19 currently requires the full ranked candidate set before pagination. Replace this with bounded query execution and truthful exact/lower-bound/unknown counts. A ranked sample cannot claim corpus exhaustiveness.
3. **Fail-closed must distinguish invalid builds from incomplete evidence.** Unknown schema, corrupt data, identity collisions, unknown SDK version and invalid required recipe dependencies block activation. Explicit unresolved/external references and ambiguity are legitimate evidence when permitted by the record contract; they remain visible and are never promoted to proof.
4. **A fixed-pointer demo is insufficient for the first proof.** Story 5.6 must depend on shared build validation and atomic activation from 6.1/6.2. An isolated handler fixture can run earlier, but it does not establish lifecycle acceptance.
5. **The 17-type graph is a project total.** Javadoc contributes 13 structural types, workflows contribute `WORKFLOW_REQUIRES`, and examples contribute `USES_TYPE`, `CONSTRUCTS`, `CALLS`. Correct the epic summary without changing Story 2.5's frozen intent. Curated-knowledge dependency types need their own explicit domain contract in 2.8, not ad-hoc additions to the SDK enum.
6. **Hash purposes are currently conflated.** The AD-30/Story 1.4 narrative equates content hashes with identity fields and build recipe with runtime compatibility. Story 2.8 must define these separately while preserving completed-story history.
7. **First-proof measurements must be decidable.** Existing 90% top-5 retrieval is provisional under B2. Retain it as a diagnostic target pending an explicit decision; define agent-task success and its improvement rule before running the decision suite. This assessment cannot claim measured benefit.
8. **Version selection is not migration.** Keep version-isolated builds and an explicit default. Return an unsupported-operation result for cross-version migration queries in the first proof; do not merge versions implicitly.

### Technical and secondary impact

Future implementation changes affect domain identity/evidence types, schema metadata, adapter wiring, query bounds, CLI outcomes, lifecycle coordination, tool schemas and documentation. They do not require a new database or runtime. Existing ONNX/training module scaffolds can remain dormant; deleting them is not part of Correct Course.

The authoring model must distinguish **record type** (API/example/workflow/guide/etc.) from **knowledge kind** (fact/approved practice/recipe/candidate). A vendor guide is not automatically an approved engineering practice. Practice approval and provenance must be explicit; candidate learning is stored outside active builds. A `Workflow` record maps to recipe knowledge without introducing two competing procedure stores.

The original implementation brief and product brief need explicit amendment notices, because downstream documents currently point to them as governing. Full rewrites of historical proposals and dated readiness reports are unnecessary. Regenerate readiness evidence later against the approved plan. Preserve proprietary-corpus restrictions and existing licensing/governance gates; do not add security work.

## 3. Recommended approach

Use **MVP scope review plus direct adjustment**. Retain implemented foundation/catalog work and stage the narrower proof behind contract freeze. Do not roll back completed stories or absorb new contracts into the in-review Story 2.5.

| Option | Effort | Risk | Assessment |
|---|---|---|---|
| Adjust stories while retaining all original MVP scope | High | High | Does not achieve the requested narrower proof or evaluation-first sequencing. |
| Roll back foundation/catalog work | High | High | No source evidence establishes a benefit; would discard useful parsing, persistence and review history. |
| Narrow proof and reconcile contracts prospectively | Medium planning; substantial implementation | Medium | Recommended. Reduces early corpus/model/tool breadth while addressing concrete contract gaps. |

No calendar estimate is justified by the current artifacts. Critical path: 2.5 review → 2.6/2.7 → 2.8 → retrieval/common lifecycle → tools and proof corpus → evaluation. Story 2.8 needs sizing into reviewable contract/schema/verification tasks; it must not become an unbounded rewrite. If satisfying a new contract changes completed behavior, implement a forward change with explicit compatibility/rebuild policy.

### Proposed sequence and gates

1. Complete Story 2.5 review and reconcile its recorded status through the existing review process. Preserve 1.x and 2.1–2.4 completion history.
2. Complete 2.6 explicit evidence and 2.7 bounded traversal. Define 10.1 evaluation protocol early, before corpus curation and model/tool tuning.
3. Complete 2.8 contract freeze and obtain the existing technical schema/identity/interface sign-offs. Define build and runtime fingerprint separation, capability-specific manifests and failure classes.
4. Build exact/BM25 retrieval (4.1/4.2/4.6), shared validation and minimum activation (6.1/6.2), diagnostics/conformance (6.5), and the common response contract (5.7).
5. Build six tool handlers and transport (5.1–5.6 as rescoped), structure-aware examples and curated knowledge (3.1–3.6), and initial guidance/citations (5.8/7.1/7.2). The contracts permit development against synthetic fixtures before every corpus family exists.
6. Run the controlled suite (10.2/10.3), diagnose failures, and apply approved corrections under 10.4. Finish release lifecycle/packaging after proof; reopen deferred scope only through a measured-benefit decision.

Reserve held-out tasks before curation. Use development tasks to choose examples/guide sections/practices; keep held-out gold answers and scoring material inaccessible to the agent and corpus authors. Do not repeatedly tune against the held-out set and continue calling it held-out.

## 4. Detailed change proposals

[proposed-edits.md](proposed-edits.md) is part of this proposal. It contains the source sections, OLD → NEW text, acceptance criteria, rationale, requirement/decision mappings, story dispositions, and tracker/deferred-work edits. Approving the main document includes those explicit edits and the clarifications in §2.

The edit set reserves FR-44–FR-50 and AD-31–AD-36; FR-37 remains intentionally unassigned. New stories are 2.8, 3.6, 4.6, 6.5 and 10.1–10.4. Check for intervening ID allocations before applying the package.

## 5. Implementation handoff

**Classification: Major. Route to Product Manager and Architect for planning reconciliation, then `bmad-ticket` for backlog application.** This is a documented handoff; no other chat or person has been messaged.

| Owner | Responsibility and output |
|---|---|
| Product owner | Approve/revise scope, six tools, proof-vs-release boundary and proposed edit package. Assign governance owners and approve evaluation decision rule. |
| PM (`bmad-prd` / PM agent) | Apply approved PRD and source-precedence amendments, success metrics, milestone scope and requirement coverage. |
| Architect (`bmad-architecture` / architect agent) | Reconcile ADs, diagrams, identity/evidence/governance contracts, shared services and freeze gates. Final schemas remain separately reviewable. |
| Backlog owner (`bmad-ticket`) | Apply added/rescoped/deferred stories from the companion, preserve IDs/history/status, and reconcile tracking vocabulary before any ticket-tree migration. |
| Developer | Finish existing 2.5 review; implement approved future stories individually after their dependencies and contract gates clear. |
| Corpus/technical owners | Supply eligible data and named practice approvals, reference hardware, SDK version and predeclared evaluation protocol. |

Application must be one coordinated planning changeset: PRD, spine, epics/coverage map, tracker, deferred register and authority notices agree before handing implementation off. Compare the recorded hashes to current files first; reconcile drift instead of overwriting it. Keep existing uncommitted source/story changes intact.

Success criteria for the **planning change**:

- All live scope/tool-count statements agree on six tools for the proof and explicitly deferred breadth.
- Every changed/new FR maps to an architecture decision and backlog owner; IDs are not silently recycled.
- The symbol-less join, unresolved-evidence, bounded-work and lifecycle contradictions are resolved.
- Completed stories and the in-review 2.5 intent retain their history; removed proof work is deferred, never marked done.
- Owners and gates for schema contracts, evaluation thresholds and release approvals are explicit.

Success criteria for the **implemented proof**:

- 100% exact lookup on canonical fixtures and citation completeness on primary content results.
- Zero unknown symbols reported valid, ambiguous overloads promoted to exact, and cross-version leakage.
- Deterministic logical identities across Windows/Linux; critical mutation checks detect deliberate trust/identity regressions.
- All build entry points reject corrupt/incompatible builds; explicit incomplete evidence remains honest.
- No candidate/unapproved practice in an active build; no recipe with invalid required dependencies.
- Controlled agent-task improvement meets the rule frozen under 10.1, with costs, failures and external compile/test results reported separately.

## 6. Checklist and unresolved decisions

| Checklist item | Status | Finding / next action |
|---|---|---|
| 1.1 Triggering story/context | [x] | Strategic proposal during Story 2.5; no claim it was caused by a 2.5 defect. |
| 1.2 Core problem | [x] | Broader mandatory MVP conflicts with narrower evidence-backed proof. |
| 1.3 Evidence | [x] | PRD/AD/story conflicts and recorded deferred risks identified. |
| 2.1 Current epic | [x] | Preserve 2.5; add 2.8 after 2.6/2.7. |
| 2.2 Epic changes | [x] | §2 epic table and companion dispositions. |
| 2.3 Remaining epics | [x] | Epics 3–9 assessed, including release/lifecycle boundaries. |
| 2.4 Invalidated/new epics | [x] | Training deferred; Epic 10 proposed. |
| 2.5 Order/priority | [x] | Protocol/freeze/minimum activation moved before proof acceptance. |
| 3.1 PRD conflicts | [x] | Scope, tools, metrics, knowledge kinds and authority rules addressed. |
| 3.2 Architecture conflicts | [x] | Identity, evidence, bounded execution, shared validation and diagrams addressed. |
| 3.3 UI/UX | [N/A] | No human UI artifact; agent tool journey updated instead. |
| 3.4 Other artifacts | [x] | Brief authority, tracker, deferred work, evaluation and later operational docs identified. |
| 4.1 Direct adjustment | [x] | Viable with scope review, insufficient alone. |
| 4.2 Rollback | [x] | Evaluated and rejected; no demonstrated benefit. |
| 4.3 MVP review | [x] | Required; proof separated from production release. |
| 4.4 Chosen approach | [x] | Hybrid of scope review and direct forward adjustment. |
| 5.1 Issue summary | [x] | §1. |
| 5.2 Impacts | [x] | §2 and companion. |
| 5.3 Recommendation | [x] | §3. |
| 5.4 Scope/action plan | [x] | §3 sequence and named dependency gates. |
| 5.5 Handoff | [x] | §5 roles, outputs, acceptance. |
| 6.1 Completion audit | [x] | Assessment complete; implementation approval remains pending. |
| 6.2 Accuracy | [x] | Checked against live planning sources; no implementation correctness claim. |
| 6.3 Explicit approval | [x] | Approved by the user on 2026-10-02. |
| 6.4 Confirm handoff | [x] | Major-scope handoff confirmed: Product Manager/Architect for planning reconciliation, then `bmad-ticket` for backlog application. |

Open gates carried forward, not silently resolved:

- **B1:** reference hardware/corpus still needed for performance acceptance.
- **B2 / proposed B6:** task set, success rubric, improvement threshold, repeated-trial policy and held-out access controls must be frozen in 10.1. The 90% retrieval target remains provisional until explicitly reconciled.
- **B3:** existing corpus/license/distribution approvals remain applicable; deferred model licensing applies when vectors return.
- **B4:** governance roles still need named owners. A label alone is not practice approval.
- **B5:** proposal retains Java 21; older planning still says the final pin needs owner approval. Do not infer formal closure from scaffolding or the stack table.
- **2.5:** reconcile story/tracker discrepancy and complete review before applying the scope change to active delivery.
- **2.8:** review exact schemas, wire formats, finite batch/traversal budgets and conditional fingerprint fields before broader implementation.

## 7. Workflow record

- User authorized Correct Course with the latest proposal, then authorized the Luciad MCP initiative and combined review.
- Created `initiative-luciad-mcp` and set the personal `core.active_initiative` setting. Existing artifacts were not moved.
- Customization resolved with no prepend, append or persistent-fact overrides.
- Loaded the existing PRD and Architecture Spine, epic/story evidence, tracker and deferred register. Used the original and product briefs for authority/scope conflicts.
- Produced this document, detailed edit companion and evidence snapshot. Authoritative plans and application source have not been patched by this Correct Course run.
- Current workflow position: Step 6, approved and routed for major-scope planning reconciliation. The proposal is finalized; the underlying planning artifacts remain unchanged pending coordinated application by the named owners.
- User approval: Approved on 2026-10-02 for the complete proposal, including the narrowed first-proof scope, six-tool contract, explicit deferrals, contract/evaluation gates and major-scope handoff.
- Handoff: Product owner/PM and Architect reconcile the planning set; `bmad-ticket` applies backlog changes; Developer proceeds only after the approved contract gates and story sequencing are in place.
