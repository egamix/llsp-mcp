---
title: Coordinated planning application traceability
date: 2026-10-02
proposal_status: approved
planning_application: applied-prospectively
implementation_application: not-applied
active_delivery_gate: Story 2.5 review and status reconciliation
---

# Coordinated planning application — 2026-10-02

Applied only the approved prospective planning package. No source, test, build, deployment or agent-policy implementation file was changed. Existing uncommitted implementation changes were preserved. Planning approval/application is separate from technical contract approval, implemented proof, and production release acceptance.

The eleven source hashes matched the approval-time snapshot before editing: no intervening source drift required reconciliation. The original snapshot, historical proposals and detailed story records remain unchanged. Its `approval: pending` is an immutable assessment-time fact; approval is recorded by the approved proposal, not rewritten into that historical evidence.

**Remaining E02 boundary:** The live epic overview now records 13 Javadoc + one workflow + three example relationship producers. The existing Story 2.5 epic summary and detailed in-review story remain intact pending reviewer-controlled correction/reconciliation. Story file stays `in-review`; tracker stays `in-progress`. This planning application does not clear the review gate or schedule revised active delivery before it. No completed/current story is reopened, expanded or marked done.

## Changed artifacts

| Artifact | Application |
|---|---|
| [PRD](../../planning-artifacts/prds/prd-luciad-mcp-2026-08-20/prd.md) | Scope/authority/journey/glossary/FRs/NFR applicability/metrics/milestones/contracts/blockers reconciled. |
| [Architecture Spine](../../planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md) | Rules, six new decisions, diagrams, conventions, capability map and gates reconciled; schema sign-offs still open. |
| [Epics](../../planning-artifacts/epics.md) | Inventories/coverage/sequence/DoD and future acceptance updated; eight new stories and Epic 10; historical completed/current sections retained. |
| [Sprint tracker](../../implementation-artifacts/sprint-status.yaml) | 10 additive keys, 11 deferred-scope entries, scope comments and gates; 63 original statuses unchanged. |
| [Deferred work](../../implementation-artifacts/deferred-work.md) | Append-only ownership/deferral/gate mapping; all earlier findings preserved, none claimed fixed. |
| [Original implementation brief](../../../docs/luciad-knowledge-mcp-implementation-brief.md) | Approval-dated precedence notice; entire baseline including kickoff marked historical. |
| [Product brief](../../planning-artifacts/briefs/brief-luciad-mcp-2026-08-24/brief.md) | Approval-dated precedence notice; baseline headlines/counts/diagrams/workflow marked historical. |
| [Approved proposal](change-evidence-backed-first-proof.md) and [companion](proposed-edits.md) | Separate planning-application event/status; approval history and source-not-applied boundary retained. |

## Product edits

| Approved edit | Applied location / consequence |
|---|---|
| P01 | PRD §0/1/UJ-1/9; both brief authority notices, including historical kickoff/diagrams. External compile/test boundary explicit. |
| P02 | PRD glossary, FR-7/8/10/11/14/15/31/44/45; stable content IDs separate from symbol coordinates and four knowledge kinds. |
| P03 | FR-1–3/9–15/35/36; FR-39/40/42 deferred, structure-aware examples retained. |
| P04 | F5/F6, FR-16–25/38/41/43, API contract; six names, exact/BM25, finite work and honest counts/cursors. |
| P05 | FR-26–34; shared validation, reader-safe handles, profile assets, external guidance/diagnostics; training image deferred. |
| P06 | FR-44–50 allocated without collision; FR-37 still reserved. Exact story/decision coverage below and in epics. |
| P07 | PRD §5–9/NFR/API, epics sequence/DoD; proof versus release, B6 added, B1–B5 retained, provisional 90% and deferred vector gate explicit. |

## Requirement disposition and architecture ownership

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

| Requirement | New requirement with testable consequence | Coverage |
|---|---|---|
| FR-44 | Structured SDK identity and frozen portable contracts. Versioned known-answer symbol/record/edge/build recipes; stable IDs unchanged by output-root relocation; unknown SDK version cannot activate. | 2.8; AD-3/12/17/18/22/32 |
| FR-45 | Governed compiled knowledge. Four knowledge kinds; schema-validated deterministic authoring; candidates outside active builds; practices require approval; required recipe dependencies resolve compatibly; inapplicable recipes are not recommended. | 2.8, 3.3/3.4/3.6, 6.1; AD-14/31 |
| FR-46 | Shared build validation. All startup/load/activation/reload/restore routes use one validation policy and return equivalent rejection reasons for the same invalid artifacts. | 2.8, 5.6, 6.1–6.5; AD-5/6/23/33 |
| FR-47 | Honest bounded interface. Six read-only tools share explicit proof, route, coverage, empty reason and continuation semantics; no bounded query claims exhaustive results without proof. | 2.8, 4.6, 5.1–5.7, 7.2; AD-11/15/19/20/24/35 |
| FR-48 | Controlled outcome evaluation and governed improvement. Predeclared paired treatments, held-out protection and cost reporting; every accepted correction has human approval and a permanent regression before activation. | 10.1–10.4; AD-34 |
| FR-49 | Contract-change and verification gates. Schema/format changes have version/changelog or reviewed exemption; Windows/Linux known answers and selected mutations demonstrate critical tests can fail. | 2.8 and affected 6.x/10.x implementation; AD-32/34 |
| FR-50 | Shared application services. CLI diagnostics and MCP produce equivalent logical proof/coverage results for identical build/query/scope, excluding transport-specific wrapping. Neither compiles generated code; evaluation does so externally. | 2.8, 6.5, 7.1, 10.2; AD-1/36 |



NFR-1/3/5–8/10/12/13 numerical targets remain unchanged. NFR-2 applies ≤2 s to lexical warm search; NFR-4 measures all active-profile assets; NFR-9 remains ≤1.5 GiB for serving image plus required assets; NFR-11 has no active training exception; NFR-14 is capability-specific. B1 evidence is still required. SM-1/2/4/6/7/9 safety goals remain, SM-5 excludes all cross-version mixing for proof, SM-3 stays provisional, SM-8 becomes the later vector-benefit gate, and SM-10/11 capture outcomes/trust/portability. No metric is claimed passed.

## Architecture edits

| Approved edit | Decision(s) / disposition |
|---|---|
| A01 | AD-1 and module graph: ports inward, bootstrap wires adapters. |
| A02 | AD-3/18: record-ID joins, optional symbol links. |
| A03 | AD-6/8/13/21/26: primary lexical/profile assets; ONNX rules deferred. |
| A04 | AD-7/7a/12: bind version/build, exact proof, migration unsupported. |
| A05 | AD-9/10/28: active zero-egress/parse-only rules; training exception deferred. |
| A06 | AD-11/19: bounded internal work/output and exact/lower-bound/unknown counts. |
| A07 | AD-14: explicit practice approval/recipe dependencies; selected vendor facts permitted. |
| A08 | AD-15/24: typed content citations; verdicts never suppressed. |
| A09 | AD-17: portable recipe identity and versioned metadata. |
| A10 | AD-20: exact tier, deterministic score then stable-ID order; bound cursors. |
| A11 | AD-22: domain recipes, storage uniqueness. |
| A12 | AD-23: validate new handles, pin readers, coordinate writers/recovery. |
| A13 | AD-29: tags are deferred filters/grouping, never identity. |
| A14 | AD-30: preserve structure/source/evidence; arity not proof; hash purposes separated. |
| A15 | New AD-31: knowledge trust boundary and compiled authoring. |
| A16 | New AD-32: contract/verification freeze and change discipline. |
| A17 | New AD-33: one validation policy across every entry route. |
| A18 | New AD-34: controlled evaluation and governed correction. |
| A19 | New AD-35: six tools, bounded progressive disclosure. |
| A20 | New AD-36: shared services and external execution boundary. |

AD-2/27 keep shared internal-host hygiene; ONNX/training wording is made conditional. AD-4/5/16/25 retain two-pass/immutability/config/staging invariants under the new contracts. System graph, dataflow, ERD, conventions, capability map and Deferred section are coordinated. Adoption of planning text does not approve exact DDL/wire formats.

## Story and tracker trace

| Approved edit | Story disposition |
|---|---|
| E01 | 1.1–1.4 and 2.1–2.4 sections/statuses unchanged; forward 2.8/6.x note only. Epic 1 remains in-progress. |
| E02 | Correct producer split in live overview; current 2.5 summary and detailed story left historical/intact until review gate clears; both observed statuses preserved. |
| E03 | 2.6 explicit resolution/provenance evidence and recipe-failure distinction. |
| E04 | 2.7 depth/cycle plus finite node/edge/result/time work and truthful truncation. |
| E05 | New 2.8 contract and verification freeze with reviewable subtasks/known answers/sign-offs. |
| E06 | 3.1/3.2 retain extraction/linking criteria; add 2.8, task selection, typed source evidence and bounded parent fetch. |
| E07 | 3.3 quality/approval metadata; broad vocabulary removed from first-proof dependency. |
| E08 | 3.4 Workflow recipes, stable knowledge dependencies/applicability. |
| E09 | 3.5 allowlisted local guide sections, honest coverage. |
| E10 | New 3.6 approved practice compiler, no second database. |
| E11 | 4.1 exact / 4.2 BM25 / new 4.6 bounded cited assembly; 4.3–4.5 deferred, not done. |
| E12 | 5.1 search+resolve, 5.2 relationships, 5.3 record fetch, 5.4 check+status; 5.3b/c/d deferred. |
| E13 | 5.5 bounds/transport, 5.6 validated handles, 5.7 common envelope before handlers, 5.8 six-tool guidance. |
| E14 | 6.1–6.4 shared validation/profile lifecycle; new 6.5 early diagnostics/outcome conformance. |
| E15 | 7.1 external compile/test/repair guidance; 7.2 typed citations without dropping verdicts. |
| E16 | 8.1/8.2 lexical release assets and shared host, 8.3 builds on early 6.5 semantics; 8.4 and 9.1–9.3 deferred. |
| E17 | New Epic 10 and 10.1 protocol, 10.2 external harness, 10.3 outcome decision, 10.4 governed corrections. |

### Every tracker entry

Existing key spellings are retained even if the displayed story title changed. This table records execution status, not proof of new-scope completion.

| Key | Preserved/new status | Planning disposition |
|---|---|---|
| `epic-1` | in-progress | Preserved; no status transition. |
| `1-1-scaffold-domain-model-and-module-skeleton` | done | Completed history preserved; no retrospective rewrite. |
| `1-2-local-only-egress-free-cli-ingest-entrypoint` | done | Completed history preserved; no retrospective rewrite. |
| `1-3-stub-the-multi-stage-resumable-pipeline-with-a-configurable-failure-policy` | done | Completed history preserved; no retrospective rewrite. |
| `1-4-deterministic-ids-content-hashes-and-a-machine-readable-ingestion-report` | done | Completed history preserved; no retrospective rewrite. |
| `epic-1-retrospective` | done | Completed history preserved; no retrospective rewrite. |
| `epic-2` | in-progress | Preserved; no status transition. |
| `2-1-discover-and-classify-javadoc-pages` | done | Completed history preserved; no retrospective rewrite. |
| `2-2-parse-modern-and-legacy-javadoc-layouts-into-equivalent-fields` | done | Completed history preserved; no retrospective rewrite. |
| `2-3-build-canonical-symbol-records-with-overload-distinction` | done | Completed history preserved; no retrospective rewrite. |
| `2-4-persist-the-versioned-canonical-catalog` | done | Completed history preserved; no retrospective rewrite. |
| `2-5-construct-the-typed-relationship-graph-in-two-passes` | in-progress | Preserved in-progress; detailed story in-review; reviewer gate open. |
| `2-6-keep-unresolved-references-explicit-scope-traversal-to-the-documented-sdk` | backlog | Preserved; no status transition. |
| `2-7-bounded-cycle-safe-relationship-traversal` | backlog | Preserved; no status transition. |
| `epic-2-retrospective` | optional | Preserved; no status transition. |
| `epic-3` | backlog | Preserved; no status transition. |
| `3-1-parse-java-examples-via-ast` | backlog | Preserved; no status transition. |
| `3-2-link-examples-to-catalog-symbols` | backlog | Preserved; no status transition. |
| `3-3-apply-governance-supplied-quality-labels-and-technique-tags` | backlog | Future scope amended in epics; stable key retained. |
| `3-4-ingest-and-link-workflow-documents` | backlog | Future scope amended in epics; stable key retained. |
| `3-5-parse-and-link-guide-tutorial-articles` | backlog | Future scope amended in epics; stable key retained. |
| `epic-3-retrospective` | optional | Preserved; no status transition. |
| `epic-4` | backlog | Preserved; no status transition. |
| `4-1-detect-and-prioritize-exact-symbol-queries` | backlog | Preserved; no status transition. |
| `4-2-index-canonical-and-lexical-fields-for-bm25` | backlog | Preserved; no status transition. |
| `4-3-local-onnx-vector-search-with-explicit-failure-semantics` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `4-4-fuse-lexical-and-vector-results-under-exact-priority` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `4-5-enforce-a-consistent-embedding-fingerprint` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `epic-4-retrospective` | optional | Preserved; no status transition. |
| `epic-5` | backlog | Preserved; no status transition. |
| `5-1-retrieval-and-symbol-tools` | backlog | Future scope amended in epics; stable key retained. |
| `5-2-relationship-tools` | backlog | Future scope amended in epics; stable key retained. |
| `5-3-example-and-workflow-tools` | backlog | Future scope amended in epics; stable key retained. |
| `5-3b-guide-tools` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `5-3c-training-tools` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `5-3d-concept-technique-search-tool` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `5-4-validation-and-status-tools` | backlog | Future scope amended in epics; stable key retained. |
| `5-5-streamable-http-transport` | backlog | Future scope amended in epics; stable key retained. |
| `5-6-serve-from-a-fixed-active-build-pointer` | backlog | Future scope amended in epics; stable key retained. |
| `5-7-structured-error-ambiguity-and-truncation-contract` | backlog | Future scope amended in epics; stable key retained. |
| `5-8-draft-the-agent-retrieval-policy-skeleton` | backlog | Future scope amended in epics; stable key retained. |
| `epic-5-retrospective` | optional | Preserved; no status transition. |
| `epic-6` | backlog | Preserved; no status transition. |
| `6-1-immutable-builds-with-pre-activation-validation-and-atomic-activation` | backlog | Future scope amended in epics; stable key retained. |
| `6-2-reject-incompatible-builds` | backlog | Future scope amended in epics; stable key retained. |
| `6-3-rollback-via-pointer-swap-and-build-retention` | backlog | Future scope amended in epics; stable key retained. |
| `6-4-full-set-backups` | backlog | Future scope amended in epics; stable key retained. |
| `epic-6-retrospective` | optional | Preserved; no status transition. |
| `epic-7` | backlog | Preserved; no status transition. |
| `7-1-ship-the-agent-skill-md` | backlog | Future scope amended in epics; stable key retained. |
| `7-2-guarantee-complete-citations-on-every-primary-result` | backlog | Future scope amended in epics; stable key retained. |
| `epic-7-retrospective` | optional | Preserved; no status transition. |
| `epic-8` | backlog | Preserved; no status transition. |
| `8-1-single-serving-image-and-compose-layout` | backlog | Future scope amended in epics; stable key retained. |
| `8-2-air-gap-transfer-bundle` | backlog | Future scope amended in epics; stable key retained. |
| `8-3-headless-cli-with-documented-exit-codes` | backlog | Future scope amended in epics; stable key retained. |
| `8-4-separate-ingest-time-only-image-for-training-material-ingestion` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `epic-8-retrospective` | optional | Preserved; no status transition. |
| `epic-9` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `9-1-ingest-training-decks-via-docling` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `9-2-link-training-references-to-the-catalog` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `9-3-apply-governance-supplied-technique-tags-to-training-records` | backlog | Excluded by deferred_scope; original backlog status retained. |
| `epic-9-retrospective` | optional | Optional retained; excluded with deferred Epic 9. |
| `2-8-contract-and-verification-freeze` | backlog (new) | Additive planning only; not ready/done. |
| `3-6-compile-approved-engineering-practices` | backlog (new) | Additive planning only; not ready/done. |
| `4-6-assemble-bounded-cited-lexical-results` | backlog (new) | Additive planning only; not ready/done. |
| `6-5-shared-diagnostics-and-service-conformance` | backlog (new) | Additive planning only; not ready/done. |
| `epic-10` | backlog (new) | Additive planning only; not ready/done. |
| `10-1-freeze-evaluation-protocol-and-task-set` | backlog (new) | Additive planning only; not ready/done. |
| `10-2-build-controlled-external-evaluation-harness` | backlog (new) | Additive planning only; not ready/done. |
| `10-3-evaluate-outcomes-and-decide-expansion` | backlog (new) | Additive planning only; not ready/done. |
| `10-4-govern-corrections-and-retain-regressions` | backlog (new) | Additive planning only; not ready/done. |
| `epic-10-retrospective` | optional (new) | Additive planning only; not ready/done. |

## Deferred findings and scope

T01 adds supported backlog keys and an additive scheduling exclusion map; no unsupported `deferred` execution value and no key rename/ticket-tree migration. T02 appends ownership while preserving every earlier finding. T03 reconciles all requirements/inventory/coverage/DoD. T04 marks brief baselines historical; operational/CLI/agent-policy/packaging/CI changes remain future implementation stories. Dated readiness reports remain historical; readiness must be rerun against this planning set before release claims.

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

| Deferred key | Reason |
|---|---|
| `4-3-local-onnx-vector-search-with-explicit-failure-semantics` | Vector benefit requires evaluation after lexical baseline. |
| `4-4-fuse-lexical-and-vector-results-under-exact-priority` | Vector fusion deferred; required bounded lexical enrichment transfers to 4.6, not completion of this story. |
| `4-5-enforce-a-consistent-embedding-fingerprint` | Vector-specific fingerprints deferred; general identity/compatibility remains 2.8/6.2. |
| `5-3b-guide-tools` | Guide aliases deferred; selected-guide capability belongs to 5.1/5.3. |
| `5-3c-training-tools` | Training corpus and specialized tools deferred; no placeholder tools. |
| `5-3d-concept-technique-search-tool` | Broad tags/concept tool deferred; symbol-less common search remains 4.2/4.6/5.1/5.3. |
| `8-4-separate-ingest-time-only-image-for-training-material-ingestion` | Training image deferred; lexical serving needs neither model nor Docling. |
| `9-1-ingest-training-decks-via-docling` | Training ingestion deferred pending benefit, provenance/governance/version decisions. |
| `9-2-link-training-references-to-the-catalog` | Training links deferred with corpus. |
| `9-3-apply-governance-supplied-technique-tags-to-training-records` | Broad training tags deferred with corpus/vocabulary. |
| `epic-9` | Entire training epic excluded until its evaluation/provenance/governance/version gates clear. |

Additional gated scope: full guide corpus, broad vocabulary, specialized aliases/migration tools, multi-SDK/plugins, external-agent installer, conversation mining, autonomous promotion/self-repair and extra databases/graph/runtimes/semantic-web platforms. Reactivation requires a diagnosed gap, controlled benefit, preserved safety and owner approval; training additionally needs provenance/governance/version decisions. Selected guides/practices/recipes remain in scope. Original scopes/dependencies are preserved in deferred story bodies and historical proposals.

## Verification and open gates

- Compared all 11 snapshot hashes before application; all matched.
- Checked unique PRD FR-1–FR-50 headings (FR-37 reserved), architecture AD-1–AD-36 plus AD-7a, and story/tracker ID coverage.
- Preserved all 63 original execution/retrospective statuses, all original tracker keys/action items, and all nine completed/current epic story sections verbatim. Added eight story keys, Epic 10 and its optional retrospective only.
- Kept every detailed story file and protected application/source/build/test file byte-identical to the pre-edit workspace, including existing uncommitted changes. No implementation tests were run because source was unchanged.
- Checked local links, balanced fenced blocks, tracker indentation/duplicate keys/scalars and allowed execution-status vocabulary. These are document/integrity checks, not schema approval, implementation conformance or measured outcome evidence.
- E02 remains reviewer-gated; B1–B6, 2.8 exact contract approvals, outcome evaluation, lifecycle completion and production-release sign-offs remain open. Scheduling must honor these gates and deferred_scope.
