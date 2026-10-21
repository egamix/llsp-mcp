package com.luciadmcp.knowledge.domain;
import java.util.*;
/** Pure shared validator. Missing ordinary source references do not appear as dependencies. */
public final class StandardBuildValidator implements BuildValidator {
    public static final int MAX_RECORDS = 100_000, MAX_DEPENDENCIES = 200_000;
    public Result validate(Input input, Operation operation) {
        Objects.requireNonNull(operation);
        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        String sdk = input.sdkVersion();
        if (sdk.isBlank() || sdk.toLowerCase(Locale.ROOT).contains("stub") || sdk.equalsIgnoreCase("unknown")) reasons.add(Reason.UNKNOWN_SDK);
        if (input.schemaVersion() != ContractVersions.SCHEMA) reasons.add(Reason.UNSUPPORTED_SCHEMA);
        if (!ContentHashes.runtimeFingerprint().equals(input.runtimeFingerprint())) reasons.add(Reason.INCOMPATIBLE_RUNTIME);
        if (input.invalidatingOutcome()) reasons.add(Reason.BUILD_INVALIDATING_OUTCOME);
        if ((long) input.catalog().size() + input.knowledge().size() > MAX_RECORDS || input.dependencies().size() > MAX_DEPENDENCIES) { reasons.add(Reason.WORK_LIMIT); return new Result(List.copyOf(reasons)); }
        Set<String> ids = new HashSet<>();
        for (StoredCatalogRecord r : input.catalog()) {
            if (!sdk.equals(r.record().sdkVersion())) reasons.add(Reason.INVALID_SCOPE);
            try {
                if (!r.recordId().equals(ContentHashes.recordHash(IdentityFields.entityTypeFor(r.record()), IdentityFields.identityValues(r.record()))) || !ids.add(r.recordId())) reasons.add(Reason.INVALID_IDENTITY);
            } catch (IllegalArgumentException e) { reasons.add(Reason.INVALID_IDENTITY); }
        }
        Map<String, KnowledgeRecord> knowledge = new HashMap<>();
        for (KnowledgeRecord k : input.knowledge()) {
            if (!ids.add(k.id()) || knowledge.put(k.id(), k) != null) reasons.add(Reason.INVALID_IDENTITY);
            if (!sdk.equals(k.sdkVersion()) || !k.applicability().sdkVersions().contains(sdk) || !"LuciadLightspeed".equals(k.applicability().product())) reasons.add(Reason.INVALID_SCOPE);
            if (k.governance().approval() != KnowledgeRecord.Approval.APPROVED) reasons.add(Reason.UNAPPROVED_KNOWLEDGE);
            if (k.recordType() == KnowledgeRecord.RecordType.PRACTICE && (k.governance().owner().isBlank() || k.governance().provenance().isBlank() || k.governance().approvalEvidence().isBlank())) reasons.add(Reason.INVALID_GOVERNANCE);
        }
        Map<String, List<String>> graph = new HashMap<>(); Map<String, Integer> indegree = new HashMap<>();
        for (KnowledgeDependency d : input.dependencies()) {
            if (!sdk.equals(d.sdkVersion())) reasons.add(Reason.INVALID_SCOPE);
            if (d.type() != KnowledgeDependency.Type.REQUIRED) continue;
            if (!ids.contains(d.sourceId()) || !ids.contains(d.targetId()) || (knowledge.containsKey(d.targetId()) && knowledge.get(d.targetId()).governance().approval() != KnowledgeRecord.Approval.APPROVED)) reasons.add(Reason.INVALID_DEPENDENCY);
            graph.computeIfAbsent(d.sourceId(), k -> new ArrayList<>()).add(d.targetId());
            indegree.putIfAbsent(d.sourceId(), 0); indegree.merge(d.targetId(), 1, Integer::sum);
        }
        ArrayDeque<String> queue = new ArrayDeque<>(); indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty()) { String id = queue.remove(); visited++; for (String target : graph.getOrDefault(id, List.of())) if (indegree.merge(target, -1, Integer::sum) == 0) queue.add(target); }
        if (visited != indegree.size()) reasons.add(Reason.REQUIRED_DEPENDENCY_CYCLE);
        return new Result(List.copyOf(reasons));
    }
}
