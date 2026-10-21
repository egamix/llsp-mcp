---
title: 'Story 2.7: Bounded, cycle-safe relationship traversal'
type: 'feature'
created: '2026-10-21'
status: 'done'
route: 'dispatch'
baseline_commit: '4b5e7678546f687b0cc16f6b57fe000e833a1189'
story_key: '2-7-bounded-cycle-safe-relationship-traversal'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The store enumerates edges but offers no bounded, cycle-safe traversal.

**Approach:** Add indexed direct/recursive traversal through a domain port, returning deterministic pages with scope, limits, cutoffs and safe continuation.

## Boundaries & Constraints

**Always:**
- Bind trusted immutable build identity and one SDK before resolving the seed key; never infer build identity from filenames. Preserve existing constructors/audit reads.
- Default DIRECT/OUT; support IN/BOTH and type filters. Direct is one hop; recursive uses requested depth. Seed starts visited and is separate metadata. Expand/emit nodes once; emit distinct typed edges, including cycle/cross edges between admitted nodes.
- Use BFS and stable edge-ID order. Charge adjacency rows before deduplication, including repeats. Bound SQL/memory/serialization before materialization; never traverse via `allEdges()` or `edges(scope)`.
- Admit a new node and its connecting edge atomically; emitted edges reference admitted nodes or seed. Bound input keys/build/version to 2 KiB each; reject nonpositive limits.
- Centralize these provisional defaults/caps; reject invalid requests. Visited nodes include seed; result items count nodes plus edges.

| Limit | Default | Cap |
|---|---:|---:|
| Recursive depth | 3 | 8 |
| Visited nodes | 1,000 | 10,000 |
| Examined adjacency rows | 5,000 | 50,000 |
| Result graph items | 1,000 | 10,000 |
| Page items | 10 | 50 |
| Execution time | 1 second | 5 seconds |
| SQLite VM steps | 1,000,000 | 10,000,000 |
| Encoded response | 1 MiB | 1 MiB |

- Report applied limits, query scope, visited/scanned/returned counts, cutoff and count relation (exact/lower-bound/unknown). Exhaustion concerns this bounded query, never corpus completeness.
- Authenticate continuation against build/SDK, normalized query/filters/limits, contract version and offset using runtime signing material. Cap token at 4 KiB. Replay consumes budgets; continuation pages only the fixed bounded window. Terminal work cutoff needs an explicit new query. Timeout/cancellation fails without a token. Test execution limits without sleeps.
- Additions remain provisional; Story 2.8 owns ratification, not this story.

**Never:** Changed identities, guessed endpoints, mixed scope, MCP handlers, network, recipe traversal or exhaustive-coverage claims. Synthetic fixtures only.

## I/O & Edge-Case Matrix

| Input/state | Expected behavior |
|---|---|
| Direct query over a chain | Immediate neighbors only |
| Self-loop, cycle or diamond | Terminates; unique nodes/edges; stable order |
| Known isolated seed / missing scoped seed | Empty scoped result / explicit seed-not-found outcome |
| Same key in another SDK/build | No leakage; mismatched continuation rejected |
| Exact limit / limit plus one | Honest completion/cutoff; no overrun |
| High-degree node or long escaped text | Work/payload bound enforced before full materialization |
| Multiple pages | Union equals bounded unpaged result, without duplicates |
| Tampered/stale/query-mismatched token | Explicit invalid-continuation outcome |
| Execution/SQL-step exhaustion | Explicit bounded-execution failure; no token |

</frozen-after-approval>

## Code Map

Paths below are under `app/`, using existing module/package roots.

- `domain/.../RelationshipStore.java:27–100`: additive port; retain test-double defaults and stable edge contracts.
- `catalog-sqlite/.../SqliteRelationshipStore.java:66–127,224–232,345–377`: indexes/adjacency; retain scoped endpoint joins/seed lookup.
- `catalog-sqlite/.../SqliteRelationshipStoreTest.java:56–188`: reuse synthetic fixtures/order/reopen tests.

## Tasks & Acceptance

**Execution:**
- [x] `domain/.../{TraversalRequest,TraversalLimits,TraversalResult,RelationshipStore}.java` plus domain tests — validated additive query/result contracts and boundary tests.
- [x] `catalog-sqlite/.../{SqliteRelationshipStore,BoundedRelationshipTraversal}.java` — indexed keyset adjacency, pinned-build handle, BFS, budgets and cancellable SQL.
- [x] `catalog-sqlite/.../TraversalCursorCodec.java` — bounded authenticated tokens/replay; injectable runtime key; reject incompatible scopes/queries/limits.
- [x] `catalog-sqlite/src/test/java/.../RelationshipTraversalTest.java` — matrix, directions/filters, reversed inserts, invalid inputs, reopen and cancellation.
- [x] This spec and `sprint-status.yaml` — verification/review evidence. Separate commits for contracts, traversal, continuation and completion, with related tests alongside.

**Acceptance Criteria:**
- Given cycles, when traversal runs, then scoped deterministic results obey depth and unique-node/distinct-edge semantics.
- Given each resource boundary, when work reaches it, then work stays bounded and cutoff reporting is correct.
- Given immutable query/build, when pages resume, then their union matches the bounded window; incompatible tokens fail.

## Implementation Notes

- Approved on 2026-10-21. Preserve pre-existing README/docs edits. Commit cohesive implementation units with their tests on `codex/story-2-7-bounded-traversal`, as required by `AGENTS.md`.
- Technical validation: configurable response budgets reserve at least 64 KiB for bounded metadata/cursors; smaller budgets are rejected. The approved default and hard cap remain 1 MiB. Boundary tests cover the validation floor.
- Implemented validated domain contracts, indexed BFS, strict SQL VM/deadline/cancellation guards, canonical bounded JSON, and authenticated deterministic replay. The coordinator supplies build identity and signing material; the serving constructor uses SQLite read-only mode and pins a read snapshot without changing the sealed file.
- Verification: `:domain:test :catalog-sqlite:test :cli:test checkDependencyDirection check` passed after the review fix (46 tasks, 17 seconds). XML audit: 205 tests, zero failures/errors, two pre-existing skips; all 11 traversal matrix/regression tests ran without skips. No performance or proprietary-corpus validation is claimed.
- Matrix audit: chain/directions/filters/SDK isolation/missing and isolated seeds — `chainDirectionsTypesIsolatedAndMissing`; cycle/diamond/self-loop/order/reopen — `cyclesDiamondSelfLoopStableAcrossInsertionAndReopen` and `bothChargesDuplicateRowsAndEmitsEdgesOnce`; limits/atomic admission — `depthAndEveryWorkBoundaryAreHonestAndAtomic` and `TraversalContractTest.defaultsAndBoundaries`; high degree/escaped payload — `longEscapedStoredKeysAndHighDegreeStayBounded` and `adjacencyPayloadGuardsReadOnlyColumnByteMetadata`; page union/replay — `continuationPagesOnlyTheFixedWindowAndReplaysBudgets`; authentication/scope/version/query mismatch — `continuationAuthenticatesEveryQueryScopeAndRuntimeKey` and `codecOffsetsAndNormalizedFiltersAreAuthenticated`; execution/SQL cancellation — `cancellationAndDeadlineNeedNoSleeps` plus the SQL-step boundary assertion.
- Runtime-only Windows verification used Story 2.6's documented Gradle cache, trust-store and nonexistent Unix-domain socket-directory workaround. Product configuration was not changed.
- Separate commits retained: approved plan, domain contracts, indexed traversal, continuation/read-only scope tests, metadata-only payload guard fix, then completion evidence. Pending README/docs edits were preserved outside this story.
- Contracts, numeric limits and provisional indexes remain subject to Story 2.8 ratification. No review findings deferred. Graft refreshed after implementation and the review fix.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Route and evidence |
|---|---|---|
| Payload guard materializes oversized TEXT inside SQLite | high | patch — `length(CAST(canonical_key AS BLOB))` emits an unrestricted SQLite Column before Cast/length; proportional native allocation occurs before the response guard. Replace with metadata-based octet length and verify the guard's SQL execution does not materialize oversized TEXT. Existing output-size tests alone do not prove this. |

Resolution: fixed in `82bf2e1` using `octet_length` for both endpoint guards. `adjacencyPayloadGuardsReadOnlyColumnByteMetadata` explains the production SQL factory and verifies metadata-only Column flags (`0xc0`) with no Cast; the focused tests and subsequent complete check passed. The reviewed BOTH-direction tight-budget concern was conservative truncation, not a filed finding.

## Design Notes

Incremental adjacency exposes global visited sets/work. A final SQL LIMIT over unbounded CTE work is insufficient. Replay cannot bypass traversal limits.

## Verification

From `app/`: `./gradlew.bat :domain:test :catalog-sqlite:test :cli:test checkDependencyDirection`, then `./gradlew.bat check`. Matrix and identity/evidence regressions must pass. Reuse Story 2.6's runtime-only Windows workaround if required.
