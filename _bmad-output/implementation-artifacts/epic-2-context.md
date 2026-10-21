# Epic 2 Context: Javadoc Knowledge Extraction and Contract Freeze

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Build a faithful, version-scoped catalog and typed relationship graph from local LuciadLightspeed Javadocs, preserving exact overloads, source provenance and explicit uncertainty. Freeze the shared identity, storage, evidence, validation and interface contracts needed for exact and lexical retrieval so downstream consumers can distinguish catalog proof from incomplete evidence without inventing APIs.

## Stories

- Story 2.1: Discover and classify Javadoc pages
- Story 2.2: Parse modern and legacy Javadoc layouts into equivalent fields
- Story 2.3: Build canonical symbol records with overload distinction
- Story 2.4: Persist the versioned canonical catalog
- Story 2.5: Construct the typed relationship graph in two passes
- Story 2.6: Keep unresolved references explicit, scope traversal to the documented SDK
- Story 2.7: Bounded, cycle-safe relationship traversal
- Story 2.8: Contract and Verification Freeze

## Requirements & Constraints

- Prefer Javadoc search indexes, with recursive HTML discovery as fallback. Classify by page structure; exclude navigation pages from API-member ingestion and retain overview/package narratives separately. Modern and legacy layouts produce equivalent semantic fields. Malformed inputs follow fail-fast or collect-and-continue policy; build-invalidating outcomes always prevent activation.
- Keep one primary record per semantic API element and distinct records for every overload. Preserve truthful relative source paths and anchors, including absent anchors. Partial records remain usable only with valid required provenance.
- Persist resolution evidence with original token/locator, SDK scope, candidates where applicable, status/kind and reason. Missing, external or undocumented targets remain unresolved; competing matches remain ambiguous with no selected exact target. Consumers must retrieve this evidence. Relevance or code outside indexed evidence cannot turn a guess into a verified symbol or edge.
- Bind one explicit or configured SDK version before lookup and one immutable build per request. Another version's symbol is unknown in the selected scope. Combined or migration queries are unsupported for first proof. SDK traversal and explicitly requested knowledge-dependency traversal remain distinct.
- Direct traversal is one hop; recursive traversal is cycle-safe, deterministic and bounded by depth, visited nodes/edges, results, execution and continuation budgets. Report scope, limits and truncation without claiming graph completeness. Test cycles, empty graphs and boundary cases.
- Ordinary permitted unresolved source evidence differs from invalid required recipe dependencies. Missing, incompatible or candidate required dependencies prevent activation; explicit ambiguity/unresolved source evidence alone does not.
- Inputs and dependencies are local/offline. Lexical operation requires no model. Vectors, training/Docling, broad technique tags and full guide ingestion remain deferred. The service statically parses evidence; SDK JAR/class-file solving and sample compilation/execution remain outside it.

## Technical Decisions

- Domain owns immutable semantic contracts, pure versioned identity recipes and ports; adapters implement storage/search and bootstrap wires them. Shared query/proof/validation services give CLI diagnostics and MCP equivalent logical results.
- Pass one builds the complete catalog and source locator map; pass two resolves against it. The 17-type SDK/example/workflow family has separate producers: 13 Javadoc structural types, one workflow type and three example types. Reconcile the specified two inverse pairs. Curated dependencies receive a separate versioned typed family rather than ad-hoc SDK enum additions.
- Use one symbol coordinate covering SDK/product, version, language, namespace, owner/type, member, parameters and element kind. Aliases aid lookup, never identity. Stable record IDs include symbol-less content. Storage enforces uniqueness; engine IDs and absolute roots/output locations never define record/edge identity.
- Evidence, storage and traversal changes before 2.8 are provisional contracts pending its ratification. The freeze owns symbol serialization, stable record/edge recipes, source/content hashes, build-recipe fingerprints and runtime-compatibility fingerprints as separate definitions with known answers. It also freezes schema/build metadata and explicit migration or rebuild-only rejection of provisional stores. Record type and knowledge kind remain independent; candidates cannot enter active builds and practices require explicit governance/applicability.
- Freeze versioned manifests/reports for coverage, modes, skipped stages, severity/completion, SDK version, compatibility, throughput/batches/errors and resolution counts. Unknown/stub SDK versions and build-invalidating outcomes cannot activate or report success. Staging checkpoints remain invisible to serving; sealed builds require validation before atomic activation.
- One BuildValidator defines integrity, identity, dependency and compatibility policy for validation, activation, startup, reload, rollback and restore. Writer locking and interruption/recovery rules are frozen before lifecycle implementation.
- Six read-only tools are search_knowledge, resolve_symbol, get_record, get_relationships, check_symbols and index_status. Freeze common request/result/error/proof/count/batch/trace/continuation contracts and finite internal-work limits. Default page 10, cap 50, serialized payload at most 1 MiB; cursors bind query/version/build. Content requires truthful typed citations; verdicts remain visible without content citations. Bound work before materializing intermediate sets.
- Verify portable logical symbols, IDs, catalog, manifests and reports across Windows/Linux, excluding declared operational timestamps/diagnostic absolute locations. Critical tests detect selected mutations in identity, hashing, scope, ambiguity, resolution and activation. Contract changes require version/changelog entries or reviewed exemptions; schema/identity/interface sign-offs remain explicit human gates.

## Cross-Story Dependencies

Completed extraction/catalog work remains historical; later amendments do not reopen it. The old 2.5 summary contains obsolete producer counts; current delivery uses 13 Javadoc types, with workflow/example production owned elsewhere. Confirm actual review/status evidence before 2.6 rather than interpreting stale planning language as current status.

Evidence work precedes bounded traversal; both feed 2.8 ratification. Contract freeze gates dependent retrieval, examples/curation, shared MCP envelopes and lifecycle validation. The evaluation protocol informs corpus/verification boundaries before curation. Planning approval does not substitute for technical sign-off, implemented proof or release acceptance.
