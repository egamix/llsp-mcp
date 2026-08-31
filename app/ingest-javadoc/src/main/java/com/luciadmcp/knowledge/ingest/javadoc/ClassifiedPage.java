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
public record ClassifiedPage(String relativePath, PageType type) {

    public ClassifiedPage {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}
