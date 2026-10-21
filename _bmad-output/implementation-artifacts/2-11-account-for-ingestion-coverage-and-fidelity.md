---
title: 'Story 2.11: Account for ingestion coverage and fidelity'
type: 'feature'
status: 'draft'
route: 'dispatch'
created: '2026-10-21'
story_key: '2-11-account-for-ingestion-coverage-and-fidelity'
after: ['2-10-preserve-structured-javadoc-and-narrative-content']
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/story-2-8-source-content-contract-input.md'
---

## Intent

Expose what ingestion actually preserved, skipped or lost, and close the ingestion path with end-to-end fidelity checks before production lexical indexing consumes it.

## Prerequisites and uncertainty

Requires 2.10 and the 2.8 report/outcome/limits contracts. Synthetic work can proceed without private gold. Any private audit selection/tuning additionally waits for 10.1/B2/B6 and corpus-owner permission/inventory; absent prerequisites mean not-run, never passed. Held-out evaluation answers remain inaccessible to corpus authors and agents.

## Execution

- Wire discovery/emission/persistence outcomes into `CatalogStage` and `IngestionReport` using the approved severity and completion contracts.
- Account for page, record and section units separately; declare counting rules and supported/partial/unsupported dispositions.
- Enforce the declared limits before unbounded input/materialization; report malformed/oversized content without a false completeness claim.
- Extend the shared harness for restart/source-removal, rollback/retry determinism, structure and citation correctness, and below/above-boundary cases.
- Provide an optional local private-corpus audit that compares an independently checked inventory and emits aggregate counts/diagnostics without dumping source bodies or committing proprietary material.

## Acceptance

- Given discovered pages, when ingestion ends, then their count equals excluded + complete + partial + failed pages under disjoint documented dispositions; record/section totals reconcile separately with persistence.
- Given malformed, unsupported or oversized content, when reporting, then its disposition is visible and incomplete content is never labeled complete.
- Given interruption/retry and later source-directory removal, when reopened, then logical stored content/citations match the uninterrupted result.
- Given a private corpus without required approvals/inventory, when auditing, then the outcome is explicitly not-run/blocked while synthetic verification remains runnable.
- Given selected audited source specimens, when independently compared, then missing notes, blocks, members or narratives produce discrepancies rather than a misleading success summary.

## Verification and completion

Run domain/ingest-javadoc/catalog-sqlite/CLI checks against deterministic synthetic fixtures. Publish the counts, skips and actual audit evidence in the build record. Tests and necessary cleanup are included. This completes ingestion accounting; end-user answer improvement still requires 4.x/5.x integration and controlled 10.x trials.
