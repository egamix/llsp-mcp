---
title: 'Preserve source-faithful Javadoc content'
type: 'feature'
created: '2026-10-21'
status: 'reference'
role: 'planning-input'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/product-quality-improvement-plan-2026-10-21.md'
---

<frozen-after-approval reason="proposed intent; approval required">

## Integration disposition

This document is retained as source material, not a standalone build spec. The approved scheduling amendment assigns contracts to Story 2.8 and implementation to 2.9–2.11 in epics.md/sprint-status.yaml. Execute those stories in dependency order; do not build this umbrella input independently. No technical schema/identity/interface approval is implied.

## Intent

Preserve documentation through ingestion and database restart so future search returns explanations and constraints, not only symbol names. Include type/member content and package/overview narratives, with meaningful structure and truthful citations.

## Boundaries & Constraints

Always preserve SDK scope, domain-owned identity, explicit uncertainty, actual anchors including absence, and ordered source meaning. Keep SQLite authoritative. Associate content and records atomically. Bound input, batching and reads; report partial/failed sources honestly.

Never invent content or locators, fetch links, run embedded markup, alter active builds, commit proprietary inputs, replace storage or reopen completed story history. Search/MCP are the next delivery, not completed by this one.

Proposed decisions for the relevant Story 2.8 approval: ordered typed blocks plus a derived searchable text projection; original link labels/hrefs and classified scope; self-contained persisted content; content hashes separate from stable identity. Keep current symbol IDs until the separately approved identity change. Narrative IDs use the approved domain source/version recipe. Unsupported provisional stores require an explicit rebuild, without automatic migration or deletion. This specification does not waive other 2.8 sign-offs.

## I/O & Edge-Case Matrix

| Case | Expected behavior |
|---|---|
| Modern/legacy type and overload | Signature, all owned descriptions, parameter/type-parameter docs, returns, throws, since/deprecation and labeled notes preserved. |
| Code, paragraphs, lists, tables | Preserve code whitespace, block order, nesting and header/cell associations. |
| Package/overview | Narrative content with a real source locator; no fabricated type. |
| Missing anchor or section | Preserve absence, not a reconstructed location or invented content. |
| External/escaping link | Retain original evidence; never fetch or verify it as a local symbol. |
| Malformed/oversized source | Explicit partial/failure outcome under declared bounds; no silent truncation labeled complete. |
| Restart without input files | Stored content and citation metadata remain retrievable by stable ID. |

</frozen-after-approval>

## Code Map

- Extraction: `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractor.java`; adjacent `ExtractedType.java` and `ExtractedMember.java` carry payloads. Reuse current symbol/reference extraction.
- Discovery handoff: `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocCatalogSource.java`; consume both member and narrative pages.
- Domain: `app/domain/src/main/java/com/luciadmcp/knowledge/domain/CatalogStore.java`; add `DocumentationContent.java` and supporting approved block/locator values in the same package. Keep identity separate from content.
- Storage: `app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStore.java`.
- Wiring/reporting: `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/CatalogStage.java` and adjacent `IngestionReport.java`.
- Tests: existing `JavadocFieldExtractorTest`, `JavadocDiscoveryTest`, `SqliteCatalogStoreTest` and `CatalogStageTest` in their matching module test packages.

## Tasks & Acceptance

**Execution:**
- [ ] Ratify payload, locators, narrative identity, schema/rebuild policy and finite limits in the existing 2.8 subtask; obtain its required technical approval.
- [ ] Extend extraction DTOs/extractor for structured blocks, all owned paragraphs, notes, original links and actual anchors.
- [ ] Extend the discovery handoff for package/overview narrative content.
- [ ] Extend domain ports/SQLite for versioned content, content hashes, atomic associations and bounded reads.
- [ ] Wire persistence and coverage accounting into catalog ingestion/reporting; release bounded batches.
- [ ] Add synthetic fidelity/roundtrip tests and optional private-corpus coverage audit without copying source into Git.

**Acceptance Criteria:**
- Given paired modern/legacy fixtures, when ingested/reopened, then equivalent semantic content, original code and truthful locators survive.
- Given overloads and narratives, when fetched, then each payload belongs to the correct version-scoped stable record.
- Given a prose-only change, when rebuilt, then its content digest changes without changing unaffected symbol identity.
- Given relocated roots, when ingested, then logical IDs remain unchanged.
- Given partial, failed or oversized sources, when reporting, then every discovered page has a reconciled disposition and incomplete content is never labeled complete.
- Given unavailable original input files after ingest, when fetching, then descriptions/code/notes/citations are still available.
- Given an unsupported schema, when opened, then the error requires a rebuild and existing files remain untouched.

## Implementation Notes

Investigation only; no runtime edits. Several modules and persisted formats change, so this requires dispatch and contract approval rather than a one-shot fix. No migration, deployment or active-build mutation is authorized implicitly.

## Spec Change Log

## Review Triage Log

## Verification

Run domain, ingest-javadoc, catalog-sqlite and CLI pipeline tests. Golden cases cover multiline code, tables/lists, notes, links, absent anchors, repeated description blocks and narratives. SQLite reopen and rollback cases prove durable content and atomicity; boundary tests cover bounded input/read behavior.

An optional local private-corpus audit compares coverage against independently checked pages/members/sections. Synthetic tests remain sufficient for ordinary CI. Retrieval/answer quality is evaluated in the next slice, not claimed by these storage tests.
