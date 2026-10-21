---
title: 'SDK version as guidance context and source provenance'
type: 'feature'
created: '2026-10-21'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
context:
  - D:/workspace/luciad-mcp/AGENTS.md
baseline_commit: '52c6e08576529120c90a78403345e4f5778ea24d'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

User-approved amendment: useful documentation must remain available when the user's SDK differs from the documentation corpus. For a user on 2023.1 with documentation from 2023.5, provide normal useful guidance and put the source version in citations. Do not say the API does not exist or present an intimidating unverified/incompatible warning merely because 2023.1 documentation is missing.

## Boundaries & Constraints

**Always:** Separate optional user target SDK context from actual source SDK/build provenance. Missing exact-target documentation does not block retrieval, resolve or symbol-check evidence from available sources. An exact match proves identity in its cited source, without silently claiming tested compatibility with another SDK. Default answers show useful content and ordinary citations. Only concrete, cited evidence of a target-relevant introduction, removal or behavior change may raise a version notice. No target-version inference from corpus absence, version labels or proximity alone. General guidance may apply without a version restriction.

Preserve finite budgets, immutable source/build pinning, source-specific stable IDs, same-source relationship evidence and ingestion integrity. Missing all usable evidence remains an honest source-availability/not-found outcome, never proof an API is unavailable. Explicit source filters remain possible; never derive a restrictive source filter automatically from target context. Preserve ambiguity and all governance requirements. Record actual approvals accurately: this policy is approved; other technical sign-offs remain pending.

**Never:** Invent compatibility, introduction/removal facts or cross-source graph edges. Do not remove SDK provenance from stored records or weaken corrupt-build rejection. Do not implement retrieval/MCP handlers, a new source registry or generic multi-SDK engine. This amendment defines tested policy/contracts for their owning downstream stories. Preserve completed-story frozen blocks and unrelated documentation edits.

## I/O & Edge-Case Matrix

| Input/state | Expected behavior | Failure policy |
| --- | --- | --- |
| Target 2023.1, source 2023.5, matching API | Useful normal result; 2023.5 source citation | No mismatch error, absence claim or automatic warning |
| Target omitted, usable source | Normal result | No mandatory user SDK prompt |
| Different or missing source version; no change evidence | No compatibility notice | Never generate UNVERIFIED as a default presentation |
| Cited introduction/removal/change relevant to target | Specific useful notice with citation | Uncited/irrelevant notices rejected or omitted |
| General guidance | Usable independently of target version | Approval/provenance still required where applicable |
| Ambiguous API / no available source | Preserve ambiguity / explicit source limitation | Never infer target API absence |
| Foreign records/edges in a single-source build | Existing validation rejects | No integrity relaxation |

</frozen-after-approval>

## Code Map

- `app/domain/.../ReadContracts.java`: existing proof enums/cursor binding; `ContractVersions.java` and `ContentHashes.runtimeFingerprint`: interface version impacts fixtures, build compatibility and cursors. Source identity formats must remain stable.
- `KnowledgeRecord.java`, `StandardBuildValidator.java`: applicability currently mixes source scope and target-version admission. Preserve source scope/governance while allowing explicit general guidance and separating target context.
- `docs/contracts/*.request.schema.json`, response schemas and `common.schema.json`: user SDK is mandatory and conflated with the single evidence SDK. `tools/contracts/verify_contracts.py:19–30` currently requires citations to equal envelope scope; adapt to declared pinned source evidence without permitting fabricated/mixed provenance.
- `docs/contracts/README.md`, CHANGELOG, sign-offs and examples: normative interface behavior and versioned changes.
- Planning `ARCHITECTURE-SPINE.md:82–84,105–107`, `epics.md` FR-16/28 and first-proof policy, PRD FR-16/28: current hard binding contradicts this approved amendment. Update active rules and the epic context; retain history in completed story specs.

## Tasks & Acceptance

- [ ] Domain policy/contracts and tests: separate target context, source proof and evidence-backed notices; test every matrix row. Keep existing storage/relationship validation protections. General applicability must be explicit and must not fabricate API compatibility.
- [ ] Public schemas/examples, verification script, README/CHANGELOG/sign-off evidence: make target SDK optional context, retain exact source scope, define bounded source selection/fallback and per-result provenance. Target mismatch alone cannot cause UNKNOWN_SDK/INAPPLICABLE/negative existence. Represent normal guidance without an automatic unverified warning; include concrete change notices with citations. Version the interface change and update impacted known-answer fixtures/cursor/runtime contracts honestly.
- [ ] Architecture spine, PRD, epics and epic context: reconcile current normative target/source/absence/presentation policy. Amend forward stories, not historical frozen intent. Record this user's policy approval without auto-approving unrelated contracts.

**Acceptance:** Given the 2023.1/2023.5 example, when policy and wire specimens run, then useful cited content passes with no mismatch warning or false absence. Given a target-relevant cited change, then only that evidence supports a specific notice. Given foreign source rows, then existing invalid-scope regressions still pass. Given absent handlers, then documentation accurately says policy/contracts are implemented and downstream integration remains required.

## Implementation Notes

- The user explicitly approved this behavior with “ok” after the concrete normal-answer/citation-only proposal. No unresolved intent choice or irreversible operation remains; continue under that authorization without asking the same policy approval again.
- This is the parent's active rendered step-03 handoff, not a new bmad-build invocation. The renderer has already succeeded once. Implement directly from this spec; the parent owns workflow review/completion.
- Keep verified substantial changes in separate local commits on the existing feature branch. Preserve known unrelated docs/README/diagram edits. No push.
- Windows Gradle process environment in `app/`: `GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle`, `GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir`. Keep that directory nonexistent. No host settings in product configuration.

## Spec Change Log

## Review Triage Log

## Verification

Run focused domain/policy tests and `python tools/contracts/verify_contracts.py`, then full `app/gradlew.bat --no-daemon check`. Update/run portable known answers if interface/runtime fingerprints change; retain relocation and source-isolation regressions. Run scoped `git diff --check`, refresh `graft build`, and independently review the complete amendment diff. No private-corpus or live-handler acceptance claim.
