---
name: luciad
description: "Answer Luciad SDK questions and implement or debug Luciad code using source evidence from the local Javadoc MCP. Use for Luciad API selection, signatures and behavior."
---

# Luciad source guidance

Before making Luciad SDK claims or generating Luciad API calls, use the local `luciad` MCP tools. Include `"contractVersion":"read-tools/2"` in every request. The available tools are `search_knowledge`, `resolve_symbol`, `get_record`, `get_relationships`, `check_symbols` and `index_status`.

1. Retrieve relevant knowledge with `search_knowledge`, using the user's task or identifiers. Read returned record IDs, available sections, citations and coverage.
2. Use `resolve_symbol` when exact types, members or overloads matter. Take identifiers from retrieved evidence or the user's code. Keep ambiguous candidates unresolved until evidence selects the required signature.
3. Fetch saved documentation with `get_record` using returned `recordIds`; use `includeContent: true` and relevant sections as needed within the advertised schema/limits. Follow continuation only when necessary for the task.
4. Use `get_relationships` with a `seed` type or member to find what to use next: supertypes and interfaces, implementations and subclasses (`"direction":"IN"`), parameter and return types, overrides and see-also links.
5. Generate an answer or code grounded in that evidence. Cite returned record IDs, actual source SDK/build and source paths/anchors. Preserve missing anchors; do not fabricate links or imply a local source path is an accessible web URL.
6. Before presenting code, pass every Luciad type and member it uses to `check_symbols` in one call. Fix or flag any `UNKNOWN_IN_SCOPE` or `AMBIGUOUS` name.
7. Compile/test externally when a project and tooling are available. Repair failures by retrieving the relevant symbol/documentation evidence. Report whether generated code was actually compiled/tested: source membership alone does not establish correctness.

Call `index_status` when you need to say which Luciad SDK version and build the answers come from.

Start a task such as checking whether a point is inside a shape with this `search_knowledge` request:

```json
{"contractVersion":"read-tools/2","query":"point inside shape"}
```

Use the returned IDs in `get_record`; copy exact symbols from the evidence into `resolve_symbol`. Do not substitute guessed SDK names for missing evidence.

`sourceSdkVersion` and `buildId` are explicit restrictive source filters; use them only when the task calls for that source. Optional `targetSdkVersion` is context, not an implicit source filter. A target/source mismatch never blocks retrieval or triggers an automatic warning: use actual-source citations normally. Only concrete cited target-relevant change evidence warrants a specific compatibility notice, and source proof does not certify tested target compatibility.

Preserve ambiguity, unknown symbols, missing records, unavailable builds, partial coverage, truncation and tool errors. An empty or partial result does not prove an API is absent. When the tools/service are unavailable or evidence is insufficient, state what could not be verified and what evidence is needed; do not silently invent Luciad-specific API details from memory. Ordinary general programming knowledge remains usable. These instructions support retrieval-first behavior; they do not mechanically guarantee agent obedience.
