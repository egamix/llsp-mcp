# First Javadoc MCP connection

This Java MVP exposes exactly `search_knowledge`, `resolve_symbol` and `get_record` at
`http://127.0.0.1:8766/mcp`, using official MCP Java SDK 2.0.1 Streamable HTTP and
Jetty 12.1 EE11. It serves saved SQLite documentation and Lucene lexical projections.
No embedding model or original input directory is needed for serving.

## Build and configure

Use Java 21 or newer and the repository Gradle wrapper:

```powershell
./app/gradlew.bat -p app :cli:installDist
Copy-Item application.example.yaml application.yaml
```

Edit `application.yaml`: `indexRoot` is the immutable bundle destination,
`sourceDefault` names this configured source, `port` defaults to 8766, and `stdio`
defaults to false. Paths resolve against the YAML file's directory. Unknown keys,
duplicate keys, custom YAML objects and malformed values fail closed. Runtime commands
accept `--config <application.yaml>` immediately after the command; otherwise they read
`application.yaml` in the working directory. Configuration never appears in agent schemas.

For the known Windows Java environment issue, set these before Gradle or client smoke tests:

```powershell
$env:GRADLE_USER_HOME='C:/Users/UserAdmin/.gradle'
$env:GRADLE_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir'
```

## Ingest, publish, activate and serve

Existing `ingest` writes completed staging and a report; it does not activate a build.
The current lexical Javadoc profile still requires the historical samples/guides invocation
paths, but reports those deferred stages as unsupported. Existing empty directories work
for `--samples` and `--guides`. No model directory or `--model` argument is needed.
Supply your local paths:

```powershell
$app='./app/cli/build/install/cli/bin/cli.bat'
& $app ingest --javadoc C:/docs/javadoc --samples C:/docs/samples --guides C:/docs/guides --out C:/index-staging --sdk-version 2023.5
```

Read the resulting `C:/index-staging/<buildId>/ingestion-report.json` for its build ID
and honest coverage. Then substitute that ID below:

```powershell
& $app publish-staged --config application.yaml C:/index-staging 2023.5 <buildId>
& $app validate --config application.yaml 2023.5 <buildId>
& $app activate --config application.yaml 2023.5 <buildId>
& $app serve --config application.yaml
```

Publication copies completed artifacts into a private publication directory, validates
manifest/report/schema/profile/content/index bindings and checksums, then atomically moves
an immutable bundle into `<indexRoot>/<sdkVersion>/<buildId>/`. The versioned `bundle.json`
inventory covers catalog, lexical files, manifest and report. `activate` alone atomically
replaces the versioned `active.json` pointer. Unsupported atomic moves fail safely.
Production ingestion and publication writers use an exclusive root lock; a competing writer
fails explicitly. Publication and activation remain separate operator actions.

Startup rejects an invalid active build. The server checks bounded pointer metadata every
two seconds and admits changed candidates through the same validation policy. Invalid
replacement retains the current build. Each request leases one validated handle; existing
leases survive replacement. Old bundles remain on disk. Staging is never a serving source.
Cleanup, rollback and backup workflows remain later work.

## Connect a client

With the server running, add a Streamable HTTP MCP server with URL
`http://127.0.0.1:8766/mcp`. For Codex CLI:

```text
codex mcp add luciad --url http://127.0.0.1:8766/mcp
codex mcp list
```

A trusted project's `.codex/config.toml` can instead contain:

```toml
[mcp_servers.luciad]
url = "http://127.0.0.1:8766/mcp"
```

In Codex desktop, use Settings > MCP servers > Add server > Streamable HTTP, enter the
URL, save and restart. These instructions follow the [official Codex MCP documentation](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

If `stdio: true`, the same `serve` invocation also enables SDK stdio on its stdin/stdout;
configure a stdio client to launch that command. Diagnostics go to stderr. Keep one local
serving process per configured HTTP port.

## Read and diagnose

Every tool request requires `"contractVersion":"read-tools/2"`. Start with
`search_knowledge` (`query` or `mode: BROWSE`), use `resolve_symbol` for proof, then
`get_record` with stable `recordIds` and `includeContent: true`. The response includes
actual-source citations, governance, source/build, target context, counts and coverage.
The same full envelope appears in MCP text and structured content.

`sourceSdkVersion` and `buildId` restrict the active source. Unavailable explicit scope
returns a typed envelope. `targetSdkVersion` provides guidance context and never filters
source evidence or automatically produces mismatch warnings. Exact proof concerns the
saved source, and does not certify target compilation or runtime compatibility.

CLI diagnostics invoke these same handlers:

```powershell
& $app query --config application.yaml resolve_symbol '{"contractVersion":"read-tools/2","symbol":"p.Widget"}'
& $app query --config application.yaml search_knowledge '{"contractVersion":"read-tools/2","mode":"BROWSE"}'
```

Default pages contain 10 items, with a hard cap of 50. Pass returned continuations with the
same request scope and limits. Cursors authenticate source/build/query/filters/target/budgets;
restart invalidates process-local signing keys. Fetch continuations retain independent section
offsets for every ID in a batch. Document hashes describe original saved content; page hashes
describe the exact returned sections, including metadata-only pages when `includeContent`
is false. Structured code, tables, lists, notes and links are preserved when representable.

Tool admission permits four concurrent calls with no tool waiting queue. HTTP workers and
queue are bounded. Bodies are capped at 8 MiB, echoed serialized request IDs at 4 KiB, and
Host/Origin are restricted to the loopback service. Malformed JSON, duplicate keys and trailing
tokens reject. Final tool responses count text, structured content, escaping and protocol
framing within the requested response budget and the hard 1 MiB cap. Oversized sections or
results produce explicit size outcomes. Batches whose minimum verdict framing cannot fit
are rejected before content admission; admitted batches preserve one verdict per ID.

Partial ingestion coverage is healthy when the stored build is otherwise valid; response
coverage remains explicit. An exhausted fetch budget preserves each batch verdict as
`INAPPLICABLE` with `WORK_LIMIT`, `DEADLINE` or `CANCELLED` reason. This is not a target-SDK
compatibility claim.

One existing storage/wire gap remains explicit: stored documentation permits an empty link
`originalTarget`, while the frozen wire schema requires a nonempty string. Such a fetched
record returns `INAPPLICABLE` with reason `Stored content is not representable under the
current wire schema`, preserving other batch verdicts. Its saved blocks are not rewritten.

This connection is a slice of existing Stories 5.1/5.3/5.5/5.6/5.7 and 6.1/6.2/6.5.
Other tools, producer coverage, technical ratification, controlled proof evaluation,
performance acceptance and production release remain pending.
