package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.List;

/**
 * A single documented member (method, constructor, or field) extracted from a type page (Story 2.2).
 *
 * <p>This is an {@code ingest.javadoc}-internal contract consumed by Story 2.3 (canonical symbols /
 * overload distinction) and Story 2.4 (persistence) — not a persisted {@code domain} record. It
 * deliberately carries <b>no</b> canonical symbol, SDK version, or HTML anchor: those arrive later.
 * {@link #signature} is the raw signature text kept as context for 2.3; identity here is structural
 * ({@link #kind} + {@link #name} + {@link #signature}).
 *
 * <p>Field values are whitespace-normalized so a modern and a legacy page documenting the same member
 * produce an equal {@code ExtractedMember} (AC 1). {@code params} and {@code throwsTypes} preserve
 * source order (parameters are positional).
 *
 * <p>Story 2.5 adds the member-level relationship-source references: the {@link #returnType}
 * ({@code RETURN_TYPE}), {@link #fieldType} ({@code FIELD_TYPE}), {@link #overrides}
 * ({@code OVERRIDES}), {@link #specifiedBy} ({@code SPECIFIED_BY}), and {@link #seeAlso}
 * ({@code SEE_ALSO}) — href-bearing where the page linked them. {@code params}/{@code throwsTypes}
 * already carry the data for {@code PARAMETER_TYPE}/{@code CONSTRUCTOR_PARAMETER_TYPE} (via the minted
 * erased parameter types) and {@code THROWS_TYPE}. Pass two resolves these against the completed
 * catalog; nothing is resolved here (AD-4).
 *
 * @param kind        method / constructor / field, from DOM structure
 * @param name        the member's simple name as written on the page
 * @param signature   the raw signature text (may be empty if the page omitted it)
 * @param description the member's description prose (empty if none)
 * @param params      ordered {@code @param} entries (empty if none)
 * @param returns     the {@code @return} prose (empty if none / not applicable)
 * @param throwsTypes ordered {@code @throws} entries (empty if none)
 * @param since       the {@code @since} value (empty if none)
 * @param deprecation the {@code @deprecated} state ({@link Deprecation#NONE} if not deprecated)
 * @param returnType  the method's return-type reference, or {@code null} (constructor, {@code void},
 *                    or a primitive that names no documented type)
 * @param fieldType   the field/enum-constant type reference, or {@code null} (not a field, or a
 *                    primitive)
 * @param overrides   the overridden supertype method reference ({@code Overrides:} note), or {@code null}
 * @param specifiedBy the interface method reference ({@code Specified by:} note), or {@code null}
 * @param seeAlso     the member-level {@code See Also} references (empty if none)
 */
public record ExtractedMember(
        MemberKind kind,
        String name,
        String signature,
        String description,
        List<Param> params,
        String returns,
        List<ThrownException> throwsTypes,
        String since,
        Deprecation deprecation,
        TypeRef returnType,
        TypeRef fieldType,
        TypeRef overrides,
        TypeRef specifiedBy,
        List<TypeRef> seeAlso) {

    public ExtractedMember {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        name = name == null ? "" : name;
        signature = signature == null ? "" : signature;
        description = description == null ? "" : description;
        params = params == null ? List.of() : List.copyOf(params);
        returns = returns == null ? "" : returns;
        throwsTypes = throwsTypes == null ? List.of() : List.copyOf(throwsTypes);
        since = since == null ? "" : since;
        deprecation = deprecation == null ? Deprecation.NONE : deprecation;
        seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
    }

    /**
     * Back-compatible constructor for callers that carry no relationship-source references (Stories
     * 2.2/2.3 and their tests): no return/field type, no overrides/specified-by, no see-also.
     */
    public ExtractedMember(
            MemberKind kind,
            String name,
            String signature,
            String description,
            List<Param> params,
            String returns,
            List<ThrownException> throwsTypes,
            String since,
            Deprecation deprecation) {
        this(kind, name, signature, description, params, returns, throwsTypes, since, deprecation,
                null, null, null, null, List.of());
    }
}
