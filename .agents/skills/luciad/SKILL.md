---
name: luciad
description: Answer questions about and write, review or debug code for the Luciad SDK (LuciadLightspeed, LuciadFusion, Lucy) using the local `luciad` MCP server, which serves the SDK's Javadoc and shipped sample code. Use whenever a task involves Luciad APIs or class names such as TLcd*, ILcd*, ALcd*, TLsp*, ILsp*, ALsp*, TLcy*, ILcy* — choosing an API, finding sample code, checking signatures, or fixing Luciad compile errors.
---

# Luciad SDK

Luciad's API is large (100k+ members) and changes between versions. Do not write Luciad
class or method names from memory. Look them up with the `luciad` MCP tools, base the answer
or code on what they return, and cite it.

Every request must include `"contractVersion": "read-tools/2"`.

## Tools

| Tool | Use it to | Minimal request |
|---|---|---|
| `search_knowledge` | Find API docs and sample code for a task or keyword | `{"contractVersion":"read-tools/2","query":"dashed line style"}` |
| `resolve_symbol` | Turn a name into the exact type/member, with overloads | `{"contractVersion":"read-tools/2","symbol":"TLspShapeLayerBuilder"}` |
| `get_record` | Read the full docs or sample code for record IDs | `{"contractVersion":"read-tools/2","recordIds":["<id>"],"includeContent":true}` |
| `get_relationships` | Supertypes, implementations, param/return types; sample code using an API | `{"contractVersion":"read-tools/2","seed":"ILcdModel","direction":"IN"}` |
| `check_symbols` | Verify up to 50 names at once before presenting code | `{"contractVersion":"read-tools/2","symbols":["ILcdShape#contains2D(double,double)"]}` |
| `index_status` | Which SDK version and build the answers come from | `{"contractVersion":"read-tools/2"}` |

Full request fields, response shapes and limits: [references/tools.md](references/tools.md).

## Workflows

**Answer "how do I …" / write code**
1. `search_knowledge` with the task in plain words. Results mix `API` records (Javadoc) and
   `EXAMPLE` records (shipped sample methods and classes).
2. `get_record` with `includeContent: true` on the 2–5 most relevant IDs. Sample records
   contain real code in a `CODE` block; API records contain the signature and description.
3. `resolve_symbol` for any type or member you intend to use, to get its exact signature.
   For overloaded methods pick the overload from the returned candidates; never guess.
4. Optional: `get_relationships` from the main type to find what it extends, what
   implements it, or what its methods take and return.
5. Write the code from that evidence. Then run `check_symbols` on every Luciad type and
   member the code uses (one call, up to 50 names). Fix or flag anything not `EXACT`.
6. Compile and run tests if the project allows it; on failure, look up the failing symbol
   again rather than guessing.

**Find sample code for an API**
1. `get_relationships` with `seed` = the type or member, `"direction":"IN"`,
   `"types":["CONSTRUCTS","CALLS","USES_TYPE"]`. Each edge's `sourceId` is an example record ID.
2. `get_record` on a few of those IDs (batch them) with `includeContent: true`.
3. If there are no links, fall back to `search_knowledge` with the class name; sample code
   that only uses the API through chained calls is found by text search, not by links.

**Explain or navigate an API**
- Type hierarchy: `get_relationships` seed = type, default `direction` OUT, types
  `EXTENDS_CLASS`, `EXTENDS_INTERFACE`, `IMPLEMENTS`. Implementations/subclasses: `"direction":"IN"`.
- What a sample uses: seed = example record ID, direction OUT.

**Fix a Luciad compile error** — `check_symbols` the names in the error, `resolve_symbol` the
failing one to see real overloads and owner, then `search_knowledge` / samples for correct usage.

Worked end-to-end session: [references/example-session.md](references/example-session.md).

## Reading results

- `proof`: `EXACT` = exactly one match in this SDK build. `AMBIGUOUS` = several candidates
  (listed); choose with more context, e.g. the parameter list. `UNKNOWN_IN_SCOPE` = not found
  in this build. `INCOMPLETE_EVIDENCE` = budget ran out or the name is underspecified (for a
  method, add the parameter list: `Type#method(double,double)`).
- Not found is not proof the API doesn't exist: it may be in another product or version, or
  not ingested. Say what you could not verify instead of inventing it.
- `truncated: true` with a `continuation` string = more results. Repeat the identical request
  plus `"continuation": "<value>"` only if you need more.
- `index_status` tells you the actual SDK version (`sdkVersion`). If the user targets a
  different version, pass it as `targetSdkVersion`; it never filters results, so mention that
  evidence comes from the served version.

## Citing

Cite what you used, compactly, e.g. `TLspShapeLayerBuilder` (Javadoc
`com/luciad/view/lightspeed/layer/shape/TLspShapeLayerBuilder.html`, SDK 2024.0) or sample
`samples/lightspeed/grid/GeorefGridStyleFactory.java` lines 269–290. Paths are relative to the
SDK's docs/samples folders, not URLs; never invent anchors or links.

## When the tools are unavailable

If the `luciad` server is not connected or errors, say so and tell the user to start it
(`cli serve --config application.yaml`). You may still give general Java help, but mark any
Luciad-specific names as unverified.
