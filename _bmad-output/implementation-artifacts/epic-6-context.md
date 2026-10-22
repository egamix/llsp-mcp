# Epic 6 Context: Shared Validation and Index Lifecycle

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Make index publication and serving reliable through one validation policy, immutable builds and atomic activation. The minimum Javadoc serving path needs validated activation, consistent compatibility checks and CLI/MCP conformance before first-proof acceptance; reader-safe rollback and complete backup/restore remain required for production release.

## Stories

- Story 6.1: Immutable builds with shared validation and atomic activation
- Story 6.2: Reject incompatible builds on every entry path
- Story 6.3: Reader-safe rollback and retention
- Story 6.4: Manifest-complete backups and validated restore
- Story 6.5: Shared diagnostics and service conformance

## Requirements & Constraints

One BuildValidator governs CLI validation/activation and MCP startup/reload, rollback and restore, with equivalent rejection reasons for identical invalid artifacts. Validate integrity, schema/version, domain identities, governance, required dependencies and declared capability compatibility before publishing a sealed build. Reject unknown/stub SDK versions, unknown schemas/profiles, corrupt data, identity collisions, invalid approval records, candidate promotion, invalid required recipe dependencies and every BUILD_INVALIDATING outcome. Contract-permitted unresolved or ambiguous source references remain explicit evidence and do not automatically invalidate a build; ambiguity of identity must not be confused with source uncertainty.

Lexical profiles explicitly require no embedding model. Missing undeclared vector assets are not a lexical failure; missing or mismatched declared assets are failures. Vector/model checks apply only after an approved capability is declared. Deferred vectors, training and broad vocabulary must not enter startup or become hidden lifecycle dependencies. A migration creates a separately validated new build; historical validation never bypasses current compatibility policy.

Publish only immutable sealed builds. Serialize competing writers, isolate unfinished staging from serving and enforce interruption/recovery contracts. Never mutate active content. Each request pins one immutable source/build; invalid replacement preserves valid current service, while invalid cold start fails clearly. Retain current and previous compatible builds where storage allows; never delete data while readers reference it. Preserve an explicit source default and isolated version scopes.

Backup includes manifest, catalog, Lucene directory, versioned report, configuration, checksums and every profile-declared asset together. Explicitly absent lexical models are valid; missing required assets, incomplete or tampered bundles cannot restore. Revalidate restored and rollback candidates against current runtime compatibility.

CLI diagnostics and MCP must produce equivalent logical query/proof/coverage and validation outcomes for identical scope, apart from transport wrapping. BUILD_INVALIDATING produces non-success and prevents activation. Classify report-write failure so reported success cannot conceal missing required evidence. The service never compiles generated code or samples; compilation/testing belongs to an external agent/evaluation environment.

## Technical Decisions

Keep one Java/Gradle process, domain-owned ports, embedded SQLite/Lucene and shared query/proof/validation services; no new runtime or database. CLI/bootstrap constructs adapters. `application.yaml` is the sole operator runtime configuration input, with redacted logging and no source bodies by default. Administrative controls never enter read-only agent tool schemas.

The ingestion coordinator alone creates build-scoped staging and portable build identity and seals the manifest once. Hash source content, governance inputs, selected capability profile and versioned build recipe; absolute host/output roots do not define portable identity. Stable record/edge identities, source/content hashes, build-recipe fingerprints and runtime-compatibility fingerprints have distinct purposes. Manifest/report/checkpoint formats and compatibility versions must be explicit. Stage catalog → relationships → selected content → lexical indexing → validation → atomic activation; declare deferred stages skipped. Resume only matching input/recipe hashes; changed inputs require a new build, and interrupted/resumed logical output must match uninterrupted output.

CLI alone mutates the active pointer through a fully written temporary pointer and atomic rename with writer coordination. Serving validates a candidate immutable handle before publishing it to readers. Freeze locking, pointer observation, recovery, retention and reader-lifetime rules in 2.8 before implementation; do not invent platform-specific durability promises or accept a demo bypass.

Optional user target SDK is guidance context, separate from actual source selection. Choose one configured usable source deterministically within finite budgets and respect explicit source/build filters. Missing exact-target documentation must not prevent available-source retrieval or imply API absence. Source-specific IDs, citations and same-source graph evidence remain mandatory; reject corrupt/foreign build data. Combined-source and migration engines remain deferred. Target/source mismatch alone does not invalidate a usable source build or justify an automatic warning; exact source evidence still does not certify tested target compatibility.

Use synthetic fixtures for invalid schemas/profiles/assets, identity/version/hash changes, governance/dependency rejection, source isolation and explicit unresolved evidence. Verify interruption, torn writes, competing writers, reload during requests and all alternate entry routes; Windows/Linux logical known answers should agree. Contract changes need version/changelog entries or reviewed exemptions. Proprietary corpora are never committed.

## UX & Interaction Patterns

Operator diagnostics and `index_status` expose active SDK/build, schema/fingerprints, capability profile, coverage, skips/errors and ingestion time without dumping content. No-model lexical operation reports healthy. Diagnostic command spelling and the transition from historical model/training CLI flags require explicit 2.8 contracts; training import returns a clear unsupported-capability outcome and must not silently disappear.

## Cross-Story Dependencies

2.8 approval gates shared validation, manifest/report/checkpoint/identity formats, capabilities, finite bounds and lock/recovery/retention semantics. Planning approval does not substitute for technical sign-off. 6.1/6.2 provide the common lifecycle policy; 6.5 integrates equivalent diagnostics and transport conformance. Their minimum implementation is required by 5.6, alongside 5.5 transport and 5.7 envelopes, before live first-proof acceptance. Javadoc source fidelity flows from 2.9–2.11 through shared 4.x retrieval and 5.x handlers; minimum lifecycle cannot be postponed behind a fixture-only connection.

6.3/6.4 depend on 6.1/6.2 and remain release obligations with 8.2 packaging. Retain their full scope even when prioritizing minimum serving. Reference hardware/corpus must be approved before NFR sign-off; corpus/legal/distribution, named owners, formal Java pin and release approvals remain open. Evaluation protocol approval precedes tuning and trials; successful first connection or product proof does not close these gates.

Retained original documentation and app-served clickable citations are explicitly deferred discovery work. Future refinement must coordinate snapshot identity, manifest assets, retention and cleanup with lifecycle, but no current endpoint/storage/backup contract or new story acceptance is authorized by that feature entry.
