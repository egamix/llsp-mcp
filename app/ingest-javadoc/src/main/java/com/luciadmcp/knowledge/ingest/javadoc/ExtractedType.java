package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.List;

/**
 * The fields extracted from a single {@link PageType#TYPE} page (Story 2.2): the type's own
 * description / {@code @since} / {@code @deprecated}, plus its documented members in source order.
 *
 * <p>An {@code ingest.javadoc}-internal contract, like {@link ClassifiedPage} — not a {@code domain}
 * record (the {@code domain} field set remains a deferred Epic-2 approval gate). No canonical symbol,
 * SDK version, or anchor is minted here (Stories 2.3 / 2.4). Field values are whitespace-normalized so
 * modern and legacy pages over the same API surface compare equal (AC 1).
 *
 * @param relativePath the page's path relative to the Javadoc root, {@code /}-separated (stable across
 *                     operating systems — same value carried by the page's {@link ClassifiedPage})
 * @param description  the type-level description prose (empty if none)
 * @param since        the type-level {@code @since} value (empty if none)
 * @param deprecation  the type-level {@code @deprecated} state ({@link Deprecation#NONE} if not)
 * @param members      the documented members in source order (empty if none)
 */
public record ExtractedType(
        String relativePath,
        String description,
        String since,
        Deprecation deprecation,
        List<ExtractedMember> members) {

    public ExtractedType {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        description = description == null ? "" : description;
        since = since == null ? "" : since;
        deprecation = deprecation == null ? Deprecation.NONE : deprecation;
        members = members == null ? List.of() : List.copyOf(members);
    }
}
