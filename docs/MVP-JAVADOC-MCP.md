# First Javadoc MCP connection

This Java server exposes the six read tools `search_knowledge`, `resolve_symbol`, `get_record`,
`get_relationships`, `check_symbols` and `index_status` at `http://127.0.0.1:8766/mcp`, using official MCP Java SDK 2.0.1 Streamable HTTP and
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
`--samples` points at the SDK sample sources (for LuciadLightspeed, `<install>/samples/src`):
every `.java` file becomes searchable example records, one per class and one per method or
constructor of three or more lines, with path and line-range citations. Files that do not
parse are reported and skipped. `--guides` is still required but not yet ingested; an empty
directory works. No model directory or `--model` argument is needed. Recoverable problems
(members without descriptions, unparseable samples) are collected in the report and do not
stop the run; pass `--failure-policy fail-fast` to stop at the first one instead.
Supply your local paths:

```powershell
$app='./app/cli/build/install/cli/bin/cli.bat'
& $app ingest --javadoc C:/Luciad/docs/reference/LuciadLightspeed --samples C:/Luciad/samples/src --guides C:/docs/guides --out C:/index-staging --sdk-version 2024.0
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

- `get_relationships` takes a `seed` (canonical key such as `p.Widget`, a record ID, or any
  name `resolve_symbol` accepts) and returns graph edges: supertypes, implementations,
  subclasses (`direction: IN`), parameter/return/throws types, overrides and see-also.
  `mode: RECURSIVE` with `depth` walks further. Ambiguous or unknown seeds return no edges
  with that proof. `family: KNOWLEDGE_DEPENDENCY` returns `NOT_APPLICABLE` for the Javadoc
  profile.
- `check_symbols` checks up to 50 names in one call and returns a per-name verdict
  (`EXACT` with the record ID, `AMBIGUOUS` with candidate IDs, or `UNKNOWN_IN_SCOPE`).
  Use it on every Luciad name in generated code.
- `index_status` reports the served SDK version, build ID, capabilities, contract versions
  and ingestion coverage counts.

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

Example results have `recordType: EXAMPLE`; fetch one with `get_record` and
`includeContent: true` to get its code. Method records name their parent class record in a
`context` block.

Examples are linked to the SDK symbols they use. To find sample code for an API, call
`get_relationships` with `seed` set to the type or member, `direction: IN` and `types`
`USES_TYPE`, `CONSTRUCTS` or `CALLS`; each edge's `sourceId` is an example record ID. Seeding with
an example record ID and `direction: OUT` lists the SDK symbols that example uses. A name is linked
only when the source proves a single target: fully qualified name, explicit import, same package or
a unique wildcard-import match. A call is linked only when exactly one overload of that name exists
on the receiver's declared type or its nearest declaring supertype. Calls on unknown receivers (for
example chained builder calls) and ambiguous names are not linked; text search still finds them.

Recipes and guides (rest of Epic 3), controlled evaluation (Epic 10), performance
acceptance and production packaging (Epic 8) remain later work.

## Agent guidance and skill installation

Both HTTP and optional stdio initialization return retrieval-first instructions, and each
of the six tools has its own description. Connected clients receive these instructions
even without a skill. They guide the model; they do not guarantee model obedience.

The skill is the folder `.agents/skills/luciad/`: `SKILL.md` (when to use it, the six tools,
workflows for writing code, finding samples and fixing compile errors, how to read results and
cite them), `references/tools.md` (every request field and response shape) and
`references/example-session.md` (a real session against LuciadLightspeed 2024.0).
`agents/openai.yaml` adds Codex dependency metadata.

Install it by copying the whole folder:

- Claude Code: `~/.claude/skills/luciad` (all projects) or `<project>/.claude/skills/luciad`.
- Codex: `~/.agents/skills/luciad`.

Register the MCP server under the name `luciad` (`claude mcp add --transport http luciad
http://127.0.0.1:8766/mcp` or `codex mcp add luciad --url http://127.0.0.1:8766/mcp`), start
`serve`, then restart the agent. The skill loads automatically for Luciad tasks; invoke it
explicitly with `/luciad` (Claude Code) or `$luciad` (Codex). Without the skill, the server's
initialization instructions and tool descriptions still apply, but the agent is less likely to
look things up unprompted or to verify generated code with `check_symbols`.
