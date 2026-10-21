---
title: 'Story 2.10: Preserve structured Javadoc and narrative content'
type: 'feature'
status: 'draft'
route: 'dispatch'
created: '2026-10-21'
story_key: '2-10-preserve-structured-javadoc-and-narrative-content'
after: ['2-9-persist-source-faithful-api-documentation']
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/story-2-8-source-content-contract-input.md'
---

## Intent

Preserve the structure and caveats that make documentation useful: complete owned descriptions, code blocks, lists/tables, API/implementation notes, original links and package/overview narratives. Extend 2.9's stored-content path rather than defining another format.

## Prerequisites and uncertainty

Requires 2.9 and its ratified 2.8 contracts/harness. Unsupported doclet structures must be reported, not silently interpreted as complete. Any source-layout ambiguity found during implementation needs a fixture and an explicit supported/partial decision; it cannot justify fabricated facts.

## Execution

- Extend `JavadocFieldExtractor` and extraction DTO mapping to preserve ordered owned blocks, code whitespace, nested lists, table header/cell relationships and labeled notes.
- Retain inline-link labels/original hrefs and actual DOM anchors; preserve external/escaping scope without following links.
- Extend `JavadocCatalogSource` to emit discovered package/overview content as symbol-less narratives using approved domain identities.
- Exclude navigation, repeated summaries and nested members from each primary content record. Generate searchable text from retained content, never replace it with flattened text.
- Extend 2.9's golden extraction/persistence fixtures across modern and legacy layouts; keep necessary cleanup in this change.

## Acceptance

- Given multiple owned paragraphs plus code/list/table specimens, when stored/reopened, then their semantic structure and original code whitespace survive in source order.
- Given API/implementation notes and type-parameter documentation, when fetched, then labels and content survive separately from ordinary positional parameters.
- Given package/overview pages, when ingested, then narrative content has a truthful source locator without an invented API symbol.
- Given external/escaping links or absent anchors, when fetched, then original evidence remains and no fetch, guessed local edge or reconstructed anchor is introduced.
- Given duplicated summary/detail or nested member markup, when emitted, then unrelated content is not duplicated or merged into the primary record.

## Verification and completion

Run synthetic golden tests in ingest-javadoc and store/pipeline roundtrips from 2.9. Assert recovered blocks and source locators, not merely nonempty strings. No private corpus is committed or required for CI. Review/commit the bounded implementation; no search quality or complete-corpus claim is made.
