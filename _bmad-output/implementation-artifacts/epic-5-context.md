# Epic 5 Context: Six-Tool MCP Surface

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Expose authoritative, cited knowledge and honest symbol verdicts through exactly six read-only MCP tools. A first Javadoc connection must serve durable documentation through shared retrieval and validated immutable builds, providing useful agent context without guessing APIs. It is an initial serving milestone within the broader supported-content epic, not completion of examples, guides, practices or production release.

## Stories

- Story 5.1: Knowledge search and symbol resolution
- Story 5.2: Bounded typed relationship tool
- Story 5.3: Bounded record fetch across supported content
- Story 5.3b: Guide tools
- Story 5.3c: Training tools
- Story 5.3d: Concept/technique search tool
- Story 5.4: Symbol validation and index status
- Story 5.5: Streamable HTTP transport
- Story 5.6: Serve validated immutable build handles
- Story 5.7: Shared six-tool result and error envelope
- Story 5.8: Draft six-tool agent guidance

## Requirements & Constraints

Expose only `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`, `check_symbols` and `index_status`. Guide aliases, training tools and broad concept/technique search remain deferred, including Stories 5.3b/c/d. Selected guide and symbol-less approved content belong in common search/fetch; do not add placeholder tools. Lexical operation is healthy without a model. Active paths have zero external egress and never compile or execute code.

Search supports scoped filters, deterministic browse, stable IDs and available sections. Resolution and batch checking preserve exact/ambiguous/unknown outcomes and overload ambiguity. Fetch uses bounded ID batches and sections with per-ID not-found/oversize outcomes; complete example source can be reached from a child hit within the same limits. Relationship traversal preserves typed evidence, implementation/subclass capability, source scope and finite cycle-safe budgets without claiming exhaustive documentation.

Every primary content result needs source identity, actual SDK/build, record type, stable record ID and a truthful typed locator; symbols are optional for symbol-less/unresolved content and missing anchors remain absent. Verdict/status results must remain visible without content citations. Status reports schema/build/fingerprints, coverage, skips/errors, ingestion time and capabilities accurately.

All six use the shared versioned envelope: proof/ambiguity, matchedBy, governance/applicability, IDs/kinds, SDK/build, coverage and exact/lower-bound/unknown counts, empty reason, truncation and continuation. Default page is 10, hard cap 50, serialized response at most 1 MiB. Finite input/query/batch/candidate/traversal/trace/time budgets require contract freeze and constrain intermediate work as well as output. Oversized content uses bounded sections/continuation or an explicit size error.

Technical schema, identity, interface and limit approvals remain pending under 2.8; planning approval and implemented read-tools/2 policy/contracts do not approve downstream integration. Named governance owners and legal/corpus approval remain open. Performance targets require reference hardware/corpus before sign-off: exact p95 at most 250 ms, warm lexical search p95 at most 2 s, at least four concurrent requests with bounded queues, startup at most 30 s and serving RSS at most 8 GiB. First connection does not satisfy controlled proof evaluation or release approval.

## Technical Decisions

Use shared query/proof/validation services through domain ports, with embedded SQLite authoritative content and Lucene BM25 projections; CLI/bootstrap wires adapters. MCP transport is Streamable HTTP at `/mcp`, with optional stdio, on the trusted internal host. Agent schemas accept no credentials, host paths, endpoints, model/device settings or activation controls; operator configuration remains outside them. Logs redact source bodies by default.

Select one configured usable source deterministically within finite budgets, respecting explicit source/build filters; pin its immutable build for the entire request. Optional target SDK is guidance context and never an implicit restrictive source filter. Target 2023.1 with source 2023.5 receives useful normal guidance with actual-source citations. Mismatch alone cannot cause UNKNOWN_SDK, INAPPLICABLE, automatic UNVERIFIED warnings or negative API-existence claims. Only concrete cited target-relevant change evidence supports a specific notice; exact source proof does not certify tested target compatibility. Preserve explicit GENERAL applicability, governance, source-specific IDs and same-source edges; combined-source engines remain deferred.

Exact proof precedes lexical rank. Join and deduplicate by stable record ID, use deterministic tie-breaking and stable-ID browse order. Cursors bind build/source, query, optional target context, explicit filters and budgets; stale scope rejects or explicitly restarts. Validate new handles before publication; invalid replacement retains valid serving state and invalid cold start fails clearly.

## UX & Interaction Patterns

Progressively disclose search IDs/sections, bounded fetch and relationship context, then batch symbol checks. Guidance uses retrieve → resolve → fetch → generate → batch-check → external compile/test → repair, retaining uncertainty and untested status. The service proves source evidence and catalog membership, not compilation or runtime behavior.

## Cross-Story Dependencies

2.8 freezes payloads, schemas, identities, envelopes, limits and lifecycle contracts before dependent acceptance. 5.7 precedes individual handlers. 5.1 consumes 4.1/4.2/4.6; 5.2 consumes 2.7; 5.3 consumes canonical record ports. The Javadoc path consumes 2.9–2.11 stored content and fidelity evidence, preserving structured code/notes and citations after original inputs are removed; production indexing does not reparse HTML. Later producer integrations remain necessary for full supported-content acceptance.

Live serving requires 5.5/5.6 and minimum 6.1/6.2/6.5; fixture handlers or a fixed-pointer bypass cannot pass lifecycle proof. 5.8 feeds 7.1; citation enforcement continues in 7.2 and controlled outcomes in Epic 10. Rollback/backup completion and final release gates remain later obligations.

App-managed source retention and clickable original-page citations are explicitly deferred. Before refining 5.5/5.6/5.7, consult the retained-source entry in deferred-work.md and assess ingestion/build/cleanup dependencies; refine and schedule a separate story before implementation. It supplies no current acceptance criterion, storage/endpoint contract or approval.
