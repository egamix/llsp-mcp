---
title: 'Story 2.11: Account for ingestion coverage and fidelity'
type: 'feature'
created: '2026-10-22'
status: 'ready-for-dev'
route: 'dispatch'
review_loop_iteration: 0
story_key: '2-11-account-for-ingestion-coverage-and-fidelity'
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/AGENTS.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Expose what ingestion preserved, skipped or lost, so successful processing cannot conceal missing documentation. Close Story 2.10's production ingestion/reopen path with reconciled reporting, bounded admission and an optional independent fidelity audit before lexical indexing consumes it.

## Boundaries & Constraints

**Always:** Count pages, logical records and payload sections separately. Every discovered page has one documented EXCLUDED/COMPLETE/PARTIAL/FAILED disposition. Exclusion requires positive non-content classification; unsupported/unknown/malformed pages are visible, never complete. INSERTED and identical retries each count one persisted logical association. Preserve structured content, stable IDs, scope, atomic rollback and useful cross-version guidance. Unknown independent denominators stay absent. Version changed report contracts and retain pending technical ratification honestly.

**Never:** Access private corpus or held-out gold during this build; fabricate approvals, inventory, counts or completeness; dump source bodies in audit diagnostics; change storage schema/identity/content/read-tools contracts; implement search, MCP, lifecycle or retained-source HTTP serving. Preserve unrelated original-checkout edits and existing story commits. No push or merge of Story 2.11.

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

Paths below are under app/ unless stated otherwise.

- ingest-javadoc/JavadocDiscovery:83-192 parses unrestricted files and materializes sorted walks/index text; JavadocCatalogSource:31-46,118-125 loses full discovery inventory. Preserve typed discovery/producer outcomes.
- cli/pipeline/CatalogStage:64-153 writes atomic documented associations but outer catch can stop remaining pages; return per-page outcomes and logical persistence counts, with unprocessed pages explicit.
- cli/pipeline/IngestPipelineRunner:198-200,252-307 skips checkpointed catalog and constructs reports; BuildContext:137-166 materializes all paths/file bytes before parser guards. Restore validated accounting on resume; bound enumeration and stream original-byte hashing without changing digests.
- cli/pipeline/IngestionReport:107-159 emits report/1 null coverage; domain/DocumentationStore:37-39 Coverage is unused and lacks partial. Use a coherent reporting model without changing the storage port contract.
- ingest-javadoc/JavadocFieldExtractor:69-87 and JavadocCatalogSource:52-79 stat then readAllBytes; replace with shared capped readers. Reuse OwnedDocumentationExtractor and DocumentationPayload work/shape caps.
- StructuredDocumentationRoundtripTest, DocumentationRoundtripTest, DocumentationStoreTest and ApplicationPortabilitySnapshotTest provide real pipeline, source removal, retry/rollback and platform oracles.
- docs/contracts/ingestion-report.schema.json, example, README and tools/contracts contain schema verification/oracles; DocumentationCodec defines actual persisted section encoding for the audit.

## Tasks & Acceptance

- [ ] ingest-javadoc discovery/source/results plus cli/CatalogStage — retain complete inventory and typed reason codes, derive disjoint page outcomes after writes and separate emitted/persisted/failed association/section totals; keep compatibility constructors where callers require them.
- [ ] cli reporting/context/runner and new build-scoped coverage snapshot — write deterministic versioned accounting atomically before catalog checkpointing; bind build/SDK/recipe/runtime and validate bounded restore. Retry recomputes instead of increments. Unavailable accounting cannot imply complete. Use report/2 with explicit coverage availability, counting rules, sorted reasons/outcomes and unknown independent expectations; update its schema/examples/verifier and affected version fixtures.
- [ ] ingest-javadoc bounded input utility and cli/BuildContext — capped limit+1 reads for HTML/search indexes (8 MiB); streaming SHA-256; finite enumeration (100,000 files/root, 4,096 UTF-8 bytes/path, 16 MiB aggregate path bytes/root). Document these implementation admission limits and preserve existing payload/storage caps. Abort beyond enumerated universe honestly rather than inventing unseen counts.
- [ ] tools/contracts/audit_ingestion.py and synthetic tests — optional bounded read-only audit of externally supplied build/SDK-bound inventory with independent expected record/section/block values or hashes. Validate/recompute from actual payload bytes, not trusted hash columns alone. Private mode first checks explicit 10.1/B2/B6, corpus-owner/development-only/held-out-exclusion/inventory attestations; missing gates produce NOT_RUN/BLOCKED before opening inventory/store. Emit aggregate categories/counts only, including sanitized errors.
- [ ] module tests, contract docs and verification evidence — exercise every matrix row through production storage and real failures, boundary pairs, source removal and restart/retry; mutation-like audit specimens must catch lost notes/blocks/members/narratives. Update portability snapshots and Graft.

**Acceptance:** Given admitted synthetic inputs, when real ingestion and restart finish, then truthful accounting reconciles with durable API/narrative content and incomplete work cannot appear complete. Given independently supplied expectations, when the optional audit compares actual stored payload, then fidelity discrepancies remain visible without proprietary output.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

One goal: trustworthy ingestion accounting. No unresolved user intent gap or irreversible operation. A build-scoped sidecar avoids SQLite schema churn; report/2 explicitly replaces the old deferred placeholders. Independent expected denominators are never inferred from producer output. User authorized the review/merge/next-story sequence; planning follows the existing Story 2.11 intent. Synthetic work proceeds independently of private access; no private audit will be executed.

Work exclusively on codex/story-2-11-ingestion-coverage in this managed worktree. Commit each verified cohesive slice with its tests; parent owns final review/tracking. Use escalated exec here as needed. Windows environment: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir (keep nonexistent). If a planned detail conflicts with repository evidence, choose the smallest compatible implementation and record it; never alter frozen intent.

## Verification

Focused tests per slice, then full Gradle check, Python contract verifier/oracle/audit synthetic tests. Audit every matrix row against tests that actually ran. Fresh content-inclusive Windows/Linux snapshots must match as bytes/JSON. Parent independently reads the complete diff and runs the configured review lenses. Record actual counts/skips and private audit NOT_RUN. Finish with whitespace checks, Graft refresh and a clean feature branch.
