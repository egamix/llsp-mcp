# Contract changes

## 2026-10-28 — Example links (Story 3.2)

`USES_TYPE`, `CONSTRUCTS` and `CALLS` edges are now produced, with an `EXAMPLE` record as source and a catalog symbol as target. The edge `evidence` column carries `L<line> <token>` and is part of the edge identity, as the column was reserved for. Validation accepts an example source only for these three types and only with non-empty evidence. In `get_relationships`, example nodes and edge `sourceId`s are example record IDs, and a `seed` may be an example record ID. Request and response schemas and store schema/3 are unchanged; existing builds remain valid.

## 2026-10-23 — EXAMPLE records (Story 3.1)

Additive: `recordType` gains `EXAMPLE` (common.schema.json). Example records come from `--samples` `.java` files: one per declared type (whole-file or nested-type source) and one per method/constructor of at least three lines. They have no SDK symbol, cite `relativePath` plus `lines`, carry the code as a `CODE` block labelled `source`, and use identity `example/1` over product, SDK, path, type and member signature. Store schema/3 is unchanged (no new tables or columns); existing builds stay valid. Clients that reject unknown `recordType` values must accept `EXAMPLE`. Recorded in sign-offs.json as an additive exemption, pending owner review, rather than a read-tools version bump.

## 2026-10-23 — get_relationships, check_symbols and index_status served

The three remaining read-tools/2 tools are now served over MCP and the CLI `query` command; request and response schemas are unchanged except one correction: `index_status.data.schemaVersion` was `const 1`, which contradicted the ratified store schema/3, and is now an integer of at least 1. Relationship edges report canonical symbol keys as `sourceId`/`targetId`. A `seed` may be a canonical key, a catalog record ID or a resolvable symbol name.

## 2026-10-23 — Technical ratification

Owner (eric) ratified identity/1, schema/3 with the docs/contracts JSON schemas, and read-tools/2 as implemented on master `12b1ba5`. The dependent-story gate in sign-offs.json is open. From here on, changes to these contracts need a version bump and an entry here (or a reviewed exemption). Story 10.1 evaluation acceptance is still not run. Earlier "ratification pending" notes in this file and in the verification docs are historical.

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

## 2026-10-21 — Story 2.9 scoped adoption

Adopt identity/1, DocumentationPayload, typed locators and bounded DocumentationStore for API persistence. schema/2 adds atomic catalog/knowledge/document/section storage, rejects older stores unchanged, and versions javadoc-lexical/2 and catalog-runtime/2. Production capabilities add api-documentation; read-tools/2 guidance is unchanged. Interface, evaluation and release sign-offs remain pending.

## 2026-10-22 — Story 2.10 structured documentation implementation

Rebuild-only schema/3 moves the documentation foreign key to knowledge_records and adds explicit PACKAGE/OVERVIEW narrative kind metadata. Narratives reuse narrative/1 source-specific IDs and have no symbol or API catalog row. API identity/1, content/1, existing block DTO wire shape and read-tools/2 guidance remain unchanged. javadoc-lexical/3, catalog-runtime/3, javadoc-parser/3 and schema/3 toolchain components invalidate incompatible builds; declared capabilities add narrative-documentation and structured-documentation.

Owned paragraphs, whitespace-preserving code, homogeneous simple nested lists, rectangular column-header tables, labeled notes and offline href provenance persist through production ingestion. Unrepresentable shapes report PARTIAL_UNSUPPORTED and preserve HTML evidence where ownership is known. Unknown/ambiguous/empty narrative ownership reports issues. Coverage reconciliation, search, MCP and lifecycle handlers remain outside this story; technical human ratification and private-corpus acceptance are not claimed.


## 2026-10-22 — Story 2.11 reconciled ingestion report and audit

Report/2 replaces deferred coverage placeholders with disjoint page dispositions and source-specific
logical record/assembled-section totals. Coverage/1 sidecars bind accounting to build, SDK, input,
recipe and runtime and are atomically written before checkpointing. Missing evidence stays
unavailable; fail-fast resume honors the configured failure policy. Source reads and filesystem
traversal have finite admission before materialization.

The optional inventory/1 audit compares independent expectations with actual structured payload
bytes, parent associations and canonical metadata under bounded read-only access. Private mode
requires explicit attestations before opening protected inputs. Schema/3, identity/1, content/1
and read-tools/2 remain unchanged; this execution does not supply pending technical ratification.
