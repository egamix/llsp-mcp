---
title: 'Javadoc MVP first MCP connection — existing Epic 5/6 slice'
type: feature
created: '2026-10-22'
status: in-progress
route: dispatch
story_key: ''
baseline_commit: '420b4fca68209264ef4b1149703774e32d6b9169'
review_loop_iteration: 0
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/_bmad-output/implementation-artifacts/epic-5-context.md
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/_bmad-output/implementation-artifacts/epic-6-context.md
---

<frozen-after-approval reason="User authorized usable MVP within existing BMad plan; no repetitive approval loops">

## Intent

Let an MCP client search the user's ingested Javadoc, resolve symbols, and fetch saved documentation through the real Java application. Deliver the existing 5.7 envelope, Javadoc 5.1/5.3 handlers, 5.5/5.6 connection and minimum 6.1/6.2/6.5 lifecycle needed for this first connection. One usable connection is the goal; preserve the broader stories and pending acceptance.

## Boundaries & Constraints

Always reuse SQLite/Lucene, CitedRetrieval, ExactSymbolResolver and StandardBuildValidator through domain ports. Implement only search_knowledge, resolve_symbol and get_record now, following their existing read-tools/2 JSON schemas exactly. Expose no placeholders. Preserve source/target separation, exact/ambiguous proof, authenticated scope-bound cursors, stored structured content, actual citations, governance, finite budgets, honest partial ingestion coverage and the final serialized 1MiB cap. Keep every admitted batch verdict visible. Unknown source/build and invalid requests use typed envelopes; no automatic target-mismatch warning.

Use application.yaml as sole operator runtime configuration: index root, source default, HTTP loopback port (default8766), optional stdio. Configuration/paths/admin operations never enter agent schemas. Use pinned official MCP Java SDK2.0.1 with actual Streamable HTTP /mcp, standard embedded servlet container, optional stdio. Logs go to stderr. Bound concurrent tool admission and request bodies; validate HTTP Host/Origin. No custom MCP protocol implementation.

CLI publishes immutable root/sdk/buildId bundles from completed staging, including SQLite, Lucene, manifest and report. Serialize production ingestion/publication writers. One shared admission path validates metadata/schema/profile/content/index bindings and bundle integrity for validate, activate, startup and reload. CLI alone atomically replaces a versioned active pointer after successful publication; unsupported atomic replacement fails safely. Staging is never served. Each request leases one validated handle; replacement is validated before publication, invalid replacement retains current service, cold invalid startup fails clearly. Keep old builds; cleanup/rollback/backup remain later. Missing vectors/models are healthy for lexical. Partial extraction remains explicit, not automatically invalid.

Never build another runtime, database, browser UI, embedding system or retained-original-doc endpoint. No proprietary files committed; use synthetic tests and authorized shape smoke only. Do not claim all six tools, full producer coverage, production release or technical ratification.

## I/O & Edge-Case Matrix

| Scenario | Input/state | Expected behavior | Failure |
|---|---|---|---|
| Real connection | Published Java import; MCP initialize/list/call | Three tools; cited search, exact/ambiguous resolution, saved fetch | Typed errors, no fixture bypass |
| Source/target | Different target; explicit unavailable source/build | Useful actual-source evidence; restrictive filters respected | No implicit target filtering |
| Limits/paging | >50 hits/sections, large/batched content, tampered cursor | Stable bounded pages, exact hashes/blocks, every batch verdict | Explicit truncation/size/work/deadline/cursor outcome |
| Lifecycle | Invalid/torn candidate, competing writers, replacement during request | No staging exposure; old lease survives; same validation reasons | Invalid cold start fails; invalid reload keeps good handle |
| Transport/config | Malformed YAML/JSON, foreign Origin/Host, overload | Bounded rejection; stdout protocol-only in stdio | No source bodies/host paths in errors |

</frozen-after-approval>

## Code Map

- app/retrieval/.../CitedRetrieval.java and ExactSymbolResolver.java: completed4.1/4.2/4.6 services; preserve tests.
- app/domain/.../DocumentationSource.java and app/catalog-sqlite/.../SqliteSymbolSource.java: pinned read-only metadata/sections. Add minimal bounded SectionPage/hash port using existing SqliteDocumentationStore.readSections/read and DocumentationCodec, never duplicate formats or open writable serving stores.
- app/cli/.../CliMain.java, IngestCommand.java, pipeline/IngestPipelineRunner.java: composition root, existing completed staging/report/checkpoints; wire serve/validate/activate/query diagnostics here. Existing ingest may remain explicit pre-publication; do not report activation that did not happen.
- app/domain/.../IndexManifest.java; docs/contracts/manifest.schema.json, ingestion-report.schema.json and three request/response schemas/common.schema.json: canonical formats. Reuse schemas as packaged resources and bundle references for tools; no competing hand-authored wire contract.
- app/mcp and app/config are skeleton modules. cli wires actual stores; mcp depends only domain/retrieval. libs.versions.toml pins MCP2.0.1. Official SDK servlet requires Jakarta6.1; Jetty12.1 supported EE11 or compatible embedded container. Safe YAML parser required.

## Tasks & Acceptance

- [x] app/config, app/cli, build scripts — validated configuration, immutable publication/admission/leases, CLI commands and runnable distribution; tests for lifecycle matrix.
- [x] app/domain, app/catalog-sqlite, app/retrieval, app/mcp — bounded fetch port, shared read-tools/2 envelopes/handlers and schema-conformant tests including budgets/cursors/partial coverage.
- [x] app/mcp, app/cli — SDK transports and real protocol integration test; shared query diagnostics equal logical tool results.
- [ ] docs, application.example.yaml, tracker/verification — short launch/connection instructions with actual tool names; story-attributed commits and accurate partial story statuses.

Given a synthetic Javadoc import whose original files are removed, when published and queried by an SDK client, then search/resolve/fetch return persisted cited content. Given interrupted or incompatible publication, then no invalid build is served. Given concurrent replacement, then each request retains one source/build throughout.

## Implementation Notes

Work only in C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp on codex/mvp-javadoc-mcp. C: writes/tests/Git require exec_command require_escalated. Original D: checkout has another session's dirty work; never alter it. No intent gaps or irreversible operations; activation modifies only generated test/local data. Retained-source deferred entry assessed: no implementation here. Commit cohesive changes/tests separately with story IDs, dependency order, no squash/push. Track affected existing stories in-progress; this slice alone does not complete broad supported-content acceptance. No additional broad exploration/review agents.

## Verification

Focused module tests and real SDK protocol tests; contract schema/semantic validation of emitted examples; parent full Gradle check and one focused review. Windows: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle, GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT, JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir. Refresh graft. Parent owns final authorized shape smoke: ignored data at D:/workspace/luciad-mcp/app/build/mvp-data, source local-shape, build b-6d18e5cf3ff2e47102e76eab80f827978cff4fd948b7da5d2b19b55489e4bd69. Never commit that corpus.

## Spec Change Log

## Review Triage Log
