---
title: Rubric-Walker Review — Architecture Spine (LuciadLightspeed Knowledge MCP)
reviewer: rubric-walker
target: ../ARCHITECTURE-SPINE.md
sources:
  - ../../../prds/prd-luciad-mcp-2026-08-20/prd.md
  - ../../../../docs/luciad-knowledge-mcp-implementation-brief.md
date: 2026-08-20
pass: final verification (AD-1..AD-27 as merged, including AD-7a and the adversarial
  seam-hole fold-ins AD-17..AD-24, plus AD-25/AD-26/AD-27)
verdict: APPROVE — one medium-severity unreconciled overlap to fix before Epic 5/6 lock-in; residual low-severity cleanup items
---

# Rubric-Walker Review — Architecture Spine (Final Pass)

## Verdict

The spine is now sound as a build substrate. All items corrected in this batch land
cleanly: the tool count is fixed everywhere, the checkpoint/resume vs. immutability
conflict is resolved (AD-25), the lexical-fallback silent-degrade risk is closed (AD-26),
and the container-hardening/operational-envelope gap is closed (AD-27) with a well-reasoned
placement of `packaging` as a non-Java, build-time-only concern. The nine adversarial
seam holes (AD-17..AD-24, AD-7a) are each enforceable, each cite the specific divergence
they close, and eight of the nine reconcile cleanly with the pre-existing AD-1..AD-16.

One genuine new problem surfaced by batching this many ADs at once: **AD-19 and AD-24
give incompatible instructions for the same three tools** (`resolve_symbol`,
`check_symbols`, `index_status`) and neither cross-references the other to resolve it. See
Finding A. This is exactly the batch-overlap risk flagged in the prior pass, now
confirmed real. It is a one-line fix, not a redesign.

A handful of previously-flagged low-severity items were not addressed in this batch (they
were not the batch's target) and remain open — listed under "Carried-over residuals."

Checklist scorecard (final):

| Checklist criterion | Result |
| --- | --- |
| Fixes the real divergence points; misses none | Pass, with one new overlap to close (Finding A) |
| Every AD Rule enforceable & prevents its stated divergence | Pass except AD-19/AD-24 pair (Finding A) |
| Nothing in Deferred lets two units diverge | Pass |
| Named tech verified-current | Pass — unchanged from prior pass, still current |
| Covers the driving spec (F1–F9 / FR-1..34, NFR-1..14) | Pass — NFR-12 now covered (AD-27); FR-28/29 retention/backup still soft (residual) |
| Every altitude-owned dimension decided/deferred/open, esp. ops envelope | Pass — hardening closed; retention-count/backup-atomicity is the one loose thread |

---

## Finding A (MEDIUM) — AD-19's universal pipeline and AD-24's tool categorization contradict each other for `resolve_symbol` / `check_symbols` / `index_status`

**AD-19** fixes one pipeline for `retrieval`, applied without qualification:

> Pipeline order is fixed: (1) bind version (AD-7a) → (2) exact-first fusion (AD-7) → (3)
> citation-completeness filter (AD-15) → (4) deterministic ranking (AD-20) → (5) bounded
> pagination (AD-11)... `retrieval` performs steps 1–4 and returns the full ranked,
> filtered candidate set plus `total`; `mcp` performs only step 5.

**AD-24** then classifies tools into two categories and says the citation-completeness drop
must *not* apply to one of them:

> Each of the 15 tools is enumerated as either **primary-result** (... subject to the AD-15
> citation-completeness drop) or **verdict/navigation** (`check_symbols`, `resolve_symbol`,
> `index_status` — report catalog ground truth per AD-7/AD-7a/AD-12 and are **never dropped
> for missing citation**).

The module graph forces every MCP tool through `retrieval` (`mcp --> retrieval`, no
`mcp --> catalog.sqlite` edge), and AD-7 itself lists `resolve_symbol` and `check_symbols`
as consumers of the same `retrieval`/`catalog.sqlite` path AD-19 is describing. AD-19 does
not carve out an exception for verdict/navigation tools — read literally, its step 3 drops
any candidate lacking complete citation *before* AD-24's per-tool categorization ever runs.
That means a real catalog symbol with, say, a missing anchor would come back `not_found`
from `resolve_symbol` (silently violating AD-7's "authoritative" guarantee and AD-24's
"never dropped for missing citation") in one conforming implementation, while another
implementation — reading AD-24 as authoritative — skips step 3 for those three tools and
contradicts AD-19's claim that `retrieval` "performs steps 1–4" uniformly. Two teams
building `retrieval` and `mcp` against this spine could genuinely diverge on this exact
seam, which is the failure mode both ADs exist to prevent.

This is a reconciliation gap, not a design flaw in either AD — AD-19's ordering and AD-24's
categorization are both correct ideas that were never cross-referenced.

**Fix (one line):** Add to AD-19 step 3: "...skipped entirely for verdict/navigation tools
(AD-24), which bypass the citation filter and report catalog ground truth directly." Or,
symmetrically, add to AD-24: "verdict/navigation tools do not enter the AD-19 pipeline at
step 3; they call `retrieval`'s version-bound exact-lookup path (steps 1–2) directly." Pick
one AD as the owner of the exception and have the other defer to it.

## Confirmed closed from the prior pass

- **Tool count (was F-1):** Fixed in all three spine locations — AD-11 ("all 15 tools"),
  the capability map's F6 row ("MCP surface — 15 tools"), and the structural seed ("15 MCP
  tools"). No stale "14" remains anywhere in the file.
- **Container-hardening / operational envelope (was F-2):** AD-27 pins non-root, read-only
  root FS, cap-drop, `no-new-privileges`, and read-only corpus/model mounts as a build
  invariant, bound to a new `packaging, cli` pairing. `packaging` now has a home in the
  structural seed (`packaging/ # Dockerfile + hardening — AD-27`) and an explicit row in
  the capability map (`NFR-12 security posture | packaging, cli | AD-27`).
- **`packaging`'s placement outside the Java module graph — sound.** The Design Paradigm
  section adds an explicit clarifying sentence: `packaging` "is a build-time/deployment
  concern (container image, hardening — AD-27), not a Java package; it wraps the built
  `cli` artifact and has no compile-time dependency edge into the module graph below." This
  directly answers the placement question: it is correct that `packaging` has no edges in
  the `graph TD` module map, because AD-1's "no cycles, dependencies point inward"
  invariant is about compile-time Java module dependencies, and a Docker-layer concern that
  wraps the finished `cli` artifact is categorically outside that graph. No contradiction
  with AD-1 or AD-9 (build-time dependencies are separately covered by AD-9's "any build
  dependency is vendored/mirrored offline").
- **Checkpoint/resume vs. immutability (was F-3):** AD-25 resolves it cleanly — checkpoints
  live only in the AD-17 coordinator-owned staging area for the in-progress `buildId`,
  never in a sealed directory; a resume re-attaches by matching the sealed input digest;
  an input change forces a new `buildId` rather than mutating the old one. Consistent with
  AD-5 and AD-17, no residual gap.
- **Lexical-fallback silent-degrade risk (was F-4):** AD-26 pins it directly — model-load
  failure is always explicit, fallback is config-gated only, and degraded mode is always
  visible via `index_status`. Clean, enforceable, no overlap with AD-8/AD-18/AD-21.

## Carried-over residuals (not touched by this batch; still open, all LOW severity)

- **Malformed-page policy (was F-5).** FR-2/FR-5's `fail-fast | collect-and-continue`
  policy for `ingest.javadoc`/`ingest.examples` still has no AD or convention forcing the
  two ingest modules to share one policy surface and one reporting shape. None of AD-17..27
  touches this. Still open.
- **Retention count / backup atomicity (was F-8, partially narrowed).** AD-23 adds a
  real GC-safety invariant ("a build directory is never deleted while any pointer or open
  handle references it; rollback and GC... gated on zero references"), which is good, but
  it does not pin FR-28's minimum-retention rule (current + previous validated build) or
  FR-29's backup-completeness rule (manifest + catalog + Lucene dir + report + config +
  model + checksums as one atomic unit; a partial index is not a valid backup). Narrower
  than before AD-23 landed, but still not a pinned invariant.
- **`[ADOPTED]` tagging inconsistency (was F-6).** Now more pronounced with more ADs:
  AD-1, AD-2, AD-4–AD-11, AD-26, AD-27 carry `[ADOPTED]`; AD-3, AD-7a, AD-12–AD-25 (16 of
  27 ADs) do not. With `status: draft` in front-matter, an untagged AD's approval state is
  still ambiguous. Cosmetic, but worth a pass before this becomes the frozen contract.
- **Structural seed still incomplete (was F-7, partially resolved).** `packaging/` was
  added (good — closes the Dockerfile gap), but `agent/SKILL.md` (FR-30, a hard
  deliverable), `compose.yaml`, `scripts/`, and `tools/evaluation/*.py` (brief §4/§16) are
  still absent from the source tree.
- **Minor cross-reference nit (new, trivial).** AD-17 says "AD-5 validation fails if any
  [fingerprint] component is missing or contributed twice" — the fingerprint's semantic
  owner is AD-6, not AD-5 (AD-5 owns immutability/atomic-activate; AD-6 owns the
  compatibility fingerprint and refusal-to-load rule). Not a real overlap — AD-5's
  "validated before activation" step is the natural place such a check runs — but the
  citation should probably read "AD-5's validation step, per AD-6's fingerprint
  completeness rule" for precision. Not worth a fix cycle on its own; bundle it with the
  next edit pass.

## Stack currency — unchanged, still current

No stack table edits in this batch beyond what was already verified in the prior pass
(Lucene 10.5.1, ONNX Runtime 1.29.0, MCP Java SDK 2.0.1, SQLite JDBC 3.53.2.1, JSoup
1.23.1, JavaParser 3.28.2 — all confirmed current as of 2026-08-20; JDK pin correctly gated
behind B5 with the Java-25-LTS caveat already recorded).

## Coverage summary (F1–F9 / FR / NFR) — final

- **F1–F9:** all covered in the capability map, now with AD-17..AD-27 filling in F5/F6/F7/
  F9's previously-thin "Defined by" columns. Remaining soft spots: FR-2/FR-5 malformed
  policy (no AD), FR-28/FR-29 retention/backup (partially covered by AD-23).
- **NFRs:** NFR-3 (AD-13), NFR-10 (AD-11), NFR-11 (AD-9), NFR-12 (AD-27, new), NFR-13 (ID
  convention, AD-5, AD-20), NFR-14 (AD-6) all covered. NFR-1/2/4–9 remain legitimately
  deferred behind B1. No NFR is silently uncovered anymore.
- **Operational/environmental envelope:** deployment topology (AD-2), hardening (AD-27),
  build coordination/manifest ownership (AD-17), pointer-write/rollback/GC ownership
  (AD-23), offline/zero-egress (AD-9) — all decided. The only remaining thread is the
  retention-count/backup-atomicity detail noted above, which is a narrow operational
  parameter, not a whole silent dimension.

## Bottom line

Approve the spine with Finding A resolved (a one-line cross-reference fix between AD-19
and AD-24). The carried-over residuals are all low severity and can be swept in the same
edit pass or deferred to the relevant epic without blocking downstream work. No dimension
this architecture is responsible for is left silently undecided.
