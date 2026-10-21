---
title: 'Story 2.9: Persist source-faithful API documentation'
type: 'feature'
status: 'done'
route: 'dispatch'
created: '2026-10-21'
review_loop_iteration: 0
baseline_commit: 'd21e3643c6a35b1e158c10540545a58abd1f6e2c'
story_key: '2-9-persist-source-faithful-api-documentation'
after: ['2-8-contract-and-verification-freeze']
context:
  - D:/workspace/luciad-mcp/AGENTS.md
  - D:/workspace/luciad-mcp/_bmad-output/implementation-artifacts/epic-2-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Persist extracted API documentation with its stable symbol identity so bounded fetches still work after restart and removal of original Javadocs. Connect extraction through domain ports to atomic SQLite storage; retain the existing useful-guidance policy when user and source SDK versions differ.

## Boundaries & Constraints

**Always:** Retain signatures, normalized descriptions, parameter/type-parameter docs, returns, exceptions, since/deprecation and truthful anchors. Keep domain-owned IDs, independent source/content hashes, source SDK/build pinning, overload/ambiguity and governance rules. Atomically associate catalog, knowledge metadata and payload; bound reads before materialization. Commit verified substantial slices separately; preserve unrelated edits.

**Approval decision:** Adopt identity/1, DocumentationPayload, typed locators and bounded DocumentationStore for this delivery, preserving read-tools/2 guidance. Authorize the missing storage extension as schema/2. Old stores require rebuild and are rejected unchanged, without automatic migration/deletion. Stable IDs stay unchanged; version schema/runtime/build fingerprints and content capability. Record only this approval scope; unrelated interface/evaluation/release gates remain pending.

**Never:** Reparse HTML during fetch or add search/MCP/lifecycle handlers. Full block/link fidelity and narratives remain 2.10; coverage remains 2.11. No private-corpus acceptance claim, fabricated source locators or mismatch-only SDK warnings.

## I/O & Edge-Case Matrix

| Input/state | Expected behavior | Failure handling |
| --- | --- | --- |
| Modern/legacy type and members; sources removed | Equivalent semantic fields fetch after SQLite reopen | No source-file fallback |
| Overloads, constructors, two source SDKs | Correct stable-ID association and actual locators | No ambiguous selection or foreign-source content |
| Type parameters or absent/legacy anchors | Retain docs and actual anchor, or explicit absence | Never synthesize citation anchors |
| Retry, content-only edit, relocated root | Identical retry; changed content/source digests; stable IDs | Conflicting identity cannot overwrite |
| Failed write between catalog/metadata/content | Entire association rolls back | No orphan or partial record |
| Missing ID, wrong build/source, invalid offset/limit, continuation | Deterministic bounded result or explicit rejection | No scope bypass or unlimited scan |
| Oversize/corrupt persisted content, old schema | Bounded rejection before content allocation/DDL | Preserve existing file on unsupported open |

</frozen-after-approval>

## Code Map

Module paths are under `app/`, using existing packages.

- `domain`: DocumentationPayload:7-128, DocumentationStore:7-23, CatalogStore:22-89 own contracts, hashes and limits.
- `ingest-javadoc`: JavadocFieldExtractor:68-80,106-198,414-432 has prose but ignores type parameters; ExtractedMember lacks actual anchors. Hash bytes parsed with charset detection. JavadocCatalogSource:31-69 retains fields; SymbolRecord.memberIndex associates members.
- `cli/CatalogStage:118-182` discards prose; anchorFor is lookup metadata, not citation proof. Reuse runner/failure policy.
- `catalog-sqlite`: SqliteCatalogStore:88-214,227-315; SchemaCompatibility:9-39. Preserve read-only preflight/byte guards; replace blanket knowledge-row rejection with supported API-content validation.
- Reuse `ingest-javadoc/src/test/resources/javadoc-fixtures/extract` modern/legacy fixtures and catalog/schema/application tests.

## Tasks & Acceptance

- [x] `domain`, `docs/contracts`: document schema/2 encoding and atomic integration ports. Preserve structured DTO roundtrip, source/document/page hashes, 256 sections, 4096 blocks, 1 MiB canonical bytes and page limit 1..50. Bound encoded/returned bytes; version runtime/capability/oracle fixtures and scoped approvals.
- [x] `catalog-sqlite`: implement atomic catalog/metadata/content writes, idempotence/clashes, scoped enumeration and section paging. Validate supported persisted payloads on CLI/startup; test corruption, preflight and injected transaction rollback.
- [x] `ingest-javadoc`, `cli`: carry original-byte hash, actual anchors and type-parameter docs; assemble deterministic sections and wire production atomic persistence. Unsupported or failed content writes cannot report tuple-only success.
- [x] Tests/evidence: shared modern/legacy reopen harness, every matrix row, fresh portability snapshots, tracker/graph refresh and independent complete-diff review.

**Acceptance:** Given synthetic Javadocs, when ingested and removed, then reopened fetches retain every expected field under its original stable ID. Given corrupt content, CLI and pinned startup both reject it. Given relocated builds on Windows/Linux, logical IDs/content match excluding operational paths/timestamps.

## Implementation Notes

User approved this presented spec and scoped contract adoption with “continue” on 2026-10-21. Preserve unrelated docs/README/diagrams. No merge, push or squash.

This is the active parent BMAD step-03 handoff; implement directly from this spec without invoking the skill renderer again. Commit verified cohesive changes separately. Windows Gradle process environment: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir (keep that directory nonexistent). Parent owns final review and fresh platform parity checks.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence and route |
| --- | --- | --- |
| Admitted single section cannot be fetched | medium | Reviewer reproduced a valid 1,048,000-character paragraph accepted by putDocumented but rejected by readSections even at limit=1. The fetch guard reserves 1024 bytes below the canonical cap while storage permits framing overhead. Patch the existing read bound to the bounded admitted encoding cap and add a near-cap reopen regression; no public surface change. |

## Verification

Focused tests per slice, Python contract verifier/oracle, full `app/gradlew.bat --no-daemon check`, fresh Windows/Linux snapshots. Audit matrix tests actually ran. Scoped diff checks, Graft refresh and independent review. Reuse process-local Gradle workarounds.

## Completion evidence

Implementation commits: `e892082`, `6932c23`, `002a3dd`; evidence `504065e`; independent review correction `53b0dad`. The one review finding was patched by aligning bounded read admission to encoded write admission, with an actual near-cap SQLite reopen regression. No findings deferred.

Final full Windows check: 241 tests, zero failures/errors, two existing environment skips. Clean native Linux application suites: 240 tests, zero failures/errors/skips. All eleven new matrix tests ran and passed on both platforms. Python contract verifier passes 122 checks; independent portable known answers pass. Content-inclusive application snapshots match byte-for-byte and as parsed JSON, SHA-256 `576cb508cdc24a8beaa0fd5d44b7eeb7b4ffb5ddcadabe5ef32f1ad7a66c254a`. Graph refreshed after the patch; scoped diff checks pass. Details: [verification](../../docs/contracts/STORY-2-9-VERIFICATION.md). Rich block/narrative extraction and coverage remain their planned next stories; no broader acceptance or serving claim.
