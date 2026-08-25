# Architecture (as-built) — LuciadLightspeed Knowledge MCP

_Generated: 2026-08-25 · Quick scan_

> **Scope of this file.** This documents the **codebase structure** — the Gradle module
> graph, how the inward-only dependency rule is enforced, and what exists today. For the
> **design narrative and full flow walkthroughs**, read the authoritative
> [../architecture.md](../architecture.md) (and the interactive
> [../architecture.html](../architecture.html)). Invariants are cited by their AD number,
> defined in `ARCHITECTURE-SPINE.md` under the planning artifacts.

## Architecture pattern

**Hexagonal (ports and adapters).** The `domain` module is the inward core: entities, value
objects, and the ports (interfaces) every adapter implements. All other modules depend
inward toward `domain`; nothing depends outward. This is not just convention — it is
**enforced at build time** (AD-1) by a custom Gradle task, `:checkDependencyDirection`.

Engines (Lucene, ONNX, SQLite) and parsers (JSoup, JavaParser) are reachable **only through
domain ports**. Those external groups are banned as direct dependencies of the inner modules,
so no adapter detail can leak into `domain`, `retrieval`, or `mcp`.

## Module graph

12 Gradle subprojects under [`app/`](../app/). Allowed inward edges (from
[app/build.gradle.kts](../app/build.gradle.kts)):

| Module | Allowed inward deps | Role |
|---|---|---|
| `domain` | _(none)_ | Inward core: entities, value objects, ports |
| `config` | domain | Boot config + compatibility fingerprint definition (AD-6) |
| `catalog-sqlite` | domain | Catalog store adapter; sole authority for record IDs (AD-22) |
| `search-lucene` | domain | BM25 + KNN lexical/vector index adapter (AD-12) |
| `embeddings-onnx` | domain | ONNX embedder adapter; session pool (AD-13) |
| `retrieval` | domain, catalog-sqlite, search-lucene, embeddings-onnx | Fixed retrieval pipeline, steps 1–4 (AD-19) |
| `ingest-javadoc` | domain, catalog-sqlite, search-lucene, embeddings-onnx | Javadoc parse → catalog → index |
| `ingest-examples` | domain, catalog-sqlite, search-lucene, embeddings-onnx | Example/workflow parse → catalog → index |
| `ingest-guides` | domain, catalog-sqlite, search-lucene, embeddings-onnx | Guide HTML parse → catalog → index |
| `ingest-trainings` | domain, catalog-sqlite, search-lucene, embeddings-onnx | Training-deck ingest (AD-28 network exception, train image only) |
| `mcp` | domain, retrieval | MCP server; binds version, paginates (AD-19 step 5) |
| `cli` | config, ingest-*, mcp | Sole build coordinator; mints buildId (AD-17); writes active pointer (AD-23) |

```
        ┌───────────────────────────── cli ─────────────────────────────┐
        │  (config, ingest-javadoc, ingest-examples, ingest-guides,      │
        │   ingest-trainings, mcp)                                       │
        ▼                                                                ▼
      config          ingest-javadoc / -examples / -guides / -trainings        mcp
        │                     │                                                  │
        │                     ▼                                                  ▼
        │        catalog-sqlite · search-lucene · embeddings-onnx           retrieval
        │                     │                                                  │
        │                     └──────────────┬───────────────────────────────────┘
        └────────────────────────────────────▼
                                           domain   ◄── everything points inward (AD-1)
```

### How AD-1 is enforced

`:checkDependencyDirection` (registered on every subproject, wired into `check`) inspects
these configurations: `api`, `implementation`, `compileOnly`, `runtimeOnly`,
`testImplementation`, `testCompileOnly`, `testRuntimeOnly`, `annotationProcessor`,
`testAnnotationProcessor`. It fails the build on:

- a **project edge** not in the module's allow-list (even test-scoped);
- a **banned external group** as a direct dependency of an inner module (`domain`, `retrieval`, `mcp`);
- a **raw file/collection dependency** on a banned-group module (a jar could smuggle a banned engine past the group check);
- an **unrecognized dependency type** on a banned-group module (fail-closed).

Banned external groups by module:

| Module | Banned direct groups |
|---|---|
| `domain` | lucene, mcp sdk, onnxruntime, xerial (sqlite), jsoup, javaparser |
| `retrieval` | lucene, onnxruntime, xerial, jsoup, javaparser |
| `mcp` | lucene, onnxruntime, xerial, jsoup, javaparser |

## Two images, one appliance

Per the design docs, the appliance ships as two container images:

| Image | Purpose | Network |
|---|---|---|
| **serving** | Air-gapped runtime + all offline ingest (docs, samples, guides). Runs `cli ingest`, `cli validate-index`, `cli serve`. | None — zero egress (AD-9) |
| **training-ingest** | Ingest of training decks only; runs docling (Python) to parse PPTX/PDF. | The single scoped exception (AD-28) |

`ingest-trainings` is the only module permitted network access and the only one allowed to
shell out to a non-Java process. Everything downstream of parsing (catalog, link, embed,
index, serve) is identical across both paths.

## Key domain types (current)

From [app/domain](../app/domain/src/main/java/com/luciadmcp/knowledge/domain/):

- **Entities:** `ApiElement`, `MethodElement`, `Example`, `Workflow`, `Guide`, `Training`, `RelationshipEdge`
- **Value objects / enums:** `Citation`, `RelationshipType`, `IndexManifest`
- **Ports (interfaces):** `CatalogStore`, `LexicalIndex`, `VectorIndex`, `Embedder`

These are compile-time contracts; the adapters that implement `CatalogStore`, `LexicalIndex`,
`VectorIndex`, and `Embedder` live in `catalog-sqlite`, `search-lucene`, and `embeddings-onnx`
respectively (currently scaffolding).

## CLI surface (current)

From [app/cli](../app/cli/src/main/java/com/luciadmcp/knowledge/cli/), the only implemented
subcommand is `ingest` (Story 1.2):

- **`CliMain`** — dispatch + exit codes (`EXIT_OK=0`, `EXIT_USAGE_ERROR=2`). `serve`,
  `validate-index`, `--help`, and the full exit-code table are deferred to Story 8.3.
- **`IngestArgumentParser` / `IngestArguments`** — the fixed six-path contract:
  `--javadoc`, `--samples`, `--guides`, `--model`, `--out` (required) and `--trainings`
  (optional). Rejects unknown options, extra positionals, duplicates, missing values, and
  non-local (URL / UNC) paths with a structured `CliError`.
- **`IngestCommand`** — validates paths are existing local directories, creates the output dir.
- **`IngestPipeline`** — functional seam; the real pipeline body is Story 1.3.
- **`TrainingIngestSeam`** — deliberately unimplemented AD-28 seam (Epic 9); throws with a
  pointer to the training-ingest image.

Ingest paths are **one-shot invocation arguments**, not `application.yaml` config — AD-16
(application.yaml is the sole serve-time config) is not violated.

### Egress guarantee (test-enforced)

`cli`'s test suite enforces the zero-egress invariant so it cannot silently regress:

- A DNS guard installed via `java.net.spi.InetAddressResolverProvider` (JEP 418, Java 18+),
  wired through `META-INF/services`. Chosen over `SecurityManager`, which JEP 486 disables on
  JDK 24+ — the guard is JDK-version-proof and **fails loud** if not actually installed.
- A **negative self-test** proves the guard trips on a deliberate lookup (no vacuous pass).
- `NoNetworkClasspathTest` structurally asserts no HTTP-client/crawler/browser-automation/
  downloader library is on the `cli` classpath.

## Retrieval and serving (designed, not yet built)

The fixed retrieval pipeline and MCP tool surface are specified in the design docs but not
yet implemented in code. Summary (see [../architecture.md](../architecture.md) for the full flows):

- **Fixed pipeline (AD-19):** bind version → exact-first → hybrid candidates → fuse →
  cite-filter → rank → paginate — in that order, always.
- **One primary join key:** the canonical symbol (AD-3, AD-18); technique tags are a secondary
  join key (AD-29).
- **One embedding fingerprint** at index and query time; mismatch fails closed (AD-8, AD-21).
- **Immutable builds, atomic activation** via a single pointer rename owned by `cli` (AD-5, AD-23).
- **Bounded, cited, deterministic responses** (AD-15, AD-11, AD-20); verdict tools are the
  sole citation exception (AD-24).

## Testing strategy

JUnit 5 (Jupiter), wired for every module via `useJUnitPlatform()`; modules opt in by
declaring the `testImplementation` deps. Today only `cli` has tests (argument-contract,
egress-guard, classpath). `:check` depends on `:checkDependencyDirection`, so the AD-1
structural rule is verified on every build.
