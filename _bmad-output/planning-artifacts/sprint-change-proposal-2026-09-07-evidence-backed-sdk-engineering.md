---
title: "Correct Course: Luciad-first evidence-backed SDK knowledge layer"
date: 2026-09-07
status: proposed
revision: 4
scope_classification: major
recommended_workflow: bmad-correct-course
decision_owner: product owner
---

# Correct Course — Luciad-First SDK Knowledge Layer

## 1. Purpose and decision

Use this document as the change input for BMAD Correct Course. It contains the required planning changes only; the external-product research is background, not implementation scope.

Continue the project and describe it as:

> A compiled, evidence-backed SDK knowledge layer that gives coding agents version-scoped Luciad facts, approved engineering practices, applicable procedures, citations, and symbol verification.

The project remains Luciad-first. Support for other SDKs or languages is a later knowledge-pack extension after the Luciad outcome is proven.

This is a **major planning change** because it changes the product contract, planned MCP interface, delivery order, and success criteria. It is not a technology rewrite.

## 2. Guardrails

BMAD must preserve these constraints:

- Preserve completed-story history and the current Story 2.5 implementation/review work.
- Finish Story 2.5 review before contract-freeze work begins.
- Keep Java 21, Gradle, SQLite, Lucene, JavaParser, JSoup, the typed graph, two-pass ingestion, immutable builds, and offline serving.
- Do not add zvec-grep, Zvec, Neo4j, RDF, OWL, SPARQL, Node.js, Tree-sitter, remote embeddings, or another runtime because of the research.
- Assume the existing local or trusted internal deployment. Do not add authentication, TLS, or reverse-proxy work.
- The service verifies indexed SDK evidence and symbols. Compilation and tests remain in the consuming agent's licensed Luciad project.
- Never promote an unresolved or ambiguous symbol using semantic similarity.
- Do not build a generic plugin framework or autonomous self-improvement system in the first product proof.

Authoritative planning inputs:

- `_bmad-output/planning-artifacts/prds/prd-luciad-mcp-2026-08-20/prd.md`
- `_bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md`
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/implementation-artifacts/deferred-work.md`
- `_bmad-output/implementation-artifacts/2-5-construct-the-typed-relationship-graph-in-two-passes.md`

## 3. Required product and domain changes

### 3.1 Truth boundary

The service may claim only what a version-scoped active build proves:

- exact symbol, member, constructor, and overload identity;
- explicit ambiguity, unresolved, external, and lifecycle state;
- documented or approved relationships and practices;
- typed provenance and citations; and
- validation of the caller-supplied Luciad symbol set.

It must not claim that generated code compiles, runs, renders correctly, or satisfies non-Luciad dependencies.

### 3.2 Knowledge kinds

Add four explicit kinds:

| Kind | Meaning | Active-build rule |
|---|---|---|
| `AUTHORITATIVE_FACT` | Source-derived SDK fact. | Requires authoritative versioned provenance. |
| `EXPERT_APPROVED_PRACTICE` | Reviewed engineering recommendation or convention. | Requires approving authority, applicability, and evidence where available; never present it as vendor documentation. |
| `RECIPE` | Procedure telling an agent how to investigate or perform a task. | References facts/practices by stable ID and declares entry, completion, stop, and escalation conditions. It is not factual evidence. |
| `CANDIDATE_LEARNING` | Unreviewed feedback, failure, observation, or proposed correction. | Never appears in an active serving build or ordinary agent result. |

Facts and practices are declarative knowledge. Recipes are procedural guidance. Keep these separate so a factual defect can be distinguished from a reasoning/procedure defect.

### 3.3 Compiled authoring model

Governed Markdown/YAML may be used as a human-readable source format for approved practices and recipes. The build must validate and compile it into the existing SQLite/Lucene/typed-graph serving representation.

Curated records must declare:

- stable ID and knowledge kind;
- SDK, language, and version applicability;
- provenance and review/approving authority;
- `appliesWhen` conditions;
- typed `dependsOn` relationships and derivable reverse consumers; and
- size/context budget where relevant.

SQLite/Lucene remains the runtime truth. Editable source files are build inputs, not a competing serving store.

## 4. Required architecture outcomes

The Architecture Spine must contain decisions equivalent to the following. Reconcile numbering with its existing decisions.

1. **Evidence boundary:** retrieval relevance is not correctness; exact proof, ambiguity, lifecycle, provenance, and external compile/test responsibilities remain distinct.
2. **SDK identity contract:** introduce one domain-owned, versioned structured symbol coordinate covering ecosystem, SDK, SDK version, language, namespace, owner/type, member, parameter types, and element kind.
3. **Proof and citation model:** use dimensional proof facts rather than one confidence score, and support typed source locators.
4. **Small read-only MCP surface:** expose the six tools in §5; keep build administration in the CLI.
5. **Exact-first routing:** known symbols, signatures, literals, and regular expressions use exact routes. Ranked retrieval may discover candidates but cannot prove identity or applicability.
6. **Honest results:** report retrieval route, coverage, active build/version, empty reason, and truncation. Ranked samples and bounded traversal must not claim exhaustiveness.
7. **Progressive disclosure:** return decisive facts and the smallest useful context first; retrieve full records and niche evidence on demand.
8. **Contract freeze:** version symbol serialization, SQLite schema, record IDs, content hashes, build recipe fingerprint, runtime compatibility fingerprint, and response contracts before broad ingestion/retrieval work.
9. **Shared engine:** CLI and MCP use the same application services and proof semantics.
10. **Immutable lifecycle:** build writes target a new build, activation is atomic, and concurrent writers/readers use bounded deterministic lifecycle rules.
11. **Knowledge trust boundary:** enforce the four knowledge kinds and reject candidates, unapproved practices, and recipes with unresolved/incompatible dependencies from active builds.
12. **Knowledge/procedure separation:** recipes reference stable knowledge IDs instead of copying versioned SDK facts; deterministic applicability gates specialized guidance.
13. **Governed improvement:** approved corrections require diagnosis, minimal change, deterministic validation, targeted replay, applicable regression tests, and human/domain-owner approval before a new immutable build.
14. **Evaluation-gated complexity:** vectors, new corpora, specialized tools, and cross-SDK generalization require measured benefit over the simpler baseline.

“Knowledge layer” describes the capability and domain model. It does not mandate Neo4j or semantic-web technology.

## 5. Default agent interface

Replace the planned 20-tool default MCP surface with six composable read-only tools:

| Tool | Responsibility |
|---|---|
| `search_knowledge` | Search facts, approved practices, recipes, APIs, examples, workflows, and source records with version, kind, applicability, and provenance filters. |
| `resolve_symbol` | Resolve a name/signature to an exact version-scoped symbol or explicit ambiguity/unresolved result. |
| `get_record` | Fetch a complete record by stable ID. |
| `get_relationships` | Perform bounded typed traversal, including SDK and curated knowledge dependencies. |
| `check_symbols` | Batch-validate the caller's proposed Luciad symbols. It never compiles code. |
| `index_status` | Report active SDK/build/schema/fingerprint versions, corpus coverage, errors/skips, and available retrieval modes. |

Every primary result must include, where applicable:

- stable ID, record type, and knowledge kind;
- SDK/version, provenance, governance state, and typed citation;
- exact proof facts or explicit ambiguity/unresolved state;
- `matchedBy` retrieval route;
- coverage scope and mode;
- active build/freshness information;
- explicit empty-result reason; and
- deterministic truncation/continuation information.

Public agent schemas must not accept credentials, machine paths, model/device settings, endpoints, activation controls, or mutable server configuration.

## 6. Contract freeze

Create **Story 2.8: Freeze SDK identity, storage, knowledge, build, and interface contracts** after Stories 2.6 and 2.7. Story 2.8 is required before broader Epic 3/4 work.

Minimum acceptance criteria:

- One domain-owned canonical symbol coordinate and versioned serialization with known-answer tests.
- SDK version is mandatory; unknown/stub versions cannot activate.
- One owned SQLite schema for records, relationships, unresolved evidence, citations, knowledge kinds, applicability, dependencies, governance, and build metadata.
- Stable record ID, content hash, build recipe fingerprint, and runtime compatibility fingerprint have separate defined purposes.
- Absolute host paths and output location do not influence build identity.
- Build-invalidating ingestion results block successful activation.
- Retrieval/ingestion depend on domain ports; bootstrap/CLI owns concrete adapter wiring.
- Source provenance is captured during ingestion where possible.
- Query, result, batch, payload, traversal, and trace bounds are centrally defined and tested.
- Active builds reject candidate learning, unapproved practice, and invalid recipe dependencies.
- If Markdown/YAML curated authoring is used, its schema and deterministic compilation are versioned and tested.
- No authentication, encryption, Neo4j/RDF, or autonomous self-improvement work is introduced.

## 7. Revised delivery sequence

1. Complete and review Story 2.5.
2. Complete Stories 2.6 and 2.7 using the approved proof terminology.
3. Complete Story 2.8 contract freeze.
4. Implement exact routing and BM25 lexical retrieval.
5. Implement the six MCP tools and shared result contract.
6. Add a small approved set of Luciad examples/workflows plus concise practices and recipes needed by representative tasks.
7. Implement the initial agent policy immediately.
8. Implement the minimum immutable validate/activate path.
9. Run the controlled outcome evaluation in §8.

This is the first product proof, not a production release.

Defer until evidence justifies them:

- vector retrieval and fusion;
- full guide ingestion;
- training-deck ingestion and its Docling/network exception;
- broad technique-tag vocabulary;
- specialized MCP aliases;
- multi-SDK/plugin framework;
- external-agent configuration installer;
- conversation mining, multi-agent knowledge editing, scheduled self-repair, and automatic promotion.

## 8. Evaluation and governed improvement

Add **Epic 10: Agent Outcome Evaluation** with these outcomes:

1. Curate 20–30 representative Luciad tasks and expected evidence within licensing rules.
2. Compare the same tasks, agent/model, prompt, environment, and limits across unaided, exact+lexical, relationship/example, and later vector treatments.
3. Record task result, initial/final compile result, invalid/wrong-version/wrong-overload symbols, citations, approved-practice adherence, tool calls, tokens, elapsed time, and repair iterations.
4. Use repeated paired trials where stochasticity matters; isolate gold material, smoke-test tool access, use blind judging where practical, and report indexing/build cost separately.
5. Classify failures as `KNOWLEDGE_GAP`, `RECIPE_DEFECT`, `RETRIEVAL_OR_ROUTING_DEFECT`, `VERSION_OR_CORPUS_DEFECT`, `GENUINE_AMBIGUITY`, or `NON_LUCIAD_FAILURE`.
6. Treat every correction as `CANDIDATE_LEARNING` until a minimal proposed change passes structural validation, symbol/citation checks, targeted replay, affected regressions, independent review where practical, and human approval.
7. Add every approved correction to the permanent regression suite before activating the new build.

Non-negotiable metrics:

- Exact canonical fixture lookup: 100%.
- Unknown symbol marked valid: 0.
- Ambiguous overload silently promoted to exact: 0.
- Cross-version leakage: 0.
- Primary-result citation completeness: 100%.
- Candidate learning or unapproved practice in an active build: 0.
- Recipe with unresolved/incompatible required knowledge: 0.

Do not build an autonomous improvement engine as part of Epic 10. That would be a separate major post-proof decision.

## 9. Required planning-artifact edits

| Artifact | Required edit |
|---|---|
| PRD | Adopt the compiled evidence-backed SDK knowledge-layer framing, truth boundary, four knowledge kinds, six tools, revised MVP scope, and outcome metrics. |
| Architecture Spine | Add the outcomes in §4, knowledge authoring/compilation flow, agent/admin separation, external compile/test loop, and deferred autonomous improvement. |
| Epic 2 | Add Story 2.8 without rewriting completed stories or absorbing it into Story 2.5. |
| Epic 3 | Prioritize benchmark examples/workflows and add the minimal governed practice-authoring path. |
| Epic 4 | Split required exact/lexical core from evaluation-gated semantic enhancement. |
| Epic 5 | Rewrite backlog stories around the six tools, shared result/trust contract, applicability routing, proof/error/coverage handling, and bounded schemas. |
| Epic 6 | Add shared diagnostic CLI/conformance tests and immutable lifecycle coordination where not already covered. |
| Epic 7 | Add concise composable recipe authoring and the retrieve → resolve → generate → batch-check → compile/test → repair policy. |
| Epic 9 | Move training ingestion out of the first proven MVP. |
| Epic 10 | Add the evaluation and governed correction loop in §8. |
| Deferred work | Record specialized tools, vectors pending gate, broad training/tags, installer, multi-SDK framework, and autonomous self-improvement. |
| Sprint tracker | Preserve completed/current statuses; add only approved new stories and mark removed MVP work deferred rather than done. |

Update the PRD, Architecture Spine, epics, deferred register, and sprint tracker atomically after product-owner approval. Do not edit source code during Correct Course.

## 10. Approval checklist

- [ ] Approve the compiled, evidence-backed SDK knowledge-layer framing.
- [ ] Approve Luciad-first delivery and the external compile/test truth boundary.
- [ ] Approve the four knowledge kinds and governed authoring/compilation model.
- [ ] Approve the six-tool read-only MCP interface.
- [ ] Approve Story 2.8 and the revised delivery sequence.
- [ ] Approve a small benchmark-driven set of practices and recipes in the first product proof.
- [ ] Approve Epic 10 and the governed correction/regression loop.
- [ ] Approve vectors, broad corpora, specialized tools, multi-SDK work, and autonomous improvement as deferred/gated.
- [ ] Approve the explicit decision not to add zvec-grep, Neo4j, semantic-web infrastructure, or new security work.
- [ ] Authorize BMAD to apply the approved changes atomically to the authoritative planning artifacts.

## 11. BMAD handoff

Validate this proposal against the authoritative inputs in §2. Preserve completed history and current Story 2.5 work. Present genuine contradictions to the product owner; otherwise prepare one consistent planning-artifact patch implementing §§3–9. Do not change source code or add technologies mentioned only in the external research. Finish with a traceability report listing every changed requirement, architecture decision, epic/story, deferred item, and sprint-status entry.
