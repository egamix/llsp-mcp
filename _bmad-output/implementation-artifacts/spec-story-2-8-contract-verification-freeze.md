---
title: 'Story 2.8: Contract and Verification Freeze'
type: 'feature'
created: '2026-10-21'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '25ee25cb7acb812ab8101b77fa6ae4eaa5fd87d7'
context:
  - D:/workspace/luciad-mcp/AGENTS.md
  - D:/workspace/luciad-mcp/_bmad-output/implementation-artifacts/epic-2-context.md
  - D:/workspace/luciad-mcp/_bmad-output/implementation-artifacts/story-2-8-source-content-contract-input.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Provisional identity/storage/interfaces block downstream work. Build IDs depend on roots; partial IDs depend on order.

**Approach:** Implement identity, schema/validation, interfaces and verification as reviewable slices; present artifacts for technical sign-off.

## Boundaries & Constraints

**Always:** Domain owns ports/contracts; bootstrap wires adapters. Version changes. Reject unsupported stores unchanged, requiring rebuild. Preserve scoped evidence, traversal safeguards and unrelated edits. Commit substantial changes separately.

**Decisions:** Separate identity/content/recipe/runtime digests; roots are operational. Preserve qualified parameters and ambiguity. Partial IDs use source-relative occurrences independent of order; collisions invalidate. Reject required-dependency cycles. Practices require approval/owner/provenance/applicability; candidates cannot activate.

**Never:** Add vectors, Docling, databases, generic multi-SDK, retrieval/MCP handlers or lifecycle orchestration. Define payload integration without implementing the source-fidelity draft. Never invent sign-offs/owners/evidence. Plan approval authorizes implementation/testing; ratification remains explicit.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected behavior | Failure |
|---|---|---|---|
| Portability | Reordered/relocated inputs | Stable logical IDs/build digest | None |
| Sensitivity | Content/recipe/runtime change | Documented digest dimensions change | Reject incompatible resume |
| Uncertainty | Missing/ambiguous link | Evidence; no guessed endpoint | Legal uncertainty |
| Integrity | Unknown SDK/schema, bad identity/dependency | Shared validation reasons | No activation/success |
| Compatibility | Provisional store | Rebuild required before DDL | Preserve bytes |
| Bounds | Excess work/payload, wrong cursor | Finite work, truthful counts/truncation | Typed error |

</frozen-after-approval>

## Code Map

Paths are repository-relative; classes use each module's Java package.

- `app/domain/`: `ContentHashes:48–164`, `IdentityFields:26–89`, `CatalogRecord:25–53`, `ReferenceResolution:7–49`, `IndexManifest:19–32`. Reuse framing, evidence invariants and traversal limits.
- `app/ingest-javadoc/`: `CanonicalSymbolMinter:123–145`, `SignatureNormalizer:131–136,363–366`; preserve constructor/method and anchor distinctions.
- `app/cli/`: `BuildContext:115–179`, `IngestPipelineRunner:154–284`, `IngestionReport:30–198`, `IngestCommand:70–87`; portability/stub/success gaps.
- `app/catalog-sqlite/`: `SqliteCatalogStore:63–105`, `SqliteRelationshipStore:68–119,329–386`; preserve SDK/provenance checks, pinned read-only transactions and metadata-only payload guards.
- `app/mcp/`: documentation only; handlers remain Epic 5. Scope: `epics.md:472–490`, architecture AD-6/11/12/19/20/31–36.

## Tasks & Acceptance

**Execution, in dependency/commit order:**

- [ ] `app/domain`, `app/ingest-javadoc`, `app/cli` — version coordinates/serialization, record/edge/evidence recipes and independent digests; portable build hashing includes profile/governed selection. Test known answers/sensitivity.
- [ ] `app/domain`, `app/catalog-sqlite`, `app/bootstrap` and tests — version records, SDK, optional symbols, typed locators, evidence, separate relationship/dependency families, knowledge kinds/governance/applicability, payload integration and build metadata. Check compatibility before DDL. Add shared `BuildValidator` port/result/implementation defining identical CLI/startup/activation/reload/rollback/restore reasons; wire existing paths.
- [ ] `app/domain`, `app/cli`, `app/mcp`, `docs/contracts/` and tests — version manifest/report coverage, profile, skips, severity and capability fingerprints. Lexical mode needs no model. Unknown/stub SDK or invalidating outcomes block activation/success. Publish exact schemas/examples for `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`, `check_symbols`, `index_status`: errors/proof/scope/count/trace/batch/sections/traversal and query/build/version-bound cursors. Pin finite defaults/caps, page 10/50 and payload 1 MiB. Separate content citations from verdict/status obligations.
- [ ] Tests, portable fixtures/runner, `docs/contracts/CHANGELOG.md`, sign-off record — cover matrix/schema/governance; detect hash/identity/scope/ambiguity/resolution/activation mutations. Run synthetic screening separately from Story 10.1 acceptance. Compare Windows/Linux fixtures excluding operational fields; record actual evidence, versions/changes/exemptions and pending/approved sign-offs.

**Acceptance:** Reviewed slices evidence all nine Story 2.8 criteria. Pending sign-off remains a reported gate; tests do not unblock dependent stories automatically.

## Source-content handoff (2026-10-21)

The schema/interface slice also consumes `story-2-8-source-content-contract-input.md`: define documentation block/section payloads, source/absent locators, narrative identity, content hashes, atomic/bounded storage ports and coverage semantics. Ratify these in the existing 2.8 artifacts; no separate schema or automatic sign-off. Stories 2.9–2.11 implement the content path after the relevant gates, not inside this freeze. The original source-fidelity draft is retained as reference-only `source-faithful-javadoc-content-input.md` and must not be executed as a parallel build.

## Implementation Notes

- 2026-10-21: User approved the updated spec including the source-content handoff. Technical ratification is separate and remains pending.
- Local verification environment in `app/`: `GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle`, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir`. Keep that socket directory nonexistent for the installed JDK's TCP fallback. Use process environment only; filesystem/cache access may require escalation.

## Spec Change Log

## Review Triage Log

## Verification

- Focused tests per slice, then `app/gradlew.bat --no-daemon check`; inspect XML failures/skips.
- Mutation screening and portable fixture comparison; report unavailable platforms.
- `git diff --check`, `graft build`, independent complete-diff/version review.
- Use established Gradle workaround without host-specific product settings.
