package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.List;

/**
 * One canonical-symbol record for a single semantic API element (Story 2.3): the type itself, or one
 * of its members (method, constructor, field, enum constant). Never spans more than one element and
 * never merges overloads — two overloaded methods produce two records with two distinct
 * {@link #canonicalKey() keys}.
 *
 * <p><b>The {@link #canonicalKey} is provisional and module-internal.</b> It is deliberately
 * <em>not</em> the AD-18 wire/join key and <em>not</em> the {@code [NEEDS APPROVAL]} Epic-3
 * canonical-symbol serialization grammar. It captures the identity <em>inputs</em> (type FQN, kind,
 * simple name, erased parameter types) explicitly so a later post-approval story can map these
 * records onto the {@code domain} canonical symbol without reshaping anything. Like
 * {@link ClassifiedPage} (2.1) and {@link ExtractedType} (2.2), this is an {@code ingest.javadoc}
 * internal contract — no {@code domain} record is minted or extended here.
 *
 * <p>No SDK-version field (version scoping is Story 2.4) and no HTML-anchor field (Story 2.4) are
 * carried; adding either now would pre-empt those stories.
 *
 * @param canonicalKey         the provisional, module-internal identity key (never null/blank)
 * @param kind                 the element kind ({@link SymbolKind#TYPE} for the type record)
 * @param typeFqn              the enclosing type's fully-qualified name, derived from the page path
 * @param simpleName           the element's bare simple name (type simple name for the type record)
 * @param erasedParameterTypes ordered, generics-erased simple parameter-type names; empty for a type,
 *                             field, or enum constant, and for a no-arg method/constructor
 * @param variants             the four normalized lookup variants (AC 2)
 * @param relativePath         the source page's {@code /}-separated path (back-reference to origin)
 * @param memberIndex          index into {@link ExtractedType#members()} for a member record, or
 *                             {@code -1} for the type-level record (ties a record back to its member)
 */
public record SymbolRecord(
        String canonicalKey,
        SymbolKind kind,
        String typeFqn,
        String simpleName,
        List<String> erasedParameterTypes,
        LookupVariants variants,
        String relativePath,
        int memberIndex) {

    public SymbolRecord {
        if (canonicalKey == null || canonicalKey.isBlank()) {
            throw new IllegalArgumentException("canonicalKey must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (typeFqn == null || typeFqn.isBlank()) {
            throw new IllegalArgumentException("typeFqn must not be blank");
        }
        simpleName = simpleName == null ? "" : simpleName;
        erasedParameterTypes = erasedParameterTypes == null ? List.of() : List.copyOf(erasedParameterTypes);
        if (variants == null) {
            throw new IllegalArgumentException("variants must not be null");
        }
        relativePath = relativePath == null ? "" : relativePath;
    }
}
