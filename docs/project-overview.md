> **Historical snapshot (August 25, 2026).** Start with the [project README](../README.md) for the current introduction and the [documentation map](index.md) for document authority. Implementation status and source-of-truth statements below reflect the original scan.

# Project Overview — LuciadLightspeed Knowledge MCP

_Generated: 2026-08-25 · Quick scan · Initial documentation_

## Purpose

An **offline knowledge MCP appliance** for the LuciadLightspeed Java SDK. It ingests SDK
documentation (Javadoc), code samples, guides, and training material into an immutable
search index, then serves cited answers to coding agents over a local MCP endpoint.
No network access at serve time (zero egress).

Coding agents call MCP tools (`search_api`, `check_symbols`, `search_concepts`, etc.) over
loopback and get back citation-complete results grounded in a specific SDK version.

## What this document is

This is a **codebase-oriented** overview generated from the repository as it stands today.
The authoritative **design** documents already live in the repo and are the source of truth
for intent and invariants:

- [../architecture.md](../architecture.md) — architecture narrative (companion to the diagram)
- [../architecture.html](../architecture.html) — interactive architecture diagram
- `_bmad-output/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md` — every invariant, cited by AD number
- [luciad-knowledge-mcp-implementation-brief.md](./luciad-knowledge-mcp-implementation-brief.md) — product/implementation brief

## Tech stack summary

| Category | Technology | Version | Notes |
|---|---|---|---|
| Language | Java | 21 (LTS, pinned) | `options.release = 21`; auto-provision disabled |
| Build | Gradle (Kotlin DSL) | 8.10 (wrapper) | Multi-project; version catalog is the single version source |
| Architecture | Hexagonal (ports & adapters) | — | Inward-only module graph, enforced (AD-1) |
| Lexical search | Apache Lucene | 10.5.1 | Pinned in catalog; **not yet wired** |
| MCP transport | MCP Java SDK | 2.0.1 | Pinned; **not yet wired** |
| Embeddings | ONNX Runtime | 1.29.0 | Pinned; **not yet wired** |
| Catalog store | SQLite JDBC (xerial) | 3.53.2.1 | Pinned; **not yet wired** |
| HTML parse | JSoup | 1.23.1 | Pinned; **not yet wired** |
| Java parse | JavaParser | 3.28.2 | Pinned; **not yet wired** |
| Test | JUnit 5 (Jupiter) | 5.11.4 | Test-only; first framework in repo (Story 1.2) |

> Engine/parser libraries are **declared but not yet applied** to any module. The current
> skeleton fixes module boundaries first; libraries get wired in later epics behind domain ports.

## Repository structure

- **Type:** Monolith, single cohesive codebase
- **Layout:** IntelliJ-style — the Gradle build lives under [`app/`](../app/), with 12 subprojects
- **Root group:** `com.luciadmcp.knowledge` (the segment "luciad" is deliberately avoided in package names)
- **Planning/tracking:** BMad artifacts under `_bmad-output/` (PRD, epics, architecture spine, sprint status)

The appliance ships as **two container images** (per the design docs): a **serving image**
(air-gapped runtime + all offline ingest) and a **training-ingest image** (the one scoped
network/non-Java exception, for docling-based training-deck parsing).

## Current implementation state

Early stage — **Epic 1 (Foundation & Domain Contracts) in progress**:

| Story | Status | What it delivered |
|---|---|---|
| 1.1 Scaffold domain model + module skeleton | done | 12-module Gradle graph, domain entities/ports, AD-1 enforcement |
| 1.2 Local-only egress-free `cli ingest` entrypoint | review | `ingest` subcommand six-path contract, egress-guard test harness, AD-28 training seam |
| 1.3 Resumable pipeline with failure policy | backlog | — |
| 1.4 Deterministic IDs, hashes, ingestion report | backlog | — |

Of the 12 modules, **`domain` (14 types + ports) and `cli` (9 main types) contain real code**.
The other 10 modules are currently `package-info.java`-only scaffolding that fixes the
inward-only dependency direction; their adapters are implemented in later epics.

## Where to go next

- Module graph and dependency rules → [architecture.md](./architecture.md)
- Directory layout → [source-tree-analysis.md](./source-tree-analysis.md)
- Build, test, run → [development-guide.md](./development-guide.md)
- Full index → [index.md](./index.md)

