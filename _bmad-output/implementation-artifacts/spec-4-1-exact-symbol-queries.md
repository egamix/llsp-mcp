---
title: 'Story 4.1: Detect and prioritize exact-symbol queries'
type: feature
created: '2026-10-22'
status: done
route: dispatch
story_key: 4-1-detect-and-prioritize-exact-symbol-queries
baseline_commit: '896a77622bac22ca4823d99551fbb0332a0d0c8e'
review_loop_iteration: 0
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/_bmad-output/implementation-artifacts/epic-4-context.md
---

<frozen-after-approval reason="Existing Story 4.1 requirements; user directed BMad MVP implementation">

## Intent

Implement existing Story 4.1 as the first dependency of the planned Java MVP. Resolve exact API questions against one actual persisted catalog, preserving overload ambiguity and useful source evidence even when user target SDK differs. Follow the existing plan and read-tools/2 semantics; do not create another demo, store, transport or roadmap.

## Boundaries & Constraints

Always pin one existing SQLite source/build before queries and use actual persisted metadata plus the shared StandardBuildValidator. Open source read-only, retain a consistent snapshot, reject incompatible/missing/invalid data without creating/migrating it. Domain owns query/result types; catalog-sqlite owns JDBC; retrieval owns exact matching. Preserve module dependency direction. Fully qualified canonical identities are case-sensitive. Aliases never fabricate an exact selected overload. Source SDK/build and optional target SDK are separate; target mismatch never filters, rejects or warns automatically. Explicit source filter mismatch is UNKNOWN_SDK. Results/citations retain stable ID, actual source SDK/build, truthful locator and proof. Missing matches mean unknown within this source, never API absence everywhere. Query <=2048 UTF-8 bytes; page cap50; bounded candidate work; deterministic ordering. Reuse existing contracts without redefining JSON envelopes (5.7 owns serialization).

Never access private corpora, modify identity/schema contracts, build lexical ranking/MCP/lifecycle in this story, add SDK JAR requirements, mutate unrelated D: checkout changes, or claim pending technical sign-offs are approved. This implementation consumes existing contracts; approval records remain truthful.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Canonical identity | FQN type or full owner#member(qualified params) | Correct stable record/source citation, exact-in-source proof | No cross-owner/scope fallback |
| Aliases | Simple type, dotted member, erased-generic/canonical variants | Unique supported match or explicit candidate ambiguity; no selected overload from incomplete evidence | Respect includeAliases=false |
| Overloads | Owner/member without sufficient signature, competing simple types | All admitted candidates deterministically; AMBIGUOUS, no guessed exact selection | Truncation cannot hide ambiguity |
| Target context | target2023.1, source2023.5 or omitted target | Same useful source match, actual source citation; target carried separately | No mismatch warning/block |
| Source filter | Explicit unknown source/build | Typed UNKNOWN_SDK/BUILD_UNAVAILABLE scope failure | Never substitute another source silently |
| No match / malformed | Unknown symbol, empty/control/oversized UTF-8 query | Unknown-in-scope or invalid request | No fabricated coordinates or missing DB creation |
| Pin/reopen | Real ingested store, then source directory removed | Resolution still works; database bytes unchanged | Invalid schema/identity/runtime/build rejected |

</frozen-after-approval>

## Code Map

- `app/domain/.../CatalogStore.java:22-99`, `StoredCatalogRecord.java`, `CatalogRecord.java`, `SymbolCoordinate.java`: existing canonical identity/catalog semantics. Preserve these producers; add a small read-only symbol-source port and typed query/result only as needed.
- `app/catalog-sqlite/.../SqliteCatalogStore.java:148-227`: validationRecords and validatePersisted already validate persisted binding/schema/runtime/governance/documentation. Reuse within same package; no writable constructor for serving. `SqliteRelationshipStore` demonstrates mode=ro snapshot pinning.
- `app/domain/.../SourceLocator.java`, `BuildValidator.java`, `StandardBuildValidator.java`: truthful locators, shared validator and current finite catalog admission bounds.
- `app/retrieval/`: currently scaffolding, intended home of ExactSymbolResolver. Concrete JDBC stays in catalog-sqlite; no parser dependencies here.
- `docs/contracts/resolve_symbol.request.schema.json`, `resolve_symbol.response.schema.json`, `common.schema.json`: source/target/proof/ambiguity vocabulary; consume semantics now, envelopes in5.7.
- Existing `app/cli/.../pipeline/DocumentationRoundtripTest.java` and portability fixtures demonstrate actual importer/store setup, reusable for integration tests. Only synthetic fixtures.

## Tasks & Acceptance

- [x] `app/domain/src/main/java/com/luciadmcp/knowledge/domain/` -- add minimal immutable source binding and exact query/result contracts.
- [x] `app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/` -- implement validated read-only pinned source/catalog using existing helpers; expose bounded records for resolver without unbounded materialization.
- [x] `app/retrieval/src/main/java/com/luciadmcp/knowledge/retrieval/` -- implement canonical-first matching and explicit unique/ambiguous/unknown results, source filtering and target separation.
- [x] Corresponding module tests/build.gradle.kts -- wire JUnit where needed; test every matrix row with actual persisted catalog identity and reopen/no-mutation assertions. Include UTF-8 cap, same names in multiple packages, constructor/method overloads, query version context, and all unsupported-source outcomes.
- [x] `docs/contracts/` or story verification notes -- record implemented API and actual test evidence without claiming other stories complete.

Acceptance: Given admitted canonical queries, when resolved after import/reopen, then exact canonical fixtures all select the correct scoped identity. Given incomplete competing aliases, when resolved at limit1, then ambiguity remains explicit rather than becoming exact. Given mismatched target context, when retrieving from available source, then useful results remain unchanged with source provenance. Given incompatible/missing source, when opening, then it fails before mutation.

## Implementation Notes

Worktree C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp, branch codex/story-4-1-exact-symbol-queries. This starts from completed Story2.11; Python demo remains on its old branch and must not be copied. The user explicitly directed implementing the BMad MVP, avoiding repetitive approval loops; proceed with existing accepted story scope. No previous Epic4 story exists. C: mutations require exec_command require_escalated. Commit substantial cohesive verified changes with tests separately; no squash/push/merge. Do not modify frozen intent.

## Verification

Run focused domain/catalog/retrieval tests and checkDependencyDirection, then parent full Gradle check. Existing Windows env: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir (keep nonexistent). Refresh Graft after code changes. Report executed tests, commits and any remaining concern concisely.

## Spec Change Log

## Review Triage Log

- medium / patch: same-name methods encoded method:Widget were excluded from constructor dotted aliases, causing false EXACT. Alias uses persisted simpleName; canonical identities stay distinct; collision regression passes at pageSize1. Fixed542119c; nothing deferred.

Parent full Windows Gradle check passed after repair (48tasks). Source deletion/reopen and all matrix scenarios passed.
