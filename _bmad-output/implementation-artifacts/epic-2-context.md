# Epic 2 Context: Javadoc Knowledge Extraction

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic turns a real Javadoc documentation tree into the system's canonical knowledge core: version-scoped symbol records plus a typed relationship graph. It must handle both modern and legacy Javadoc HTML layouts, discover and classify pages by structure rather than filename, mint one record per API element with overloads kept distinct, persist everything to a versioned catalog, and construct typed relationship edges in two passes without ever fabricating a link it cannot cite. This is the foundation every later epic joins against — examples, guides, training, retrieval, and the MCP tools all reference these canonical symbols — so correctness and determinism here gate the whole system. (Merges what the source brief split into structural parsing and catalog/graph construction, since both touch the same modules with no standalone value between them.)

## Stories

- Story 2.1: Discover and classify Javadoc pages
- Story 2.2: Parse modern and legacy Javadoc layouts into equivalent fields
- Story 2.3: Build canonical symbol records with overload distinction
- Story 2.4: Persist the versioned canonical catalog
- Story 2.5: Construct the typed relationship graph in two passes
- Story 2.6: Keep unresolved references explicit, scope traversal to the documented SDK
- Story 2.7: Bounded, cycle-safe relationship traversal

## Requirements & Constraints

- Page discovery uses the Javadoc search index (`*-search-index.js`) where present and falls back to recursive `*.html` scanning; a tree with and a tree without a search index must yield the same classified page set. Page type is detected by HTML structure, never by filename.
- Nav/tree/help/index pages are excluded from member extraction; overview/package pages are retained as narrative records, not member records.
- Modern and legacy layout families must parse into equivalent extracted fields (description, params, returns, throws, since, deprecated) so downstream code never depends on which generator produced the tree.
- Broken or incomplete HTML degrades according to a configurable `fail-fast | collect-and-continue` policy (inherited from the pipeline scaffold); it must never crash or silently abort the run. The default policy value and the shape of the collect-and-continue error report are an open decision owned by this epic.
- Exactly one record per API element. Overloads are always distinct records with distinct canonical keys; no primary record spans more than one semantic API element.
- Each record retains normalized lookup variants (simple-name, dotted-member, erased-generic, camel-case) in addition to its canonical key.
- Catalog is version-scoped: the same simple name in two SDK versions gets its own version-scoped unique key with no cross-version collision. Every record stores its relative HTML path and anchor, resolvable back to source.
- All required relationship types must be produced with forward/inverse pairs reconciled. Unresolved external or undocumented links are recorded explicitly as unresolved, never guessed or dropped. Traversal is restricted to the documented SDK universe and must report the scope it operated within.
- Recursive traversal must terminate within a configurable depth limit and handle cycles, visiting no node twice in a single result.
- Determinism is mandatory: an interrupted-then-resumed build must produce the same records, edges, and IDs as an uninterrupted build over the same inputs.
- Tests use only synthetic fixtures reproducing the required HTML/source structures (modern/legacy layouts, interfaces, abstract classes, constructors, overloads, generics/arrays, deprecated elements, since/params/returns/throws, inherited-method sections, broken HTML). No proprietary Luciad documentation may be committed.

## Technical Decisions

- **Module ownership:** Javadoc parsing lives in `ingest.javadoc`; the catalog and relationship store live in `catalog.sqlite`; record and relationship-type definitions live in `domain`. All modules depend inward toward `domain`, which depends on nothing; engines are reached only through `domain`-defined ports (`CatalogStore` here).
- **Parse-only:** Use JSoup for HTML and JavaParser for source AST, statically. No compilation, no symbol-solving against JARs, no Maven/Gradle invocation, no execution of any ingested content.
- **Canonical symbol is the single join key:** Every record and edge references API elements by the one canonical symbol form (overloads distinguished by it) minted in `domain`. Exactly one canonical variant is the cross-module wire/join key; all other normalized variants are module-internal and must never cross a port boundary. No module derives its own symbol string.
- **Two-pass ingestion:** Pass one builds the full symbol catalog plus the path/anchor map; pass two resolves links and declarations into typed edges against the completed catalog. A reference that does not resolve to a catalog symbol is recorded as explicitly unresolved — never invented, never silently dropped.
- **Catalog is the sole identity authority:** `catalog.sqlite` computes all record and edge IDs from an `identityFields` spec declared in `domain`, applied to the wire/join key, and deduplicates by computed ID. Ingest code submits resolved logical tuples; it never computes IDs or assumes a hash recipe. IDs are stable content hashes — never random, never wall-clock-derived (reproducibility).
- **SDK-version scoping is mandatory** on every record, edge, and query.
- **Relationship types are a fixed `UPPER_SNAKE_CASE` enum, 17 values total.** This epic's two-pass Javadoc work produces 14 of them — the 13 structural types plus `WORKFLOW_REQUIRES`. The three example-usage edges (`USES_TYPE`, `CONSTRUCTS`, `CALLS`) are emitted later by example ingestion (Epic 3), not here. Structural types: `EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`, `IMPLEMENTED_BY`, `DIRECT_SUBCLASS`, `RETURN_TYPE`, `PARAMETER_TYPE`, `THROWS_TYPE`, `FIELD_TYPE`, `CONSTRUCTOR_PARAMETER_TYPE`, `OVERRIDES`, `SPECIFIED_BY`, `SEE_ALSO`. No ad-hoc additions.
- **Build coordination:** `cli ingest` is the sole build coordinator; it computes the one `buildId`, creates the `{sdkVersion}/{buildId}` directory once, and passes an opaque `BuildContext` to ingest modules. Ingest modules never mint a build ID or create a build directory. Builds are immutable; checkpoints write only to build-scoped staging.
- **Relationship storage:** SQLite with an adjacency representation and recursive CTE traversal for bounded depth-limited queries.
- **Approval gates that constrain this epic's design (must be resolved before the affected work is finalized):** the concrete SQLite catalog schema (DDL, adjacency + recursive-CTE representation) and the canonical-symbol / relationship serialization format (string grammar, on-disk edge format, the designated join variant). Both are governance sign-off gates; do not treat schema choices as free to change silently.
- **Stack pins:** Java 21 LTS (final pin pending blocker B5), JSoup 1.23.1, JavaParser 3.28.2, SQLite JDBC (Xerial) 3.53.2.1, Gradle multi-project (Kotlin DSL, wrapper 8.10).

## Cross-Story Dependencies

- Stories are ordered and largely sequential: discovery/classification (2.1) feeds layout parsing (2.2), which feeds canonical record construction (2.3), which is persisted (2.4); relationship construction (2.5) requires the catalog and path/anchor map from the earlier stories; unresolved-reference handling and scoping (2.6) is part of pass two; traversal (2.7) operates over the persisted graph.
- Depends on Epic 1: the domain records and ports, the module skeleton, the `cli ingest` egress-free entrypoint, the multi-stage resumable pipeline with the `fail-fast | collect-and-continue` policy, and the deterministic ID/hash + ingestion-report machinery all come from Epic 1.
- Feeds forward: Epic 3 (examples, workflows, guides) links against this catalog and emits the remaining three relationship types; Epic 4 indexes these records for hybrid retrieval; Epic 5 exposes them through MCP tools. The catalog and canonical-symbol format defined here are the join substrate for all of them.
