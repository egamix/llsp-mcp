package com.luciadmcp.knowledge.domain;
import java.util.List;
import java.util.Optional;
/** Symbol-less narratives and governed practices share a stable domain ID, never an engine ID. */
public record KnowledgeRecord(String id, String sdkVersion, RecordType recordType, KnowledgeKind knowledgeKind,
        Optional<SymbolCoordinate> symbol, SourceLocator locator, Governance governance, Applicability applicability) {
    public enum RecordType { API, NARRATIVE, RECIPE, PRACTICE }
    public enum KnowledgeKind { AUTHORITATIVE_FACT, EXPERT_APPROVED_PRACTICE, RECIPE, CANDIDATE_LEARNING }
    public enum Approval { CANDIDATE, APPROVED }
    public record Governance(Approval approval, String owner, String provenance, String approvalEvidence) {
        public Governance { if (approval == null || owner == null || provenance == null || approvalEvidence == null) throw new IllegalArgumentException("governance required"); }
    }
    public enum ApplicabilityScope { VERSION_SCOPED, GENERAL }
    public record Applicability(String product, List<String> sdkVersions, List<String> conditions, ApplicabilityScope scope) {
        public Applicability(String product, List<String> sdkVersions, List<String> conditions) {
            this(product, sdkVersions, conditions, ApplicabilityScope.VERSION_SCOPED);
        }
        public Applicability {
            if (product == null || product.isBlank() || sdkVersions == null || conditions == null || scope == null) throw new IllegalArgumentException("applicability required");
            sdkVersions = List.copyOf(sdkVersions); conditions = List.copyOf(conditions);
            if (sdkVersions.stream().anyMatch(String::isBlank) || (scope == ApplicabilityScope.GENERAL && !sdkVersions.isEmpty()) || (scope == ApplicabilityScope.VERSION_SCOPED && sdkVersions.isEmpty())) throw new IllegalArgumentException("explicit applicability scope required");
        }
    }
    public KnowledgeRecord {
        if (id == null || id.isBlank() || sdkVersion == null || sdkVersion.isBlank() || recordType == null || knowledgeKind == null || symbol == null || locator == null || governance == null || applicability == null) throw new IllegalArgumentException("record fields required");
        if (symbol.isPresent() && !sdkVersion.equals(symbol.get().sdkVersion())) throw new IllegalArgumentException("symbol scope mismatch");
    }
    public static String narrativeId(String sdkVersion, String relativePath, String narrativeKind) {
        return ContentHashes.digest("narrative/1", List.of("LuciadLightspeed", sdkVersion, relativePath, narrativeKind));
    }
}
