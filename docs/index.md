# Documentation map

**Start with the [project README](../README.md)** for the user-facing introduction, intended workflow, and development status. This page explains the other documents so you can read only what you need.

## Current planning and delivery

| Document | Purpose |
|---|---|
| [PRD](../_bmad-output/planning-artifacts/prds/prd-luciad-mcp-2026-08-20/prd.md) | Controlling product requirements, scope, success metrics, and open questions. |
| [Architecture Spine](../_bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md) | Controlling architecture decisions and implementation constraints. Consult this architecture file first. |
| [Epics](../_bmad-output/planning-artifacts/epics.md) | Planned work and story acceptance criteria. |
| [Sprint tracker](../_bmad-output/implementation-artifacts/sprint-status.yaml) | Recorded delivery status; individual stories contain implementation and review details. |
| [Deferred work](../_bmad-output/implementation-artifacts/deferred-work.md) | Known issues and work deliberately left for later. |
| [September 7 proposal](../_bmad-output/planning-artifacts/sprint-change-proposal-2026-09-07-evidence-backed-sdk-engineering.md) | Proposed course correction, pending approval and incorporation into the controlling plan. |

## Why there are several architecture files

| File | How to use it |
|---|---|
| [Architecture Spine](../_bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md) | Governing decisions and rules. |
| [Root architecture narrative](../architecture.md) | Supporting explanation of flows and invariants; reconcile against the Spine if they differ. |
| [Interactive architecture diagram](../architecture.html) | Visual companion to the narrative; it may lag planning changes. |
| [Generated architecture snapshot](architecture.md) | Dated codebase scan, not a live specification. Check its generation date before relying on implementation-status claims. |

## Briefs and implementation references

- [Product brief](../_bmad-output/planning-artifacts/briefs/brief-luciad-mcp-2026-08-24/brief.md): August 24, 2026 product framing. Useful background; older tool counts and scope may differ from subsequent proposals.
- [Original implementation brief](luciad-knowledge-mcp-implementation-brief.md): detailed background and design input. Consult the PRD and Spine for controlling requirements.
- [Generated project overview](project-overview.md): August 25, 2026 codebase snapshot. Its implementation-status claims are historical.
- [Source tree analysis](source-tree-analysis.md): generated repository layout reference; verify against the current tree.
- [Development guide](development-guide.md): build and contributor instructions. The Gradle build root is `app/`; historical status notes may lag the code.

## Reading order

For a product introduction, read the **README**. For a product decision, follow it with the **PRD** and relevant **proposal**. For implementation, consult the **Architecture Spine**, scheduled **story**, and **development guide**.

The `_bmad/` directory contains workflow machinery. `_bmad-output/` contains project planning and delivery artifacts. Dated proposals and reports preserve decision history; they are not additional competing specifications.
