# Story 2.11 verification

All specimens below are synthetic. No private SDK corpus, private independent inventory or
held-out material was accessed. Private fidelity execution remains NOT_RUN. Technical
ratification, Story 10.1 acceptance and unrelated human approvals remain PENDING.

## Implemented evidence

- Mixed API/package/overview/navigation/unknown pages reconcile into disjoint dispositions;
  records and assembled payload sections reconcile independently. Retrying replaces the
  ledger and identical puts count once.
- Missing, corrupt or mismatched sidecars restore as unavailable; clean resume preserves
  sidecar bytes and measured coverage. Coverage affects report/2 completion.
- A real SQLite trigger abort exercises rollback after the tuple insert. No tuple/payload
  survives that atomic write, remaining pages are explicitly unprocessed, and retry restores
  five logical persisted associations. A real changed-source identity clash counts three
  failed associations while preserving the original stored payload bytes.
- Existing modern/legacy structure, notes, code whitespace, lists, tables, links, actual
  anchors, citations and source-version scope survive source deletion and store reopening.
  Coverage can also restore after source deletion.
- Source reads admit exactly 8 MiB and reject 8 MiB + 1. Streaming file SHA-256 matches the
  previous original-byte recipe. The actual incremental enumeration budget admits exactly
  100,000 files, 4,096 UTF-8 path bytes and 16 MiB aggregate path bytes, rejecting the next
  item/byte. Budget tests drive the same admission branch without requiring 100,001 disk files.
- Oversized source and unsupported authored structures cannot claim complete. Authored
  doc-file pages remain unknown even when their heading resembles an API type.
- Independent audit specimens detect missing/changed notes, blocks, members, narratives and
  sections, equal-count changes, wrong build/SDK, actual payload corruption and stale hash
  columns. Read-only execution preserves original SQLite bytes. All seven private gates are
  tested before protected input/store opens, and diagnostics exclude identifiers/body/errors.

## Executed commands

```text
app/gradlew.bat -p app :domain:test :ingest-javadoc:test :cli:test --console=plain
app/gradlew.bat -p app :cli:test --tests '*CoverageIntegrationTest' --tests '*StructuredDocumentationRoundtripTest' --console=plain
app/gradlew.bat -p app check --console=plain
python tools/contracts/verify_contracts.py
python tools/contracts/portable_fixture.py
python -m unittest discover -s tools/contracts -p test_audit_ingestion.py -v
python tools/contracts/audit_ingestion.py --synthetic --inventory docs/contracts/fixtures/ingestion-audit.synthetic.json --store build/story-2-11-audit/catalog.sqlite
python tools/contracts/audit_ingestion.py --inventory nonexistent-private-inventory --store nonexistent-private-store
```

Focused and full Java checks passed. Final Gradle check: 265 discovered tests, 264 passed,
0 failures/errors and 1 pre-existing skip (IngestArgumentContractTest.optionalTrainingsIsAcceptedAndPassedThrough). Module totals: domain 30, ingest-javadoc 84,
catalog-sqlite 42 and CLI 109. Contract/oracle checks: 127 passed. Audit unittest
specimens: 14 passed. The audit of the real Java-ingested independent fixture returned PASS,
comparing one record and one actual section. Default private mode returned NOT_RUN with
seven missing gates before any input access, as expected (nonzero exit).

Use GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle,
GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT and
JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir
for the local Windows Gradle workaround; keep that Unix socket directory nonexistent.

Fresh native Windows logical snapshot: build/story-2-11-windows-snapshot.json, produced by
ApplicationPortabilitySnapshotTest via LUCIAD_CONTRACT_SNAPSHOT. It includes report/2,
coverage and actual catalog/documentation bytes. Independent native Linux execution and
cross-platform comparison are owned by the parent build verification step and are not
claimed here. Logs are retained under app/build/story-2-11/ (ignored build output).

## Limits and interpretation

A section is emitted once its payload is assembled; unknown sections of unprocessed or
rejected records are not invented. Independent denominators remain null in ingestion reports.
An unavailable sidecar on a skipped catalog reports PARTIAL rather than silently reconstructing
counts from the database. Explicit catalog reruns replace coverage. Enumeration failure aborts
before a complete admitted universe exists, so it cannot supply a trustworthy page denominator.
Audit PASS covers only the explicitly independent complete inventory supplied to the audit;
it is neither a completeness oracle for an unknown private corpus nor technical ratification.
