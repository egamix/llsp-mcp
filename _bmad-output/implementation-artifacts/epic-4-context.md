# Epic 4 Context: Exact and Lexical Retrieval; Gated Semantic Enhancement

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Provide offline, source-bound exact-symbol resolution and useful BM25 discovery over approved knowledge, then assemble concise, cited results with bounded relationship context. This establishes the first product-proof retrieval baseline without an embedding model. Exact identity, explicit ambiguity, source provenance and honest coverage take precedence over relevance. Vector search, fusion and embedding fingerprints remain deferred until a diagnosed retrieval gap, controlled benefit, preserved safeguards and owner approval justify reactivation.

## Stories

- Story 4.1: Detect and prioritize exact-symbol queries
- Story 4.2: Index canonical and lexical fields for BM25
- Story 4.3: Local ONNX vector search with explicit failure semantics
- Story 4.4: Fuse lexical and vector results under exact priority
- Story 4.5: Enforce a consistent embedding fingerprint
- Story 4.6: Assemble bounded, cited lexical results

## Requirements & Constraints

- First-proof delivery comprises 4.1, 4.2 and 4.6. Moving lexical enrichment into 4.6 does not complete deferred fusion. No model, broad technique vocabulary or placeholder vector capability is required for healthy lexical operation.
- Resolve fully qualified signatures against catalog facts. Canonical fixtures must achieve 100% exact lookup; unknown symbols never become verified and competing overloads remain explicitly ambiguous. Relevance cannot prove identity, applicability or tested compatibility.
- Pin one available source SDK and immutable build per request, independently of optional target SDK context. Respect explicit source filters. Missing exact-target documentation must not block useful available-source evidence or imply API absence. Prevent foreign-source records and edges.
- Index exact, prefix and camel-case symbol variants alongside descriptions, parameters, returns, exceptions, notes, signatures and narrative text. Support highlighting and scoped filters for source SDK, record type, knowledge kind, applicability, governance/provenance, package, element kind, deprecation and quality. Approved symbol-less content participates normally.
- Production lexical indexing consumes durable approved catalog payloads in bounded batches. It must work after restart without original source directories and must not reparse source HTML. Preserve section identity, code/note context and truthful locators during result enrichment.
- Every primary content result carries a stable record ID, actual source identity, SDK scope, record type and truthful typed locator. Include a canonical symbol only when resolved. Never invent anchors or discard verdict/status outcomes because content citations are absent.
- Enforce finite query/input, candidate, traversal, execution, continuation and payload budgets before materializing large intermediate sets. Default pages contain 10 results, the hard cap is 50, and serialized payloads are at most 1 MiB. Expose count relation as exact, lower-bound or unknown, plus coverage, work/payload truncation and build-bound continuation.
- Distinguish not-found, not-indexed, filtered/inapplicable and unavailable-capability outcomes. Bounded candidates or neighborhoods never establish complete-corpus proof.
- Preserve zero external egress and read-only serving. Initial targets are exact lookup p95 ≤250 ms, warm lexical search p95 ≤2 s, at least four concurrent requests with bounded queues, and serving RSS ≤8 GiB. Performance acceptance requires owner-supplied reference hardware and representative corpus. Citation completeness is 100%; the provisional natural-language diagnostic is an expected approved record in the top five for at least 90% of gold queries, subject to evaluation-protocol approval.

## Technical Decisions

- Domain owns immutable identity, query/proof/result contracts and ports. Shared application services depend on those ports; bootstrap wires embedded SQLite/Lucene adapters. Retrieval and MCP must not import parsers, ingestion or engine implementations.
- Stable domain record IDs join catalog, index and content and drive deduplication. Lucene document IDs are never public identity. Versioned symbol coordinates represent API identity; normalized aliases are lookup aids. Record type and knowledge kind remain independent.
- Retrieval owns the complete bounded pipeline: source/build selection → exact/lexical candidates → governance, applicability and primary-content citation checks → deterministic ordering → bounded paging and enrichment. Transport must not obtain an unbounded candidate collection.
- Order exact proof before lexical candidates, then the declared deterministic score/order, then stable record ID ascending. Filter-only browse orders by stable ID. Logical order must not depend on threads, iteration order or wall-clock timing.
- Continuation binds source/build, query, optional target, explicit filters and budgets. Reject stale scope or explicitly restart; never combine builds across pages.
- Candidate learning stays outside active builds. Practices require approval; recipes satisfy applicability and required dependencies. Content citations and optional verdict diagnostics have different requirements.
- Validate catalog/index/schema/profile compatibility through the shared build-validation policy. Active builds are immutable; each request retains one validated read-only handle. Common identity/runtime compatibility remains required despite deferral of vector fingerprints.

## UX & Interaction Patterns

The direct consumer is a coding agent. Return useful normal guidance with ordinary citations to the actual source version even when the supplied target differs; no mandatory SDK prompt, mismatch-only error or automatic unverified warning. Only concrete, cited introduction/removal/behavior-change evidence relevant to that target supports a specific notice. GENERAL guidance may have unrestricted target applicability while retaining governance and provenance.

Expose how a result matched, proof or ambiguity, applicability, coverage and continuation so the agent can progressively fetch authoritative sections and relationships. Retrieval supplies evidence; compilation, testing and runtime validation remain external.

## Cross-Story Dependencies

- Story 2.8 must freeze and obtain technical sign-off for identity, symbols, payloads, locators, schema, lexical compatibility, query/proof/result/error/continuation contracts and finite bounds before dependent work. Planning approval alone does not satisfy this gate.
- Production 4.2 additionally waits for durable source-content delivery in 2.9–2.11. Story 4.6 depends on 4.1/4.2 and cycle-safe bounded traversal from 2.7.
- Epic 3 supplies selected supported examples and approved guide/practice/recipe content. Epic 5 consumes the same retrieval/proof services through the six-tool interface; Epic 6 supplies shared validation and immutable lifecycle handling.
- Story 10.1 predeclares retrieval diagnostics, thresholds, held-out controls and trial budgets before evaluation or curation. Later evaluation measures the lexical baseline before any vector reactivation.