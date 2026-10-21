# Product quality improvement plan

Date: 2026-10-21. Status: planning integration authorized 2026-10-21 and recorded in the existing epics/tracker; runtime implementation and technical sign-offs remain pending.

## Desired result

A user asks a question without knowing an API name and receives relevant, version-correct documentation with the signature, caveats and usable source context needed to act. An exact API question returns the correct overload or explicit ambiguity. Content survives ingestion, storage and restart without losing source meaning or requiring the original input directory at serving time.

The immediate objective is ingestion fidelity and useful retrieval, not a new framework, more agent ceremony or a replacement database.

## Evidence from this repository

The existing forward requirements already authorize this direction: FR-5/8/17/19 and Stories 2.8, 4.1, 4.2, 4.6, 5.1/5.3/5.7. Completed stories remain historical; missing future functionality is not retroactively classified as failure of their accepted scope.

| Current boundary | Evidence | User consequence |
|---|---|---|
| Rich fields exist only during extraction | `ExtractedType.java:35`, `ExtractedMember.java:43`, `CatalogStage.java:130-132`, `CatalogRecord.java:25-33` | Persisted symbols lack the explanations and constraints needed for useful answers. |
| Narratives are discovered but not ingested | `JavadocCatalogSource.java:34-43`, `DiscoveryResult.java:29-31` | Package-level introductions and overview guidance cannot be retrieved. |
| Prose normalization flattens structure | `JavadocFieldExtractor.java:551-553` | Code indentation, ordered steps and table associations are lost. |
| Some meaningful notes and ordinary prose links are not retained | `JavadocFieldExtractor.java:414-433,712-717` | Answers can miss implementation caveats and references to further instructions. |
| Only the first description block is retained | `JavadocFieldExtractor.java:495-505`; already in deferred-work register | Later source paragraphs can disappear. |
| Retrieval remains scaffolding | `LexicalIndex.java:10-11`; `search-lucene`, `retrieval`, `mcp` module surfaces | There is no complete search-to-cited-content path yet. |

Paths are relative to `app/`, using their existing module/package locations. These observations concern current implementation; they are not comparative benchmark results.

## What to adopt from the other products

- **zvec-grep:** natural-language discovery with bounded, source-linked results, explicit retrieval routes, and paired task evaluation. Its default agent toolset exposes search while leaving administration in the CLI. Apply that simplicity to the existing six-tool contract; do not add a parallel tool taxonomy. [MCP interface](https://github.com/zvec-ai/zvec-grep/blob/main/docs/03-mcp.md), [sample search workflow and evaluation](https://github.com/zvec-ai/zvec-grep).
- **QuantMind:** source-faithful deterministic preprocessing and self-contained typed, cited knowledge that remains useful after persistence. Its own evaluation section distinguishes designed benchmarks from completed evidence; do the same here. [Positioning and processing model](https://github.com/LLMQuant/quant-mind/blob/master/contexts/design/positioning.md).
- **Graphify:** carry source location and relationship confidence through extraction, validate before graph construction, and let users inspect connections. Borrow explainable evidence, not its complete graph architecture or inferred-edge policy. The reviewed v8 document is a versioned reference, not a claim about every current release. [v8 architecture](https://github.com/Graphify-Labs/graphify/blob/v8/ARCHITECTURE.md).
- **data-mcp:** search followed by bounded record/section reads, with clear client usage. [Product interface](https://github.com/LLMQuant/data-mcp).
- **TurboVec:** measure retrieval quality and resource tradeoffs before introducing a different vector implementation. It is a library, not an alternative ingestion/MCP product. No vector adoption is proposed for this delivery. [Product scope](https://github.com/RyanCodrai/turbovec).

Sources checked on 2026-10-21. No vendor benchmark numbers are used to claim our product is ahead or behind.

## Delivery sequence

### A. Preserve the knowledge before ranking it

Implement Stories 2.9–2.11, retaining `source-faithful-javadoc-content-input.md` as reference: structured content, actual locators, package/overview narratives, durable payloads and accountable ingestion coverage. Integrate its payload/locator/schema decisions into Story 2.8's existing approval gate. Do not approve unrelated 2.8 contracts implicitly.

Exit evidence: modern and legacy fixtures round-trip semantic content after database restart and removal of the original input tree; code, notes, links and tables remain recoverable; every discovered page has an honest disposition. A private-corpus audit reports supported/partial/failed coverage without checking proprietary source into Git.

### B. Retrieve the right content and let the user inspect it

After the relevant 2.8 and build-validation contracts are approved, implement the Javadoc subset of 4.1/4.2/4.6 and the required 5.7 envelope. Lucene indexes stored content in bounded batches, not re-parsed HTML. Exact lookup remains separate from ranked discovery. SQLite is the authoritative content store; stable record IDs join search hits to content.

Use one shared retrieval service for CLI and MCP. Implement the existing `search_knowledge`, `resolve_symbol` and `get_record` paths first, with truthful capability reporting; do not claim the remaining tools or broader stories complete. Return bounded snippets, section identifiers, truthful citations, scope and truncation. Preserve absent anchors rather than reconstructing them. Enrich with existing relationship traversal only where it adds useful context within the request budget.

Exit evidence: a query using only description words finds the relevant content; exact overload lookup is correct; wrong-version content is excluded; record fetch includes code/notes; all primary content results have resolvable citations; restart and catalog/index mismatch tests pass.

### C. Improve real answers using selected examples and measured failures

Reuse Stories 3.1/3.2 for complete examples plus class/method projections, and 3.4/3.5/3.6 for selected instructions and practices. Preserve complete code and prerequisites; retrieve a coherent method or workflow rather than a cut-off sequence. Local guides/doc-files require explicit scope selection; merely retaining an href does not authorize crawling it.

Define the 10.1 protocol before selecting or tuning evaluation content. Compare the same agent/model/tasks/budgets with ordinary source search, exact/BM25, then relationship/example context. Separate missing-source, extraction-loss, retrieval-miss, incomplete-context and answer-generation failures. Keep held-out cases separate; measure indexing cost separately. Add vectors or reranking only for demonstrated retrieval failures.

Exit evidence: independently checked source fidelity and citations, improved task correctness/completion on the predeclared task set, and no regressions in exactness or version isolation. Improvements are measured; none are claimed by this plan.

## Changes deliberately excluded

No new database, graph platform, generic multi-SDK plugin system, cloud crawler, LLM-generated source facts, blanket training-deck ingestion, autonomous promotion or mandatory vector infrastructure. No completed story or unrelated working-tree edit is rewritten. This integration schedules forward work and reconciles completed tracker entries; it does not implement runtime changes or approve schema/identity/interface contracts.

## BMAD integration

The system of record remains epics.md and sprint-status.yaml. Story 2.8 receives the source-content contract input; 2.9–2.11 own implementation. The former umbrella spec is now a reference input, not a parallel build. Build each actual story with bmad-build after its stated gates clear. This scoped refinement clarifies existing FR-5/8/17/19 delivery without restarting the initiative or migrating its ticket store.
