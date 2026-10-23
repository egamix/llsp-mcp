# Development Guide — LuciadLightspeed Knowledge MCP

_Generated: 2026-08-25 · Quick scan_

## Prerequisites

- **JDK 21** (LTS). The build is pinned to Java 21 and will **not** auto-download a JDK —
  it fails loudly if the running JVM is unsuitable (`org.gradle.java.installations.auto-download=false`
  in [app/gradle.properties](../app/gradle.properties)). Make sure `java -version` reports 21.
- **Gradle wrapper 8.10** — no system Gradle needed; use `./gradlew` (or `gradlew.bat`).
- Git.

> Start with the [project README](../README.md) for an introduction. This guide covers building and contributing; historical implementation-status notes may lag the code.

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
| Run tests | `./gradlew test` | JUnit 5; currently only `cli` has tests |
| Test one module | `./gradlew :cli:test` | |
| Verify AD-1 only | `./gradlew checkDependencyDirection` | Fails on any forbidden module edge |
| List projects | `./gradlew projects` | Shows the 12 subprojects |

## Corporate-proxy / TLS workaround

First dependency resolution from Maven Central (e.g. JUnit) can fail PKIX behind a corporate
proxy. If you hit a PKIX / certificate error, trust the Windows cert store for the build:

```bash
GRADLE_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" ./gradlew check
```

## Running the CLI

The only implemented subcommand is `ingest` (Story 1.2, in review). It takes a fixed set of
**local directory** paths — five required, one optional:

```
ingest \
  --javadoc  <dir> \     # SDK Javadoc
  --samples  <dir> \     # code samples / workflows
  --guides   <dir> \     # guide HTML
  --model    <dir> \     # embedding model dir
  --out      <dir> \     # output index dir (created if missing)
  [--trainings <dir>]    # optional; routes to the AD-28 training seam (unimplemented)
```

Rules enforced by the parser:

- Unknown options, extra positional args, duplicate options, and missing values are rejected
  with a structured error and **exit code 2**.
- URL-shaped (`scheme://…`) and UNC (`\\host\share`) paths are rejected as non-local.
- Required input dirs must exist and be directories.
- The pipeline body is not implemented yet (Story 1.3), so a valid invocation currently
  validates inputs, creates the output dir, and returns success without producing an index.

`serve`, `validate-index`, `--help`, and the full documented exit-code table are deferred to
Story 8.3.

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

Work is planned and tracked under `_bmad-output/`:

- Story specs + `sprint-status.yaml` → `_bmad-output/implementation-artifacts/`
- PRD, epics, architecture spine → `_bmad-output/planning-artifacts/`

Current: Epic 1 in progress (1.1 done, 1.2 in review, 1.3–1.4 backlog).

## Deployment (designed, not yet built)

The appliance is intended to ship as two container images — a zero-egress **serving image**
and a **training-ingest image** (the AD-28 exception). Packaging is Epic 8 and not present in
the repository yet.

