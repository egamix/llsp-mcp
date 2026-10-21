package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Optional;

/** Policy contracts for future read handlers. Source identity never implies target compatibility. */
public final class SdkGuidancePolicy {
    private SdkGuidancePolicy() {}
    public static final int MAX_NOTICES = 50;
    public record SourceScope(String sdkVersion, String buildId) {
        public SourceScope { require(sdkVersion); require(buildId); }
    }
    public record Citation(SourceScope source, String recordId, SourceLocator locator) {
        public Citation {
            if (source == null || locator == null || locator.kind() != SourceLocator.Kind.SOURCE) throw new IllegalArgumentException("actual source citation required");
            require(recordId);
        }
    }
    public enum ChangeKind { INTRODUCED, REMOVED, BEHAVIOR_CHANGED }
    /** Relevance must be explicitly supported by the cited change; version ordering is never inferred. */
    public record ChangeNotice(ChangeKind kind, String message, List<String> relevantTargets, Citation citation) {
        public ChangeNotice {
            if (kind == null || citation == null || relevantTargets == null || relevantTargets.isEmpty() || relevantTargets.size() > 50) throw new IllegalArgumentException("cited target-relevant change required");
            require(message); relevantTargets = List.copyOf(relevantTargets); relevantTargets.forEach(SdkGuidancePolicy::require);
        }
    }
    public static List<ChangeNotice> notices(Optional<String> targetSdkVersion, SourceScope pinnedSource, List<ChangeNotice> evidence) {
        if (targetSdkVersion == null || pinnedSource == null || evidence == null || evidence.size() > MAX_NOTICES) throw new IllegalArgumentException("bounded policy context required");
        targetSdkVersion.ifPresent(SdkGuidancePolicy::require);
        if (evidence.stream().anyMatch(n -> !n.citation().source().equals(pinnedSource))) throw new IllegalArgumentException("foreign source evidence");
        return evidence.stream().filter(n -> targetSdkVersion.filter(n.relevantTargets()::contains).isPresent()).toList();
    }
    private static void require(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) throw new IllegalArgumentException("bounded nonblank value required");
    }
}
