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

- [x] `app/domain`, `app/ingest-javadoc`, `app/cli` — version coordinates/serialization, record/edge/evidence recipes and independent digests; portable build hashing includes profile/governed selection. Test known answers/sensitivity.
- [x] `app/domain`, `app/catalog-sqlite`, `app/bootstrap` and tests — version records, SDK, optional symbols, typed locators, evidence, separate relationship/dependency families, knowledge kinds/governance/applicability, payload integration and build metadata. Check compatibility before DDL. Add shared `BuildValidator` port/result/implementation defining identical CLI/startup/activation/reload/rollback/restore reasons; wire existing paths.
- [x] `app/domain`, `app/cli`, `app/mcp`, `docs/contracts/` and tests — version manifest/report coverage, profile, skips, severity and capability fingerprints. Lexical mode needs no model. Unknown/stub SDK or invalidating outcomes block activation/success. Publish exact schemas/examples for `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`, `check_symbols`, `index_status`: errors/proof/scope/count/trace/batch/sections/traversal and query/build/version-bound cursors. Pin finite defaults/caps, page 10/50 and payload 1 MiB. Separate content citations from verdict/status obligations.
- [x] Tests, portable fixtures/runner, `docs/contracts/CHANGELOG.md`, sign-off record — cover matrix/schema/governance; detect hash/identity/scope/ambiguity/resolution/activation mutations. Run synthetic screening separately from Story 10.1 acceptance. Compare Windows/Linux fixtures excluding operational fields; record actual evidence, versions/changes/exemptions and pending/approved sign-offs.

**Acceptance:** Reviewed slices evidence all nine Story 2.8 criteria. Pending sign-off remains a reported gate; tests do not unblock dependent stories automatically.

## Source-content handoff (2026-10-21)

The schema/interface slice also consumes `story-2-8-source-content-contract-input.md`: define documentation block/section payloads, source/absent locators, narrative identity, content hashes, atomic/bounded storage ports and coverage semantics. Ratify these in the existing 2.8 artifacts; no separate schema or automatic sign-off. Stories 2.9–2.11 implement the content path after the relevant gates, not inside this freeze. The original source-fidelity draft is retained as reference-only `source-faithful-javadoc-content-input.md` and must not be executed as a parallel build.

## Implementation Notes

- 2026-10-21: User approved the updated spec including the source-content handoff. Technical ratification is separate and remains pending.
- Local verification environment in `app/`: `GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle`, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir`. Keep that socket directory nonexistent for the installed JDK's TCP fallback. Use process environment only; filesystem/cache access may require escalation.
- Implementation commits: `7436b57`, `c59e202`, `726b5b8`, `851ade7`, `0b12689`, `fa96ba9`, `dc99e9f`, `d85cacc`. Parent audit closed coordinate serialization, collision severity, persisted metadata/resume, bounded startup, model-free output guarding and public-envelope/content/browse gaps with regression evidence.
- Parent verification: fresh Windows `check --rerun-tasks` passed (224 tests, zero failures/errors, two existing environment skips); all 14 named matrix tests ran and passed. Linux domain/parser/SQLite/CLI suites passed on a clean committed-source export (223 tests, zero failures/errors/skips). Windows/Linux actual application snapshots were byte-identical, SHA-256 `2d13d1705cadb127196380dd747129183501d20bb6ffdf15907694ed7ff070d9`. Schema suite passed 87 checks; six selected mutations were detected. See [verification evidence](../../_bmad-output/implementation-artifacts/verification/VERIFICATION.md).
- Temporary Linux JDK/cache/source exports are verification tools only. Human identity/schema/interface ratification remains pending in [sign-offs](../../docs/contracts/sign-offs.json); Story 10.1 acceptance was not run.
- Final post-review verification on `f80d41c`: Windows full `check --rerun-tasks` passed (226 tests, zero failures/errors, two existing environment skips); Linux contract suites passed (225 tests, zero failures/errors/skips). Application snapshot bytes still match SHA-256 `2d13d1705cadb127196380dd747129183501d20bb6ffdf15907694ed7ff070d9`. All six mutations were detected again and source bytes restored. Schema checks remain 87. Both independent review findings were fixed; none deferred.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence and route |
| --- | --- | --- |
| Persisted startup/CLI scope parity | medium | Confirmed: startup passes an SDK-filtered catalog list while CLI passes the complete bounded list; persisted edge validation skips foreign SDK rows. Mixed-scope data therefore yields different validation outcomes. Patch the existing admission call and edge scope branch; add persisted-path regression coverage. |
| Practice/recipe governance bypass | medium | Confirmed: governance requirements inspect only knowledge kind, so independently typed PRACTICE/RECIPE records labeled AUTHORITATIVE_FACT can pass with blank governance. Patch the existing condition to include record type and test the independent combinations. |

Resolution: `f80d41c` fixed both findings. `SchemaCompatibilityTest.mixedScopeCatalogAndForeignEdgesRejectCliAndPinnedStartupIdentically` exercises actual persisted CLI/startup paths; `FreezeContractTest.independentPracticeAndRecipeTypesRequireGovernanceEvenWhenMarkedAsFacts` covers independent kinds/types with an authoritative API fact control. Both tests passed on Windows and Linux in the final runs. No review finding was deferred.

## Verification

- Focused tests per slice, then `app/gradlew.bat --no-daemon check`; inspect XML failures/skips.
- Mutation screening and portable fixture comparison; report unavailable platforms.
- `git diff --check`, `graft build`, independent complete-diff/version review.
- Use established Gradle workaround without host-specific product settings.

## Review Findings

Review scope completed: `app/**` implementation and tests. Other Story 2.8 file groups remain to be reviewed, and the edge-case review layer failed for this chunk.

### Patch
- [x] [Review][Patch] Classify malformed persisted catalog rows through the shared validation result [app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStore.java:169]
- [x] [Review][Patch] Make schema initialization atomic so interruption cannot strand an unrecoverable staging catalog [app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SchemaCompatibility.java:29]
- [x] [Review][Patch] Verify invalidating outcomes persist and remain rejected after reopen [app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunnerTest.java:115]
- [x] [Review][Patch] Align the published MCP contract version with the frozen interface constant [app/mcp/CONTRACTS.md:3]

### Rejected
- [false] SQLite `octet_length()` compatibility concern — the pinned JDBC runtime is 3.53.2.1, and the repository already uses this function in bounded traversal.
- [false] Runtime fingerprint is not compared with the current contract — `StandardBuildValidator` compares the persisted fingerprint against `ContentHashes.runtimeFingerprint()`.
- [false] Null catalog fields pass the metadata preflight — the catalog schema declares all selected columns `NOT NULL`; the separate malformed non-null row finding remains above.
- [false] The persisted validator ignores build-invalidating failures — `markInvalid()` stores a nonempty `validation_reasons` value and `validatePersisted()` maps it to `BUILD_INVALIDATING_OUTCOME`.
- [false] `markInvalid()` can silently lose a failure when no row is updated — a missing `build_metadata` row is independently rejected as `INVALID_IDENTITY`; normal invalidation follows a bound manifest.
- [false] Nested list items are unbounded — `canonicalFields()` caps depth, total fields, and canonical bytes before a payload is accepted.
- [false] `searchableText()` omits locator and link-target metadata — the structured payload retains those fields, and the spec describes searchable text as a projection rather than a full metadata export.
- [false] Blank governance fields and duplicate applicability entries bypass validation — practice/recipe governance is checked by `StandardBuildValidator`, applicability constructors enforce scope and nonblank versions, and no harmful behavior from duplicate versions was demonstrated.
