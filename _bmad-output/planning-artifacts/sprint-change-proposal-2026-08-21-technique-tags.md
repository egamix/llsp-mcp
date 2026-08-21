# Sprint Change Proposal — Technique Tags as a Secondary, Non-Symbol Join Key

**Date:** 2026-08-21
**Project:** luciad-mcp (LuciadLightspeed Knowledge MCP)
**Prepared by:** BMad Correct Course (Developer role), for luciadguy
**Mode:** Incremental (each edit reviewed and approved individually)

## Section 1: Issue Summary

### Problem Statement

Two related architecture gaps surfaced while explaining how the five corpus record types (Javadoc/`ApiElement`, `Example`, `Workflow`, `Guide`, `Training`) cohere at retrieval time:

1. **AD-15 bug:** the citation-tuple rule required canonical symbol unconditionally on every primary-result payload. Canonical symbol (AD-3/AD-18) is also the system's *only* cross-boundary join key. Together, these two facts mean any record with no resolved canonical-symbol link — a symbol-less `Guide` article or `Training` deck section, which do exist — is **permanently unservable and undiscoverable**, not just harder to find. This is a hard-blocking defect in AD-15 as originally written, not a soft discoverability gap.
2. **No path for cross-cutting technique knowledge:** generalizable Java coding techniques and best practices embedded in sample code, workflow docs, guide prose, or training decks (e.g. "always release native resources in a try-with-resources block," "prefer the builder pattern for X") are not tied to any one canonical symbol. There was no mechanism to make this content joinable or searchable across record types by *technique* rather than by *symbol*.

### Context of Discovery

Surfaced by luciadguy via two sharper follow-up questions after a conceptual walkthrough of retrieval-time record coherence: (a) what happens to symbol-less training slides/docs with no canonical-symbol anchor, and (b) how does the architecture surface generalizable coding techniques not tied to one specific symbol. Both were identified as real, previously unaddressed gaps — not merely already-covered mechanics.

### Decisions Made Prior to This Proposal

Resolved via three `AskUserQuestion` forks before any concrete edits were drafted, user selecting the "(Recommended)" option each time:

1. **Mechanism:** Tag-only extension of the existing AD-14 sidecar-metadata mechanism, rather than a new sixth "Concept" record type. Technique tags are an attribute on the owning `Example`/`Workflow`/`Guide`/`Training` record, not a new entity.
2. **Exposure:** A new, dedicated `search_concepts` MCP tool, rather than a tag-filter parameter bolted onto the existing `search_*` tools. Tool surface grows 18 → 19 (PRD/epics convention) / 19 → 20 (Architecture Spine's own pre-existing +1-offset convention).
3. **Vocabulary:** A fixed, curated vocabulary of technique-tag values (governed the same way as AD-14's quality labels — sidecar-supplied, never parser/ML-inferred), rather than freeform tags.

The AD-15 bug fix itself required no user decision — it is a straightforward correctness fix (make canonical symbol conditional, never fabricated) with no alternative design worth presenting.

## Section 2: Impact Analysis

### Epic Impact

| Epic | Impact |
|---|---|
| 1 — Foundation & Domain Contracts | No change. Technique tags are a record attribute, not a new domain entity or module. |
| 2 — Javadoc Knowledge Extraction | No change — technique tags never apply to `ApiElement`/`MethodElement`/Javadoc records. |
| 3 — Example, Workflow & Guide Knowledge | **Modify.** Story 3.3 retitled and extended to cover technique-tag sidecar application (examples), with matching AC additions on Story 3.4 (workflows) and Story 3.5 (guides). |
| 4 — Hybrid Retrieval Engine | No change — technique-tag fusion is `search_concepts`'s own concern (Epic 5), not a change to exact-symbol/lexical/vector fusion. |
| 5 — MCP Tool Surface | **Modify.** New Story 5.3d (`search_concepts`); Story 5.7/5.8 tool-count bumps 18 → 19. |
| 6 — Index Lifecycle | No change. |
| 7 — Agent Guidance & Usability | No edit needed — Story 7.1 references tools generically via §10, not an enumerated list (same finding as the guides and training changes). |
| 8 — Air-Gap Packaging & Release | No change — no new module, image, or network exception introduced. |
| 9 — Training Material Knowledge | **Modify.** New Story 9.3 mirroring Story 3.3's technique-tag AC shape for training records. |

No resequencing needed. No epic gains or loses a dependency ordering constraint.

### Story Impact

New stories: **Story 5.3d** (`search_concepts`), **Story 9.3** (technique tags on training records). Modified stories: **3.3** (retitled + extended ACs), **3.4**, **3.5** (one AC line each), **5.7**, **5.8** (tool-count bumps).

### Artifact Conflicts

- **PRD:** Glossary needs a "Technique tag" entry and an amended "Citation" entry (conditional canonical symbol); new **FR-42** (technique-tag vocabulary + sidecar mechanism) and **FR-43** (`search_concepts` tool); FR-17 amended (training prose, technique-tag filter); FR-31 amended (conditional canonical symbol, AD-15 fix); F6 tool count 18 → 19; new Non-Goals bullet (no automated technique inference); §6.1/§14/API-Contracts tool-count and scope mentions.
- **Architecture Spine:** `binds` frontmatter extended to FR-43; AD-11/AD-24 tool counts 19 → 20; AD-14 amended (sidecar mechanism now covers a second metadata field); AD-15 amended (conditional canonical symbol, with an explicit note framing the original defect); new **AD-29** (technique tags as a secondary, non-symbol join key); Structural Seed comment 19 → 20 tools; Capability Map rows for F4/F6/F10 gain AD-29/FR-42/FR-43 references; new Deferred bullet for the technique-tag vocabulary *content* (distinct from and not blocked by the still-open Guide/Training quality-tag governance questions).
- **Epics:** Requirements Inventory gains FR-42/FR-43 bullets under F4/F6; F10 gains a cross-reference bullet; Additional-Requirements AD list gains AD-29 and amends the AD-3/AD-18 and AD-14/AD-15/AD-24 bullets; Deferred gains the vocabulary-content gate; FR Coverage Map gains dual-epic rows for FR-42 (Epic 3 & Epic 9) and a row for FR-43 (Epic 5); Epic List amends Epic 3/5/9 descriptions and FRs-covered lists; Story 3.3 retitled/extended; Story 3.4/3.5 gain one AC line each; new Story 5.3d; Story 5.7/5.8 tool-count bumps; new Story 9.3.
- **UI/UX:** No impact — no UX artifact exists or is implied.
- **Testing strategy:** Needs fixtures for a technique-tag sidecar (valid vocabulary value, value outside the curated vocabulary — rejected at ingest), a symbol-less `Guide`/`Training` record that is tagged and must surface via `search_concepts`, and a `search_concepts` federation test spanning all four taggable record types.

### Technical Impact

No code exists yet for any epic (consistent with the prior two changes), so this remains a pure planning-artifact change with zero rework cost. The scope growth is deliberately small: no new module, no new container image, no new network exception, no new record type, no new ERD entity — only a second sidecar-metadata field reusing AD-14's existing mechanism, one new MCP tool, and a correctness fix to an existing rule (AD-15).

## Section 3: Recommended Approach

**Direct Adjustment** — apply the AD-15 correctness fix and add the technique-tag mechanism, tool, and vocabulary governance to the existing planning artifacts in place.

- **Rollback:** N/A. Nothing has been implemented; there is no completed work to revert.
- **MVP Review:** Considered and resolved — both the bug fix and the technique-tag mechanism ship in MVP; the fix is required for MVP correctness (symbol-less records must be servable), and the tag mechanism directly answers a gap the user flagged as needing to close now, not defer.

**Rationale:** This is the smallest of the three corpus-level changes made in this session. Unlike the Guide and Training changes, it introduces no new module, record type, image, or network exception — it reuses AD-14's existing sidecar mechanism for a second field and adds one new, narrowly-scoped tool. The AD-15 fix is uncontroversial: it corrects a rule that would otherwise make an entire class of already-scoped records (symbol-less `Guide`/`Training`) permanently unservable, which is a correctness defect regardless of the technique-tag work.

**Effort/Risk/Timeline:** Low risk and low effort. No rework, no rollback, no epic reordering. Timeline impact is limited to Story 3.3/3.4/3.5/9.3's incremental sizing and the new Story 5.3d at sprint planning; no existing story is invalidated.

## Section 4: Detailed Change Proposals

All edits below were applied directly to the three planning artifacts (`prd.md`, `ARCHITECTURE-SPINE.md`, `epics.md`), following the three decisions locked via `AskUserQuestion` in Section 1 plus the AD-15 correctness fix — the same incremental-review pattern used for the Guide and Training changes.

### PRD Edits (`prd.md`)

1. **Glossary:** New "Technique tag" entry added after "Quality label"; "Citation" entry amended so canonical symbol/example ID is included when resolved and omitted — never fabricated — when not.
2. **New FR-42** (F4 section, after FR-36): Cross-cutting technique tags — a curated, fixed vocabulary of coding-technique/best-practice labels, supplied only via the same governance sidecar mechanism as FR-14, attachable to `Example`, `Workflow`, `Guide`, and `Training` records — never `ApiElement`/Javadoc records.
3. **FR-17 amended:** record-type list extended to include "training" prose; Consequences line extended to filter by technique tag (FR-42).
4. **F6 tool count:** "18 tools" → "19 tools", convention parenthetical extended to "14 → 16 → 18 → 19".
5. **New FR-43** (after FR-41, before FR-25): Concept/technique search tool — `search_concepts`, querying by one or more technique tags across `Example`/`Workflow`/`Guide`/`Training` records.
6. **FR-31 amended:** canonical symbol made conditional in the citation tuple, with an amendment note citing the AD-15 fix.
7. **New Non-Goals bullet:** no automated inference of coding techniques/best practices from source, prose, or file naming — technique tags are governance-supplied only.
8. **§6.1 In Scope:** mentions technique tags/FR-42; tool count 18 → 19; `search_concepts` added to the ships-in-MVP list.
9. **§14 Governance:** amended to note technique-tag vocabulary content is a corpus-owner decision, deferred (distinct from the sidecar *mechanism*, which is decided).
10. **API Contracts:** "18 MCP tools" → "19 MCP tools"; FR-43 added to the enumeration.

### Architecture Spine Edits (`ARCHITECTURE-SPINE.md`)

11. **Frontmatter `binds`:** extended to `FR-1..FR-43`.
12. **AD-11 Binds:** "mcp (all 19 tools)" → "mcp (all 20 tools)".
13. **AD-14 amended:** Binds extended to `ingest.examples, ingest.guides, ingest.trainings, retrieval, domain`; new Amendment bullet noting the sidecar mechanism now also carries technique tags, orthogonal to the still-open quality-tag governance questions.
14. **AD-15 amended:** rule text makes canonical symbol conditional; new Amendment bullet framing the original unconditional requirement as a hard-blocking defect, not a soft gap.
15. **AD-24 amended:** "19 tools" → "20 tools"; `search_concepts` added to the primary-result tool enumeration; historical note extended with this change's tool-count bump.
16. **New AD-29:** Technique tags are a secondary, non-symbol join key — full rule defining the curated vocabulary, the four taggable record types, the join semantics relative to AD-18, the `catalog.sqlite` storage shape (attribute, no new entity), and `search_concepts` (FR-43) as primary consumer.
17. **Structural Seed:** `mcp/` comment "19 MCP tools" → "20 MCP tools".
18. **Capability Map:** F4 row gains FR-42/AD-29; F6 row gains FR-43/AD-29, tool count → 20; F10 row gains FR-42/AD-29.
19. **Deferred:** new bullet for the technique-tag vocabulary's initial *content* — explicitly independent of and not blocked by the still-open Guide/Training quality-tag questions; must resolve before Story 3.3 (amended), Story 5.3d, and Story 9.3 are sized.
20. Confirmed no changes needed to AD-27, Consistency Conventions, Stack table, dataflow diagram, or Core-entity ERD — this change adds no new module, image, or record type.

### Epics.md Edits (`epics.md`)

21. **Requirements Inventory:** new FR-42 bullet under F4; F6 header "18 tools" → "19 tools" with new FR-43 bullet; new F10 cross-reference bullet for FR-42.
22. **Additional Requirements:** AD-3/AD-18 bullet extended (AD-29 adds a secondary join key, never displacing canonical symbol); AD-14 bullet extended with the sidecar amendment; AD-15/AD-24 bullet amended (tool count 19 → 20, `search_concepts` added, conditional-canonical-symbol amendment note); new AD-29 bullet.
23. **Deferred:** new bullet for the technique-tag vocabulary content gate.
24. **FR Coverage Map:** new row "FR-42: Epic 3 & Epic 9" (dual-epic attribution, since the FR spans both); new row "FR-43: Epic 5".
25. **Epic List:** Epic 3 description amended (workflows/guides also get technique tags), FRs covered gains FR-42; Epic 5 description amended (18 → 19 tools), FRs covered gains FR-43; Epic 9 description amended (training records get technique tags), FRs covered gains FR-42.
26. **Story 3.3** retitled "Apply governance-supplied quality labels and technique tags"; ACs extended to cover technique-tag sidecar rejection of out-of-vocabulary values and the shared mechanism with workflows/guides.
27. **Story 3.4 / Story 3.5:** each gains one AC line confirming sidecar-supplied technique tags are received via Story 3.3's mechanism.
28. **New Story 5.3d:** `search_concepts` tool — federated, deduplicated, citation-complete technique-tag search across `Example`/`Workflow`/`Guide`/`Training`, including symbol-less records per AD-29.
29. **Story 5.7 / Story 5.8:** "18 tools" → "19 tools"; Story 5.8's ACs extended to reference Story 5.3d alongside 5.3b/5.3c.
30. **New Story 9.3:** technique tags on training records, mirroring Story 3.3's AC shape, closing the loop for symbol-less `Training` records via `search_concepts`.
31. **Epic 7 Story 7.1:** confirmed no edit needed — tools referenced generically via §10, not an enumerated list (same finding as the guides and training changes).

## Section 5: Implementation Handoff

### Change Scope Classification: **Moderate**

This change touches PRD, Architecture Spine, and Epics simultaneously and adds new FRs (FR-42, FR-43) and a new architectural decision (AD-29), growing the MCP tool surface by one — a locked human-sign-off item per the PRD's own governance list. However, unlike the Training change, it introduces no new module, container image, network exception, or record type — it fixes an existing rule (AD-15) and extends an existing mechanism (AD-14's sidecar) rather than establishing new infrastructure. Per the correct-course workflow's scope rules, this is smaller than "Major" but still warrants backlog reorganization (new stories, retitled story, FR renumbering) beyond a single Developer's direct implementation.

### Routed To

**Product Owner / Developer.**

### Deliverables for Handoff

1. This Sprint Change Proposal (complete impact analysis + edit proposals).
2. The 31 edits (Section 4) already applied to `prd.md`, `ARCHITECTURE-SPINE.md`, and `epics.md`.
3. One flagged open design question for the architect/PO:
   - Initial technique-tag vocabulary content (the actual list of values, e.g. `resource-lifecycle`, `builder-pattern`) — AD-29 fixes the mechanism/governance, not the content. Must resolve before Story 3.3 (amended), Story 5.3d, and Story 9.3 are sized.

### Success Criteria

- PRD, Architecture Spine, and epics.md updated to reflect all 31 edits, with FR IDs reaching FR-43 and MCP tool count 18 → 19 (PRD/epics) / 19 → 20 (Architecture Spine's own +1-offset convention, preserved as before) consistent everywhere referenced.
- Technique-tag vocabulary content question tracked as open before Story 3.3 (amended), Story 5.3d, and Story 9.3 enter sprint planning.
- Re-run (or manually re-verify) the FR coverage matrix includes FR-42/FR-43 before proceeding to `bmad-sprint-planning`.
