# luciad MCP tool reference

All six tools are read-only. Every request needs `"contractVersion": "read-tools/2"`.

## Fields every request accepts

| Field | Meaning |
|---|---|
| `pageSize` | Items per page, 1–50, default 10 |
| `continuation` | Value from a previous response's `continuation`; send it with an otherwise identical request |
| `limits` | `{"work":5000,"candidates":1000,"executionMillis":1000,"responseBytes":1048576}` (defaults; maxima 50000 / 10000 / 5000 / 1048576). Raise `work` or `executionMillis` only when a result says it was cut off |
| `targetSdkVersion` | The version the user's project uses. Context only: never filters results |
| `sourceSdkVersion`, `buildId` | Restrict to that served source/build; a mismatch returns an `UNKNOWN_SDK` / `BUILD_UNAVAILABLE` error |

## Envelope every response has

| Field | Meaning |
|---|---|
| `sdkVersion`, `buildId` | The SDK build that actually answered |
| `proof` | `EXACT`, `AMBIGUOUS`, `UNKNOWN_IN_SCOPE`, `INCOMPLETE_EVIDENCE`, `NOT_APPLICABLE` |
| `data` | Tool-specific payload (below); `null` on error |
| `error` | `null`, or `{code, message, retryable}`. Codes: `INVALID_REQUEST`, `UNKNOWN_SDK`, `BUILD_UNAVAILABLE`, `INCOMPATIBLE_BUILD`, `INVALID_CONTINUATION`, `PAYLOAD_TOO_LARGE`, `WORK_LIMIT`, `DEADLINE`, `CANCELLED`, `INTERNAL_ERROR` |
| `counts` | `{value, relation}`; `relation` `LOWER_BOUND` means "at least" |
| `truncated`, `continuation` | More results available, and the token to get them |
| `emptyReason` | Why `data` is empty: `NO_MATCH`, `UNKNOWN_IN_SCOPE`, `INCOMPLETE_EVIDENCE`, `NOT_APPLICABLE` |
| `coverage` | `{complete, reason}`: whether the ingested docs are complete |

Records (search hits, resolve matches, fetched records) share one shape:
`recordId`, `recordType` (`API`, `NARRATIVE`, `EXAMPLE`, …), `symbol` (encoded coordinate, null
for examples and narratives), `proof`, `matchedBy` (`COORDINATE`, `ALIAS`, `LEXICAL`, …),
`citations[]` with `locator` = `{relativePath, anchor, lines: {first, last}, …}`, and `snippet`.

## search_knowledge

```json
{"contractVersion":"read-tools/2","query":"create a lightspeed layer for vector shapes","pageSize":10}
```
- `query`: plain words, a class name or `Type#member`. An exact type/member name is ranked first
  (`matchedBy: COORDINATE`).
- `mode`: `LEXICAL` (default) or `BROWSE` (empty query, list records).
- `kinds`: optional filter on knowledge kind (`AUTHORITATIVE_FACT`, `EXPERT_APPROVED_PRACTICE`, `RECIPE`).
- `data.hits[]`: records with a `snippet`. Common words make `counts.relation` `LOWER_BOUND`;
  ranking still covers all matches.

## resolve_symbol

```json
{"contractVersion":"read-tools/2","symbol":"TLspShapeLayerBuilder#newBuilder()"}
```
- Accepts a fully qualified name (`com.luciad.shape.ILcdShape`), a simple type name
  (`ILcdShape`), or `Type#member(paramTypes)` / `Type.member`. Parameter types may be simple names.
- `includeAliases` (default true): allow simple names.
- `data.matches[]`: one match = `EXACT`; several = `AMBIGUOUS` (e.g. overloads). A method
  name without `(...)` is `INCOMPLETE_EVIDENCE` even with one candidate: add the parameter list.

## get_record

```json
{"contractVersion":"read-tools/2","recordIds":["<id1>","<id2>"],"includeContent":true}
```
- `recordIds`: IDs from any other tool's results (batch them).
- `includeContent`: `false` (default) returns section titles only, which is cheap for seeing what records are.
- `sectionIds`: fetch only these sections.
- `data.records[]`: one entry per requested ID, in order, each with a `status`:
  `FOUND`, `NOT_FOUND`, `INAPPLICABLE` (with `reason`), `PAYLOAD_TOO_LARGE`.
  A `FOUND` entry has `record` and `sections[]`, each `{id, title, locator, blocks[]}`. A block is
  `{kind: PARAGRAPH|CODE|TABLE|LIST|NOTE, label, text, links[], table, items[]}`.
- API records: blocks labelled `signature`, `description`, `parameter x`, `returns`, `throws X`,
  `since`, `deprecated`.
- Example records: optional `summary` (the sample's Javadoc), a `context` block naming the parent
  class record (for method records), `luciad imports` (class records), and `source` (the code, `CODE`).
- `sectionContinuation`: more sections of a very large record; pass the response `continuation`.

## get_relationships

```json
{"contractVersion":"read-tools/2","seed":"com.luciad.view.lightspeed.style.TLspLineStyle","direction":"IN","types":["CONSTRUCTS","CALLS","USES_TYPE"]}
```
- `seed`: a canonical key (`com.luciad.x.Type`, `com.luciad.x.Type#member(params)`), a record ID
  (API or example), or any name `resolve_symbol` accepts. Ambiguous seeds return no edges.
- `direction`: `OUT` (default) = what the seed points to; `IN` = what points to the seed; `BOTH`.
- `mode`: `DIRECT` (default, one hop) or `RECURSIVE` with `depth` (1–8, default 3).
- `types` (any subset; empty = all):
  - Javadoc structure: `EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`, `IMPLEMENTED_BY`,
    `DIRECT_SUBCLASS`, `RETURN_TYPE`, `PARAMETER_TYPE`, `CONSTRUCTOR_PARAMETER_TYPE`,
    `THROWS_TYPE`, `FIELD_TYPE`, `OVERRIDES`, `SPECIFIED_BY`, `SEE_ALSO`.
  - Sample code: `USES_TYPE`, `CONSTRUCTS`, `CALLS` (source is an example record ID).
- `data.nodes[]`: keys reached; `data.edges[]`: `{edgeId, sourceId, targetId, type, proof}`.
  Keys are canonical keys for SDK symbols and record IDs for examples.
- Each new node also counts toward `pageSize`, so a page of 10 holds about 5 edges. Follow
  `continuation` for more.
- Sample links exist only where the code proves the target: explicit import, fully qualified
  name, or unique wildcard import, plus a single matching overload for calls. Chained calls
  such as `X.newBuilder().label(…)` link only the first call.

## check_symbols

```json
{"contractVersion":"read-tools/2","symbols":["TLcdLonLatPoint","ILcdShape#contains2D(double,double)","TLspShapeLayerBuilder#newBuilder()"]}
```
- Up to 50 names per call, in the same formats as `resolve_symbol`.
- `data.verdicts[]`: `{input, proof, selectedRecordId, candidateRecordIds, reason}`.
- Envelope `proof`: `EXACT` only if every name is exact. Otherwise it is the worst verdict.

## index_status

```json
{"contractVersion":"read-tools/2"}
```
- `data`: `sdkVersion`, `buildId`, `state` (`ACTIVE`), `capabilities`, `mode` (`LEXICAL`),
  `coverage` `{pages, records, sections}` counts, and contract/schema versions.
