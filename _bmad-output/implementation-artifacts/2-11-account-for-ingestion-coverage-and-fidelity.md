---
title: 'Story 2.11: Account for ingestion coverage and fidelity'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_commit: 'b6ad6ad08eccb18fdebf0b6e9989e85926dd5650'
route: 'dispatch'
review_loop_iteration: 0
story_key: '2-11-account-for-ingestion-coverage-and-fidelity'
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/AGENTS.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Report what ingestion preserved, skipped or lost. Close Story 2.10 with reconciled accounting, bounded admission and an optional independent fidelity audit before lexical indexing.

## Boundaries & Constraints

**Always:** Count pages, logical records and payload sections separately. Each discovered page is EXCLUDED/COMPLETE/PARTIAL/FAILED; only positively classified non-content is excluded. Unsupported/unknown/malformed content is never complete. Inserts/retries count one logical association. Preserve structure, IDs, scope, rollback and cross-version guidance. Independent unknown denominators stay absent; version reports without inventing technical ratification.

**Never:** Access private/held-out material; invent approvals/counts/completeness; dump source bodies; change storage/identity/content/read-tools contracts; implement search/MCP/lifecycle/HTTP serving. Preserve original-checkout edits and separate commits. No push or Story 2.11 merge.

## I/O & Edge-Case Matrix

| Input/state | Expected behavior | Failure handling |
| --- | --- | --- |
| Mixed API/package/overview/navigation pages | Disjoint page totals reconcile; emitted/persisted/failed records and sections reconcile separately | Unknown class or unsupported evidence prevents completeness |
| Malformed/unsupported/oversized source or payload | Bounded diagnostics and truthful partial/failed dispositions | No silent omission or complete claim |
| Atomic write failure or identity clash | Failed association counts; surviving content remains accurate | Real storage/integrity errors invalidate build |
| Interrupted ingestion and retry/resume | Stable logical totals and restored validated coverage | Missing/corrupt/mismatched coverage is unavailable, never zero/complete |
| Source deletion and reopen | Exact retained structure/links/citations match uninterrupted content | No dependence on original directory |
| Exact/above file, enumeration and work boundaries | Finite admission before materialization | Explicit rejection and unsuccessful result when admission aborts |
| Private audit missing approvals/inventory | NOT_RUN/BLOCKED before protected input/store access | Synthetic mode remains runnable |
| Independent inventory with missing/changed note/block/member/narrative | Aggregate discrepancies based on actual stored payload | Never count-only success or body/identifier leakage |

</frozen-after-approval>

## Code Map

Under app/: ingest-javadoc/JavadocDiscovery:83-192 has unbounded reads/walks; JavadocCatalogSource:31-46,118-125 drops full inventory. cli/pipeline/CatalogStage:64-153 owns atomic writes; IngestPipelineRunner:198-200,252-307 owns resume/report; BuildContext:137-166 hashes materialized inputs; IngestionReport:107-159 emits null coverage. JavadocFieldExtractor:69-87 needs capped reads. Reuse payload/extractor limits, DocumentationCodec, and StructuredDocumentationRoundtripTest/DocumentationRoundtripTest/DocumentationStoreTest/ApplicationPortabilitySnapshotTest. docs/contracts and tools/contracts own schemas/oracles.

## Tasks & Acceptance

- [x] ingest-javadoc discovery/source/results, cli/CatalogStage — retain inventory/typed reasons; reconcile disjoint page dispositions and emitted/persisted/failed record/section totals after writes. Retries count once; unprocessed pages stay explicit.
- [x] cli report/context/runner and new coverage snapshot — atomic deterministic build/SDK/recipe/runtime-bound sidecar before checkpoint. Missing/corrupt/mismatched restore is unavailable; retry replaces totals. Report/2 exposes availability, counting rules, sorted outcomes/reasons and unknown expectations; completion considers coverage. Update contracts/oracles.
- [x] ingest-javadoc shared bounded reader, cli/BuildContext — limit+1 HTML/search-index reads (8 MiB), streaming original-byte SHA-256, enumeration caps (100,000 files/root, 4,096 UTF-8 bytes/path, 16 MiB aggregate path bytes/root). Document limits; preserve payload/storage caps and admitted digests. Abort beyond enumerated universe honestly.
- [x] tools/contracts/audit_ingestion.py plus synthetic tests — bounded read-only comparison of independently supplied build/SDK-bound record/section/block expectations. Verify actual payload bytes, not hash columns alone. Before private inventory/store access, require explicit 10.1/B2/B6, corpus-owner/development-only/held-out-exclusion/inventory attestations. Missing gates yield NOT_RUN/BLOCKED. Diagnostics contain aggregate categories/counts only, including sanitized errors.
- [x] module tests and contract docs/evidence — execute every matrix row, real rollback/failures, exact/above boundaries, retry and source deletion. Independent audit specimens detect missing notes/blocks/members/narratives. Update portability snapshots and Graft.

**Acceptance:** Given synthetic inputs, when real ingestion/restart finish, then accounting reconciles with durable API/narrative content and incomplete work cannot appear complete. Given independent expectations, when auditing stored payload, then fidelity discrepancies remain visible without proprietary output.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

No intent gap or irreversible operation. Sidecar avoids SQLite churn; report/2 replaces placeholders. Private execution remains NOT_RUN. Commit verified slices on this feature branch; parent owns final review. Escalated exec needed. Windows environment: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir (keep nonexistent). Record compatible routine decisions.

## Verification

Run focused tests, full Gradle check, Python contracts/oracle/audit tests. Audit executed matrix tests. Compare fresh Windows/Linux snapshot bytes/JSON. Parent reads full diff and runs review lenses. Record counts/skips/NOT_RUN; refresh Graft, check whitespace, finish clean.

### 2026-10-22 — Independent review pass

- verdicts: 14 findings — high 0, medium 13, low 0, false 1, maybe-false 0. Eleven unique patch entries; no intent gap, bad spec or deferred entry. Every patch is an internal correction/verification of demonstrated state, without expanding the public contract.
- findings:
  - `[false]` `[reject]` Blind: unprocessed records have zero emitted sections — sections are explicitly counted when a payload is assembled, not inferred from extracted records. CatalogStage calls sections only after successful assembly; unprocessed records/pages are separately failed and independent denominators remain null. The published counting rule intentionally makes no claim about unknown/unassembled sections.
  - `[medium]` `[patch]` Blind: conflicting source pages share a persistence flag — CoverageAccounting keys globally by canonical key, so a losing page can borrow another page's success. Keep source-specific association outcomes while deduplicating true same-source retries. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: narrative payload rejection becomes a storage failure — assembleNarrative is outside the local IllegalArgumentException handling used for APIs. Handle rejection per page so later narratives continue. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: audit accepts missing documentation parents — it reads section rows without checking the parent required by production read/enumeration. Check parent and section-count/association integrity with production-store mutations. Fixed in 5495bca; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: metadata column mutations evade audit — only ID/type/SDK columns are compared. Existing documentation.metadata already carries the canonical 19 metadata fields; bounded comparison against all stored knowledge columns and association invariants catches demonstrated product/locator/governance/kind corruption without adding inventory fields. Fixed in 5495bca; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: payload aggregate admission follows fetch — up to 257 blobs are materialized before charging their lengths; unexpected sections escape charging. Admit lengths, including unexpected sections, before blob fetch. Fixed in 5495bca; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: directory traversal is unbounded — only regular files consume admission. Add private visited-entry/depth guards and real traversal coverage. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: indeterminate file attributes silently omit input — isRegularFile maps errors to false after walker admission. Explicit attribute failures must abort admission or become visible failures. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: inventory schema/runtime disagree — schema allows reordered ordinals and 4096-character multibyte labels; runtime rejects them by index/byte length. Align accepted inputs and add shared specimens. Fixed in 5495bca; focused and final platform checks passed.
  - `[medium]` `[patch]` Blind: missing coverage bypasses fail-fast — restore failure is added directly to collected, outside stage failure policy. Abort FAIL_FAST before later stages. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Edge: narrative admission aborts later pages — shares blind narrative root cause; same local rejection repair. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Edge: resume coverage failure bypasses policy — shares blind fail-fast root cause; same policy repair. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Verification gap: production enumeration admission is untested — removing enumerate's admit call leaves direct Budget tests green. Exercise rejection through the actual traversal path. Fixed in 16c8c8c; focused and final platform checks passed.
  - `[medium]` `[patch]` Verification gap: audit structured decoder lacks evidence — all fixtures have absent tables, no list items and no links. Add genuine Java-written structured payload plus independent expectations and structural mutations. Fixed in 5495bca; focused and final platform checks passed.

## Build Result — 2026-10-22

Story 2.11 implemented on codex/story-2-11-ingestion-coverage. Report/2 now reconciles source-specific logical associations, disjoint pages and assembled sections; build-bound atomic accounting survives resume. Admission is bounded before reads/walk materialization, and the optional independent audit verifies actual structured bytes and usable metadata/parent associations. Storage schema/3, identity/1, content/1 and read-tools/2 stay unchanged.

Review: three independent lenses completed; 14 individual findings, 13 medium patch rows in 11 root-cause entries, one false/rejected finding with its refutation above. All patch entries resolved in 16c8c8c and 5495bca. Nothing deferred; no remaining findings. Parent read the complete baseline diff and both repair diffs and audited actual executed tests.

Final parent verification on repair commit 5495bca:
- Full Windows and native Ubuntu-24.04 Gradle check --rerun-tasks: PASS, all 46 tasks executed each. 269 discovered tests per platform, zero failures/errors; Windows has one pre-existing symlink skip, Linux none. Thirty-three matrix-relevant extraction/storage/admission/accounting methods ran and passed on each.
- Python audit suite: 22 tests passed on Windows and Linux, no skips. LUCIAD_AUDIT_STORE exported a fresh genuine Java-produced structured SQLite store per platform, so all four real-store tests executed, including all 19 metadata column mutations and table/list/link changes.
- The independent synthetic audit of each native Java export: PASS, one record/section each. Private mode without attestations returns NOT_RUN before input access; actual private execution remains NOT_RUN.
- Windows contract/schema/oracle checks: 127 passed; portable known answers agree with Java and Linux Python.
- Fresh complete Windows/Linux snapshots match byte-for-byte and as parsed JSON: 21,767 bytes, SHA-256 ca1a13accdece56e1189a57a624e097376bba7060c02b9f7d54aa3be885fb54c.
- Graft refreshed and whitespace checks passed. Raw final Java logs, snapshots, Linux XML archive and synthetic audit exports are in ignored app/build/story-2-11/.

### Executed matrix mapping

| Matrix row | Passing evidence |
| --- | --- |
| Mixed pages/units | reconcilesMixedPagesAndRetryWithoutInflation, conflictingPagesHaveIndependentLosingAssociations; report schema/semantic checks |
| Malformed/unsupported/oversized | oversizedAndUnsupportedPagesCannotBeComplete, narrativeCanonicalOverflowIsLocalAndLaterNarrativePersists, authoredDocFileHeadingCannotBeExcludedOrPromotedToApi |
| Atomic write/clash | realAtomicRollbackLeavesFailedAssociationsAndUnprocessedPagesExplicit, realIdentityClashCountsFailedAssociationAndPreservesOriginalContent; production DB mutation audit |
| Interruption/retry/resume | cleanResumeRestoresIdenticalCoverageAndCorruptionIsUnavailable, missingAndCorruptCompletedCoverageHonorFailFastBeforeLaterStages, realAtomicRollbackLeavesFailedAssociationsAndUnprocessedPagesExplicit |
| Source deletion/reopen | modernAndLegacyApiAndNarrativesSurviveDeletionExactly plus structuredReopenPagingScopesAndRetry and existing documentation roundtrip tests |
| Admission boundaries | exactAndAboveReadBoundaryAndStreamingDigest, exactAndAboveEnumerationFilePathAndAggregateBoundaries, realWalkRejectsFilesEmptyDirectoriesDepthAndAttributeFailures, existing exactSectionAndBlockCapsPagingAndEncodedAdmission |
| Missing private gates | test_private_missing_gates_touch_neither_inventory_nor_store and test_every_private_gate_is_required |
| Independent discrepancies | Python missing/changed note/block/member/narrative tests, all four JavaStoreAuditTests, pre-fetch byte admission and schema-alignment tests |

Known boundaries: independent expected denominators remain unknown unless supplied. The audit is finite and verifies the supplied independently attested inventory; it does not establish private-corpus completeness or answer-quality improvement. Technical ratification and other existing gates remain pending. No private/held-out input was accessed. Original-checkout edits were preserved. Story 2.10 was merged locally as authorized (f6d1109); Story 2.11 remains on its feature branch. Existing commits are preserved; final commit records remaining spec/tracker/evidence changes only.
