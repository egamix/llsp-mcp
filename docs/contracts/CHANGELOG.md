# Contract changes

## read-tools/2 — 2026-10-21 — policy approved, technical ratification pending

Breaking interface change: remove conflated request sdkVersion; add optional targetSdkVersion context and independent optional sourceSdkVersion filter. Responses echo nullable target context and declare actual pinned source SDK/build, with per-content citations matching that source. Default source selection remains bounded and deterministic; no retrieval/MCP handler or source registry is implemented. Missing target documentation does not block available evidence or imply API absence. Exact matches prove cited-source identity only. Notices require concrete, cited target-relevant change evidence; mismatch alone produces no automatic warning. Explicit GENERAL applicability preserves governance without imposing a target restriction.

read-tools/2 changes catalog-runtime/1 and complete build identity; rebuild old runtime builds and reject stale cursors. Cursor-binding/2 includes optional target independently from source/build. SQLite schema/1, identity/1, symbol:1 and source-specific record/edge IDs remain unchanged. Portable runtime known answers were recomputed independently; prior read-tools/1 snapshot hashes below and in historical verification are not current v2 claims. The user approved this guidance policy only; schema/identity/interface technical sign-offs remain pending.


## v1 - 2026-10-21 - technical ratification pending

Breaking rebuild-only change from provisional schema 0 to SQLite schema 1; identity/1 record/edge domains and recoverable symbol:1 wire grammar; qualified parameter preservation and source-relative partial occurrence identities; complete portable build digests; content/1, javadoc-lexical/1, catalog-runtime/1 and knowledge-dependency/1 separation. Old stores are rejected unchanged before DDL.

Added optional-symbol knowledge/governance/applicability/dependency and durable section/block/locator/storage contracts; shared validation operations/reasons with persisted build metadata; manifest/report v1 and read-tools/1 schemas for all six read-only tools. Lexical operation no longer requires or hashes a model. Unsupported capabilities remain skipped and cannot simulate activation.

No reviewed exemptions are claimed. Human schema, identity and interface approvals remain pending in sign-offs.json. Synthetic checks do not establish evaluation acceptance or approval.

## v1 implementation-candidate edge-case corrections - 2026-10-21

Pre-binding error envelopes now explicitly permit paired null build/capability binding while successful responses require both. Added missing-build and unknown-SDK specimens. FOUND section content requires citations independently of snippets. Search mode defaults to LEXICAL with an explicit nonblank query; explicitly selected BROWSE permits an omitted/empty query and kind-only filters. Added positive/adverse schema checks for each case. This remains an unratified v1 candidate; no approval or product-handler behavior is implied.

Independent review corrections enforce complete persisted SDK-scope validation identically at CLI/startup and require practice/recipe governance independently of knowledge kind. Existing v1 semantics are preserved; focused and full cross-platform regression checks pass. Human technical ratification remains pending.
