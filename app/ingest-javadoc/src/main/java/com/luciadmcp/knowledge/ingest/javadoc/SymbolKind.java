package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * The kind of semantic API element a {@link SymbolRecord} represents (Story 2.3).
 *
 * <p>This mirrors {@link MemberKind} (method / constructor / field / enum constant) and adds a
 * {@link #TYPE} value for the type-level record. A dedicated enum is used — rather than overloading
 * {@link MemberKind} — precisely so the type-level record is not forced to carry a member kind: a
 * type is not a member.
 *
 * <p>Like the rest of this package, this is an {@code ingest.javadoc}-internal contract. It is not a
 * {@code domain} type and does not encode the AD-18 wire/join variant.
 */
public enum SymbolKind {

    /** The documented type itself (class / interface / enum / annotation). */
    TYPE,

    /** A method — from {@link MemberKind#METHOD}. */
    METHOD,

    /** A constructor — from {@link MemberKind#CONSTRUCTOR}. */
    CONSTRUCTOR,

    /** A field — from {@link MemberKind#FIELD}. */
    FIELD,

    /** An enum constant — from {@link MemberKind#ENUM_CONSTANT}. */
    ENUM_CONSTANT;

    /** Maps a structural {@link MemberKind} onto the matching member-level {@code SymbolKind}. */
    static SymbolKind fromMemberKind(MemberKind kind) {
        return switch (kind) {
            case METHOD -> METHOD;
            case CONSTRUCTOR -> CONSTRUCTOR;
            case FIELD -> FIELD;
            case ENUM_CONSTANT -> ENUM_CONSTANT;
        };
    }
}
