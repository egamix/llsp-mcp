package com.luciadmcp.knowledge.mcp;

/** Shared initialization guidance for both supported MCP transports. */
final class AgentGuidance {
    private AgentGuidance() { }

    static final String INSTRUCTIONS = """
        Before making Luciad SDK claims or generating Luciad API calls, retrieve source evidence: search_knowledge for relevant knowledge, resolve_symbol for exact symbols and overloads when needed, then get_record for saved documentation. Generate grounded answers/code; compile/test externally when available and repair from retrieved evidence. Include contractVersion: "read-tools/2" in every request. Only these three tools are available.

        Preserve returned citations: record IDs, actual source SDK/build, relative paths and existing anchors; never invent anchors or claim a path is an accessible URL. Honor ambiguity, unresolved symbols, missing records, partial coverage, truncation and errors. Missing evidence is not proof an API does not exist. If tools/service are unavailable, report that limitation rather than inventing Luciad-specific API details from memory; ordinary general programming knowledge remains usable.

        sourceSdkVersion and buildId are explicit restrictive source filters. targetSdkVersion is optional context: a target/source mismatch never blocks retrieval or creates an automatic warning. Use actual-source evidence normally; only concrete cited target-relevant change evidence supports a specific compatibility notice. Source proof is not compilation or runtime verification. These instructions guide agent behavior; they do not mechanically guarantee obedience.
        """;

    static String description(String name) {
        return switch (name) {
            case "search_knowledge" -> "Find relevant persisted Javadoc knowledge by query, or browse with mode BROWSE and an empty query. Returns record IDs, snippets and actual-source citations for follow-up get_record. Include contractVersion read-tools/2; source SDK/build filters are restrictive, target SDK is context only.";
            case "resolve_symbol" -> "Resolve a Luciad type or member to exact source symbols and overload candidates before generating API calls. Preserve EXACT, AMBIGUOUS and unresolved evidence; fetch selected record IDs with get_record. Include contractVersion read-tools/2; target SDK does not restrict source evidence.";
            case "get_record" -> "Fetch saved documentation for returned recordIds, optionally bounded sections and includeContent, to ground Luciad answers/code. Preserve per-ID missing/size outcomes, source citations and partial coverage. Include contractVersion read-tools/2; this tool does not compile or execute code.";
            default -> throw new IllegalArgumentException("Unknown read tool: " + name);
        };
    }
}
