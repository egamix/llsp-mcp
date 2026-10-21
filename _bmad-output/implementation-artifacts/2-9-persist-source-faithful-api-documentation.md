---
title: 'Story 2.9: Persist source-faithful API documentation'
type: 'feature'
status: 'draft'
route: 'dispatch'
created: '2026-10-21'
story_key: '2-9-persist-source-faithful-api-documentation'
after: ['2-8-contract-and-verification-freeze']
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/story-2-8-source-content-contract-input.md'
---

## Intent

Implement the first complete path from extracted type/member documentation through domain ports and SQLite to a bounded fetch by stable ID. This is a persistence delivery, not a search or MCP implementation.

## Prerequisites and uncertainty

Implementation waits for the relevant 2.8 payload/locator/schema/identity/limits ratification. Wire formats, narrative identity recipes and finite caps are consumed from those contracts; draft status is not permission to invent them. No human approver is assigned or assumed by this story.

## Execution

- Extend the approved domain documentation-content values and catalog ports; keep identity separate from mutable content digests.
- Connect `JavadocFieldExtractor`/`JavadocCatalogSource` output to `CatalogStage` without discarding documented semantic fields.
- Implement atomic record/content association and bounded fetch/enumeration in `SqliteCatalogStore`; reject unsupported schemas before mutation.
- Establish the shared synthetic ingestion/reopen fixture harness used by 2.10/2.11. Tests and necessary cleanup ship with the implementation.

## Acceptance

- Given paired modern/legacy type/member fixtures, when ingestion completes and SQLite reopens after source removal, then signature, descriptions, parameter/type-parameter docs, returns, throws, since/deprecation and citation metadata are recoverable.
- Given overloads and two SDK versions, when fetched by stable ID, then payloads remain attached to the correct symbols/scope without silent ambiguity resolution.
- Given a content-only edit or root relocation, when rebuilt, then appropriate content digests change and unaffected logical identity stays stable.
- Given an interrupted transaction or unsupported format, when writing/opening, then no orphan content or partial association is visible and existing files are not silently migrated/deleted.

## Code map and verification

Use the existing packages in `app/domain`, `app/ingest-javadoc`, `app/catalog-sqlite`, and `app/cli`. Primary touchpoints are `CatalogStore`, the approved documentation DTOs, `JavadocCatalogSource`, `CatalogStage`, and `SqliteCatalogStore`. Extend domain/extractor/store/pipeline tests with known-answer content and real SQLite reopen/rollback checks. Do not add a second persistence engine or source-reparse fetch path.

## Done when

The above checks pass, the scoped changes are independently reviewed and committed on a feature branch, and required 2.8 approvals are cited. This draft remains backlog until those gates clear; it does not reopen Story 2.4.
