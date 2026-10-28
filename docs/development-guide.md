# Development Guide — LuciadLightspeed Knowledge MCP

How to build and contribute. To run the server and ingest an SDK, see the [server guide](server-guide.md).

## Prerequisites

- **JDK 21** (LTS). The build targets Java 21 and will **not** auto-download a JDK
  (`org.gradle.java.installations.auto-download=false` in [app/gradle.properties](../app/gradle.properties)).
  Make sure `java -version` reports 21.
- **Gradle wrapper 8.10** — no system Gradle needed; use `./gradlew` (or `gradlew.bat`).
- Git.

## Important: run from `app/`

The Gradle build root is [`app/`](../app/), not the repository root. Run all Gradle commands
from inside `app/`:

```bash
cd app
./gradlew <task>          # Linux/macOS/Git Bash
gradlew.bat <task>        # Windows cmd/PowerShell
```

## Common commands

| Task | Command (from `app/`) | Notes |
|---|---|---|
| Compile everything | `./gradlew build` | |
| Run all checks | `./gradlew check` | Includes `:checkDependencyDirection` (AD-1) on every module |
| Run tests | `./gradlew test` | JUnit 5 |
| Test one module | `./gradlew :cli:test` | |
| Verify AD-1 only | `./gradlew checkDependencyDirection` | Fails on any forbidden module edge |
| List projects | `./gradlew projects` | Shows the 12 subprojects |

The contract schemas and examples in [contracts/](contracts/README.md) are checked separately
with the Python verifiers listed there.

## Corporate-proxy / TLS workaround

First dependency resolution from Maven Central can fail PKIX behind a corporate proxy. If you
hit a PKIX / certificate error, trust the Windows cert store for the build:

```bash
GRADLE_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" ./gradlew check
```

## Conventions and constraints (for contributors)

- **Package names never contain the segment `luciad`** — the root group is
  `com.luciadmcp.knowledge.*`. (Directory names, Gradle project names, and filenames are
  unaffected — only Java packages.)
- **Respect the inward-only dependency direction (AD-1).** New module edges must be added to
  the `allowedProjectDeps` allow-list in [app/build.gradle.kts](../app/build.gradle.kts);
  otherwise `:checkDependencyDirection` fails the build. Reach engines/parsers (Lucene, ONNX,
  SQLite, JSoup, JavaParser) only through `domain` ports — never as direct deps of `domain`,
  `retrieval`, or `mcp`.
- **Zero egress at serve/offline-ingest time (AD-9).** No HTTP/DNS/socket calls, no crawlers,
  no model downloaders on the serving classpath. The `cli` egress-guard tests enforce this.
  The single network exception is training-deck ingest (AD-28), which lives only in the
  separate training-ingest image.
- **Version pins live in the catalog** ([libs.versions.toml](../app/gradle/libs.versions.toml)),
  not scattered in module build files.
- **Tests are JUnit 5.** `useJUnitPlatform()` is wired globally; a module opts in by declaring
  `testImplementation(libs.junit.jupiter)` and `testRuntimeOnly(libs.junit.platform.launcher)`.

## Project tracking (BMad)

Work is planned and tracked under `_bmad-output/`; see the [documentation map](index.md) for
the PRD, architecture spine, epics and sprint tracker.

## Deployment

The appliance is intended to ship as two container images — a zero-egress **serving image**
and a **training-ingest image** (the AD-28 exception). Packaging is Epic 8 and is not in the
repository yet.
