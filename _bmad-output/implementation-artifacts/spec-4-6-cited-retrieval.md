---
title: 'Story 4.6: Assemble bounded cited lexical results'
type: feature
created: '2026-10-22'
status: done
route: dispatch
story_key: 4-6-assemble-bounded-cited-lexical-results
baseline_commit: '6c986069fea679009bf00c265857375489da542a'
review_loop_iteration: 0
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/_bmad-output/implementation-artifacts/epic-4-context.md
---

<frozen-after-approval reason="Existing Story4.6 Javadoc retrieval handoff; user requested BMad MVP">

## Intent

Implement shared Java retrieval for the current Javadoc/narrative corpus: join Story4.1 exact resolution and4.2 Lucene discovery to authoritative SQLite content, returning useful bounded snippets, truthful citations, deterministic paging and optional bounded relationship context. This is existing Story4.6 in the requested MVP path, not another demo or replacement plan. MCP envelopes/handlers remain existing Epic5 work.

## Boundaries & Constraints

Always depend on domain ports, keeping JDBC/Lucene/parser types out of retrieval. Use one validated source binding and matching lexical index; reject mismatched sources/builds/runtime before query work. Exact source identity precedes lexical candidates; lexical scores never fabricate exact proof or target-version compatibility. Return stored KnowledgeRecord metadata with actual source SDK/build, record/section IDs and typed source locator for every snippet. Verify snippets/section associations against authoritative saved content, preserving code/notes/table/list text and symbol-less narrative identity. Retain governance/applicability and exclude candidates/unapproved records. Optional target context alone never filters or emits mismatch notices.

Enforce per-request candidates/work/time/page/payload limits across exact, lexical, content and graph work. Defaults1000/5000/1000ms/page10/1MiB; caps10000/50000/5000ms/page50. Charge before materialization. Deduplicate stable IDs and order exact-first, then declared score, then stable ID. Browse orders by stable ID. Count exact only when proven; otherwise lower-bound/unknown. Distinguish no match, insufficient evidence, missing capability/index, source/build mismatch and filtered content. Truncation must remain explicit. Continuations must be authenticated, bounded and bind tool/query/filters/limits/target/source/build/contract+identity versions; reject stale/tampered/rebound tokens before content or graph reads. Pagination stays within an explicit candidate window and never claims complete-corpus enumeration when exhausted.

Reuse existing2.7 traversal for an optional small relationship-context operation, sharing source/build and remaining budgets; never invent an edge or imply graph completeness. Keep primary retrieval and related context distinguishable. Existing search_knowledge wire schema only exposes primary hits; do not modify frozen JSON contracts to add fields. A backend related-context API can be consumed separately by later5.2 integration.

Never implement transport, activation, vectors, new databases or a UI here. Do not access more private corpora or claim measured answer improvement. No technical approvals are invented; broader Epic3 integrations remain future.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Exact plus lexical | Canonical symbol with overlapping lexical hits | Exact item first, deduplicated stable IDs and honest per-item proof | Incomplete aliases never select guessed overload |
| Prose/narrative | Description-only query, target differs | Relevant saved snippet with actual record/section source citation and metadata | No target mismatch block/warning |
| Paging/browse | More than50 items, repeated tokens | Stable nonoverlapping pages within candidate window; deterministic browse | Stale/query/source/limit/target tampering rejected before content work |
| Scope/filter | Knowledge-kind/source/build filters, no matches | Scoped results or explicit empty reason | Missing index/source and unknown source filter distinguished |
| Budget/content | Small candidate/work/time/response cap, large section | Finite work, truthful truncation/count; no silent content slicing as complete | Typed work/deadline/payload errors or explicitly partial result |
| Source integrity | Bad/missing content ID/section or mismatched index | No uncited or wrongly-associated snippet | Explicit invalid-source/content failure |
| Relationships | Requested context on persisted typed graph, cycle/bound | Existing bounded traversal returns source-bound evidence within budget | No traversal for ordinary search; no graph completeness claim |

</frozen-after-approval>

## Code Map

- Prior specs `spec-4-1-exact-symbol-queries.md`, `spec-4-2-lucene-index.md` are done. ExactSymbolResolver, SymbolSource and source binding provide exact/ambiguous/unknown proof. DocumentationSource provides bounded IDs, KnowledgeRecord and sections; SqliteSymbolSource implements both within one read-only snapshot.
- `app/domain/.../LexicalIndex.java`: source/runtime/recipe binding, Query and bounded Page/Hit with section ID/excerpt/highlights. Add a bounded offset/search-after facility if needed for paging, preserving current constructors/callers. `LuceneLexicalIndex` currently retains top min(pageSize,candidateLimit), scans matching work with stable score/ID tie-break; extend bounded heap/window rather than caching unbounded results or exposing Lucene doc IDs.
- `app/search-lucene/.../LuceneLexicalIndex.java`: blockText currently projects authoritative block/text/table/list/link labels. Move a small reusable projection helper into domain if necessary so retrieval verifies the same projection without duplicating formats; keep output/recipe unchanged.
- `app/domain/.../KnowledgeRecord.java`, `SourceLocator.java`, `DocumentationPayload.java`: authoritative metadata/content/citation inputs. `docs/contracts/common.schema.json` describes eventual wire item/citation/limits vocabulary; no serializer required here.
- `app/domain/.../RelationshipStore.java`, `TraversalRequest.java`, `TraversalLimits.java`, `TraversalResult.java`; `SqliteRelationshipStore` pinned serving constructor: reuse existing bounded traversal behind domain port for optional context. Do not reimplement traversal.
- `app/retrieval/`: application service and internal cursor/budget helper, minimal immutable DTOs as appropriate. Keep future CLI/MCP consumption straightforward.

## Tasks & Acceptance

- [x] `app/retrieval/...` -- shared service for cited exact/lexical/browse results, source checks, governance/applicability, aggregate budgets and typed outcomes.
- [x] `app/domain/...`, `app/search-lucene/...` -- minimal bounded paging support and shared text projection where necessary; preserve all4.1/4.2 behaviour/tests.
- [x] `app/retrieval/...` -- compact authenticated cursor binding; deterministic pages and bounded optional relationship context using existing domain traversal.
- [x] Corresponding module tests -- actual stored structured content and index; correct source/section associations, exact-before-lexical dedup, source/target filters, >50-result paging without overlap, cursor rebinding rejection before work, budget exhaustion, missing/mismatched content, governed exclusions and persisted bounded graph context.
- [x] Story verification note -- executed evidence and precise Javadoc slice boundary; no completion claim for future content producers or Epic5.

Acceptance: Given an ingested source reopened without its HTML, when searched by prose, then source-faithful cited snippets are usable. Given exact and lexical overlap, when searched, then exact identity is first with no duplicates. Given matching next-page request, then ordering is stable and pages do not overlap; changed cursor binding fails before source reads. Given any configured budget, then enrichment/context cannot exceed it while reporting truthful coverage/truncation.

## Implementation Notes

Worktree C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp, branch codex/story-4-6-cited-retrieval. User explicitly requested BMad plan-aligned MVP without repetitive approval loops. Write/test in C: via exec_command require_escalated. Commit cohesive substantial changes/tests separately, no squash/push/merge. Do not change the frozen intent or original D: tracked work. No additional broad investigation or review agents.

## Verification

Focused domain/search/retrieval/CLI regressions and checkDependencyDirection; parent full Gradle check and one focused review. Existing Windows env: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir. Graft refresh after changes. Do not commit proprietary data; parent has authorized shape smoke data only under ignored app/build.

Implementation evidence: `story-4-6-verification.md`. Parent full Gradle check and focused review remain workflow-level verification.

## Spec Change Log

## Review Triage Log

- medium / patch — Full locator equality rejects legitimate anchorless member records because an explanatory absence reason differs from reconstructed catalog evidence. Compare identity/location fields; regression required.
- medium / patch — matches().size() is page-bounded and undercounts ambiguous exact candidates. Charge candidatesMatched() before lexical admission; regression required.


Parent full Gradle check passed after both review repairs (50 tasks). Authorized local-shape smoke passed: exact ILcdShape and cited contains2D / point inside shape / bounds rectangle retrieval. Both medium review findings fixed in 54b6d7f; none deferred.
