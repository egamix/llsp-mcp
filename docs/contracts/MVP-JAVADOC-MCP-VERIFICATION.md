# Javadoc MVP connection verification — 2026-10-22

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
