# Epic 2 Context: Javadoc Knowledge Extraction and Contract Freeze

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Turn local modern and legacy Javadocs into durable, source-faithful, version-scoped API and narrative knowledge with explicit relationship evidence. Freeze the shared identity, storage, validation and interface contracts before dependent implementation. Documentation must survive restart and removal of original inputs, preserving enough meaning and provenance for useful cited answers. Completed acceptance remains historical; planning authorization does not supply pending technical sign-offs.

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

- Discover API pages equivalently through search-index files or recursive HTML fallback. Classify by structure; exclude navigation/help/index content from API extraction, retaining package/overview prose as cited symbol-less narratives.
- Preserve one primary record per semantic API element and distinct overload. Retain signatures, descriptions, parameter/type-parameter documentation, returns, exceptions, since/deprecation, API/implementation notes, original links and truthful locators.
- Preserve owned description blocks, code whitespace, list order/nesting and table header/cell relationships. Searchable text is a projection of retained documentation, not its replacement. External or escaping links remain evidence without fetching or becoming verified local edges; absent anchors stay absent.
- Persist original reference token/locator, source SDK scope, resolution kind/status/reason and candidates. Only exact evidence selects a target; unresolved and ambiguous evidence remains retrievable. Partial records require valid provenance. Invalid required recipe dependencies block activation; ordinary permitted incomplete source evidence does not.
- Traversal is deterministic, cycle-safe and source-version isolated, with explicit direct/recursive scope and finite depth, visited-node/edge, result, time and continuation budgets. Bounded traversal never proves graph completeness.
- Reports reconcile discovered pages with excluded, complete, partial and failed dispositions, and separately reconcile emitted/persisted records and sections. Declare unsupported/oversized inputs, profile, coverage, skips, severity/completion, throughput, errors, batch sizes and grouped resolution counts. Enforce limits before unbounded materialization.
- All active paths are local, offline and parse-only. No model, crawler, external egress, SDK compilation or sample execution is required. Vectors, training/Docling and broad technique vocabulary remain deferred.
- Verify content after store reopen without original sources, atomic content association/rollback, relocation and retry determinism, version isolation and unsupported-schema rejection before mutation. Critical mutations and Windows/Linux known answers must detect identity, hash, ambiguity, relationship and activation regressions. Synthetic CI fixtures exclude proprietary content.
- Contract-impact changes require version/changelog entries or a reviewed exemption. Schema, identity and interface approvals remain explicit; user policy approval cannot substitute for technical sign-off. Ingestion checks alone establish no answer-quality improvement.

## Technical Decisions

- Domain owns immutable logical contracts, pure versioned identity recipes and ports; bootstrap wires in-process SQLite/Lucene adapters. Shared services own proof/query/validation semantics. Retrieval and MCP cannot depend on ingestion/parsers or engine implementations.
- One symbol coordinate contains SDK/product, version, language, namespace, owner, member, parameter types and element kind. Lookup aliases never create alternate identities. Stable record IDs also cover symbol-less content; edge IDs, source/content hashes, build-recipe fingerprints and runtime-compatibility fingerprints have distinct documented purposes. Engine IDs, absolute roots and output locations cannot define logical identity.
- Freeze one versioned schema for records, durable ordered payload sections/blocks, locators, evidence, relationships, governance/applicability/dependencies and build metadata. Define bounded persistence/read/enumeration contracts, narrative identity and content hashing. Provisional stores receive an explicit compatible migration or rebuild-only rejection policy.
- Build catalog and source-locator map before relationship resolution. Current producer ownership is 13 Javadoc structural relation types; workflows own WORKFLOW_REQUIRES and examples own USES_TYPE/CONSTRUCTS/CALLS. Reconcile the specified inverse pairs. Curated dependency relations have a separate versioned family.
- Record type is independent of knowledge kind: authoritative facts, approved practices, recipes and candidate learning. Candidates stay outside active builds; practice approval and recipe dependencies/applicability are validated explicitly.
- Ingestion uses bounded stages and build-scoped staging checkpoints; resume checks source/content and recipe hashes. Sealed builds are immutable. One BuildValidator contract governs validation, activation, startup, reload, rollback and restore, rejecting unknown SDK/schema/profile, corrupt identity and build-invalidating outcomes. Locking, recovery and reader-safe publication are frozen here and implemented downstream.
- Six read-only tools are search_knowledge, resolve_symbol, get_record, get_relationships, check_symbols and index_status. Freeze common request/result/error, count, pagination, batch and trace contracts: default 10 results, cap 50, serialized response at most 1 MiB, plus finite internal budgets. Cursors bind source/build/query/filters/budgets and separate optional target context. Primary content requires typed citations; verdict/status paths remain visible without content citations.
- Pin one actual source SDK/build independently of optional target guidance. Exact proof establishes identity in that source only; missing target documentation cannot block available-source guidance or imply API absence. No guessed overload or foreign-source attribution is allowed.

## Cross-Story Dependencies

- Preserve existing story IDs and tracker history. Story 2.8 ratifies common evidence/traversal contracts after 2.6/2.7 and freezes payload/schema/locator/hash/limit contracts before 2.9. Delivery proceeds 2.9 → 2.10 → 2.11 using one roundtrip harness; the freeze does not absorb all ingestion/search implementation.
- Define evaluation protocol 10.1 alongside 2.8. Reserve held-out questions/sources before private-corpus selection or tuning; synthetic fidelity implementation proceeds independently. Private audits need the corpus owner's independent inventory and applicable approvals.
- Exact proof 4.1 depends on 2.8. Lexical indexing 4.2 consumes retained 2.9–2.11 content through bounded enumeration rather than re-parsing HTML. Result assembly 4.6 joins by stable ID and consumes bounded traversal.
- Shared envelopes/services feed 5.1/5.3/5.7; live serving also requires 5.5/5.6 and minimum 6.1/6.2/6.5. A Javadoc-only connection does not complete broader content support or production acceptance.
