# Sprint Change Proposal — Guide Articles as a Fourth Corpus Record Type

**Date:** 2026-08-21
**Project:** luciad-mcp (LuciadLightspeed Knowledge MCP)
**Prepared by:** BMad Correct Course (Developer role), for luciadguy
**Mode:** Incremental (each edit reviewed and approved individually)

## Section 1: Issue Summary

### Problem Statement

The corpus includes a fourth input type — written SDK tutorial/guide HTML articles (prose + code snippets + step-by-step instructions) from the LuciadLightspeed doc portal — that does not fit any of the three currently-scoped ingestion paths (Javadoc HTML, `.java` code samples, Markdown/YAML workflow docs). This was discovered on 2026-08-20, immediately after the Implementation Readiness Assessment marked the PRD/Architecture/Epics artifacts **READY** at 34 FRs.

### Context of Discovery

Surfaced by the product/technical owner (luciadguy) while reviewing the readiness verdict, using a concrete example from the vendor doc portal:

`https://dev.luciad.com/portal/productDocumentation/LuciadLightspeed/docs/articles/tutorial/model/dataobject/data_model.html?subcategory=lls_dataobject`

### Evidence

- WebFetch of the linked URL confirmed it is a narrative tutorial/how-to HTML article (prose + embedded code snippets + step-by-step instruction), structurally distinct from Javadoc doclet HTML (FR-4/FR-5's `*-search-index.js` + `section.class-description`/`ul.blockList` structures) and from the Markdown/YAML workflow format (FR-15).
- The system's own architecture has no server-side generation or local LLM (AD-2, AD-9, PRD non-goals) — so fine-tuning a model on these guides was ruled out; ingestion via the existing pipeline pattern (parse → link to catalog → index → cite) is the only approach consistent with the citation-completeness (FR-31), never-mark-unknown-valid (SM-2), and immutable-versioned-rollback (FR-26/28) guarantees.

### Decisions Made Prior to This Proposal

1. **Record type:** New first-class record type (`Guide`), not normalized into the existing `Workflow` format or a generic narrative path — confirmed 2026-08-20.
2. **MCP exposure:** New dedicated tools (`search_guides`, `get_guide`) rather than folding into `search_api`/`search_workflows` — confirmed 2026-08-21. Tool surface grows 14 → 16.
3. **Epic scoping:** Extend Epic 3 (renamed "Example, Workflow & Guide Knowledge") rather than add a standalone epic — confirmed 2026-08-21.
4. **MVP scope:** Ships in MVP alongside the other three record types, not deferred — confirmed 2026-08-21.

## Section 2: Impact Analysis

### Epic Impact

| Epic | Impact |
|---|---|
| 1 — Foundation & Domain Contracts | **Modify.** Domain model gains `Guide` record type; module skeleton gains `ingest.guides`; `cli ingest` accepts a 5th local path (guides dir) instead of 4. |
| 2 — Javadoc Knowledge Extraction | No change. |
| 3 — Example & Workflow Knowledge | **Modify + rename** → "Example, Workflow & Guide Knowledge." New Story 3.5 (guide parsing & catalog linking). Same ordering constraint as examples/workflows (needs Epic 2's catalog first). |
| 4 — Hybrid Retrieval Engine | **Modify.** Guide prose/snippets must flow through the same lexical (FR-17) and vector (FR-20) indexing and fusion as the other three record types. |
| 5 — MCP Tool Surface | **Modify.** +1 story (Story 5.3b) for `search_guides`/`get_guide`; tool count 14 → 16 propagates into Story 5.7 (structured contract) and Story 5.8 (SKILL.md skeleton). |
| 6 — Index Lifecycle | No change. Lifecycle mechanics are type-agnostic. |
| 7 — Agent Guidance & Usability | **Modify.** SKILL.md (Story 7.1) references the two new tool names and gains a policy point on guide vs. workflow vs. raw-API selection; citation guarantee (Story 7.2) extends to guide results. |
| 8 — Air-Gap Packaging & Release | **Minor.** Guide corpus provenance/licensing folds into existing blocker B3 scope; no new story. |

No resequencing needed — guide ingestion slots into the existing epic order without new forward dependencies.

### Story Impact

New stories: **Story 3.5** (guide parsing & linking), **Story 5.3b** (guide MCP tools). Modified stories: 1.1, 1.2, 5.7, 5.8, 7.1 (see Section 4 for exact text).

### Artifact Conflicts

- **PRD:** Glossary needs a "Guide (record)" entry; FR-1 needs a 5th input path; F4/F6 need new FRs (FR-35, FR-36, FR-38) and renamed feature titles; F5 (FR-17/FR-20) needs clarifying language that guides fall under existing generic scope; NFR-14 needs a clarifying note that record-type additions don't bump the fingerprint schema; every "14 tools" reference becomes "16 tools."
- **Architecture Spine:** New `ingest.guides` module; `Guide` added to domain record types and catalog `identityFields`; AD-15/AD-24 citation classification enumerates the new type. One **open design question** flagged for the architect, not resolved by this proposal: whether `Guide` records get a governance quality-tag sidecar (like FR-14 for examples) or ship untagged since they're vendor-authored rather than team-curated.
- **Epics:** As detailed in Epic Impact above.
- **UI/UX:** No impact — no UX artifact exists or is implied (PRD §2.2/§9, confirmed unchanged).
- **Testing strategy:** Needs guide-article fixtures, including malformed/broken-HTML cases mirroring the FR-5 Javadoc-degradation tests.

### Technical Impact

No code exists yet for Epics 1–5 (readiness report confirmed nothing implemented), so this is a pure planning-artifact change with zero rework cost. The only "technical impact" is scope growth: one new ingestion module, one new domain record type, two new MCP tools, and their associated test fixtures.

## Section 3: Recommended Approach

**Direct Adjustment** — add the new stories, FRs, and record type to the existing planning artifacts in place.

- **Rollback:** N/A. Nothing has been implemented; there is no completed work to revert.
- **MVP Review:** Considered and resolved — guides ship in MVP (see decision 4 in Section 1), consistent with treating this as a fourth corpus input from the start rather than a future enhancement.

**Rationale:** Epics 1–4 have not started. The change is additive (new record type, new module, new tools) and reuses every existing guarantee (unresolved-reference handling, citation shape, fingerprint independence, offline/egress posture) rather than requiring new architectural mechanisms. Effort is bounded to one new story in Epic 3, one new story in Epic 5, and edit-in-place propagation of tool/module/path counts through Epics 1/4/7 and the PRD.

**Effort/Risk/Timeline:** Low risk, low effort — no rework, no rollback, no epic reordering. Timeline impact is limited to the added stories' own sizing at sprint planning; no existing story is invalidated.

## Section 4: Detailed Change Proposals

All 8 edits below were presented individually in Incremental mode and approved.

### PRD Edits

**Edit 1 — Glossary:** Added a "Guide (record)" definition parallel to the existing "Workflow (record)" entry, distinguishing a narrative explainer from a discrete task recipe.

**Edit 2 — FR-1:** Changed "Javadoc/samples/model/output dirs only" to "Javadoc/samples/guides/model/output dirs only." Offline-egress test harness (Epic 1, Story 1.2) now enumerates five permitted paths.

**Edit 3 — F4 (new FRs):** Renamed "Example & workflow ingestion and linking" → "Example, workflow & guide ingestion and linking." Added:
- FR-35: Guide article ingestion (HTML parsing into title/prose-sections/code-snippets; graceful degradation on malformed HTML per FR-5's policy).
- FR-36: Guide reference extraction & catalog linking (reuses FR-7 canonical/normalized keys and FR-10's unresolved-reference guarantee).

**Edit 4 — F5 (clarification, no new FR):** FR-17 and FR-20 already state scope generically ("content," "records"); added explanatory clauses making explicit that guides fall under them by extension. No new FR numbers.

**Edit 5 — F6 (new FR, tool count):** Renamed header "(14 tools)" → "(16 tools)." Added FR-38: Guide tools — `search_guides`, `get_guide`, both citation-bearing per FR-31.

**Edit 6 — NFR-14 (clarification):** Added a note that the compatibility fingerprint is independent of which record types are indexed — adding `Guide` changes index content, not the fingerprint definition.

### Architecture Spine Edits

**Edit 7:** Added `ingest.guides` to the module list; added `Guide` to domain record types and catalog `identityFields`; extended AD-15/AD-24 citation classification to include `guide`. Flagged one open design question for the architect: guide governance/quality-tagging model (sidecar like examples, or untagged).

### Epics.md Edits

**Edit 8 (multi-section):**
- Requirements Inventory: module list, FR-1, F4/F6 headers, FR-35/36/38, FR Coverage Map rows for FR-35/36/38.
- Epic List: Epic 3 renamed "Example, Workflow & Guide Knowledge" with FR-35/36 added to its FRs-covered list; Epic 5's FRs-covered list gains FR-38.
- Epic 1: Story 1.1 domain model gains `Guide`, module skeleton gains `ingest.guides` (eleven modules total); Story 1.2 "four local paths" → "five local paths."
- Epic 3: **New Story 3.5** — parse and link guide articles (full user story + Given/When/Then ACs, matching the existing story format).
- Epic 4: Epic description updated to state fusion/indexing covers all four record types including guides.
- Epic 5: **New Story 5.3b** — guide MCP tools (full user story + ACs); Story 5.7 and Story 5.8 "14 tools" → "16 tools."
- Epic 7: Story 7.1 SKILL.md tool-name references updated to include `search_guides`/`get_guide`.

## Section 5: Implementation Handoff

### Change Scope Classification: **Major**

This change touches PRD, Architecture Spine, and Epics simultaneously; adds new FRs (FR-35, FR-36, FR-38); and grows the MCP tool surface — a locked human-sign-off item per the PRD's own governance list (MCP tool schemas are never auto-closed). Per the correct-course workflow's scope rules, this requires PM/Architect involvement, not direct Developer implementation.

### Routed To

**Product Manager / Solution Architect.**

### Deliverables for Handoff

1. This Sprint Change Proposal (complete impact analysis + approved edit proposals).
2. The 8 approved before/after edits (Section 4) to apply verbatim to `prd.md`, `ARCHITECTURE-SPINE.md`, and `epics.md`.
3. One flagged open design question for the architect to resolve before Epic 3 Story 3.5 is sized: guide governance/quality-tagging model.

### Success Criteria

- PRD, Architecture Spine, and epics.md updated to reflect all 8 edits, with FR count 34 → 37 and MCP tool count 14 → 16 consistent everywhere they're referenced.
- Guide governance/quality-tagging question resolved (or explicitly deferred with a tracked note) before Epic 3 Story 3.5 enters sprint planning.
- Re-run (or manually re-verify) FR coverage matrix includes FR-35/36/38 at 37/37 before proceeding to `bmad-sprint-planning`.
