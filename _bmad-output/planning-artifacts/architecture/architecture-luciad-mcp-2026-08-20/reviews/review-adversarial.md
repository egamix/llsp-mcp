---
name: Adversarial Review — LuciadLightspeed Knowledge MCP Architecture Spine
type: architecture-review
lens: adversarial (spine-hole hunting)
target: _bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md
reviewer: adversarial-reviewer
created: 2026-08-20
verdict: STRUCTURALLY SOUND BUT LEAKY — 9 holes where two conforming units still build incompatibly; 3 are HIGH severity and must be closed before Epic 1.
---

# Adversarial Review — Architecture Spine

## Method

For each hole I name a **concrete pair of units one level down** (two modules, two epics, or two invocations of the same module). Both members of the pair obey **every** AD-1..AD-16, the consistency conventions, and the stack table *to the letter* — yet they still produce artifacts that do not compose. Each hole is classified by failure category (clashing shared-data shape / two owners of one entity / conflicting state-mutation path / ambiguous ordering / unowned cross-cutting concern) and closed with an exact proposed AD.

The spine is genuinely strong on *direction of dependency* (AD-1), *isolation* (AD-2/9/10), and *anti-hallucination* (AD-7). The holes are almost all in **who owns the seams between two writers** and **the order in which two rules compose** — precisely the places a dependency-direction rule cannot reach.

---

## HIGH severity

### H1 — Split-brain build: no single owner of the build directory, `buildId`, or manifest

**Pair:** `ingest.javadoc` vs `ingest.examples` (both fan into `catalog.sqlite` + `search.lucene`, per the module graph).

**How both conform:** AD-5 says "each build writes to a new immutable directory keyed by `{sdkVersion}/{buildId}`." The ID convention says "`buildId` is a content hash of the build inputs." Neither AD names *who* computes `buildId`, *who* creates the directory, or *who* seals the manifest. So:
- `ingest.javadoc` legitimately computes `buildId = hash(javadoc inputs)` and writes to `.../v2024/A/`.
- `ingest.examples` legitimately computes `buildId = hash(example + sidecar inputs)` and writes to `.../v2024/B/`.

Both obeyed AD-5 and the ID convention exactly. The result is two immutable directories for one logical build, each holding half the catalog. There is no single point that owns "the build inputs" as one set.

**Category:** two owners of one entity (the build) + conflicting state-mutation path.

**Compounding:** AD-6's manifest fingerprint spans components owned by four different modules (catalog schema version, Lucene major, canonical-symbol format version from `domain`; embedding model id / dim / tokenizer from `embeddings.onnx`; text-preprocessing version). AD-6 binds `domain, mcp, embeddings.onnx, search.lucene` but names no *author* of the assembled manifest. Two modules can each write a manifest fragment; the sealed fingerprint is undefined.

**Proposed AD (new) — AD-17: Single build coordinator owns build identity and manifest.**
> `cli ingest` is the sole build coordinator. It computes the one `buildId` as a content hash over the *complete, ordered* set of build inputs (docs + samples + governance sidecars + the effective embedding/preprocessing config), creates the `{sdkVersion}/{buildId}` directory once, and passes an opaque `BuildContext` (target dir + sealed input digest) to `ingest.javadoc` and `ingest.examples`. Ingest modules never mint a `buildId` and never create a build directory. `domain` defines a single `IndexManifest` builder; each engine adapter contributes exactly one fingerprint component through a `domain` port; the coordinator seals the manifest exactly once and validation (AD-5) fails if any component is missing or contributed twice.

---

### H2 — The join key has undefined normalization ownership: catalog and index can silently miss-join

**Pair:** `catalog.sqlite` vs `search.lucene`.

**How both conform:** AD-3 mandates "the single canonical symbol key **plus its defined normalized variants**" and forbids a module deriving *its own* symbol string. But it explicitly blesses *a set* of normalized variants. Nothing pins *which* variant is the cross-module join key:
- `catalog.sqlite` builds its unique index / adjacency keys on variant N1 (e.g. fully-qualified, generics preserved, case-sensitive) — a legitimate "defined normalized variant."
- `search.lucene` indexes documents keyed on variant N2 (e.g. generics erased, lowercased for its analyzer) — also a "defined normalized variant."

Both used only `domain`-minted variants; neither invented its own string. Yet `retrieval` (AD-7) must take a confirmed exact catalog match and reconcile it against lexical/vector candidates *by canonical symbol*. If catalog emits N1 and search emits N2, the join misses and exact-first fusion cannot attach the semantic candidates to the authoritative hit. AD-7 is silently defeated while every module reports compliance.

**Category:** clashing shared-data shape.

**Proposed AD (new) — AD-18: One cross-boundary join variant.**
> `domain` designates exactly one canonical-symbol variant as the **wire/join key** used for every cross-module reference (catalog rows, edges, example links, Lucene stored id field, citations, and all fusion joins in `retrieval`). All other normalized variants are module-internal only and MUST NOT cross a port boundary. `retrieval` joins strictly on the wire/join key; any candidate lacking it is not fusable. (Feeds the Deferred canonical-symbol serialization gate — that gate must specify the join variant, not just the grammar.)

---

### H3 — Ambiguous composition order of AD-7 (exact-first) and AD-12 (version scoping)

**Pair:** two conforming `retrieval`/`mcp` implementations of `resolve_symbol` / `check_symbols`.

**How both conform:** AD-7 says exact resolution "runs against the catalog first and is authoritative," and `check_symbols` marks valid "only if it exists in the catalog **for the target version**." AD-12 says retrieval is scoped to a single version with one configured default and "no cross-version results unless the caller requests them." The two rules each declare themselves first, and their interleaving is unspecified:
- **Impl A** applies version scope *first*: resolves the target version (caller's or default), then does exact match within it. A symbol present in v2023 but absent in default v2024 → `unknown`.
- **Impl B** treats "exact resolution is authoritative and runs first" literally: finds the exact symbol in the catalog (any version), *then* checks version → could surface the v2023 hit or report `valid` before scoping bites.

Both cite an AD as justification. For `check_symbols` — the anti-hallucination core — these produce **opposite truth values** for the same input. Two callers building against the two behaviors disagree on what "valid" means.

**Category:** ambiguous ordering (of two invariants).

**Proposed AD (tighten AD-7 + AD-12) — AD-7a: Version scope is resolved before exact resolution.**
> The effective SDK version (explicit caller argument, else the configured default) is bound **before** any catalog lookup. Exact resolution and validation are authoritative *within that bound version only*. A symbol existing solely in a non-target version is reported `unknown` for the target version (and MAY carry an optional "exists in other versions" hint only when the caller opted into cross-version). "Exact-first" (AD-7) orders exact vs semantic; it never overrides version scoping (AD-12).

---

## MEDIUM severity

### H4 — Bounded-response vs citation-completeness: undefined pipeline order corrupts `total` and pagination

**Pair:** `retrieval` (candidate producer) vs `mcp` (envelope/limit enforcer).

**How both conform:** AD-11 caps every tool at page size 10 / 50 / ≤1 MiB with truncation metadata. AD-15 drops any record lacking complete provenance from *primary* results. The tool envelope carries `results[] + truncated + total`. No AD fixes the order of {version-scope, exact-first fusion, citation-completeness filter, ranking, pagination}, nor what `total` counts:
- **Impl A**: rank → truncate to 10 → then drop uncited ones → returns 6 valid results though 10+ existed; `total` = pre-filter count.
- **Impl B**: filter uncited → rank → paginate; `total` = post-filter count.

Both obey AD-11 and AD-15. But `total` has two incompatible meanings, so agent pagination (page 2 = offset 10) lands on different records depending on which module computed `total`. The two units cannot share a pagination contract.

**Category:** ambiguous ordering + clashing shared-data shape (`total` semantics).

**Proposed AD (new) — AD-19: Canonical retrieval pipeline and `total` definition.**
> The retrieval pipeline order is fixed: (1) bind version (AD-7a) → (2) exact-first fusion (AD-7) → (3) citation-completeness filter (AD-15) → (4) deterministic ranking (AD-20) → (5) bounded pagination (AD-11). `total` is defined as the count of citation-complete, version-scoped, fused candidates **before pagination**; `truncated` reflects step (5) only. `retrieval` performs steps 1–4 and returns the full ranked-and-filtered candidate set plus `total`; `mcp` performs only step 5.

---

### H5 — Nondeterministic tie-breaking defeats reproducibility (NFR-13) at the page boundary

**Pair:** `retrieval` fusing `search.lucene` BM25 (lexical) vs `search.lucene` KNN (vector) candidates.

**How both conform:** AD-8 guarantees the same embedding space; AD-7 guarantees exact-first. But nothing specifies the fusion/normalization method *or the tie-break* when two candidates receive equal fused scores. Two candidates tied at the rank-10/rank-11 boundary can be ordered by hash-map iteration order — legal under every AD. The ID convention promises deterministic IDs and NFR-13 promises reproducibility, but a nondeterministic *ordering* over deterministic IDs still yields different page-1 contents across runs. Two builds of `retrieval` produce different top-10 for the same query and index.

**Category:** unowned cross-cutting concern (ordering determinism).

**Proposed AD (new) — AD-20: Total deterministic result order.**
> Fusion MUST impose a total order: primary key = fused score (using one declared fusion method — RRF or a declared weighting, fixed here or in the F5 gate), secondary tie-break = the canonical wire/join key (AD-18) ascending. No result ordering may depend on collection iteration order, thread scheduling, or wall-clock. Identical (index, query, config) MUST yield byte-identical ordered results (NFR-13).

---

### H6 — Text-preprocessing has no single owner: index-time and query-time vectors can diverge while both "read the same config"

**Pair:** `ingest.javadoc` (embeds passages) vs `retrieval` (embeds queries), both calling the `Embedder` port.

**How both conform:** AD-8 requires identical "model, tokenizer, text preprocessing, normalization, dimension, prefix convention," "sourced from one shared configuration." A shared *config value* is not a shared *implementation*. If preprocessing (whitespace collapse, HTML/entity stripping, casing, prefix insertion) is applied by each **caller** before calling `Embedder`:
- `ingest.javadoc` collapses runs of whitespace and strips residual HTML entities from Javadoc passages.
- `retrieval` receives already-clean query strings and skips that step.

Both read the same `application.yaml` preprocessing block; both claim AD-8 compliance. The vectors land in different sub-spaces and cosine similarity is silently wrong — exactly what AD-8 exists to prevent. AD-6's "text-preprocessing version" fingerprint won't catch it because both sides stamp the *same config version* while executing different code.

**Category:** unowned cross-cutting concern (a behavior specified by config but implemented in two places).

**Proposed AD (new) — AD-21: Preprocessing lives behind the Embedder port, once.**
> All text preprocessing, tokenization, normalization, and query/passage prefixing are implemented solely inside `embeddings.onnx` behind the `Embedder` port. The port accepts *raw* text plus a role (`QUERY` | `PASSAGE`) and returns the vector. No other module preprocesses text destined for embedding. The `text-preprocessing version` in the fingerprint (AD-6) is owned and stamped by `embeddings.onnx` from its own code version, not from config alone.

---

### H7 — Two writers to the edge/link store with no owner of the ID-hash recipe

**Pair:** `ingest.javadoc` (writes structural `RELATIONSHIP_EDGE`s) vs `ingest.examples` (writes `EXAMPLE_SYMBOL_LINK` / `WORKFLOW_REQUIREMENT`, and possibly edges).

**How both conform:** AD-4 pass 2 has both modules resolving relationships/links into `catalog.sqlite` through the `CatalogStore` port (AD-1). The ID convention says edge IDs are "stable hashes over canonical identity fields." But "canonical identity fields" per entity is undefined, and *both* ingest modules build the record objects that feed the hash:
- For an example that documents a `USES` relationship to a method, `ingest.examples` may populate the link tuple one way.
- `ingest.javadoc`, encountering the same structural fact, populates the edge tuple another way (different field subset, different variant per H2).

Same logical fact → two different hash IDs → duplicate/near-duplicate edges, or (worse) two different facts colliding to one ID. Both obeyed AD-4, AD-1, and the ID convention.

**Category:** two owners of one entity + clashing shared-data shape.

**Proposed AD (tighten ID convention) — AD-22: Adapter owns identity computation.**
> `catalog.sqlite` (the adapter) is the sole authority that computes edge/link/record IDs, from a `domain`-declared, per-entity `identityFields` spec applied to the wire/join key (AD-18). Ingest modules submit *resolved logical tuples* through `CatalogStore`; they never compute IDs and never assume a hash recipe. The store deduplicates by computed ID. `domain` declares `identityFields` once per entity type.

---

### H8 — Active-pointer swap across two processes is an unowned mutation path

**Pair:** `cli` (the process that finishes ingest / performs activation) vs `mcp`/`retrieval` (the live serve process holding the active index read-only).

**How both conform:** AD-5 says activation is an atomic swap of the active-index pointer, the serving process opens read-only and "never mutates an active index," and rollback re-points. This cleanly forbids the *serve* process from writing. But it leaves open **who writes the pointer while serve is live**, whether serve *observes* a swap, and the cross-process atomicity of the pointer file:
- **Impl A**: `cli activate` rewrites the pointer while `serve` runs; `serve` read the pointer only at startup and never notices → keeps serving the old index (or crashes on a since-deleted-but-immutable dir).
- **Impl B**: `serve` re-reads the pointer per request and hot-swaps → but nothing defines locking/rename semantics, so a reader can observe a half-written pointer.

Both satisfy AD-5's literal text (serve never mutates the active index). AD-2's "single loopback listener" doesn't cover a second *process* touching shared files. This is an unowned concern with a data-race at the seam.

**Category:** conflicting state-mutation path (cross-process).

**Proposed AD (new) — AD-23: Pointer-write ownership and observation contract.**
> Only `cli` writes the active-index pointer, via atomic `rename(2)` of a fully-written temp pointer (never in-place edit). The live `serve` process re-resolves the pointer at a defined boundary (start of each request or an explicit `reload`), and because builds are immutable (AD-5) an in-flight request continues safely against the directory it already opened. A build directory is never deleted while any pointer or open handle references it; rollback and GC of old builds are `cli`-only operations gated on zero references.

---

### H9 — "Primary result" is undefined, so `citation` in the envelope is per-tool guesswork

**Pair:** `check_symbols` / `resolve_symbol` (validation-navigation tools) vs `search_api` / `get_*` (content tools) — all in `mcp`, all over `retrieval`.

**How both conform:** AD-15 requires "every **primary result**" to carry a full citation tuple and forbids serving provenance-incomplete records as primary. The envelope convention says `citation` appears "where applicable." Neither defines which of the 14 tools yield "primary results," nor what "applicable" means:
- One implementer treats a `check_symbols` valid/unknown verdict as a primary result and attaches citations (and then must *drop* validly-known-but-uncited symbols under AD-15 — breaking validation!).
- Another treats `check_symbols` as non-primary, returns bare verdicts, no citation.

The AD-15 filter, applied to a validation tool, would suppress *known* symbols that happen to lack a full citation tuple — a direct regression of AD-7's "unknown is always reported unknown; known is known." The ambiguity lets AD-15 and AD-7 collide.

**Category:** ambiguous ordering / unowned classification (what is a "primary result").

**Proposed AD (tighten AD-15 + envelope convention) — AD-24: Enumerate primary-result tools.**
> The spine enumerates each of the 14 tools as either **primary-result** (retrieval/content tools whose hits are subject to the AD-15 citation-completeness filter — e.g. `search_api`, `get_*`, workflow/example retrieval) or **verdict/navigation** (e.g. `check_symbols`, `resolve_symbol`, `index_status`) which report catalog ground truth and are NOT subject to the AD-15 drop rule. `citation` is REQUIRED on primary-result payloads and MUST be absent (not empty) on verdict tools. Version-scoped catalog existence (AD-7/AD-12) governs verdict tools; citation-completeness (AD-15) governs only primary-result tools.

---

## Holes considered and judged NOT spine-level (recorded for completeness)

- **Fusion method choice (RRF vs weighted).** A quality/tuning decision; only its *determinism* is spine-level (closed by AD-20). The method itself may live in the F5 selection gate.
- **Vector-field schema (one field vs javadoc/example split).** Already an explicit Deferred approval gate (Lucene schema, Epic 5); acceptable to defer, provided AD-17's coordinator writes both through one build context.
- **Config validation / unknown-key handling in `application.yaml`.** AD-16 makes it the sole input; strictness is an implementation policy, not a two-unit composition hole.

---

## Severity summary

| # | Pair | Category | Severity | Closing AD |
| --- | --- | --- | --- | --- |
| H1 | ingest.javadoc vs ingest.examples | two owners of build + mutation path | HIGH | AD-17 (build coordinator + manifest author) |
| H2 | catalog.sqlite vs search.lucene | clashing shared-data shape (join key) | HIGH | AD-18 (one wire/join variant) |
| H3 | resolve/check_symbols impls | ambiguous ordering (AD-7 vs AD-12) | HIGH | AD-7a (scope before resolve) |
| H4 | retrieval vs mcp | ambiguous ordering + `total` shape | MED | AD-19 (canonical pipeline + total def) |
| H5 | lexical vs vector fusion | unowned ordering determinism | MED | AD-20 (total deterministic order) |
| H6 | ingest.javadoc vs retrieval embedding | unowned preprocessing owner | MED | AD-21 (preprocessing behind port) |
| H7 | ingest.javadoc vs ingest.examples edges | two owners + shape (ID recipe) | MED | AD-22 (adapter owns identity) |
| H8 | cli vs mcp active pointer | cross-process mutation path | MED | AD-23 (pointer ownership + observe) |
| H9 | verdict vs content tools | unowned classification | MED | AD-24 (enumerate primary-result tools) |

**Verdict:** The spine's dependency-direction and isolation invariants are solid, but nine seams let two fully-conforming units build incompatibly. H1–H3 are load-bearing (split-brain builds, silent join misses, and opposite `check_symbols` verdicts) and should be closed before Epic 1. H4–H9 should be closed before their owning epics (retrieval F5, index lifecycle F7, MCP surface F6). Most fixes are ownership assignments and one explicit composition order — cheap now, expensive after code exists.
