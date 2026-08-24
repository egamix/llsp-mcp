---
title: "Sprint Change Proposal: v1 deployment — loopback single-user → shared internal-host team"
date: 2026-08-24
status: applied
scope_classification: moderate
trigger: product-brief backfill scope change
---

# Sprint Change Proposal — Shared Internal-Host Deployment (v1)

## 1. Issue Summary

During the product-brief backfill (`briefs/brief-luciad-mcp-2026-08-24/`), the v1 deployment scope was changed: the appliance must serve a **small trusted internal team (≤ ~5 developers)** from a **shared internal host**, not a single developer over a loopback-only bind. The user directed dropping the "loopback-bound" and "hardened appliance" framing because v1 is "purely for internal dev usage."

The brief was updated to reflect this. The PRD, ARCHITECTURE-SPINE, and epics still specified **loopback-only, single-user, hardened appliance**, creating a cross-artifact contradiction. This proposal reconciles the three specification documents to the brief.

**Unchanged by this change:** the air-gapped / zero-external-egress / offline guarantee. Only the *inbound* network bind (loopback → internal host interface) and the security *framing* change. No authentication, TLS, or tenant isolation is added in v1 — a trusted internal network is assumed.

## 2. Impact Analysis

- **PRD** (`prds/prd-luciad-mcp-2026-08-20/prd.md`): §2.2 Non-Users, UJ-2, §F9/FR-32 + consequences, §6.1 In Scope, §6 out-of-scope lines, NFR-12.
- **Architecture** (`architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md`): AD-2 (title + rule), AD-27 (title + rule + amendment), capability-map note, security table, source-tree comment, serve-flow diagram node, deferred-profile note.
- **Epics** (`epics.md`): FR-32 summary, NFR-12 row, AD-2 line, FR-32→Epic-8 mapping, Epic 8 description, Story 8.1 (title + AC).
- **Technical impact:** the Compose/Dockerfile bind changes from `127.0.0.1` to the internal host interface; container-hygiene settings (non-root, read-only FS, cap-drop ALL, `no-new-privileges`, `pull_policy: never`) are **retained as standard defaults** (user decision), only the "hardened appliance" positioning is dropped.

## 3. Recommended Approach

**Direct adjustment** — no rollback, no MVP scope reduction. The change widens the inbound bind and re-frames security posture; it removes no functional requirement and adds no new engine or module. True hardening (auth/TLS/tenant isolation) is explicitly deferred to a future deployment profile, consistent with the brief's Vision.

## 4. Detailed Change Proposals (applied)

### PRD
| Location | Old | New |
|---|---|---|
| §2.2 L34 | "Multi-user / shared-team deployments deferred" | deferred = public/cross-org/authenticated multi-tenant; single trusted internal team (≤~5) served in v1 |
| §2.2 L36 | "v1 binds loopback only" | serves small trusted team from shared internal host on internal network |
| UJ-2 | "serve on loopback" | "serve on the shared internal host"; "zero network access" → "zero external network access" |
| §F9 desc | "hardened, loopback-bound" | "deployable on a shared internal host" |
| FR-32 title/body/consequence | "hardened appliance image", "MCP port bound to 127.0.0.1", "loopback-only" | "serving image", "bound to the internal host interface (not the public internet)"; amendment note added |
| §6.1 In Scope | "per-developer loopback deployment" | "shared internal-host deployment for a small trusted team (≤ ~5 developers)" |
| §6 out-of-scope | "No shared/multi-user or LAN-exposed"; "Shared authenticated-TLS multi-user deferred" | "No public/cross-org/authenticated multi-tenant"; "Authenticated-TLS multi-tenant (cross-team/org-wide) deferred" |
| NFR-12 | "loopback bind" | "internal-host bind (no public exposure)" |

### Architecture
| Location | Old | New |
|---|---|---|
| AD-2 title | "single loopback listener" | "single internal-host listener" |
| AD-2 rule | "bound to loopback (v1)" | "bound to the internal host interface reachable by the trusted team (v1); never exposed to the public internet" + amendment note; egress guarantee retained |
| AD-27 title | "Container hardening posture…" | "Container baseline posture…" |
| AD-27 rule | "container/appliance", implied hardened appliance | "container", framed as standard container-hygiene defaults for an internal deployment; 2026-08-24 amendment added |
| Capability-map / security table / source-tree / serve diagram | "hardening", "loopback", "no secrets (loopback, offline)" | "baseline", "internal host", "(trusted internal network, offline)" |
| Deferred profile | "Shared multi-user / TLS / auth… (loopback only, AD-2)" | "Authenticated / TLS / multi-tenant (cross-team or org-wide)… serves a single trusted internal team without auth" |

### Epics
| Location | Old | New |
|---|---|---|
| FR-32 summary | "MCP bound to 127.0.0.1" | "bound to the internal host interface… (not the public internet)" + amendment note |
| NFR-12 row | "loopback bind" | "internal-host bind (no public exposure)" |
| AD-2 line | "single loopback MCP listener" | "single internal-host MCP listener" |
| FR-32→Epic-8 mapping | "hardened Compose deployment" | "internal-host Compose deployment" |
| Epic 8 desc | "Produces a hardened, air-gap-transferable release" | "Produces an air-gap-transferable release… on a shared internal host" |
| Story 8.1 title | "Single hardened image and Compose layout" | "Single serving image and Compose layout" |
| Story 8.1 story/AC | "MCP bound to 127.0.0.1"; "bind beyond loopback"; "unreachable from outside the loopback interface" | "bound to the internal host interface"; "be exposed to the public internet"; "reachable only on the internal network, never from the public internet" |

## 5. Implementation Handoff

**Scope: Moderate.** No code exists yet (planning stage); the change lands entirely in planning artifacts plus a future Compose/Dockerfile bind setting. When Epic 8 is implemented, Story 8.1 must bind the MCP port to the host's internal interface rather than `127.0.0.1`, and its acceptance test asserts internal-network reachability + no public exposure (not loopback-only).

**Follow-ups (not done here):**
- `implementation-readiness-report-2026-08-20.md` (L90, L109) is a dated snapshot that still says "loopback"; regenerate it rather than hand-edit.
- Prior sprint-change-proposals (2026-08-21) and `.memlog.md` audit trails retain historical "hardened/loopback" wording by design — point-in-time records, left unchanged.
- **Separate discrepancy noticed, not in this change's scope:** PRD §6.1 still says "the 19 MCP tools" while the reconciled count is 20 (per epics/architecture/memory). Flagged for a separate fix.
