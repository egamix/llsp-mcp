# Source Tree Analysis — LuciadLightspeed Knowledge MCP

_Generated: 2026-08-25 · Quick scan_

## Top level

```
luciad-mcp/
├── app/                        # Gradle build root (IntelliJ-style layout) — all code lives here
│   ├── settings.gradle.kts     # rootProject.name = "luciad-knowledge-mcp"; includes 12 subprojects
│   ├── build.gradle.kts        # Root aggregator: AD-1 allow-list + :checkDependencyDirection enforcement
│   ├── gradle.properties       # JDK 21 pin; auto-download of JDK disabled (fail loud, never fetch)
│   ├── gradlew / gradlew.bat    # Gradle wrapper (8.10)
│   ├── gradle/
│   │   ├── libs.versions.toml   # Version catalog — single source of version truth
│   │   └── wrapper/             # gradle-wrapper.properties (gradle-8.10-bin.zip)
│   └── <12 module dirs>        # see below
├── architecture.md             # Authoritative architecture narrative (design source of truth)
├── architecture.html           # Interactive architecture diagram (self-contained)
├── docs/                       # ← generated project documentation (this folder)
│   └── luciad-knowledge-mcp-implementation-brief.md
├── _bmad/                      # BMad method tooling + config (_bmad/bmm/config.yaml)
├── _bmad-output/               # Planning + implementation artifacts
│   ├── planning-artifacts/     # PRD, epics, ARCHITECTURE-SPINE.md (invariants by AD number)
│   └── implementation-artifacts/  # Story files + sprint-status.yaml
├── .claude/                    # Claude Code skills + settings
└── .gitignore                  # Ignores .gradle/, **/build/, IDE files, stale target/
```

## Module directories (under `app/`)

Each Structural-Seed package is its own Gradle subproject so the inward-only dependency
direction (AD-1) can be **enforced**, not just documented. Directory names match the module
names in `settings.gradle.kts`.

```
app/
├── domain/            # ★ Inward core — entities, value objects, ports. Depends on nothing.
│   └── src/main/java/com/luciadmcp/knowledge/domain/
│       ├── ApiElement.java, MethodElement.java     # API entities
│       ├── Example.java, Workflow.java, Guide.java, Training.java   # content entities
│       ├── RelationshipEdge.java, RelationshipType.java             # relationship graph
│       ├── Citation.java, IndexManifest.java                        # value objects
│       ├── CatalogStore.java, LexicalIndex.java,
│       │   VectorIndex.java, Embedder.java          # PORTS (implemented by adapters)
│       └── package-info.java
├── config/            # Boot config + compatibility fingerprint (AD-6)     [scaffold: package-info only]
├── catalog-sqlite/    # CatalogStore adapter; record-ID authority (AD-22)  [scaffold]
├── search-lucene/     # LexicalIndex/VectorIndex adapter; BM25+KNN (AD-12)  [scaffold]
├── embeddings-onnx/   # Embedder adapter; ONNX session pool (AD-13)         [scaffold]
├── retrieval/         # Fixed retrieval pipeline steps 1–4 (AD-19)          [scaffold]
├── ingest-javadoc/    # Javadoc parse → catalog → index                     [scaffold]
├── ingest-examples/   # Example/workflow parse → catalog → index            [scaffold]
├── ingest-guides/     # Guide HTML parse → catalog → index                  [scaffold]
├── ingest-trainings/  # Training-deck ingest — AD-28 network exception      [scaffold]
├── mcp/               # MCP server; version bind + pagination (AD-19 step 5) [scaffold]
└── cli/               # ★ Sole build coordinator (AD-17) + active-pointer writer (AD-23)
    ├── src/main/java/com/luciadmcp/knowledge/cli/
    │   ├── CliMain.java              # entrypoint + dispatch + exit codes
    │   ├── IngestArgumentParser.java # six-path contract parsing
    │   ├── IngestArguments.java      # parsed args record
    │   ├── IngestCommand.java        # path validation + output dir creation
    │   ├── IngestPipeline.java       # functional seam (body = Story 1.3)
    │   ├── TrainingIngestSeam.java   # AD-28 seam (unimplemented; Epic 9)
    │   ├── CliError.java, CliErrorCode.java, CliException.java  # structured errors
    │   └── package-info.java
    └── src/test/java/com/luciadmcp/knowledge/cli/
        ├── IngestArgumentContractTest.java   # rejects bad args with non-zero exit
        ├── IngestEgressTest.java             # zero-egress + guard negative self-test
        ├── NoNetworkClasspathTest.java       # no network libs on classpath
        └── testsupport/
            ├── EgressGuard.java                       # arm/disarm/record DNS attempts
            └── GuardingInetAddressResolverProvider.java  # JEP 418 DNS interceptor
    └── src/test/resources/META-INF/services/
        └── java.net.spi.InetAddressResolverProvider   # registers the guard
```

★ = modules with real implementation today. The other 10 are `package-info.java`-only
scaffolding whose sole current job is to fix the module boundary and inward dependency edge;
their adapters are implemented in later epics.

## Entry points

- **`app/cli` → `CliMain.main(String[])`** — the only executable entry point today. Dispatches
  the `ingest` subcommand. `serve` / `validate-index` are designed but not yet implemented.
- **Build entry:** `app/gradlew` (wrapper) — run all tasks from inside `app/`.

## Critical files to know

| File | Why it matters |
|---|---|
| [app/build.gradle.kts](../app/build.gradle.kts) | AD-1 allow-list + `:checkDependencyDirection`; changing module edges starts here |
| [app/settings.gradle.kts](../app/settings.gradle.kts) | The 12-module include list |
| [app/gradle/libs.versions.toml](../app/gradle/libs.versions.toml) | Single source of version truth |
| [app/gradle.properties](../app/gradle.properties) | JDK 21 pin; no network JDK provisioning |
| [../architecture.md](../architecture.md) | Design authority (flows + invariants) |
| `_bmad-output/.../ARCHITECTURE-SPINE.md` | Definitions for every AD-N invariant |
| `_bmad-output/implementation-artifacts/sprint-status.yaml` | Current story/epic status |

## Build output (ignored)

`**/build/` and `.gradle/` are git-ignored. Stale Maven `target/` dirs are also ignored
(the build tool was migrated from Maven to Gradle).
