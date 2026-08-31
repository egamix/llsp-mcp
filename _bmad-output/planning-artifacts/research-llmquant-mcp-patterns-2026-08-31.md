# Research: MCP patterns borrowed from LLMQuant (2026-08-31)

Studied LLMQuant's `quant-mind` (agent-native knowledge extraction/retrieval framework) and
`data-mcp` (hosted MCP server over finance data sources) for ideas transferable to luciad-mcp.

**Overall finding:** on the actual product, luciad-mcp is equal or ahead — verified symbols, mandatory
provenance + five content hashes, a 15-type relationship graph (data-mcp/quant-mind's graph is still a
placeholder), and a deliberate no-server-side-synthesis stance. `data-mcp` (the MCP server) had the most
to borrow, all at the tool-surface / ergonomics level.

Candidates are parked until the MCP-serving Feature is planned. Nothing here is committed.

## Scope / feature changes (touch FRs + tool count)

Adopt via **one bundled correct-course** so the FR and tool-count deltas propagate consistently across
brief/PRD/architecture/Features — do not ad-hoc edit (see the prior 19-vs-20 tool-count reconciliation).

- **Cross-version / migration queries** — the one genuinely new capability. "What API changed / is
  deprecated / is new between SDK vN and vN+1"; "does this symbol/example still work in vN+1". The data
  model already supports it (every record carries `sdkVersion` + deterministic record IDs, so the same
  symbol is diffable across builds); it is simply not exposed as a tool. Plausibly its own new Feature.
  Inspired by quant-mind treating time as a first-class query dimension (`as_of` / `available_at` /
  `freshness()`); the luciad-mcp analog is version, not wall-clock time.
- **Structured `browse` / filter tool, distinct from semantic `search_*`** — faceted listing by package /
  type / technique-tag / quality-label / sdkVersion. data-mcp splits `_browse` (exact filter) from
  `_search` (semantic); luciad-mcp currently has only semantic/lexical search. Composes with data already
  present.
- **Batch reads on `get_method` / `get_class` / `get_example`** — optional `items` list, "omit to fetch
  all", capped (data-mcp uses Max 25), unknown ids silently dropped. `check_symbols` already batches;
  extend the pattern to the getters to cut agent round-trips.
- **Section-scoped reads on `get_guide` / `get_training`** — return named sections rather than whole
  documents (data-mcp `paper_read` returns intro/methods/conclusion). Aligns with the bounded-response
  rule (AD-11).
- **Self-describing hits** — search/browse results advertise what is fetchable next (data-mcp
  `sec_filing_browse` rows carry `sectionKeys`). Backed by the existing 3-level example model + SourceRange.

## Implementation conventions (no correct-course)

Capture as architecture decisions or in project-context when the `mcp` module is built. These are how the
module is built, not what the product does — no scope/FR impact.

- **One result envelope, one formatter** — every tool returns `{summary, data, meta}` via a shared
  formatter; put citations (AD-15) and truncation/pagination (AD-11) in `meta` so both invariants are
  enforced by a single code seam instead of per-tool.
- **Structured errors with stable codes, split by cause** — bad-input vs engine-failure
  (catalog/lucene/onnx) vs index-not-activated / version-mismatch (AD-6). The agent branches on the code;
  do not rephrase per tool. (data-mcp forwards a stable public `code` + `message` verbatim.)
- **Tool descriptions written as workflow instructions** — cross-reference sibling tools and the call
  sequence at the point of use ("resolve_symbol first, pass the canonical symbol here"), reinforcing the
  agent/SKILL.md mandatory-retrieval policy. Cheapest high-leverage change.
- **Module layout** — one file per tool grouped by record type, a central registrar function, a shared
  `schemas` / `result` / `errors` trio, and per-group test files.
- **Strict registration seam + legacy-parameter-alias map** — lets tool params be renamed across builds
  without breaking existing callers on the shared internal host. Can wait until there are real tools to
  keep stable.

## Does not transfer

The remote/hosted `client` + `remote` split, API-key auth, and per-tool credit metering — all consequences
of data-mcp being a paid cloud service, which luciad-mcp deliberately is not (local, offline, zero-egress).
