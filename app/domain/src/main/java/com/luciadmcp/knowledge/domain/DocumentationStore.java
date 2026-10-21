package com.luciadmcp.knowledge.domain;
import java.util.List;
import java.util.Optional;
/** Contract only: bounded atomic storage, implemented by Story 2.9. A failed write changes nothing.
 * Reads return one section page, never materialize an entire unbounded document. All cursors bind
 * SDK, build, record, query and contract versions; incompatible formats require an unchanged rebuild. */
public interface DocumentationStore extends AutoCloseable {
    enum WriteStatus { INSERTED, IDENTICAL, IDENTITY_CLASH }
    WriteStatus putAtomically(KnowledgeRecord record, DocumentationPayload payload);
    Optional<DocumentationPayload> readSections(String recordId, String buildId, String sdkVersion, int firstSection, int limit);
    List<String> enumerateIds(String buildId, String sdkVersion, String afterId, int limit);
    @Override void close();
    static void checkLimit(int limit) { if (limit < 1 || limit > 50) throw new IllegalArgumentException("INVALID_LIMIT"); }
    /** Page: discovered/classified/excluded/failed; record: expected/stored/partial/failed;
     * section: expected/preserved/unsupported/failed. Unknown denominator must remain absent. */
    record Coverage(long discovered, long completed, long excluded, long failed, Optional<Long> expected) {
        public Coverage { if (discovered < 0 || completed < 0 || excluded < 0 || failed < 0 || expected == null || completed + excluded + failed > discovered || expected.filter(v -> v < discovered).isPresent()) throw new IllegalArgumentException("unreconciled coverage"); }
    }
}
