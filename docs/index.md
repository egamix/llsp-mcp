# Project Documentation Index — LuciadLightspeed Knowledge MCP

_Generated: 2026-08-25 · Initial scan (quick) · Primary entry point for AI-assisted development_

## Project Overview

- **Type:** Monolith (single cohesive codebase), documented as one part
- **Project type:** Backend / CLI knowledge appliance (offline MCP server)
- **Primary Language:** Java 21
- **Build:** Gradle 8.10 (Kotlin DSL), multi-project — 12 subprojects under `app/`
- **Architecture:** Hexagonal (ports & adapters); inward-only module graph enforced at build time (AD-1)

## Quick Reference

- **Tech stack:** Java 21 · Gradle 8.10 · Lucene 10.5.1 · ONNX Runtime 1.29.0 · SQLite (xerial) 3.53.2.1 · MCP SDK 2.0.1 · JSoup 1.23.1 · JavaParser 3.28.2 · JUnit 5.11.4 _(engine/parser libs pinned but not yet wired)_
- **Entry point:** `app/cli` → `CliMain.main` (subcommand `ingest`)
- **Build root:** `app/` (run `./gradlew` from there)
- **Root package group:** `com.luciadmcp.knowledge` (never the segment `luciad` in packages)
- **Current state:** Epic 1 in progress — Story 1.1 done, Story 1.2 in review

## Generated Documentation

- [Project Overview](./project-overview.md)
- [Architecture (as-built)](./architecture.md)
- [Source Tree Analysis](./source-tree-analysis.md)
- [Development Guide](./development-guide.md)

## Existing Documentation (authoritative design sources)

- [Architecture narrative](../architecture.md) — flows + invariants (source of truth)
- [Architecture diagram (interactive)](../architecture.html) — self-contained HTML
- [Implementation brief](./luciad-knowledge-mcp-implementation-brief.md) — product/implementation brief
- `_bmad-output/planning-artifacts/` — PRD, epics, `ARCHITECTURE-SPINE.md` (every AD-N invariant)
- `_bmad-output/implementation-artifacts/` — story specs + `sprint-status.yaml`

## Getting Started

1. Install **JDK 21** (`java -version` must report 21 — the build won't fetch one).
2. `cd app && ./gradlew check` — compiles all modules and runs the AD-1 dependency check + tests.
   - Behind a corporate proxy hitting PKIX: prefix with `GRADLE_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`.
3. Read [architecture.md](./architecture.md) for the module graph, then [../architecture.md](../architecture.md) for the design flows.
4. See [development-guide.md](./development-guide.md) for CLI usage and contributor conventions.

## Notes on scan depth

This documentation was produced by a **quick scan** (config + structure + existing planning
artifacts; no deep source-logic reading). Given how early the codebase is — 10 of 12 modules
are scaffolding — this covers the current reality. Re-run the Document Project workflow with a
**deep** or **exhaustive** scan once the ingest/retrieval/serve adapters are implemented, to
capture per-module behavior, API contracts, and data models.
