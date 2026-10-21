package com.luciadmcp.knowledge.domain;
import java.util.List;
/** Shared finite read-only interface policy; handlers are Epic 5. */
public final class ReadContracts {
    private ReadContracts() {}
    public static final int DEFAULT_PAGE=10, MAX_PAGE=50, MAX_BYTES=1_048_576,
            MAX_BATCH=50, MAX_QUERY_BYTES=2048, MAX_CURSOR_BYTES=4096,
            DEFAULT_CANDIDATES=1000, MAX_CANDIDATES=10000, DEFAULT_WORK=5000, MAX_WORK=50000,
            DEFAULT_EXECUTION_MILLIS=1000, MAX_EXECUTION_MILLIS=5000;
    public enum Error { INVALID_REQUEST, UNKNOWN_SDK, BUILD_UNAVAILABLE, INCOMPATIBLE_BUILD, INVALID_CONTINUATION,
        PAYLOAD_TOO_LARGE, WORK_LIMIT, DEADLINE, CANCELLED, INTERNAL_ERROR }
    public enum Proof { EXACT, AMBIGUOUS, UNKNOWN_IN_SCOPE, INCOMPLETE_EVIDENCE, NOT_APPLICABLE }
    public static String cursorBinding(String tool,String normalizedQuery,String sdk,String build,List<String> filters) {
        return cursorBinding(tool, normalizedQuery, java.util.Optional.empty(), sdk, build, filters);
    }
    public static String cursorBinding(String tool,String normalizedQuery,java.util.Optional<String> targetSdkVersion,String sourceSdkVersion,String build,List<String> filters) {
        java.util.ArrayList<String> fields=new java.util.ArrayList<>(List.of(ContractVersions.INTERFACE,ContractVersions.IDENTITY,tool,normalizedQuery,sourceSdkVersion,build));
        fields.add(targetSdkVersion.orElse(null));
        fields.addAll(filters.stream().sorted().toList()); return ContentHashes.digest("cursor-binding/2",fields);
    }
}
