# Run the luciad server

The luciad server loads your Luciad SDK's Javadoc and sample code and serves them to coding agents
over MCP (Streamable HTTP, optionally stdio) at `http://127.0.0.1:8766/mcp`. Agents connect to it
through the luciad plugin; see [Get started](../README.md#-get-started) for installing that.

Serving needs no embedding model and no access to the original documentation folders: everything
is read from the published index.

## 1. Build

Requires Java 21 or newer. From the repository root:

```powershell
./app/gradlew.bat -p app :cli:installDist     # Linux/macOS: ./app/gradlew -p app :cli:installDist
```

The command is then `app/cli/build/install/cli/bin/cli.bat` (Linux/macOS: `.../bin/cli`). The
examples below call it `cli`.

## 2. Configure

Copy `application.example.yaml` to `application.yaml` and edit it:

```yaml
indexRoot: ./luciad-index        # where published builds are kept; relative to this file
sourceDefault: "2024.0"          # label of the SDK source this server serves
port: 8766                       # HTTP port; agents connect to http://127.0.0.1:<port>/mcp
stdio: false                     # also serve MCP on stdin/stdout
```

Quote values that look like numbers (`"2024.0"`): unquoted, YAML reads them as numbers and the file
is rejected. Unknown keys, duplicate keys and malformed values are rejected too. Every command below
reads `application.yaml` from the working directory, or the file given with `--config <file>`
directly after the command name.

## 3. Ingest

```powershell
cli ingest --javadoc "<install>/docs/reference/LuciadLightspeed" --samples "<install>/samples/src" --guides <empty-dir> --out <staging-dir> --sdk-version 2024.0
```

- `--javadoc`: the SDK's Javadoc root (the folder with `index.html` and `com/`).
- `--samples`: the SDK's sample sources. Every `.java` file becomes searchable example records,
  one per class and one per method or constructor of three or more lines, linked to the SDK
  symbols they use. Point it at an empty folder to skip samples.
- `--guides`: required, not yet used; pass an empty folder.
- `--out`: a staging folder outside every input folder.
- `--sdk-version`: the version label stored with every record; agents see it in citations.

A full LuciadLightspeed SDK (about 17,600 pages and 2,000 sample files) takes 20–30 minutes.
Problems in individual pages or samples, such as members without a description or a sample that
doesn't parse, are recorded in the report and the run continues; add
`--failure-policy fail-fast` to stop at the first one instead. If a run is interrupted, run the
same command again: completed stages are skipped.

Progress goes to stderr. The build ID is the `b-...` folder created under `--out`;
`<out>/<buildId>/ingestion-report.json` lists what was stored, what was skipped and why.

## 4. Publish, activate and serve

```powershell
cli publish-staged <staging-dir> 2024.0 <buildId>
cli activate 2024.0 <buildId>
cli serve
```

- `publish-staged` checks the build and copies it into `indexRoot` as an immutable bundle. Run
  `cli validate 2024.0 <buildId>` to re-check a published bundle at any time.
- `activate` points the server at that bundle.
- `serve` prints `Luciad MCP listening at http://127.0.0.1:8766/mcp` when ready. On a full SDK,
  startup takes about a minute while it validates the bundle. Logs go to stderr.

To update, ingest and publish a new build, then `activate` it while the server runs. The server
notices within two seconds, validates the new build (about a minute for a full SDK) and then
switches to it. Requests already running finish on the old build, and a build that fails
validation is not switched to. Old bundles stay on disk until you delete them.

The server accepts connections from the same machine only (it binds to `127.0.0.1` and rejects
other `Host`/`Origin` headers). Run one server per port.

## Check it from the command line

`cli query <tool> '<json>'` runs one tool call against the active build, the same as an agent would:

```powershell
cli query index_status '{"contractVersion":"read-tools/2"}'
cli query search_knowledge '{"contractVersion":"read-tools/2","query":"dashed line style"}'
cli query resolve_symbol '{"contractVersion":"read-tools/2","symbol":"TLspShapeLayerBuilder"}'
```

Each `query` validates the bundle first, so on a full SDK it takes about a minute; use a connected
agent for interactive testing. In Windows PowerShell 5.1, passing JSON with double quotes to a
`.bat` file can lose the quotes; use PowerShell 7 or a `cmd` prompt if the request is rejected.

## What agents get

Six read-only tools: `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`,
`check_symbols` and `index_status`. Every request field, response shape and limit is listed in the
skill's [tool reference](../plugins/luciad/skills/luciad-sdk/references/tools.md). The server also
sends agents usage instructions when they connect, so a client without the plugin's skill still gets
basic guidance.

Limits that apply to every request: at most 4 concurrent tool calls (others get `WORK_LIMIT` and
may retry), request bodies up to 8 MiB, responses up to 1 MiB, pages of up to 50 items. Paging
tokens (`continuation`) are tied to the request and build, and stop working when the server restarts.

## Known limitations

- Only Javadoc and Java samples are ingested; guides and recipes are not yet.
- Sample-code links cover names the code proves (imports, fully qualified names) and calls on
  receivers with a declared type. Chained calls such as `X.newBuilder().label(...)` link only the
  first call. Text search still finds the rest.
- Five members of `TLspSelectController` in LuciadLightspeed 2024.0 are not stored: their overloads
  differ only in the package of a parameter type (AWT vs JavaFX `MouseEvent`), which current record
  identities can't tell apart. The ingest report lists them.
- A stored link with an empty target can't be returned by `get_record`; that record comes back
  `INAPPLICABLE` with the reason "Stored content is not representable under the current wire schema".
- No rollback, cleanup or backup commands yet; old bundles are kept on disk.
