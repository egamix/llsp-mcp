# Application portability snapshot

`com.luciadmcp.knowledge.cli.pipeline.ApplicationPortabilitySnapshotTest.realPipelineProducesPortableLogicalSnapshot` runs the real production catalog, relationship and validation stages against two compact synthetic Javadoc pages. It includes distinct qualified overloads, resolved inheritance with inverse edges, and legal unresolved parameter evidence. It asserts a clean validated result, four persisted records, two edges, preserved uncertainty and truthful skipped activation.

The test builds twice in different temporary roots with reversed file creation order, then compares every exported logical field. The snapshot includes recoverable symbols, complete stored catalog tuples/record IDs, relationship IDs/edges, resolution evidence, the domain manifest, persisted schema/build metadata, completed stages and the full actual ingestion report. Stable row/list ordering is explicit. It does not export staging/output directories, SQLite engine occurrence IDs or console diagnostics, because those are operational rather than logical contract data. No logical report field is stripped; the successful specimen has neither timestamps nor absolute failure locations.

Export is optional. Set `LUCIAD_CONTRACT_SNAPSHOT` to an absolute destination before running:

```text
app/gradlew.bat --no-daemon :cli:test --tests com.luciadmcp.knowledge.cli.pipeline.ApplicationPortabilitySnapshotTest.realPipelineProducesPortableLogicalSnapshot --rerun-tasks
```

On Linux use `./gradlew` from `app/` with the same task/test selector. The opt-in environment variable is inherited by the test JVM. Use independent Windows/Linux output paths. Compare parsed JSON objects or UTF-8 bytes; output uses LF on both platforms. The tests themselves always run even when export is disabled.

The Windows focused run passed and exported `build/contracts/windows-application.json`. Cross-platform execution/comparison evidence is recorded separately after both platform runs; this document does not claim a Linux outcome.
