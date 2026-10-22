---
title: 'Story 2.10: Structured Javadoc and narrative content'
type: 'feature'
created: '2026-10-22'
status: 'ready-for-dev'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/AGENTS.md
warnings: [oversized]
deferred: []
story_key: '2-10-preserve-structured-javadoc-and-narrative-content'
---

<intent-contract>

## Intent

Retain the structure and caveats currently flattened or dropped from Javadoc, including package/overview prose. Extend Story 2.9's real ingestion-to-SQLite-to-bounded-fetch path so reopened content remains useful after source removal.

## Boundaries & Constraints

**Always:** Preserve ordered owned descriptions, code whitespace, simple list nesting/order, rectangular tables, API/implementation note labels, original hrefs/labels and actual/absent anchors. Narrative records use existing narrative/1 IDs and absent symbols, never fabricated API catalog entries. Retain stable API identities, source/build isolation, independent hashes, atomic writes, bounded read/validation and read-tools/2 useful-guidance policy. Derive searchable text from stored blocks. Preserve unrelated work in the original D:/workspace/luciad-mcp checkout; all implementation occurs in this clean managed worktree. Commit each verified substantial slice separately.

**Never:** Fetch links, invent local edges/anchors, merge members/navigation/summary duplicates, silently claim unsupported structures are fully preserved, implement coverage reconciliation/search/MCP/lifecycle handlers, or claim private-corpus acceptance. Unsupported representational shapes need explicit partial/error evidence and fixtures; never discard them silently. Keep completed-story frozen text unchanged and record no invented human sign-off.

## I/O & Edge-Case Matrix

| Input/state | Expected output | Failure handling |
| --- | --- | --- |
| Modern/legacy multiple paragraphs, preformatted code, lists/table | Ordered typed content survives production ingestion, source deletion and reopen | No first-block-only or whitespace flattening |
| API/implementation notes and type parameters | Labels/structured values retained; generic and positional docs separate | No duplicate note-as-prose output |
| Package/overview owned prose | Stable source-specific NARRATIVE, no symbol/catalog API row | Empty/unsupported layouts reported honestly |
| External, escaping and ordinary inline hrefs; absent anchors | Original label/target retained with truthful scope/locator | No network, guessed edge or fabricated anchor |
| Nested members, repeated summaries/navigation | Only each owner's prose retained once | No descendant-selector leakage |
| Ambiguous ownership, spans/ragged tables, unsupported rich list cells | Explicit partial/unsupported issue with source context | No false fidelity claim |
| API+narrative retries, relocation, SDK/build mismatch and transaction failure | Stable IDs/content; scoped paging; atomic rollback | Existing near-cap/corruption guards retained |
| Schema/2 or corrupt narrative ID/kind/symbol/source metadata | CLI and pinned startup reject consistently | Older stores remain byte-identical |

</intent-contract>

## Code Map

Module paths below are under app/, using existing package structure.

- ingest-javadoc/JavadocFieldExtractor:93-225,392-467,520-529,733-808: entry points, owned descriptions/notes, actual anchors and offline href classification. Current norm/first-block path flattens content. Extend ExtractedType/ExtractedMember with retained blocks while preserving semantic compatibility fields.
- JavadocDocumentationAssembler:7-47 and JavadocCatalogSource:31-84: assemble existing payloads; source currently loops only memberPages. DiscoveryResult.narrativePages already separates PACKAGE/OVERVIEW.
- domain/DocumentationPayload:7-128 supports ordered CODE/NOTE/TABLE/LIST, links and canonical hashes; ListItem children lack independent ordered flags and table cells are strings. Preserve current wire shape; unsupported shapes are explicit partial cases.
- domain/KnowledgeRecord:5-30: reuse narrativeId(sdk,path,kind), NARRATIVE and optional symbol. Persist explicit PACKAGE/OVERVIEW kind to validate identity; never infer an API coordinate.
- cli/CatalogStage:91-145 and RelationshipsStage:164-170: preserve API pass-two evidence; add separate domain-port narrative writes.
- catalog-sqlite/SqliteDocumentationStore:21-182: currently requires API tuple/symbol, documentation FK references records, reads join records and validation loops API rows. Generalize the common metadata/content association to knowledge records while retaining API tuple integrity. SqliteCatalogStore validation must reject unsupported governance/scope and corrupt narratives on both CLI/startup paths.
- ContractVersions, IndexManifest, IngestPipelineRunner and IngestionReport contain schema/recipe/runtime/capability declarations; update together with Python oracle and portability fixtures.
- Reuse DocumentationRoundtripTest source-removal harness, DocumentationStoreTest rollback/paging/near-cap tests, era fixtures, discovery narrative fixtures and ApplicationPortabilitySnapshotTest.

## Tasks & Acceptance

- [ ] domain, catalog-sqlite, docs/contracts: implement truthful narrative metadata/content association. Keep identity/1, narrative/1, content/1 and read-tools/2. Version schema to 3 for documentation -> knowledge FK and explicit narrative-kind metadata; rebuild-only reject older stores unchanged. Version relevant recipe/runtime/parser fingerprints and declared capabilities consistently. Validate every supported knowledge/document, not only API catalog rows; retain finite aggregate byte/work guards.
- [ ] ingest-javadoc: bounded owned-block extraction reused for API/narratives, with deterministic sections, notes and truthful links. Supported: known modern/legacy ownership wrappers, simple nested lists with representable ordering, rectangular column-header tables. Complex spans/row headers/mixed-order nested lists or rich cell structures not representable by the DTO produce explicit partial issues and tested fallback evidence, never claimed full fidelity.
- [ ] cli and domain ports: wire classified narratives to the same atomic store via an additive symbol-less domain path, no synthetic CatalogRecord. Preserve API relationship resolution and actual locators. Unsupported ports/content failures cannot report tuple-only success.
- [ ] tests, docs/contracts: modern/legacy golden fixtures and real pipeline source-removal/reopen assertions for every matrix row. Check exact recovered blocks/order/whitespace/links/IDs, mixed API+narrative enumeration, retry/rollback, corruption/startup parity, byte caps and relocation. Include narratives and structured sections in platform snapshots; refresh graph and record independent review.

**Acceptance:** Given modern/legacy API, package and overview fixtures with structured prose, when the real CLI ingestion pipeline runs and inputs are removed, then bounded reopened fetches retain the exact expected structure/locators under stable IDs and narratives have no symbol or API catalog row. Given a content edit/relocation, IDs remain stable while appropriate hashes change. Given corrupt narrative associations or unsupported storage, CLI validation and pinned startup both reject; an unsupported source shape is explicitly partial rather than silently complete.

## Design Notes

Narrative kind must be explicit bounded metadata (PACKAGE/OVERVIEW), independent of source locator sections. Extend domain metadata compatibly and schema encoding as needed; retain nullable symbols and approved source provenance. No broad human technical ratification is inferred from automatic implementation authorization. Documentation describes current support and pending unrelated gates accurately.

This is one story, not a schema redesign or another storage engine. Existing DTO fields stay reusable for the next coverage story. Failure/partial issues use existing stage policy; complete reconciled page/record/section coverage is still Story 2.11.

## Spec Change Log

## Review Triage Log

## Verification

Run focused tests per verified slice, Python contract verifier/oracle, then full app/gradlew.bat --no-daemon check. Audit every matrix row against tests that actually ran. Export fresh content-inclusive Windows/Linux snapshots, including narratives, and compare logical bytes/JSON excluding operational fields. Scoped diff checks, graft build, independent complete-diff review. No private corpus or live handler claims.

Implementation uses this authorized managed worktree exclusively; escalated exec may be needed for writes/cache/Git here. Windows Gradle process environment: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir (keep it nonexistent). Do not invoke another skill renderer; parent owns this active auto workflow. No merge, push or squash.
