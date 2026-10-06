---
title: "Consolidated Correct-Course Proposal: Luciad Knowledge MCP"
date: 2026-10-02
status: proposed
revision: 1
scope_classification: major
decision_owner: product owner
supersedes_if_approved:
  - sprint-change-proposal-2026-09-22-consolidated-evidence-engineering.md
  - sprint-change-proposal-2026-09-07-evidence-backed-sdk-engineering.md
---

# Consolidated Correct-Course Proposal: Luciad Knowledge MCP

## 1. Purpose and disposition

This document brings together the seven sprint-change proposals in the planning-artifacts directory as of 2026-10-02. It combines the already-applied scope changes with the proposed September evidence-backed redesign, resolves conflicts in favor of the latest proposed product direction, and records which earlier capabilities remain deferred.

The August proposals for guide articles, training materials, technique tags, shared internal-host deployment, and structure-aware Java examples describe prior scope decisions. The September 7 proposal introduced a narrower, evidence-backed first product proof. The September 22 proposal consolidated that direction and superseded September 7. This document is the current single review point and, if approved, supersedes both September proposals. It does not itself change the PRD, Architecture Spine, epics, deferred-work register, or sprint tracker.

Preserve completed story history and finish the current Story 2.5 review before applying planning changes. Do not change source code as part of the planning update.

## 2. Product decision

Build a Luciad-first knowledge service for coding agents. The first product proof should establish exact, version-scoped SDK identity and useful evidence retrieval before adding broad corpora or more complex retrieval methods.

The service can report what the active indexed corpus supports. It cannot establish that generated code compiles, runs, renders correctly, or works with dependencies outside the indexed Luciad knowledge. Those checks remain in the caller's build and runtime environment.

Keep the current core technology direction: Java 21, Gradle, SQLite, Lucene, JSoup, JavaParser, domain-owned ports, staged ingestion, typed relationships, immutable builds, and offline serving. Do not add a graph database, semantic-web stack, new runtime, or new security layer as part of this proposal.

## 3. Decisions to carry forward

### Deployment and operating boundary

- Serve a small trusted internal team (approximately five developers or fewer) from a shared internal host; do not expose the service to the public internet.
- Retain the zero-external-egress/offline serving guarantee. The inbound internal-network bind does not authorize outbound network access.
- Keep standard container-hygiene defaults. The v1 scope does not add authentication, TLS, or tenant isolation; authenticated cross-team or cross-organization multi-tenancy remains deferred.

### Identity, evidence, and knowledge

- Introduce one domain-owned, versioned symbol coordinate covering SDK, SDK version, language, namespace, owner/type, member, parameters, and element kind.
- Keep stable record IDs independent of database row IDs, search-engine document IDs, filesystem paths, output locations, and retrieval-engine implementation.
- Represent proof as explicit facts and typed citations; do not collapse identity, applicability, provenance, coverage, and ambiguity into one confidence score.
- Use four distinct knowledge kinds: `AUTHORITATIVE_FACT`, `EXPERT_APPROVED_PRACTICE`, `RECIPE`, and `CANDIDATE_LEARNING`. Candidate learning is quarantined and never served from an active build.
- Recipes reference stable knowledge IDs rather than copying versioned SDK facts. Applicability and dependency checks gate their use.
- Curated Markdown or YAML may be an authoring input, but must be schema-validated and deterministically compiled into the existing SQLite/Lucene/graph representation. It is not a second serving database.

### Ingestion and corpus scope

- Preserve the structure-aware Java example design: retain whole examples, create class/method projections, preserve source ranges, and emit evidence-bearing exact, ambiguous, or unresolved links. Implement it after identity and evidence contracts are frozen.
- Ingest only the examples, guide sections, approved practices, and recipes needed to support the initial representative task set.
- Defer full guide-corpus ingestion, training-deck ingestion through Docling, and a broad technique-tag vocabulary until evaluation demonstrates a concrete need. The earlier proposals' dedicated `search_trainings`/`get_training` and `search_concepts` tools are therefore not part of the first proof.
- Keep symbol-less records servable: citations include a canonical symbol only when one is resolved; never invent one. This corrects the earlier unconditional-canonical-symbol citation rule.

## 4. Agent interface and result contract

Replace the planned specialized tool collection with six composable, read-only MCP tools:

| Tool | Purpose |
|---|---|
| `search_knowledge` | Search facts, approved practices, recipes, APIs, examples, workflows, and source records with version, kind, applicability, and provenance filters. |
| `resolve_symbol` | Resolve a name or signature to an exact version-scoped symbol, or return explicit ambiguity/unresolved state. |
| `get_record` | Fetch a complete record by stable ID, with bounded batches and optional section selection. |
| `get_relationships` | Traverse bounded, typed relationships, including SDK and curated-knowledge dependencies. |
| `check_symbols` | Batch-check proposed Luciad symbols against the active catalog; it does not compile code. |
| `index_status` | Report active SDK/build/schema/fingerprint versions, corpus coverage, errors/skips, and retrieval modes. |

Use one result envelope with stable status/error codes and, where applicable, stable IDs, record and knowledge kinds, active build and SDK version, provenance and typed citations, proof or ambiguity state, retrieval route, coverage, empty-result reason, deterministic truncation, and continuation information. Ranked retrieval discovers candidates; it does not prove identity or applicability. Specialized tools, including migration tools, require evidence of a concrete need.

## 5. Contract and engineering freeze

After Stories 2.5–2.7, add Story 2.8, **Contract and Verification Freeze**, before broad Epic 3/4 work. Freeze and test:

- versioned symbol serialization and known-answer cases;
- SQLite schema, stable record and relationship identity recipes, citation/provenance structures, and ingestion-report schema;
- knowledge kinds, governance, applicability, and dependency rules;
- build-recipe and runtime-compatibility fingerprints;
- MCP request, result, error, batch, pagination, traversal, and trace bounds; and
- build validation, activation, reload, restore, and failure behavior.

Use one shared `BuildValidator` for CLI validation, activation, MCP startup, reload, restore, and conformance checks. Unknown schemas, corrupt data, incompatible fingerprints, ambiguous identities, and unresolved required dependencies fail closed. Unknown or stub SDK versions cannot activate. Absolute host paths and output location do not affect build identity.

Adopt the following engineering gates:

- Selective mutation tests must detect regressions in hashing, identity, version scoping, ambiguity, relationship resolution, and activation policy.
- Contract-impacting changes require a schema/changelog version update or an explicit reviewed exemption.
- Windows and Linux known-answer tests must agree on symbols, IDs, logical catalog content, manifests, and reports.
- No alternate startup or load path may bypass the shared validation path.
- Test pipeline interruption, torn writes, competing writers, reload during requests, and values just below and above limits.
- Separate contract, parser, storage, resolver, wiring, and documentation changes where practical.
- Use fast synthetic screening during development and a controlled task suite for architectural claims.

## 6. Delivery and evaluation

1. Complete and review Story 2.5 without rewriting its history.
2. Implement explicit unresolved evidence and bounded traversal in Stories 2.6–2.7.
3. Define the evaluation protocol and reserve a held-out Luciad task set.
4. Complete Story 2.8 and the engineering gates above.
5. Implement exact resolution, BM25 lexical retrieval, the six MCP tools, and minimum immutable validate/activate behavior.
6. Add only the knowledge records needed for 20–30 representative Luciad tasks.
7. Ship concise agent guidance for retrieve → resolve → generate → batch-check → compile/test → repair.
8. Compare the same agent/model, prompt, environment, and limits on unaided and progressively supported conditions; report build/indexing cost separately.
9. Expand only when measured failures identify a specific gap and the proposed addition improves outcomes without weakening exactness, provenance, determinism, or operational simplicity.

Classify failures as `KNOWLEDGE_GAP`, `RECIPE_DEFECT`, `RETRIEVAL_OR_ROUTING_DEFECT`, `VERSION_OR_CORPUS_DEFECT`, `GENUINE_AMBIGUITY`, or `NON_LUCIAD_FAILURE`. Treat proposed corrections as `CANDIDATE_LEARNING` until a minimal change passes structural validation, symbol/citation checks, targeted replay, affected regression tests, independent review where practical, and human/domain-owner approval. Add approved corrections to the permanent regression suite before activating a new immutable build. Do not build autonomous self-repair or promotion as part of this proposal.

Acceptance gates for the first proof:

- 100% exact lookup on canonical fixtures;
- zero unknown symbols reported as valid;
- zero ambiguous overloads silently promoted to exact;
- zero cross-version leakage;
- 100% citation completeness for primary content results;
- deterministic known-answer identities across supported platforms;
- fail-closed handling of incompatible or corrupt builds;
- zero candidate or unapproved knowledge in an active build; and
- demonstrable improvement on representative Luciad tasks over the unaided baseline.

## 7. Explicitly deferred

Defer until evidence justifies a separate decision:

- vector retrieval and rank fusion;
- full guide-corpus and training-deck/Docling ingestion;
- broad technique-tag vocabularies and the dedicated `search_concepts` tool;
- dedicated training search/get tools;
- specialized MCP aliases and cross-version migration tooling;
- multi-SDK/plugin architecture;
- external-agent configuration installer;
- conversation mining, autonomous knowledge promotion, and self-repair; and
- additional databases, graph platforms, runtimes, or semantic-web infrastructure.

Keep adapter boundaries that permit later evaluation without changing public identities or contracts. Do not interpret deferral as a removal of already-authorized small, task-relevant guide sections or curated practices/recipes.

## 8. Planning-artifact changes required on approval

Apply one coordinated update to the PRD, Architecture Spine, epics, deferred-work register, and sprint tracker. Add Story 2.8 and evaluation work (including an evaluation epic if still needed after reconciliation); reshape Epic 4 around exact/lexical core plus gated semantic enhancement; rewrite Epic 5 around the six-tool contract; retain the shared internal-host deployment decision; and move broad corpora and extra tools to deferred status. Preserve completed/current story statuses and do not mark removed MVP work as done. Reconcile IDs and tool counts against authoritative artifacts rather than copying stale proposal references. Finish with a traceability report of every changed requirement, decision, story, deferred item, and tracker entry.

## 9. Source proposals consolidated

- `sprint-change-proposal-2026-08-21.md` — guide articles as a corpus record type.
- `sprint-change-proposal-2026-08-21-training.md` — training material ingestion via Docling.
- `sprint-change-proposal-2026-08-21-technique-tags.md` — technique tags and the symbol-less citation correction.
- `sprint-change-proposal-2026-08-24.md` — shared internal-host deployment for a small trusted team.
- `sprint-change-proposal-2026-08-25.md` — structure-aware Java example ingestion.
- `sprint-change-proposal-2026-09-07-evidence-backed-sdk-engineering.md` — initial evidence-backed correct-course proposal.
- `sprint-change-proposal-2026-09-22-consolidated-evidence-engineering.md` — prior consolidation and immediate predecessor.

Research background in `research-llmquant-mcp-patterns-2026-08-31.md` informed interface patterns but is not a separate proposal or authorization to add its technologies.

## 10. Approval checklist

- [ ] Approve the narrowed Luciad-first, evidence-backed first proof and external compile/test truth boundary.
- [ ] Approve the symbol, proof, knowledge-kind, and compiled-authoring contracts.
- [ ] Approve the six-tool read-only interface and shared result envelope.
- [ ] Approve Story 2.8, the engineering gates, delivery sequence, and evaluation criteria.
- [ ] Approve carrying forward shared internal-host deployment and offline serving.
- [ ] Approve task-relevant guide/practice/recipe content while deferring broad guide, training, and technique-tag scope.
- [ ] Approve the explicit deferrals in §7.
- [ ] Authorize BMAD to reconcile and apply the planning-artifact changes atomically.
