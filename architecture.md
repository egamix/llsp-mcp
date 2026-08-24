# Knowledge MCP — Architecture

An offline knowledge MCP appliance for the LuciadLightspeed Java SDK. It ingests
SDK documentation, code samples, guides, and training material into an immutable
search index, then serves cited answers to coding agents over a local MCP endpoint.
No network access at serve time.

This document is the text companion to [architecture.html](architecture.html) — the
interactive diagram. Open the HTML in a browser to click through the flows; read this
for the same content without the diagram.

The authoritative source for every invariant below is
[ARCHITECTURE-SPINE.md](_bmad-output/planning-artifacts/architecture/architecture-luciad-mcp-2026-08-20/ARCHITECTURE-SPINE.md).
Decisions are cited by their AD number.

---

## Two images, one appliance

The system ships as two container images. The interactive diagram models these as its
two **modes**, because most nodes are shared between them.

| Mode | Image | Purpose | Network |
|---|---|---|---|
| **serve** | serving image | Air-gapped runtime + all offline ingest (docs, samples, guides). Runs `cli ingest`, `cli validate-index`, `cli serve`. | None — zero egress (AD-9), hardened container (AD-27) |
| **train** | training-ingest image | Ingest of training decks only. Runs docling (Python) to parse PPTX/PDF. | The one exception (AD-28) |

The training-ingest module is the **only** module permitted network access (to fetch
docling's vision-layout models) and the **only** one allowed to shell out to a non-Java
process. That is a deliberately scoped exception to the otherwise strict parse-only
(AD-10) and zero-egress (AD-9) rules. Everything downstream of parsing — cataloging,
linking, embedding, indexing, serving — is identical to the offline path.

---

## Components

### Runtime nodes (shown in the diagram)

| Node | Module | Role | Notes |
|---|---|---|---|
| Coding agent | (external) | client | Calls tools over loopback Streamable HTTP (AD-2) |
| Local corpus | (files) | source | Javadoc, examples, guide HTML, governance sidecars — read from disk only |
| Training decks | (files) | source | PPTX/PDF training material — train mode only |
| cli | `cli` | orchestration | Sole build coordinator; mints the single buildId (AD-17); sole writer of the active pointer (AD-23) |
| Offline ingest | `ingest.javadoc`, `ingest.examples`, `ingest.guides` | job | Parse-only (AD-10); two-pass (AD-4) |
| Training ingest | `ingest.trainings` | job | docling + Python + network; the AD-28 exception |
| mcp | `mcp` | orchestration | MCP server; binds version, paginates (AD-19 step 5) |
| retrieval | `retrieval` | orchestration | Fixed retrieval pipeline, steps 1–4 (AD-19) |
| Embeddings (ONNX) | `embeddings.onnx` | compute | ONNX session pool ≥4 concurrent (AD-13); one fingerprint at index and query time (AD-8, AD-21) |
| Catalog | `catalog.sqlite` | store | Sole authority for record IDs (AD-22); canonical-symbol primary join key (AD-3, AD-18) |
| Lucene | `search.lucene` | store | BM25 + KNN; version-scoped (AD-12); technique-tag filter field (AD-29) |
| Index build | (files) | store | Immutable `{sdkVersion}/{buildId}/` directory + manifest (AD-5, AD-6) |

### Layers omitted from the interactive view

Two layers carry no runtime data packet, so they are described here rather than drawn
(a node that never appears in any flow would render permanently dimmed and look broken):

- **`domain`** — the inward core. Entities (ApiElement, Example, Guide, Training,
  RelationshipEdge), value objects (canonical symbol, citation, technique tag), and the
  ports every adapter implements. All dependencies point inward to it (hexagonal /
  ports-and-adapters). It is a compile-time contract, not a runtime hop.
- **`config`** — startup configuration and the compatibility fingerprint definition
  (AD-6). Read once at boot; not part of per-request flow.

---

## Flows

Six scenarios, selectable as tabs in the diagram. Serve mode shows the first five;
the training flow switches the diagram to train mode automatically.

### 1 · Offline Ingest & Build (`cli ingest`) — serve mode

Builds an index from the local corpus. One coordinator, one buildId, two passes,
immutable output (AD-4, AD-17).

1. **corpus → cli** — Read docs, samples, guide HTML, and governance sidecars from the
   local filesystem only. No DNS/HTTP/model download (AD-9).
2. **cli → offline ingest** — Compute one buildId (content hash of all inputs + config),
   create `{sdkVersion}/{buildId}/` once, hand parsers an opaque BuildContext. Parsers
   never mint a buildId (AD-17).
3. **offline ingest → catalog** — Pass 1: parse-only (JSoup + JavaParser, AD-10), mint the
   canonical symbol for every API element, store records. Catalog computes all IDs (AD-22).
4. **offline ingest → catalog** — Pass 2: resolve relationships, example/guide links, and
   cross-refs against the completed catalog. Unresolved references are recorded as
   explicitly unresolved — never invented, never dropped (AD-4).
5. **offline ingest → ONNX** — Embed passages offline. Preprocessing/tokenization/prefix
   live behind the Embedder port (AD-21); role, model, dimension match the manifest
   fingerprint (AD-8).
6. **ONNX → Lucene** — Write BM25 + KNN fields keyed by the canonical join key (AD-18),
   tagged with SDK version (AD-12).
7. **cli → index build** — Seal one IndexManifest carrying the compatibility fingerprint
   (AD-6) into the immutable build directory (AD-5).

### 2 · Validate & Activate — serve mode

Promotes a build to live. Validate-then-atomic-activate; serve stays read-only
(AD-5, AD-23).

1. **index → cli** — `cli validate-index` recomputes the runtime fingerprint and checks
   completeness — every fingerprint component present exactly once. Mismatch fails
   activation, fail-closed (AD-5, AD-6).
2. **cli → index** — Atomic active-pointer swap via rename of a fully-written temp pointer,
   never in-place edit. Only cli writes the pointer (AD-23). Rollback = re-point.
3. **index → mcp** — Serve re-resolves the pointer at a request boundary and opens the new
   build read-only. Immutable builds mean in-flight requests continue safely (AD-23).

### 3 · Hybrid API Search (`search_api`) — serve mode

The primary read path. Fixed pipeline: bind version → exact-first → cite-filter → rank →
paginate (AD-19).

1. **agent → mcp** — `search_api(query, version?)` over loopback (AD-2). Effective version
   is the argument, else the configured default.
2. **mcp → retrieval** — Bind version scope before any lookup (AD-7a); dispatch pipeline
   steps 1–4.
3. **retrieval → catalog** — Exact symbol resolution, authoritative; a confirmed exact
   match is never displaced by a semantic candidate (AD-7).
4. **retrieval → ONNX** — Embed the query with the identical fingerprint used at index
   time (AD-8, AD-21), from the bounded session pool (AD-13).
5. **retrieval → Lucene** — Fetch BM25 + KNN candidates, version-scoped (AD-12). Semantic
   candidates augment but never promote to a valid symbol (AD-7).
6. **retrieval → retrieval** — Fuse (exact-first) → drop citation-incomplete records
   (AD-15) → total deterministic order, tie-break on join key (AD-20). `total` counted here.
7. **retrieval → mcp** — Pagination: default page 10, hard cap 50, ≤1 MiB, explicit
   truncation metadata (AD-11, AD-19 step 5).
8. **mcp → agent** — Primary-result payload: each result carries a full citation tuple
   (source doc, SDK version, anchor, canonical symbol when resolved) plus total/truncated
   (AD-15, AD-24).

### 4 · Symbol Validation (`check_symbols` / `resolve_symbol`) — serve mode

A verdict tool, not a content tool. Catalog is ground truth; no citation; bypasses the
citation filter (AD-24).

1. **agent → mcp** — `check_symbols([...], version)`. Verdict/navigation tool (AD-24).
2. **mcp → retrieval** — Bind version first (AD-7a). Verdict tools skip AD-19 step 3
   (citation-completeness) entirely (AD-24).
3. **retrieval → catalog** — Authoritative existence lookup (AD-7). A symbol present only
   in another version is unknown for the target (AD-7a); unknown stays unknown.
4. **mcp → agent** — Verdict payload (valid/unknown), citation field omitted (AD-24).

### 5 · Concept Search (`search_concepts`) — serve mode

Federates by curated technique tag — a secondary join key across four record types (AD-29).

1. **agent → mcp** — `search_concepts(tag)`. Unrecognized tags are rejected, never coerced
   to the nearest known value (AD-29).
2. **mcp → retrieval** — Tag is a secondary join key, used in addition to, never instead
   of, the canonical-symbol primary join (AD-29, AD-18).
3. **retrieval → catalog** — Group by technique tag. Stored as an attribute on the owning
   record row (no new ERD entity). Only Example, Workflow, Guide, Training may carry them.
4. **retrieval → Lucene** — Tag is an exact-match filterable field, alongside quality
   filter (AD-29, FR-17).
5. **retrieval → mcp** — Fuse matches across all four taggable record types into one
   citation-complete list. Symbol-less Guides and Trainings become discoverable here.
6. **mcp → agent** — Primary-result payload federating the four record types, each cited
   (AD-15, AD-24).

### 6 · Training-Deck Ingest (`cli ingest --trainings`) — train mode only

Runs only in the separate ingest-time image. The one module with network + non-Java exec
(AD-28).

1. **decks → training ingest** — Read PPTX/PDF from the local training-materials
   directory; run docling (Python, vision models, network) to extract section titles,
   ordered blocks, tables — the scoped AD-28 exception.
2. **training ingest → catalog** — Freeze docling output as source-of-record; submit
   through the same CatalogStore + two-pass discipline as every other record type
   (AD-28, AD-4, AD-22). Only how the input is *read* differs.
3. **training ingest → ONNX** — Embed training passages with the same offline model and
   fingerprint as all other content (AD-8, AD-21).
4. **ONNX → Lucene** — Index content with BM25 + KNN plus sidecar-supplied technique tags
   (AD-29), making decks discoverable via `search_trainings` and `search_concepts`.

---

## Cross-cutting invariants

- **One primary join key.** The canonical symbol is the sole primary join key across all
  stores and the wire format (AD-3, AD-18). Technique tags are a *secondary* join key
  (AD-29) — additive, never a replacement.
- **One embedding fingerprint.** The same model, tokenizer, and preprocessing are used at
  index time and query time; a mismatch fails closed (AD-8, AD-21).
- **Immutable builds, atomic activation.** A build directory is write-once; going live is a
  single atomic pointer rename owned by cli (AD-5, AD-23). Rollback is re-pointing.
- **Bounded, cited, deterministic responses.** Every content result is citation-complete
  (AD-15), responses are paginated and size-capped (AD-11), and ordering is totally
  deterministic (AD-20). Verdict tools are the sole exception to citation (AD-24).
- **Fixed retrieval pipeline.** Version bind → exact-first → hybrid candidates → fuse →
  cite-filter → rank → paginate, in that order, always (AD-19, AD-7a).

---

## Workshop scenarios

Suggested walk-throughs when presenting the diagram:

1. **"How does an answer get built?"** — Play the Ingest flow end to end, then Validate &
   Activate. Emphasize one buildId, two passes, immutable output, atomic swap.
2. **"How does a query become a cited answer?"** — Play Hybrid API Search. Stop on step 6
   (fuse → cite-filter → rank) to explain why exact-first and citation-completeness matter.
3. **"Why are there two images?"** — Toggle serve ↔ train (press `O`). Point out that
   catalog, Lucene, ONNX, and cli are shared; only the ingest source and the network
   exception differ (AD-28).
4. **"What's the difference between a content tool and a verdict tool?"** — Compare Hybrid
   Search (cited results) against Symbol Validation (valid/unknown, no citation) (AD-24).
5. **"How are symbol-less docs found?"** — Play Concept Search to show technique-tag
   federation across Guides, Trainings, Examples, and Workflows (AD-29).

---

## Opening the diagram

Open [architecture.html](architecture.html) in any modern browser — it is a single
self-contained file, no server or build step.

- Click a **flow tab** to jump to a scenario; **Play** or `Space` to autoplay; `←`/`→` to step.
- Click any **node** to jump to its first step in the current flow.
- **Drag** nodes to rearrange; `R` resets the layout.
- `O` toggles serve/train mode; `T` toggles light/dark; `F` toggles fullscreen.
