// mcp -> domain, retrieval (AD-1). The 20 MCP tools + Streamable HTTP (/mcp). Must NOT depend
// on any ingest.* module or reach engines/parsers directly — enforced by
// :mcp:checkDependencyDirection. The MCP Java SDK is wired in Epic 5, not here.
dependencies {
    api(project(":domain"))
    api(project(":retrieval"))
}
