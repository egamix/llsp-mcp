package com.luciadmcp.knowledge.domain;
import java.util.List;
import java.util.Optional;
/** Contract only: bounded atomic storage, implemented by Story 2.9. A failed write changes nothing.
 * Reads return one section page, never materialize an entire unbounded document. All cursors bind
 * SDK, build, record, query and contract versions; incompatible formats require an unchanged rebuild. */
public interface DocumentationStore extends AutoCloseable {
    enum WriteStatus { INSERTED, IDENTICAL, IDENTITY_CLASH }
    WriteStatus putAtomically(KnowledgeRecord record, DocumentationPayload payload);
    Optional<SectionPage> readSections(String recordId, String buildId, String sdkVersion, int firstSection, int limit);
    /** Continuations bind the complete read scope and limits; they are not authorization tokens. */
    default Optional<SectionPage> readSections(String recordId, String buildId, String sdkVersion, String continuation, int limit) {
        checkLimit(limit);
        TraversalRequest.boundedText(recordId); TraversalRequest.boundedText(buildId); TraversalRequest.boundedText(sdkVersion);
        try {
            if (continuation == null || continuation.length() > 128) throw new IllegalArgumentException("INVALID_CONTINUATION");
            int separator = continuation.indexOf(':');
            int offset = Integer.parseInt(continuation.substring(0, separator));
            if (offset < 1 || offset >= DocumentationPayload.MAX_SECTIONS
                    || !continuation.equals(continuation(recordId, buildId, sdkVersion, offset, limit))) throw new IllegalArgumentException("INVALID_CONTINUATION");
            return readSections(recordId, buildId, sdkVersion, offset, limit);
        } catch (IndexOutOfBoundsException | NumberFormatException e) { throw new IllegalArgumentException("INVALID_CONTINUATION", e); }
    }
    static String continuation(String recordId, String buildId, String sdkVersion, int offset, int limit) {
        return offset + ":" + ContentHashes.digest("documentation-page/1", List.of(recordId,buildId,sdkVersion,
                Integer.toString(offset),Integer.toString(limit),Integer.toString(ContractVersions.SCHEMA),ContentHashes.runtimeFingerprint()));
    }
    record SectionPage(String recordId, String documentContentHash, String pageContentHash,
            List<DocumentationPayload.Section> sections, Optional<String> continuation) {
        public SectionPage { if(recordId==null || documentContentHash==null || pageContentHash==null || sections==null || continuation==null) throw new IllegalArgumentException("section page fields required"); checkLimit(sections.size()); sections=List.copyOf(sections); if(!pageContentHash.equals(ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)))) throw new IllegalArgumentException("page hash mismatch"); }
    }
    List<String> enumerateIds(String buildId, String sdkVersion, String afterId, int limit);
    @Override void close();
    static void checkLimit(int limit) { if (limit < 1 || limit > 50) throw new IllegalArgumentException("INVALID_LIMIT"); }
    /** Page: discovered/classified/excluded/failed; record: expected/stored/partial/failed;
     * section: expected/preserved/unsupported/failed. Unknown denominator must remain absent. */
    record Coverage(long discovered, long completed, long excluded, long failed, Optional<Long> expected) {
        public Coverage { if (discovered < 0 || completed < 0 || excluded < 0 || failed < 0 || expected == null || completed + excluded + failed > discovered || expected.filter(v -> v < discovered).isPresent()) throw new IllegalArgumentException("unreconciled coverage"); }
    }
}
