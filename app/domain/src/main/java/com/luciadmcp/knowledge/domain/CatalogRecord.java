package com.luciadmcp.knowledge.domain;

/**
 * A resolved, logical catalog record submitted to {@link CatalogStore} (Story 2.4, AD-22).
 *
 * <p>This is the tuple ingest hands the store: the version-scoping key, the provisional canonical
 * (wire/join) key, the element kind, and the fields needed to persist a version-scoped row that
 * resolves back to source ({@code relativePath} + {@code anchor}). Ingest never computes an ID and
 * never assumes a hash recipe — the store alone computes the record ID from the {@link IdentityFields}
 * spec and deduplicates by it (AD-22).
 *
 * <p><b>Provisional.</b> The {@code canonicalKey} is Story 2.3's module-internal provisional key, not
 * the {@code [NEEDS APPROVAL]} AD-18 wire/join grammar (deferred to Epic 3). Identity inputs are
 * captured explicitly so a later approved grammar maps on without reshaping this tuple.
 *
 * @param sdkVersion   the SDK version this record is scoped to (AD-12); part of the version-scoped key
 * @param canonicalKey the provisional canonical key (AD-3/AD-18 role, provisional grammar)
 * @param kind         the element kind ({@code TYPE}, {@code METHOD}, {@code CONSTRUCTOR},
 *                     {@code FIELD}, {@code ENUM_CONSTANT}); the string form of the ingest enum
 * @param typeFqn      the enclosing type's fully-qualified name (context, not identity)
 * @param simpleName   the element's bare simple name (empty if none)
 * @param relativePath the source page's {@code /}-separated path relative to the Javadoc root
 * @param anchor       the on-page HTML fragment locating the element (empty for a type-level record)
 */
public record CatalogRecord(
        String sdkVersion,
        String canonicalKey,
        String kind,
        String typeFqn,
        String simpleName,
        String relativePath,
        String anchor) {

    public CatalogRecord {
        if (sdkVersion == null || sdkVersion.isBlank()) {
            throw new IllegalArgumentException("sdkVersion must not be blank");
        }
        if (canonicalKey == null || canonicalKey.isBlank()) {
            throw new IllegalArgumentException("canonicalKey must not be blank");
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        if (typeFqn == null || typeFqn.isBlank()) {
            throw new IllegalArgumentException("typeFqn must not be blank");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        simpleName = simpleName == null ? "" : simpleName;
        anchor = anchor == null ? "" : anchor;
    }
}
