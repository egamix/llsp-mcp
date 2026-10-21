package com.luciadmcp.knowledge.domain;
import java.util.List;
/** The same validation applies to every selection operation. Operation never changes reasons. */
public interface BuildValidator {
    enum Operation { CLI, STARTUP, ACTIVATION, RELOAD, ROLLBACK, RESTORE }
    enum Reason { UNKNOWN_SDK, UNSUPPORTED_SCHEMA, INCOMPATIBLE_RUNTIME, INVALID_IDENTITY, INVALID_SCOPE,
        INVALID_DEPENDENCY, REQUIRED_DEPENDENCY_CYCLE, UNAPPROVED_KNOWLEDGE, INVALID_GOVERNANCE, BUILD_INVALIDATING_OUTCOME, WORK_LIMIT }
    record Input(String sdkVersion, int schemaVersion, String runtimeFingerprint, boolean invalidatingOutcome,
            List<StoredCatalogRecord> catalog, List<KnowledgeRecord> knowledge, List<KnowledgeDependency> dependencies) {
        public Input { if (sdkVersion == null || runtimeFingerprint == null || catalog == null || knowledge == null || dependencies == null) throw new IllegalArgumentException("validation input required"); catalog = List.copyOf(catalog); knowledge = List.copyOf(knowledge); dependencies = List.copyOf(dependencies); }
    }
    record Result(List<Reason> reasons) {
        public Result { reasons = reasons.stream().distinct().sorted().toList(); }
        public boolean valid() { return reasons.isEmpty(); }
    }
    Result validate(Input input, Operation operation);
}
