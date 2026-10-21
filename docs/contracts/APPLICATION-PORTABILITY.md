> Historical read-tools/1 snapshot evidence. The SDK guidance amendment changes the runtime/build digest; these hashes are not current read-tools/2 parity claims.

# Application portability snapshot

`com.luciadmcp.knowledge.cli.pipeline.ApplicationPortabilitySnapshotTest.realPipelineProducesPortableLogicalSnapshot` runs the real production catalog, relationship and validation stages against two compact synthetic Javadoc pages. It includes distinct qualified overloads, resolved inheritance with inverse edges, and legal unresolved parameter evidence. It asserts a clean validated result, four persisted records, two edges, preserved uncertainty and truthful skipped activation.

The test builds twice in different temporary roots with reversed file creation order, then compares every exported logical field. The snapshot includes recoverable symbols, complete stored catalog tuples/record IDs, relationship IDs/edges, resolution evidence, the domain manifest, persisted schema/build metadata, completed stages and the full actual ingestion report. Stable row/list ordering is explicit. It does not export staging/output directories, SQLite engine occurrence IDs or console diagnostics, because those are operational rather than logical contract data. No logical report field is stripped; the successful specimen has neither timestamps nor absolute failure locations.

Export is optional. Set `LUCIAD_CONTRACT_SNAPSHOT` to an absolute destination before running:

```text
app/gradlew.bat --no-daemon :cli:test --tests com.luciadmcp.knowledge.cli.pipeline.ApplicationPortabilitySnapshotTest.realPipelineProducesPortableLogicalSnapshot --rerun-tasks
```

On Linux use `./gradlew` from `app/` with the same task/test selector. The opt-in environment variable is inherited by the test JVM. Use independent Windows/Linux output paths. Compare parsed JSON objects or UTF-8 bytes; output uses LF on both platforms. The tests themselves always run even when export is disabled.

The Windows focused run and clean Linux-native Java run passed. Exports `build/contracts/windows-application.json` and `build/contracts/linux-application.json` are byte-identical: SHA-256 `2d13d1705cadb127196380dd747129183501d20bb6ffdf15907694ed7ff070d9`. Linux used Ubuntu-24.04 with a temporary Temurin Java 21.0.12.1 JDK and the committed Gradle wrapper JAR. See `VERIFICATION.md` for platform counts, setup constraints and evidence boundaries.
