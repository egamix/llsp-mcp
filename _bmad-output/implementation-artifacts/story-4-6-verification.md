# Story 4.6 verification

Implemented on `codex/story-4-6-cited-retrieval` from baseline `6c986069fea679009bf00c265857375489da542a`.

The shared `CitedRetrieval` backend depends on domain ports. It joins existing exact resolution and bounded Lucene discovery to saved knowledge metadata and structured sections. Every primary item includes the actual source SDK/build, stored record metadata, section ID and typed source locator. Excerpts must match the shared durable text projection of that section. Code, notes, tables, lists and symbol-less narratives retain their saved identities.

Bounded candidate-window offsets provide deterministic exact-first, score/ID ordering and stable ID browse. HMAC continuations bind tool, query, explicit filters, source/build/runtime/recipe, limits, target, and contract/identity versions. Invalid or rebound cursors fail before content reads. Incomplete lexical rankings have no continuation. Exhausting a candidate window reports truncation rather than complete-corpus enumeration. Per-request candidate, work, deadline and conservative payload accounting includes enrichment; exhausted first-item budgets raise typed errors and later exhaustion can return explicitly partial pages.

The separate `searchWithRelated` backend operation admits a validated matching relationship handle before query work and delegates to existing bounded traversal using remaining budgets. Ordinary search never traverses. Primary hits and optional relationship context remain separate; the frozen search wire schema is unchanged.

## Executed evidence

- `:domain:test :search-lucene:test :retrieval:test :retrieval:checkDependencyDirection` passed.
- `:retrieval:test :catalog-sqlite:test :cli:test :retrieval:checkDependencyDirection` passed (35 seconds).
- Final focused `:retrieval:test :retrieval:checkDependencyDirection` passed after aggregate candidate/graph-budget tightening.
- Added lexical regression over 65 records: stable offsets, exact-priority ordering, no overlap and candidate-window boundary validation.
- Added stored SQLite/Lucene integration coverage: reopen without HTML; code/note/table/list/narrative citations; exact-first deduplication; optional target independence; 66-result browse; cursor replay and query/filter/limit/target/tamper rejection; source filtering; governance exclusions; work/payload failure; missing metadata/sections; rejection before content reads; and persisted cyclic graph context under remaining budgets.
- `graft build` refreshed the local ignored context graph.

## Delivery boundary

This verifies the currently durable Javadoc API/narrative slice with synthetic stored fixtures. It does not complete future guide/practice/recipe producers, Epic 5 transport/handlers, activation, vectors, or technical governance ratification. No proprietary corpus or answer-quality/performance claim is included. Payload estimates conservatively reserve framing and JSON escaping; a future transport must still enforce the final serialized cap. Parent workflow owns the full Gradle check and focused review.
