# Javadoc MVP connection verification - 2026-10-22

Implemented existing Javadoc slices of Stories 5.7, 5.1, 5.3, 5.5, 5.6 and minimum
6.1, 6.2, 6.5. Broad stories remain in progress; technical ratification is pending.

Focused Java verification passed:

- `ApplicationConfigTest.safeStrictBoundedConfiguration`: defaults, relative paths,
  duplicate keys, unknown keys, unsafe YAML objects and malformed values.
- `ReadToolsBoundaryTest.exactAmbiguityPagingFetchHashesLimitsAndTypedFailures`: real
  SQLite/Lucene, 60 competing aliases and stable resolution pages, 60 persisted sections,
  mixed-length batch continuation, exact page hashes, size/work limits with visible verdicts,
  partial coverage, changed cursor context and duplicate/trailing JSON rejection.
- `MvpLifecycleTest.invalidReloadRetainsOldLeaseAndColdStartRejects`: two genuinely different
  real ingestion builds, atomic activation, old lease surviving replacement, unchanged-pointer
  observation, torn pointer, invalid cold start, exclusive writer and identical corrupt-candidate
  rejection through validate/activate/reload.
- `MvpConnectionTest.publishedImportSurvivesDeletionAndRealSdkConnection`: real Java import,
  original-input removal, immutable publication, official SDK initialize/list/call over Jetty HTTP,
  exactly three tools, actual-source cited search/exact resolution/structured fetch, missing batch ID,
  target context without notices, CLI logical parity, schema-conformant results, text/structured
  parity, actual SDK response size and foreign Origin rejection.
- `McpEndpointAdmissionTest.hostOriginBodyAndConcurrentAdmissionAreBounded`: four concurrent
  admitted SDK calls, fifth typed WORK_LIMIT, foreign Host 421, malformed JSON, oversized
  request ID and body 413. Official SDK still owns protocol handling.

Runnable distribution: `app/gradlew.bat -p app :cli:installDist` passed. Windows verification
used the documented Gradle trust-store and Java loopback workaround in the launch guide.
The parent owns full Gradle check, focused final review and authorized local shape smoke;
those final outcomes are recorded separately after handoff. No proprietary fixture is committed.

The existing empty-link storage/wire mismatch has an explicit per-record INAPPLICABLE outcome;
see the launch guide. No performance, production release, complete supported-content or
technical-sign-off claim follows from these synthetic results.

## Final-check repair

The existing NoNetworkClasspathTest blanket Jetty ban conflicted with the planned inbound
servlet transport. Its exception now names only the seven required server/servlet artifact
families, with stable versioned JAR filenames. Jetty client, HTTP/2 client, WebSocket client,
HTTP-client, browser-automation and crawler families remain rejected, including misleading
filenames that resemble an admitted server artifact. The real classpath test and rejection
matrix both passed. Jetty is pinned to 12.1.14, superseding 12.1.3 after upstream advisory
[GHSA-wjpw-4j6x-6rwh](https://github.com/jetty/jetty.project/security/advisories/GHSA-wjpw-4j6x-6rwh) identified affected releases through 12.1.4. Focused MCP admission/CLI
connection verification and installDist were rerun for the corrected pin.

Post-admission error regressions also pass: tampered continuations and exhausted resolve
work retain actual source/build/capability binding; unknown explicit sources remain unbound.
Canonical resolution requires one proof lookup plus one charged metadata enrichment read.
A fetch batch larger than its candidate budget returns typed WORK_LIMIT before batch admission,
with zero candidates examined and the selected source binding preserved.

Startup and reload now admit exactly the captured pointer snapshot and record that same
snapshot as observed, preventing an intervening activation from being marked observed
while a different build is served. Existing replacement/lease tests and installDist passed
after this repair.

## Parent final verification

- Full Windows Gradle check: PASS, 55 tasks, after narrow inbound-server classpath guard repair and Jetty12.1.14 update.
- Canonical contract verifier: PASS, 127 synthetic schema/example/adverse checks.
- Final focused independent code review: no high-confidence findings; no review deferrals.
- Authorized local-shape smoke:432 discovered HTML pages,3087 persisted/indexed records; ingestion remains PARTIAL (104 complete,110 partial,218 excluded,0 failed pages). This is local development smoke evidence, not controlled evaluation or technical ratification.
- Real immutable bundle publication/activation and official SDK HTTP connection: PASS, protocol2025-11-25 and exactly search_knowledge, resolve_symbol, get_record. Exact ILcdShape, contains2D lexical results and saved structured fetch succeeded. Canonical Python response schema/semantic checks, source local-shape with target2023.1 and no automatic notices, text/structured parity and final response size checks all passed.
- Parent audit fixes have focused regressions: post-admission binding retention, resolve enrichment work, fetch candidate admission. Pointer snapshot consistency repaired without changing the public lifecycle surface.
- Live Java endpoint is local development operator state, not a committed deployment. Proprietary source, database, generated bundle, smoke outputs and local configuration remain outside version control. Prior Python demo stopped.
