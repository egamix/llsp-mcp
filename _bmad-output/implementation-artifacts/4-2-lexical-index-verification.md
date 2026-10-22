# Story 4.2 verification — 2026-10-22

Implemented on `codex/story-4-2-lucene-index` in the designated worktree.

- `2442f7e`: domain lexical/documentation ports, pinned SQLite documentation reads, persistent Lucene BM25 adapter and six adapter tests.
- `e15e708`: production lexical stage, coordinator-owned index path, committed-index admission on resume, retry/checkpoint/source-byte handling and two importer roundtrip tests.

Executed successfully:

```powershell
./app/gradlew.bat -p app :domain:test :catalog-sqlite:test :search-lucene:test :cli:test :retrieval:test checkDependencyDirection --console=plain
```

Result: `BUILD SUCCESSFUL`. The affected module XML reports contain 204 tests, zero failures, one existing skipped CLI test. Gradle reused up-to-date tasks where appropriate. The seven exact-symbol resolver tests remain green, including method/constructor and overload ambiguity. The final focused importer tests also passed independently. `graft build` succeeded after the code commits (192 indexed files).

Evidence covers actual modern Javadoc import into SQLite and Lucene, HTML removal, reopen, structured notes/table/list/code and symbol-less narrative search; actual source metadata filters; target SDK context without automatic filtering; exact/prefix/camel tokens; stable ID ordering; 128-ID and 16-section batch boundaries; excerpt/highlight and conservative serialized-payload budgets; query input/page/candidate/work bounds; mismatched SDK/build/capability/runtime/lexical recipe admission; atomic rollback after a partially written failed generation; retry; stale checkpoint rejection; and unchanged source SQLite bytes during rebuild/serving and missing-index failure reporting.

The lexical commit includes the actual source build/SDK/capability/runtime binding and explicit lexical recipe plus Lucene format version. Source manifests and approved identity/schema contracts were not changed. Lexical scores do not confer exact-symbol proof. The pipeline continues to report examples/workflows, embeddings, vector index and activation as unsupported.

Remaining scope and acceptance limits: Epic 3 content producers/quality labels, cited enrichment in Story 4.6, MCP, activation/rollback and vector enhancement are not implemented here. No representative-corpus retrieval quality or owner-hardware latency/concurrency/RSS claim is made. Time bounds use Lucene's cooperative query timeout plus collection deadline checks; they are not a hard OS deadline. Parent full Gradle check and focused review remain pending at this handoff. Existing technical approvals remain pending.
