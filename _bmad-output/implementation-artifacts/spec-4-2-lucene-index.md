---
title: 'Story 4.2: Index canonical and lexical fields for BM25'
type: feature
created: '2026-10-22'
status: in-progress
route: dispatch
story_key: 4-2-index-canonical-and-lexical-fields-for-bm25
baseline_commit: 'ecd00e95051c9b28da6739800ede112a994c6102'
review_loop_iteration: 0
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/_bmad-output/implementation-artifacts/epic-4-context.md
---

<frozen-after-approval reason="Existing Story 4.2 Javadoc source-content handoff; user requested BMad MVP">

## Intent

Build the persistent Java Lucene/BM25 index from already-saved SQLite documentation, completing the Javadoc and narrative slice of existing Story4.2. The result must support prose-only queries and exact/prefix/camel-case symbol discovery after restart and removal of original HTML. This is the next planned dependency of the real Java retrieval/MCP MVP; no separate demo.

## Boundaries & Constraints

Always use pinned validated SQLite source and stable record IDs, domain-owned immutable ports/DTOs, the pinned Lucene10.5.1 dependencies in search-lucene, and bounded ID/section batches. SQLite remains authoritative for content; Lucene stores derived search fields, excerpts/highlight information and metadata only. Index code/signatures, descriptions, parameters, returns, exceptions, notes, table/list text and package/overview narratives. Preserve labels/section IDs for later cited enrichment. Index canonical/normalized exact, symbol-prefix and camel-case tokens separately from analyzed prose. Filter by actual source SDK, record type, knowledge kind, package/element kind, deprecation and governance/applicability fields available in stored metadata. Do not invent quality labels for absent Epic3 producers.

Bind Lucene commits to actual source build/SDK/capability/runtime plus an explicit lexical recipe/format identifier; reopening against wrong/stale metadata must fail clearly. No model is required. One logical index row per stable record ID; deterministic result tie-break by record ID. Query defaults/caps match existing contracts: UTF-8 query2048 bytes, page10/max50, finite candidate/work/time, explicit lower-bound/truncated outcomes. Empty filter-only browse must be deterministic. User target SDK does not silently become a source filter or warning. Exact identity proof remains the existing4.1 resolver's responsibility; lexical relevance never proves identity.

Never reparse original HTML for search, create another database/search framework, modify identity/schema approvals, build MCP/activation/rollback in this story, access private corpora, or claim Epic3 support/whole-roadmap completion. Retain unchanged source DB bytes during indexing/serving. Failed index generation must not expose a partial index as complete.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Build/reopen | Java-imported API and narratives | Durable FS Lucene index; stable IDs and actual source binding survive restart | Missing/invalid source/index or mismatched commit binding fails explicitly |
| Discovery | Prose-only query; partial/camel-case API name | Relevant records retrieved with bounded excerpts/highlight spans and IDs | No fabricated exact proof |
| Filters/browse | Available-source metadata filter or empty query | Only matching rows; deterministic order | Target context alone never filters |
| Retained content | Notes/table/list/code and symbol-less narrative prose | Their text is searchable; authoritative structured payload unchanged | Unsupported producer/metadata not silently invented |
| Limits | Long query, excess page/candidate/time, ties | Input rejection or truthful bounded results; stable tie ordering | No unbounded hit count/materialization |
| Pipeline | ingest, completed resume, retry/index failure | LEXICAL_INDEX executes and is checkpointed only after committed index; resume retains equivalent logical results | Unsupported vectors/activation remain explicitly skipped |

</frozen-after-approval>

## Code Map

- `app/domain/.../LexicalIndex.java` is currently empty. Define the minimal engine-independent query/hit/page/binding port used by retrieval later; keep Lucene types out.
- Story4.1 added `SymbolSource`, `SymbolSourceBinding`, `ExactSymbolResolver`, `SqliteSymbolSource`; reuse source binding and validated read-only snapshot. Prior code map/spec: spec-4-1-exact-symbol-queries.md. Canonical lookup and constructor/method ambiguity must remain correct.
- `app/catalog-sqlite/.../SqliteDocumentationStore.java:97-145` has bounded section reads/enumeration, and lines151-199 canonical19-field KnowledgeRecord metadata decode/validation. Extract a reusable metadata decode helper if needed; do not duplicate formats. DocumentationCodec decodes actual structured sections. Add a domain documentation-read source port and implement it in the existing pinned source, preserving one snapshot for metadata/content.
- `app/domain/.../KnowledgeRecord.java`, `DocumentationPayload.java`, `DocumentationStore.java`: authoritative metadata/sections; use them for projections and later record fetch.
- `app/search-lucene/build.gradle.kts`, `app/gradle/libs.versions.toml`: wire existing libs.lucene.core and libs.lucene.analysis.common/queryparser as needed, no new version guesses.
- `app/cli/.../pipeline/IngestPipelineRunner.java:119-144` currently real catalog/relationships/validation, unsupported LEXICAL_INDEX. Add a LexicalIndexStage with coordinator-owned path under BuildContext.stagingDir; do not claim activation. Existing source manifest remains authoritative; bind derived index commit to it without modifying stored source metadata.

## Tasks & Acceptance

- [ ] `app/domain/...` -- lexical port/immutable DTOs and minimal documentation-read source port.
- [ ] `app/catalog-sqlite/.../SqliteSymbolSource.java`, `SqliteDocumentationStore.java` -- bounded KnowledgeRecord/section/ID reads from the existing validated snapshot, reusing codec/metadata helpers.
- [ ] `app/search-lucene/...`, module build -- persistent BM25 adapter, explicit fields/filters, bounded search/browse/highlights, stable ordering and commit binding validation; rollback failed writes.
- [ ] `app/cli/.../pipeline/LexicalIndexStage.java`, `BuildContext.java`, `IngestPipelineRunner.java` -- execute lexical stage in existing pipeline and preserve retry/resume semantics and unsupported-stage reporting.
- [ ] Corresponding module tests -- actual importer-to-index roundtrip, prose/notes/structured/narrative discovery, partial/camel symbols, filters/browse, reopen/source deletion, mismatch/failed build, bounds/ties and unchanged DB bytes. Update existing tests affected by newly implemented lexical stage truthfully.
- [ ] Story verification note -- actual executed evidence and remaining Epic3 integrations, no fake technical approval.

Acceptance: Given a Javadoc-only imported source, when queried with description words after HTML deletion/index reopen, then the expected stable record appears with useful context. Given a different source/build or incompatible index, when opening, then reads fail explicitly. Given interrupted/failed indexing or resumed ingest, then only committed matching indices count as completed. Given exact4.1 fixtures, when existing tests run, then identities and ambiguity remain unchanged.

## Implementation Notes

User authorized this existing story sequence for an MVP without approval loops. Worktree C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp, branch codex/story-4-2-lucene-index. No Python demo code exists on this branch; do not introduce it. C: writes/tests require exec_command require_escalated. Preserve cohesive commits with tests, no squash/push/merge. No additional broad investigation/review agents. Use actual source code and official pinned library APIs. Technical approvals remain pending where recorded.

## Verification

Run focused domain/catalog/search-lucene/CLI affected tests plus checkDependencyDirection. Parent runs full Gradle check and focused code review. Windows env: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir. Refresh Graft after code changes.

## Spec Change Log

## Review Triage Log
