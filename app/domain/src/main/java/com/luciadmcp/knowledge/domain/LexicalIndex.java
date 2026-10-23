package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Derived lexical discovery only: a score never establishes exact symbol identity. */
public interface LexicalIndex extends AutoCloseable {
    String RECIPE = "lucene-bm25/1;format/1;standard-prose;normalized-symbol;camel/1";
    Set<String> FILTER_FIELDS = Set.of("sourceSdk", "recordType", "knowledgeKind", "package", "elementKind",
            "deprecated", "approval", "owner", "provenance", "approvalEvidence", "product", "applicabilityScope",
            "applicableSdk", "condition", "narrativeKind");
    record Binding(SymbolSourceBinding source, String runtimeFingerprint, String recipe) {
        public Binding { java.util.Objects.requireNonNull(source); ExactSymbolQuery.text(runtimeFingerprint); ExactSymbolQuery.text(recipe); }
        public static Binding of(DocumentationSource source) { return new Binding(source.binding(), source.runtimeFingerprint(), RECIPE); }
    }
    record Query(String text, int pageSize, int candidateLimit, int workLimit, int timeoutMillis,
            Map<String,String> filters, Optional<String> targetSdkVersion, int offset, Set<String> priorityRecordIds) {
        public Query(String text, int pageSize, int candidateLimit, int workLimit, int timeoutMillis, Map<String,String> filters, Optional<String> targetSdkVersion) {
            this(text,pageSize,candidateLimit,workLimit,timeoutMillis,filters,targetSdkVersion,0,Set.of());
        }
        public Query(String text) { this(text, 10, 1000, 5000, 1000, Map.of(), Optional.empty()); }
        public Query {
            if(text==null) throw new IllegalArgumentException("INVALID_REQUEST: query required");
            if(!text.isEmpty()) ExactSymbolQuery.text(text);
            if(pageSize<1 || pageSize>50 || candidateLimit<1 || candidateLimit>10000
                    || workLimit<1 || workLimit>50000 || timeoutMillis<1 || timeoutMillis>5000)
                throw new IllegalArgumentException("INVALID_REQUEST: lexical limits");
            if(offset<0 || offset>=candidateLimit) throw new IllegalArgumentException("INVALID_REQUEST: candidate window offset");
            priorityRecordIds=Set.copyOf(priorityRecordIds);
            if(priorityRecordIds.size()>50) throw new IllegalArgumentException("INVALID_REQUEST: priority IDs");
            priorityRecordIds.forEach(ExactSymbolQuery::text);
            filters=Map.copyOf(filters);
            if(!FILTER_FIELDS.containsAll(filters.keySet())) throw new IllegalArgumentException("INVALID_REQUEST: unsupported filter");
            filters.values().forEach(ExactSymbolQuery::text);
            java.util.Objects.requireNonNull(targetSdkVersion); targetSdkVersion.ifPresent(ExactSymbolQuery::text);
        }
    }
    record Span(int start, int end) {
        public Span { if(start<0 || end<=start) throw new IllegalArgumentException("invalid highlight"); }
    }
    record Hit(String recordId, float score, String sectionId, String label, String excerpt, List<Span> highlights) {
        public Hit { highlights=List.copyOf(highlights); }
    }
    enum CountRelation { EXACT, LOWER_BOUND }
    record Page(Binding binding, Optional<String> targetSdkVersion, List<Hit> hits, long matchedCount,
            CountRelation countRelation, int workCharged, boolean truncated, String reason) {
        public Page { hits=List.copyOf(hits); }
    }
    Binding binding();
    Page search(Query query);
    @Override void close();
}
