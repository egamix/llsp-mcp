package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * One discovered Javadoc page and its structural classification (Story 2.1).
 *
 * <p>This is an {@code ingest.javadoc}-internal contract consumed by later stories, not a persisted
 * {@code domain} record — the domain record field set is a deferred Epic-2 decision. It deliberately
 * carries no extracted fields, canonical symbol, or anchor: those arrive in Stories 2.2/2.3.
 *
 * @param relativePath the page's path relative to the Javadoc root, with {@code /} separators so the
 *                     key is stable across operating systems
 * @param type         the structure-based classification
 */
public record ClassifiedPage(String relativePath, PageType type, Reason reason) {

    public enum Reason { CONTENT, POSITIVE_NON_CONTENT, UNKNOWN_CLASSIFICATION, SOURCE_READ_FAILED, SOURCE_FILE_TOO_LARGE }
    public ClassifiedPage(String path,PageType type) { this(path,type,type==PageType.EXCLUDED?Reason.POSITIVE_NON_CONTENT:type==PageType.UNKNOWN?Reason.UNKNOWN_CLASSIFICATION:Reason.CONTENT); }
    public ClassifiedPage {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (reason == null) throw new IllegalArgumentException("reason required");
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}
