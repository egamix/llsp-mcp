---
title: "Consolidated Proposal: Evidence-Backed Luciad Knowledge Layer"
date: 2026-09-22
status: proposed
revision: 1
scope_classification: major
decision_owner: product owner
supersedes_if_approved:
  - sprint-change-proposal-2026-09-07-evidence-backed-sdk-engineering.md
---

# Consolidated Proposal: Evidence-Backed Luciad Knowledge Layer

## 1. Decision

Continue the project, but narrow the first product proof and freeze its contracts before expanding the corpus or retrieval stack.

This proposal consolidates and reevaluates:

- the applied guide, training, technique-tag, deployment, and structure-aware example proposals;
- the LLMQuant MCP-pattern research;
- the September 7 evidence-backed SDK engineering proposal; and
- engineering practices observed in TurboVec, independently of its vector technology.

Preserve completed story history and finish the current Story 2.5 review before applying this change.

## 2. Product contract

Build a Luciad-first, offline knowledge layer that gives coding agents:

- version-scoped SDK facts;
- exact symbol and overload resolution;
- typed relationships;
- cited examples and explanations;
- approved engineering practices and task recipes; and
- batch validation of proposed Luciad symbols.

The service may prove what the active indexed corpus contains. It must not claim that generated code compiles, runs, renders correctly, or satisfies non-Luciad dependencies.

Preserve Java 21, Gradle, SQLite, Lucene, JSoup, JavaParser, the domain-port architecture, two-pass ingestion, the typed graph, immutable builds, and offline serving.

## 3. Knowledge and identity contracts

Adopt four explicitly separated knowledge kinds:

1. `AUTHORITATIVE_FACT`
2. `EXPERT_APPROVED_PRACTICE`
3. `RECIPE`
4. `CANDIDATE_LEARNING`

Candidate learning is quarantined and never served from an active build.

Introduce one domain-owned, versioned symbol coordinate covering SDK, version, language, namespace, owner, member, parameters, and element kind.

Stable record IDs must remain independent of SQLite row numbers, Lucene document numbers, filesystem paths, output locations, and future retrieval-engine internals.

Curated Markdown or YAML may be used for authoring practices and recipes, but it must be validated and compiled into the same SQLite/Lucene/graph representation. It is not a second runtime database.

## 4. Six-tool agent interface

Replace the planned specialized tool collection with six composable read-only tools:

- `search_knowledge`
- `resolve_symbol`
- `get_record`
- `get_relationships`
- `check_symbols`
- `index_status`

Fold the useful LLMQuant patterns into these tools instead of creating more tools:

- `search_knowledge` with only filters acts as deterministic browse.
- `get_record` supports a bounded list of IDs and optional section selection.
- Search hits advertise stable IDs and available follow-up sections.
- Tool descriptions explain the expected retrieve -> resolve -> generate -> check workflow.

All tools use one result envelope containing stable status/error codes, active build and SDK version, retrieval route, proof state, coverage, citations, empty-result reason, truncation, and continuation information.

Cross-version migration queries remain a later capability. The identity model must support them, but no dedicated migration tool is needed for the first proof.

## 5. Contract and verification freeze

After Stories 2.5-2.7, add Story 2.8: **Contract and Verification Freeze**.

It must freeze and test:

- symbol serialization;
- SQLite schema;
- record and relationship identity recipes;
- citation and provenance structures;
- knowledge-kind and governance rules;
- build recipe and runtime compatibility fingerprints;
- ingestion-report schema;
- MCP request, result, error, pagination, and traversal contracts; and
- build-validation and activation rules.

Create one shared `BuildValidator` used by CLI validation, activation, MCP startup, reload, restore, and conformance tests.

Unknown schemas, corrupt data, incompatible fingerprints, ambiguous symbols, and unresolved dependencies must fail closed.

## 6. Engineering standards

Adopt the following patterns from TurboVec's engineering process, independently of its implementation technology:

- **Selective mutation testing:** critical tests must detect deliberate changes to hashing, identity, version scoping, ambiguity handling, relationship resolution, and activation policy.
- **Contract-impact gate:** changes to schemas, identities, CLI/MCP contracts, or persisted formats require a version/changelog update or an explicit reviewed exemption.
- **Cross-platform known-answer tests:** Windows and Linux must produce equivalent symbols, IDs, logical catalog contents, manifests, and reports.
- **Single validation path:** alternate load or startup routes may not bypass validation.
- **Transition testing:** test interruption at every pipeline stage, torn writes, competing writers, reload during requests, and limits immediately below and above boundaries.
- **Reviewable changes:** separate contract, parser, storage, resolver, wiring, and documentation changes where practical.
- **Two benchmark tiers:** use fast synthetic screening for development and a controlled decision suite for architectural claims.

These practices directly address the existing project action item concerning vacuously passing tests.

## 7. Revised delivery sequence

1. Complete and review Story 2.5 without rewriting its history.
2. Implement explicit unresolved evidence and bounded traversal in Stories 2.6-2.7.
3. Define the evaluation protocol and reserve a held-out Luciad task set.
4. Complete Story 2.8 and the engineering gates above.
5. Implement exact resolution, BM25 retrieval, the six MCP tools, and minimum immutable validate/activate behavior.
6. Add only the examples, guide sections, approved practices, and recipes required by 20-30 representative tasks.
7. Ship the initial agent retrieval policy.
8. Run controlled outcome evaluation against unaided agents.
9. Expand only where measured failures justify it.

The August structure-aware example proposal remains valuable: preserve whole examples once, create class/method projections, retain source ranges, and emit evidence-bearing exact/ambiguous/unresolved links. Implement it only after the identity and evidence contracts are frozen.

## 8. Deferred until evidence justifies them

Defer:

- vector retrieval and rank fusion;
- full guide-corpus ingestion;
- training-deck ingestion and Docling;
- broad technique-tag vocabularies;
- specialized MCP aliases;
- dedicated migration tooling;
- multi-SDK/plugin architecture;
- autonomous knowledge promotion or self-repair; and
- new databases, graph platforms, runtimes, or semantic-web infrastructure.

Keep the existing adapter boundaries so these can be evaluated later without changing public identities or tool contracts.

## 9. Acceptance gates

The first proof is successful only if it achieves:

- 100% exact lookup on canonical fixtures;
- zero unknown symbols reported as valid;
- zero ambiguous overloads silently promoted to exact;
- zero cross-version leakage;
- 100% citation completeness for primary content results;
- deterministic known-answer identities across supported platforms;
- fail-closed handling of incompatible or corrupt builds;
- no candidate or unapproved knowledge in an active build;
- mutation tests that detect critical identity and trust-rule regressions; and
- demonstrable improvement on representative Luciad tasks over the unaided baseline.

Vector search, additional corpora, or specialized tools are approved only when evaluation identifies a concrete gap and demonstrates that the proposed addition improves outcomes without weakening exactness, provenance, determinism, or operational simplicity.

## 10. Research inputs

- `research-llmquant-mcp-patterns-2026-08-31.md`
- `sprint-change-proposal-2026-09-07-evidence-backed-sdk-engineering.md`
- `sprint-change-proposal-2026-08-25.md`
- `sprint-change-proposal-2026-08-21.md`
- `sprint-change-proposal-2026-08-21-training.md`
- `sprint-change-proposal-2026-08-21-technique-tags.md`
- TurboVec repository and engineering workflow: <https://github.com/ryancodrai/turbovec>

## 11. Approval effect

If approved, this proposal supersedes the September 7 proposal as the correct-course input. Apply the resulting PRD, Architecture Spine, epics, deferred-work, and sprint-tracker changes atomically. Do not change source code as part of the planning update.
