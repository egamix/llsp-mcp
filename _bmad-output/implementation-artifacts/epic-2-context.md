# Epic 2 Context: Javadoc Knowledge Extraction and Contract Freeze

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Build a faithful, version-scoped catalog and typed relationship graph from local LuciadLightspeed Javadocs, preserving distinct overloads, source provenance and explicit uncertainty. Freeze shared identity, storage, evidence, validation and interface contracts so downstream exact and lexical retrieval can distinguish catalog proof from incomplete evidence without inventing APIs.

## Stories

- Story 2.1: Discover and classify Javadoc pages
- Story 2.2: Parse modern and legacy Javadoc layouts into equivalent fields
- Story 2.3: Build canonical symbol records with overload distinction
- Story 2.4: Persist the versioned canonical catalog
- Story 2.5: Construct the typed relationship graph in two passes
- Story 2.6: Keep unresolved references explicit, scope traversal to the documented SDK
- Story 2.7: Bounded, cycle-safe relationship traversal
- Story 2.8: Contract and Verification Freeze
- Story 2.9: Persist source-faithful API documentation
- Story 2.10: Preserve structured Javadoc and narrative content
- Story 2.11: Account for ingestion coverage and fidelity

## Requirements & Constraints

- Prefer search-index-assisted discovery with recursive HTML fallback. Classify by structure; exclude navigation from member ingestion and retain overview/package narratives separately. Modern and legacy layouts produce equivalent semantic fields. Malformed inputs follow the configured failure policy; build-invalidating outcomes always block activation.
- Keep one primary record per semantic API element, with every overload distinct. Preserve descriptions, signature/type information, parameter/return/throws documentation, lifecycle metadata and truthful source locators, including absent anchors. Partial records require valid provenance.
- Retain original reference token/locator, SDK scope, candidates where applicable, resolution status/kind and reason as retrievable evidence. Missing/external targets remain unresolved; competing matches remain ambiguous with no selected exact target. Relevance never establishes identity or applicability.
- Keep optional target SDK context separate from actual source SDK/build. Missing exact-target docs do not block useful available-source retrieval, resolution or checks. Select one configured usable source within finite budgets and pin its immutable build; explicit source filters are respected and never inferred from target context. Source-specific IDs and same-source graph evidence remain mandatory; combined-source engines remain deferred.
- Direct traversal is one hop; recursive traversal is cycle-safe and deterministic. Bound depth, visited nodes/edges, results, execution and continuation; report scope, limits and truncation without claiming graph completeness. Test cycles, empty graphs and limit boundaries.
- Ordinary permitted unresolved source evidence does not invalidate a build. Missing, incompatible or candidate required recipe dependencies do. Separate SDK traversal from explicitly requested knowledge-dependency traversal.
- Active paths are local, offline and parse-only, without SDK JAR/class-file solving or sample compilation/execution. Lexical operation needs no model. Vectors, training/Docling, broad technique tags and full guide ingestion remain deferred.
- The 2026-10-21 planning amendment is authorized and merged. Story 2.8 owns content contracts; 2.9–2.11 implement them. Planning authorization does not ratify technical schema/identity/interfaces or waive their sign-offs.

## Technical Decisions

- Domain owns immutable semantic contracts, pure versioned identity recipes and ports. Adapters implement storage/search; bootstrap wires them. Shared query/proof/validation services give CLI and MCP equivalent logical behavior.
- Pass one builds the catalog and source locator map; pass two resolves against it. Producer ownership is 13 Javadoc structural types, WORKFLOW_REQUIRES from workflows and USES_TYPE/CONSTRUCTS/CALLS from examples: 17 total. Reconcile the two specified inverse pairs. Curated dependencies use a separate versioned typed family.
- One symbol coordinate contains SDK/product, version, language, namespace, owner/type, member, parameter types and element kind. Aliases aid lookup only. Stable record IDs also cover symbol-less content. SQLite enforces uniqueness; engine IDs, absolute roots and output locations never define record/edge identity.
- Contracts preceding the freeze remain provisional. Freeze symbol serialization, record/edge recipes, source/content hashes, build-recipe and runtime-compatibility fingerprints separately, with known-answer fixtures. Freeze storage/build metadata and an explicit migration or rebuild-only rejection policy for provisional stores. Record type and knowledge kind are independent; candidates stay outside active builds and practices require approval/applicability.
- Version manifests/reports for coverage, modes, skipped stages, severity/completion, SDK identity, compatibility, throughput/batches/errors and resolution counts. Unknown/stub SDK versions and build-invalidating outcomes cannot activate or report success. Checkpoints remain in staging; sealed immutable builds require validation before atomic activation.
- One BuildValidator defines integrity, identity, dependency and capability compatibility across validation, activation, startup, reload, rollback and restore. Freeze competing-writer coordination and interruption/recovery rules before lifecycle implementation.
- Freeze the six read-only tools: search_knowledge, resolve_symbol, get_record, get_relationships, check_symbols and index_status. Common request/result/error/proof/count/batch/trace/continuation contracts need finite work limits before intermediate sets are materialized. Default page 10, cap 50, serialized payload at most 1 MiB; cursors bind query/version/build. Content requires truthful typed citations; verdicts remain visible without content citations. Oversize content needs bounded sections/continuation or an explicit size error.
- Verify portable logical symbols, IDs, catalog, manifests and reports across Windows/Linux, excluding declared operational timestamps/absolute diagnostic locations. Critical tests detect selected mutations in hashing, identity, scope, ambiguity, relationship resolution and activation. Contract-impact changes need version/changelog entries or reviewed exemptions. Human schema/identity/interface sign-offs remain separate gates; scaffolding or planning approval cannot supply them.

## Cross-Story Dependencies

2026-10-21 source-content handoff: read `story-2-8-source-content-contract-input.md` beside this file. Story 2.8 defines payload/locator/hash/schema/limit contracts only; 2.9 implements durable API content, 2.10 adds structured blocks/notes and package/overview narratives, and 2.11 provides reconciled coverage and closing ingestion fidelity checks. Production 4.2 indexes these stored records, not a second HTML parse; 4.6 joins by stable ID for cited results. Define 10.1 before private-corpus curation/tuning. Existing technical sign-offs and validated live-serving gates remain open. The legacy epics/tracker pair remains authoritative; the umbrella source-content input is reference-only.

Completed extraction/catalog acceptance remains historical. The old relationship story summary has obsolete producer counts; it does not authorize extra Javadoc/workflow production. Resolve review/status from actual implementation evidence rather than stale planning labels.

Catalog and relationships precede explicit evidence and bounded traversal; those feed contract ratification. The freeze gates dependent examples/curation, retrieval, shared MCP envelopes and lifecycle validation. The evaluation protocol informs corpus and verification boundaries before tuning. Technical sign-off, implemented proof and release acceptance remain distinct from planning approval.

## Approved SDK guidance amendment — 2026-10-21

Target 2023.1/source 2023.5 yields normal useful guidance with ordinary 2023.5 source citations. No mandatory target prompt, automatic mismatch/UNVERIFIED warning or inferred target API absence. Only concrete, cited introduction/removal/behavior-change evidence relevant to the target supports a specific notice. GENERAL applicability is explicit and preserves governance/provenance; exact identity in a cited source never certifies tested target compatibility. Ambiguity, source-availability limitations and corrupt/foreign build rejection remain honest and unchanged. read-tools/2 implements policy/contracts, not retrieval/MCP handlers; forward stories own integration. User policy approval does not approve pending technical sign-offs. Historical frozen completed-story blocks remain unchanged.
