package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Optional;

/** Read-only documentation view sharing the validated symbol source's pinned snapshot. */
public interface DocumentationSource extends AutoCloseable {
    SymbolSourceBinding binding();
    String runtimeFingerprint();
    List<String> documentationIdsAfter(String afterId, int limit);
    Optional<KnowledgeRecord> knowledgeRecord(String recordId);
    List<DocumentationPayload.Section> sections(String recordId, int offset, int limit);
    @Override void close();
}
