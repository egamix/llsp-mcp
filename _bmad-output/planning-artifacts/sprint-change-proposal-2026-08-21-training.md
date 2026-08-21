# Sprint Change Proposal — Training Material Ingestion via Docling as a Fifth Corpus Record Type

**Date:** 2026-08-21
**Project:** luciad-mcp (LuciadLightspeed Knowledge MCP)
**Prepared by:** BMad Correct Course (Developer role), for luciadguy
**Mode:** Incremental (each edit reviewed and approved individually)

## Section 1: Issue Summary

### Problem Statement

luciadguy has extra training materials — PowerPoint decks exported as PDF — that carry SDK knowledge not present in the Javadoc/examples/workflows/guides corpus already scoped. These decks are not machine-readable text (they're vision/layout content: slide titles, bullet text, embedded tables/figures), so ingesting them requires a fundamentally different tool: **docling**, a Python library that runs vision/layout ML models over PPTX/PDF input. This tool needs network access (to fetch its models) and shells out to non-Java tooling — both of which the system's existing architecture explicitly forbids everywhere (AD-9 zero egress, AD-10 parse-only/Java-only static analysis).

### Context of Discovery

Surfaced by the product/technical owner (luciadguy) immediately after the Guide-articles Sprint Change Proposal (`sprint-change-proposal-2026-08-21.md`) was approved and applied, while reviewing what other corpus gaps existed beyond the four now-scoped record types (Javadoc, examples, workflows, guides).

### Decisions Made Prior to This Proposal

Resolved via `AskUserQuestion` across two rounds before any concrete edits were drafted:

1. **Ingestion mechanism:** Docling becomes a new first-class ingest module (`ingest.trainings`), invoked directly by `cli ingest` — an explicit, scoped exception to AD-9 and AD-10, carved out only for training-material ingestion, not a general relaxation.
2. **Appliance posture:** The deployed/served MCP appliance (Epic 8, NFR-11) remains fully air-gapped and zero-egress, unchanged. Only the ingest/build step gains network access, and only for this one input type.
3. **MCP exposure:** New dedicated tools (`search_trainings`, `get_training`) rather than folding into existing tools. Tool surface grows 16 → 18.
4. **Epic scoping:** New standalone epic ("Epic 9: Training Material Knowledge") rather than extending Epic 3 — because training ingestion's mechanics (network, Python/docling) are architecturally distinct from Epic 1–4's fully offline, Java-only pipeline, unlike guides which fit Epic 3's existing HTML-parsing shape.
5. **MVP scope:** Ships in MVP alongside the other four record types, not deferred.
6. **Image packaging:** Docling/Python/network tooling ships only in a **separate, ingest-time-only container image** — never inside the hardened serving image that runs `cli serve`. Confirmed via `AskUserQuestion` (chosen over shipping it inside the single existing appliance image).
7. **Governance/quality-tagging:** Whether `Training` records get an AD-14 governance quality-tag sidecar (like examples/workflows) is left **open/deferred** — same treatment as the still-unresolved `Guide` governance question. Confirmed via `AskUserQuestion` after clarifying what AD-14's sidecar mechanism means.

## Section 2: Impact Analysis

### Epic Impact

| Epic | Impact |
|---|---|
| 1 — Foundation & Domain Contracts | **Modify.** Domain model gains `Training` record type; module skeleton gains `ingest.trainings` (twelve modules total); `cli ingest` accepts a 6th local path (training-materials dir) instead of 5. |
| 2 — Javadoc Knowledge Extraction | No change. |
| 3 — Example, Workflow & Guide Knowledge | No change — training ingestion is deliberately *not* folded in here (see Epic 9 rationale). |
| 4 — Hybrid Retrieval Engine | **Modify (description only).** Lexical/vector indexing and fusion now cover all five record types, including training-deck sections, once Epic 9 produces them. No new FRs. |
| 5 — MCP Tool Surface | **Modify.** +1 story (Story 5.3c) for `search_trainings`/`get_training`; tool count 16 → 18 propagates into Story 5.7 (structured contract) and Story 5.8 (SKILL.md skeleton). |
| 6 — Index Lifecycle | No change. Lifecycle mechanics are type-agnostic. |
| 7 — Agent Guidance & Usability | No edit needed — Story 7.1 references tool names generically via §10, not an enumerated list (same as the guides change). |
| 8 — Air-Gap Packaging & Release | **Modify.** New Story 8.4: a distinct, ingest-time-only image for training-material ingestion, built and shipped separately from the serving image. |
| 9 — Training Material Knowledge (**new**) | Ingests training decks via docling and links them to the catalog. Standalone epic; FRs FR-39, FR-40. |

No resequencing needed — Epic 9 has the same catalog-dependency ordering constraint as Epic 3 (needs Epic 2's catalog first) but no forward dependency on Epics 3–8.

### Story Impact

New stories: **Story 8.4** (separate ingest-time image), **Story 9.1** (docling ingestion), **Story 9.2** (catalog linking), **Story 5.3c** (training MCP tools). Modified stories: 1.1, 1.2, 5.7, 5.8 (see Section 4 for exact text).

### Artifact Conflicts

- **PRD:** Glossary needs a "Training (record)" entry; FR-1 needs a 6th input path; new F10 section (FR-39, FR-40); F6 needs FR-41 and a renamed tool count; FR-32/FR-33 need the separate-image packaging split; NFR-9/NFR-11 need scoping notes; Non-Goals and §6.1/§6.2 scope statements need the docling exception carved out explicitly.
- **Architecture Spine:** New `ingest.trainings` module (packaged separately per new **AD-28**); AD-9/AD-10 amended with the AD-28 cross-reference; AD-11/AD-15/AD-24 tool counts bumped 17 → 19; new Stack row for docling; new Structural Seed line; dataflow diagram gains a separate-image subgraph; ERD gains `TRAINING`/`TRAINING_SYMBOL_LINK`; Capability map gains an F10 row; Deferred gains the training governance question (paired with the existing open Guide question).
- **Epics:** As detailed in Epic Impact above.
- **UI/UX:** No impact — no UX artifact exists or is implied.
- **Testing strategy:** Needs training-deck fixtures (PPTX/PDF), including malformed/broken-deck cases mirroring the FR-5 Javadoc-degradation tests, plus a test confirming the serving image contains no docling/Python/network tooling.

### Technical Impact

No code exists yet for Epics 1–5 (readiness report confirmed nothing implemented at the time of the prior guides change), so this remains a pure planning-artifact change with zero rework cost. The scope growth: one new ingestion module, one new domain record type, two new MCP tools, one new epic, one new packaging artifact (the separate ingest-time image), and their associated test fixtures.

## Section 3: Recommended Approach

**Direct Adjustment** — add the new epic, stories, FRs, module, and image-packaging split to the existing planning artifacts in place.

- **Rollback:** N/A. Nothing has been implemented; there is no completed work to revert.
- **MVP Review:** Considered and resolved — training decks ship in MVP (decision 5 in Section 1), consistent with treating this as a fifth corpus input from the start.

**Rationale:** Epics 1–5 have not started. The change is additive and isolates its one genuinely new architectural risk — network access and non-Java tooling — into a single module (`ingest.trainings`) and a single packaging artifact (the separate ingest-time image), governed by one new, explicit invariant (AD-28) rather than a general relaxation of AD-9/AD-10. Every downstream guarantee (unresolved-reference handling, citation shape, fingerprint independence, catalog identity) is reused unchanged.

**Effort/Risk/Timeline:** Low-to-moderate risk (the network/non-Java exception is a first for this system, but tightly scoped and isolated to one module/image), low effort — no rework, no rollback, no epic reordering of existing epics. Timeline impact is limited to Epic 9's own sizing at sprint planning plus the added Epic 5/8 stories; no existing story is invalidated.

## Section 4: Detailed Change Proposals

All edits below were applied directly to the three planning artifacts (prd.md, ARCHITECTURE-SPINE.md, epics.md) following the six/seven decisions locked via `AskUserQuestion` in Section 1 — the same incremental-review pattern used for the prior guides change, with the `AskUserQuestion` rounds serving as the substantive per-decision review.

### PRD Edits (`prd.md`)

1. **Glossary:** `Record` definition amended to add "or one training-deck section"; new **Training (record)** entry added after Guide.
2. **F1 / FR-1:** Added local training-materials dir as a sixth permitted path, with an explicit cross-reference exception to FR-39.
3. **New F10 section** (before Non-Goals): FR-39 (training-deck ingestion via docling) and FR-40 (training reference extraction & catalog linking).
4. **F6 (tool count):** "16 tools" → "18 tools"; new FR-41 (training tools: `search_trainings`, `get_training`).
5. **FR-32 (packaging):** Renamed/amended to "One serving image, three modes, hardened compose; separate ingest-time image for training materials."
6. **FR-33 (air-gap bundle):** Clarified as serving-image-only; serving image tar contains no docling/Python tooling.
7. **Non-Goals:** Zero-egress/no-Python-tooling statements scoped to serving + the four offline record types, with the docling exception noted as the sole carve-out.
8. **§6.1/§6.2 scope:** In-scope list gains training-deck ingestion; tool count updated to 18; out-of-scope Python-tooling bullet scoped with the FR-39 exception.
9. **NFR-9/NFR-11:** Scoped to the serving image / the four offline record types, with the training-ingestion exception noted.

### Architecture Spine Edits (`ARCHITECTURE-SPINE.md`)

10. **Module diagram:** Added `ingestTrainings` node + dependency edges; explanatory sentence that it's packaged and shipped separately (AD-28) and never ships inside the `serve` image.
11. **AD-9 / AD-10:** Amended Binds/Rule text to exclude `ingest.trainings`, cross-referencing the new AD-28 exception.
12. **New AD-28:** Defines the scoped exception — `ingest.trainings` is the sole module permitted network access and non-Java tooling execution, only via `cli ingest`, shipped only in the separate ingest-time image; the served appliance remains fully air-gapped.
13. **AD-27 amendment:** Notes the hardened image is the serving image only; training ingestion runs from a distinct image.
14. **AD-11 / AD-15 / AD-24:** Tool count 17 → 19; `search_trainings`/`get_training` added to the primary-result tool enumeration.
15. **AD-14:** New "Open" note extending the existing Guide governance question to `Training` records, flagged as user-chosen-to-defer.
16. **Consistency Conventions:** Module-naming row gains `ingest.trainings`; record-naming row gains `Training`.
17. **Stack table:** New docling+Python row, distinct from the existing offline-eval-only Python row.
18. **Structural Seed:** New `ingest/trainings/` source-tree line; `mcp/` comment bumped 17 → 19 tools.
19. **Dataflow diagram:** New subgraph for the separate ingest-time image (decks → docling), feeding into the offline ingest pipeline via a files-only edge.
20. **Core-entity ERD:** New `TRAINING` entity + `TRAINING_SYMBOL_LINK` relationship, parallel to `GUIDE`/`GUIDE_SYMBOL_LINK`.
21. **Capability → Architecture Map:** New F10 row (`ingest.trainings` → AD-28/AD-4/AD-3/AD-22); F6 row tool count bumped to 19 with FR-41 added.
22. **Deferred:** New bullet for the training governance/quality-tagging open question, explicitly noting the user's decision to leave it open/deferred.

### Epics.md Edits (`epics.md`)

23. **Requirements Inventory:** Module list, FR-1, new F10 (FR-39/FR-40), F6/F9 amendments, architecture-invariant list (AD-9/AD-10/AD-14/AD-15/AD-24/new AD-28), Stack pins, module seed, Deferred gates, FR Coverage Map rows for FR-39/40/41.
24. **Epic List:** New **Epic 9: Training Material Knowledge** (FRs FR-39, FR-40); Epic 5's FRs-covered list gains FR-41 (tool count 16 → 18); Epic 8's description gains the separate-image note; Epic 4's description gains training-record coverage.
25. **Epic 1:** Story 1.1 domain model gains `Training`, module skeleton gains `ingest.trainings` (twelve modules total, packaged separately per AD-28); Story 1.2 "five local paths" → "six local paths," with an explicit AD-28 carve-out in its acceptance criteria.
26. **Epic 5:** New **Story 5.3c** — training MCP tools (`search_trainings`/`get_training`); Story 5.7 and Story 5.8 "16 tools" → "18 tools."
27. **Epic 8:** New **Story 8.4** — separate ingest-time-only image for training-material ingestion, with acceptance criteria confirming the serving image ships no docling/Python/network tooling.
28. **New Epic 9 section:** Story 9.1 (docling ingestion into `Training` records) and Story 9.2 (catalog linking), full user stories + Given/When/Then ACs matching the existing story format.
29. **Epic 7:** No edit needed — Story 7.1 references tools generically by §10 name, not an enumerated list (same finding as the guides change).

## Section 5: Implementation Handoff

### Change Scope Classification: **Major**

This change touches PRD, Architecture Spine, and Epics simultaneously; adds new FRs (FR-39, FR-40, FR-41) and a new architectural invariant (AD-28); grows the MCP tool surface (a locked human-sign-off item per the PRD's own governance list); and — unlike the guides change — introduces the system's first-ever network-capable, non-Java-tooling ingestion path and a second container image. Per the correct-course workflow's scope rules, this requires PM/Architect involvement, not direct Developer implementation.

### Routed To

**Product Manager / Solution Architect.**

### Deliverables for Handoff

1. This Sprint Change Proposal (complete impact analysis + edit proposals).
2. The 29 edits (Section 4) already applied to `prd.md`, `ARCHITECTURE-SPINE.md`, and `epics.md`.
3. Two flagged open design questions for the architect:
   - Training governance/quality-tagging model (same treatment as the still-open Guide question) — must resolve before Epic 9's stories are sized.
   - docling version pin — deferred alongside the JDK pin (B5), owned by Epic 9.

### Success Criteria

- PRD, Architecture Spine, and epics.md updated to reflect all 29 edits, with FR IDs reaching FR-41 and MCP tool count 16 → 18 (PRD/epics) / 17 → 19 (Architecture Spine's own +1-offset convention, preserved as before) consistent everywhere referenced.
- Training governance/quality-tagging question tracked as open (or explicitly resolved) before Epic 9 enters sprint planning.
- Re-run (or manually re-verify) the FR coverage matrix includes FR-39/40/41 before proceeding to `bmad-sprint-planning`.
