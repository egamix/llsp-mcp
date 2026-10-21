# SDK guidance amendment verification - 2026-10-21

Current interface: read-tools/2. User guidance-policy approval is recorded in sign-offs.json; technical identity/schema/interface ratification remains pending. This implements domain policy/contracts and wire specimens only. Retrieval/MCP integration, private-corpus evaluation and Story 10.1 acceptance are not implemented or claimed.

Executed:

- `python tools/contracts/verify_contracts.py`: PASS, 122 synthetic schema/example/adverse checks. All six requests allow omitted target context and explicit independent source filters; the old conflated request field is rejected. Positive search/resolve/check specimens use target 2023.1 with source 2023.5, exact source identity and no automatic warning. General guidance and a cited change notice validate; uncited, irrelevant, foreign-source, UNVERIFIED and absent-locator notices are rejected. Existing no-build, ambiguity/unknown, bounded payload, citation, family and batch specimens remain exercised.
- `python tools/contracts/portable_fixture.py`: PASS, independent framed-hash known answers. Current runtime digest is `081fc49c705e75755738bf28715be5d2a2300b983d48ac85a55fef7db13faf19`. Record/edge/content/recipe/capability known answers are unchanged. The Java PortableFixtureTest checks the same values.
- `app/gradlew.bat --no-daemon :domain:test`: PASS, 27 tests including four new policy tests.
- `app/gradlew.bat --no-daemon check`: PASS, aggregate XML reports 230 tests, zero failures/errors, two environment skips: ScratchRealDocsTest.classifyRealTree and IngestArgumentContractTest.outputSymlinkedIntoInputDirectoryIsRejected. Source-isolation, persisted metadata corruption, relocation and authenticated traversal continuation regressions pass.
- Scoped `git diff --check`: PASS for application, contracts, contract tools and amended planning/context files. Unrelated user documentation/README/diagrams are preserved.

Used only process-local documented Gradle environment variables; the nonexistent socket directory remains nonexistent. The existing Gradle cache and local Git commits required sandbox escalation. No host setting was added to product configuration.

| Spec matrix row | Current verification |
| --- | --- |
| Target 2023.1/source 2023.5 | SdkGuidancePolicyTest.differentOrOmittedTargetDoesNotCreateNotice; search/resolve/check target-mismatch wire specimens |
| Omitted target | Same policy test; omitted-target request checks for all six tools; nullable response context check |
| Mismatch/missing change evidence has no notice | Empty versionNotices in normal and unavailable-source specimens; no inferred notices in policy test |
| Cited relevant change only | noticesRequireActualCitedRelevantEvidenceAndPinnedSource; positive change-notice specimen and five adverse mutations |
| General guidance | generalGuidanceRetainsActualSourceAndGovernanceWithoutTargetAdmission; general wire specimen |
| Ambiguity/no usable source | Existing exact/ambiguous/unresolved and UNKNOWN_IN_SCOPE domain/storage tests; missing-build and explicit-source unknown-SDK wire specimens; no target-absence claim |
| Foreign records/edges reject | SchemaCompatibilityTest.mixedScopeCatalogAndForeignEdgesRejectCliAndPinnedStartupIdentically; general policy test rejects foreign-source build |

Cursor target/source independence is tested by SdkGuidancePolicyTest.cursorBindsOptionalTargetSeparatelyFromSource. Source-specific identity formats and SQLite schema remain unchanged; the interface bump changes runtime and complete build digests and requires rebuilding incompatible runtime builds. Historical read-tools/1 Windows/Linux application snapshot hashes and mutation evidence remain labeled historical. Fresh application parity for read-tools/2 is separate evidence, not implied by the independent Python recipe oracle.
