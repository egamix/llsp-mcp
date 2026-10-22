package com.luciadmcp.knowledge.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The AD-22 {@code identityFields} spec: the ordered identity fields, per catalog entity, from which
 * {@code catalog.sqlite} computes a record ID (Story 2.4). {@code domain} declares the recipe; the
 * store is the sole code that applies it (via {@link ContentHashes#recordHash(String, List)}) and the
 * sole authority that assigns IDs. Ingest never computes an ID and never assumes a hash recipe.
 *
 * <p><b>Recipe.</b> A catalog record is identified by its provisional canonical (wire/join) key
 * scoped to an SDK version (AD-12) — the ordered fields {@code (canonicalSymbol, sdkVersion)}. Because
 * the SDK version is part of the identity, the same simple name in two SDK versions hashes to two
 * distinct IDs and never collides. The entity type (type-level vs. member) is folded into the hash by
 * {@code recordHash} so the two occupy separate hash spaces even when their fields coincide.
 *
 * <p>The type-level record maps to the {@link ApiElement} entity; every member record (method,
 * constructor, field, enum constant) maps to the {@link MethodElement} entity. Both currently identify
 * by the same ordered fields; keeping them as distinct entity names honors the domain record types the
 * spec references and leaves room for the entities to diverge without reshaping this recipe.
 *
 * <p>This is a provisional recipe applied to Story 2.3's provisional key; it does not stamp that key
 * as the {@code [NEEDS APPROVAL]} AD-18 wire grammar (deferred to Epic 3).
 */
public final class IdentityFields {

    /** Entity name for a type-level catalog record (maps to {@link ApiElement}). */
    public static final String ENTITY_API_ELEMENT = "ApiElement";

    /** Entity name for a member catalog record — method/constructor/field/enum constant ({@link MethodElement}). */
    public static final String ENTITY_METHOD_ELEMENT = "MethodElement";

    private static final String FIELD_CANONICAL_SYMBOL = "canonicalSymbol";
    private static final String FIELD_SDK_VERSION = "sdkVersion";

    // Ordered identity fields. Both entities identify by the canonical (wire/join) key scoped to an
    // SDK version (AD-12, AD-22). The order here is the single source of truth for identityValues().
    private static final List<String> API_ELEMENT_FIELDS = List.of(FIELD_CANONICAL_SYMBOL, FIELD_SDK_VERSION);
    private static final List<String> METHOD_ELEMENT_FIELDS = List.of(FIELD_CANONICAL_SYMBOL, FIELD_SDK_VERSION);

    private IdentityFields() {
    }

    /** Domain-owned stable ID, independent of source location and documentation content. */
    public static String recordId(CatalogRecord record) {
        return ContentHashes.recordHash(entityTypeFor(record), identityValues(record));
    }

    /**
     * The entity name a record belongs to: {@link #ENTITY_API_ELEMENT} for a type-level record,
     * {@link #ENTITY_METHOD_ELEMENT} for a member (method, constructor, field, enum constant). The
     * kind is enumerated exactly — an unknown or unexpected kind is rejected rather than defaulted, so
     * a typo can never silently hash a record into the wrong entity's hash space.
     */
    public static String entityTypeFor(CatalogRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        return switch (record.kind()) {
            case "TYPE" -> ENTITY_API_ELEMENT;
            case "METHOD", "CONSTRUCTOR", "FIELD", "ENUM_CONSTANT" -> ENTITY_METHOD_ELEMENT;
            default -> throw new IllegalArgumentException("unknown record kind: " + record.kind());
        };
    }

    /** The ordered identity field names declared for {@code entityType}. */
    public static List<String> forEntity(String entityType) {
        return switch (entityType) {
            case ENTITY_API_ELEMENT -> API_ELEMENT_FIELDS;
            case ENTITY_METHOD_ELEMENT -> METHOD_ELEMENT_FIELDS;
            default -> throw new IllegalArgumentException("unknown entity type: " + entityType);
        };
    }

    /**
     * The ordered identity field <em>values</em> for {@code record}, in the exact order
     * {@link #forEntity(String)} declares — the argument {@code catalog.sqlite} passes to
     * {@link ContentHashes#recordHash(String, List)}. Reading values by declared field name keeps the
     * field order defined in exactly one place.
     */
    public static List<String> identityValues(CatalogRecord record) {
        String entityType = entityTypeFor(record);
        List<String> names = forEntity(entityType);
        List<String> values = new ArrayList<>(names.size());
        for (String name : names) {
            values.add(switch (name) {
                case FIELD_CANONICAL_SYMBOL -> SymbolCoordinate.from(record).serialize();
                case FIELD_SDK_VERSION -> record.sdkVersion();
                default -> throw new IllegalArgumentException("unknown identity field: " + name);
            });
        }
        return List.copyOf(values);
    }
}
