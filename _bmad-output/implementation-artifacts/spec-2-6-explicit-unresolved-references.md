---
title: 'Story 2.6: Explicit unresolved references within the documented SDK'
type: 'feature'
created: '2026-10-20'
status: 'done'
route: 'dispatch'
baseline_commit: '0aad54b2737688f760af995d375f945aaa2fa160'
story_key: '2-6-keep-unresolved-references-explicit-scope-traversal-to-the-documented-sdk'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Pass two reduces unresolved references to failure strings and loses external hrefs, qualification and ambiguity evidence.

**Approach:** Preserve references, persist deterministic evidence beside exact edges, and expose SDK-scoped domain reads without inventing targets.

## Boundaries & Constraints

**Always:**
- Resolve within one completed SDK catalog. Retain original token/locator, valid source provenance, relationship type, SDK scope, status/kind/reason, and sorted candidate IDs.
- Exact selects one catalog target; ambiguous selects none. External/dead explicit locators never fall back to display names. Bare names resolve by FQN or unique simple name, recording the method.
- Preserve canonical keys/edge identities and both inverse pairs. Extract qualified parameter/throws references separately from identity normalization. Ignore primitive/void references.
- Ordinary unresolved references do not abort valid ingestion under either failure policy. Invalid provenance, cross-version IDs, conflicting evidence and storage failures remain build-invalidating. Required recipe dependency failures remain a separate validation concern.
- Additive schemas/contracts remain provisional under the Stories 2.4/2.5 precedent; Story 2.8 owns ratification and compatibility policy. No governance approval is implied.

**Never:** Network/JAR resolution, guessed symbols, changed canonical identities, completeness claims, recipes/MCP handlers, or Story 2.7 recursive traversal. Synthetic fixtures only.

## I/O & Edge-Case Matrix

| Scenario | Expected result |
|---|---|
| Unique local target | Exact evidence and typed edge |
| External href matching a local display name | Preserve href; unresolved; no edge |
| Dead local href or root escape | Unresolved; no name fallback |
| Competing candidates | Ambiguous; sorted scoped IDs; no selected target |
| Missing qualified type, local simple-name match | Unresolved; preserve qualification |
| Legal unresolved reference, fail-fast | Source survives; evidence persists |
| Invalid provenance or cross-version candidate | Build-invalidating; reject evidence |
| Partial retry | Identical evidence/edges; no duplicates |

</frozen-after-approval>

## Code Map

Paths below are under `app/`, using existing module/package roots.

- `cli/.../pipeline/RelationshipsStage.java:155–365,398–459`: replace key/null resolution and collision overwrites.
- `ingest-javadoc/.../JavadocFieldExtractor.java:162–217,649–712`: preserve hrefs; fix annotation selection/root escapes.
- `catalog-sqlite/.../SqliteRelationshipStore.java:128–253`: reuse endpoint validation/idempotence; scope reads.

## Tasks & Acceptance

**Execution:**
- [x] `domain/.../{ReferenceResolution,RelationshipScope,RelationshipStore}.java` — validated evidence with status/kind/reason enums, scoped reads and scope descriptor; preserve audit reads.
- [x] `catalog-sqlite/.../SqliteRelationshipStore.java` — provisional evidence/candidate tables, composite identity, scoped provenance validation, retries and defensive joins.
- [x] `ingest-javadoc/.../{TypeRef,ExtractedMember,JavadocFieldExtractor,SignatureNormalizer}.java` — preserve raw href/token and qualified references independently of identity; match actual type links; classify external/root escapes.
- [x] `cli/.../pipeline/RelationshipsStage.java` — persist explicit outcomes, preserve competing candidates and existing inverse edges.
- [x] `cli/.../pipeline/{IngestPipelineRunner,IngestionReport}.java` — populate existing totals/type-kind cells from storage, including resume; declare SDK scope.
- [x] Corresponding modules' `src/test/java` — every matrix row plus SQLite reopen, version isolation, locator collisions, modern/legacy extraction, annotation links and partial-write retry.

**Acceptance Criteria:**
- Given unresolved/competing references, when storage reopens, then consumers retrieve original evidence, reasons and candidates without guesses.
- Given two SDK versions, when scoped reads request one, then only its results appear with an explicit documented-universe boundary.
- Given a partial retry, when ingestion finishes, then evidence, edges, ordering and counts equal an uninterrupted build.
- Given ordinary unresolved links with valid provenance, when either failure policy runs, then useful records survive; invalid provenance/identity still invalidates the build.

## Implementation Notes

- Implemented additive resolution evidence and candidate tables with indexed occurrence lookup, scoped validation/reads, deterministic outcomes, preserved source references and unchanged exact-edge identities.
- Report summaries derive from persisted evidence, including resumed builds. An unreadable summary on an already-aborted build is `null`, preserving the original failure instead of presenting false zero counts.
- Verification: module tests, dependency-direction checks and full `check` passed. Final XML audit: 193 tests, zero failures/errors, two pre-existing skips (local-corpus and unavailable symlink tests). All matrix-covering tests ran without skips.
- Matrix audit: exact/inverse behavior is covered by `allThirteenStructuralTypesArePresentAfterBothPasses` and `inversePairsAreReconciledInBothDirections`; external/dead/escaping links by `explicitExternalDeadAndEscapingLinksNeverFallbackToLocalNames`; ambiguity by `ambiguousSimpleNameIsLeftUnresolved` and `locatorCollisionsRemainAmbiguousAndVersionScoped`; qualification/failure policies by `ordinaryUnresolvedEvidenceSurvivesBothPoliciesAndResumeReport`; invalid provenance/version candidates by `invalidSourceProvenanceInvalidatesPassTwo` and `ambiguousCandidatesSortDeduplicateAndRejectInvalidProvenance`; retries by `partialEvidenceWriteRetryReproducesUninterruptedEdgesAndEvidence` and `reopenRetryConflictAndVersionIsolation`.
- Windows verification used `GRADLE_USER_HOME=C:\Users\UserAdmin\.gradle`, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, and runtime-only `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir`. The socket directory deliberately does not exist, causing the installed JDK's pipe implementation to use its TCP fallback. No product configuration was changed.
- Review identified two medium findings; both were fixed and regression-tested. No findings deferred. Provisional contracts still require Story 2.8 ratification; no proprietary-corpus validation is claimed.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Route and evidence |
|---|---|---|
| Distinct qualified exceptions lost during linked-note merging | medium | patch — matching only the simple name removes a legitimate declared exception when an unrelated linked unchecked exception shares that name. Preserve both references and cover this concrete case. |
| Report summary read masks handled SQLite failure | medium | patch — reopening the failed catalog during aborted report generation can throw before the existing invalidating outcome/report is returned. Preserve the aborted outcome and diagnostic report when the summary cannot be read. |

Resolution: both patches applied. `linkedUncheckedThrowsNoteDoesNotReplaceDistinctQualifiedDeclaration`, `corruptDatabaseDoesNotMaskHandledInvalidatingFailureOrAbortReport`, and `unreadableResolutionSummaryCannotReturnSuccessfulResult` passed; full verification passed afterward.

## Design Notes

Evidence has no fabricated edge endpoint. Composite occurrence identity uses source/version/type/original token/locator, separate from outcome content; identical retries are no-ops and conflicting content is rejected. Sort/deduplicate candidates. Do not reuse edge hashes.

## Verification

From `app/`: `./gradlew.bat :ingest-javadoc:test :catalog-sqlite:test :cli:test checkDependencyDirection`, then `./gradlew.bat check`. Apply the documented Windows trust-store workaround if required. Expect all checks green, including 13-type/inverse and identity regressions.
