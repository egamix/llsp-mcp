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
 * <p>Story 2.5 adds the type-level relationship-source references — the direct {@link #superclass}
 * ({@code EXTENDS_CLASS}), the direct {@link #superInterfaces} of an interface
 * ({@code EXTENDS_INTERFACE}), the direct {@link #implementedInterfaces} of a class/enum
 * ({@code IMPLEMENTS}), and the type-level {@link #seeAlso} ({@code SEE_ALSO}) — each carrying its link
 * href where the page rendered one. Pass two resolves them against the completed catalog; nothing is
 * resolved here (AD-4).
 *
 * @param relativePath          the page's path relative to the Javadoc root, {@code /}-separated (stable
 *                              across operating systems — same value carried by the page's
 *                              {@link ClassifiedPage})
 * @param description           the type-level description prose (empty if none)
 * @param since                 the type-level {@code @since} value (empty if none)
 * @param deprecation           the type-level {@code @deprecated} state ({@link Deprecation#NONE} if not)
 * @param members               the documented members in source order (empty if none)
 * @param superclass            the direct superclass reference for a class/enum, or {@code null} if none
 *                              (an interface, {@code java.lang.Object}, or an undocumented header)
 * @param superInterfaces       the direct super-interface references of an interface (empty otherwise)
 * @param implementedInterfaces the direct interface references implemented by a class/enum (empty for an
 *                              interface)
 * @param seeAlso               the type-level {@code See Also} references (empty if none)
 */
public record ExtractedType(
        String relativePath,
        String description,
        String since,
        Deprecation deprecation,
        List<ExtractedMember> members,
        TypeRef superclass,
        List<TypeRef> superInterfaces,
        List<TypeRef> implementedInterfaces,
        List<TypeRef> seeAlso) {

    public ExtractedType {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        description = description == null ? "" : description;
        since = since == null ? "" : since;
        deprecation = deprecation == null ? Deprecation.NONE : deprecation;
        members = members == null ? List.of() : List.copyOf(members);
        superInterfaces = superInterfaces == null ? List.of() : List.copyOf(superInterfaces);
        implementedInterfaces = implementedInterfaces == null ? List.of() : List.copyOf(implementedInterfaces);
        seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
    }

    /**
     * Back-compatible constructor for callers that carry no relationship-source references (Stories
     * 2.2/2.3 and their tests): no superclass, no interfaces, no see-also.
     */
    public ExtractedType(
            String relativePath,
            String description,
            String since,
            Deprecation deprecation,
            List<ExtractedMember> members) {
        this(relativePath, description, since, deprecation, members, null, List.of(), List.of(), List.of());
    }
}
