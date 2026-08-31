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
 * @param kind        method / constructor / field, from DOM structure
 * @param name        the member's simple name as written on the page
 * @param signature   the raw signature text (may be empty if the page omitted it)
 * @param description the member's description prose (empty if none)
 * @param params      ordered {@code @param} entries (empty if none)
 * @param returns     the {@code @return} prose (empty if none / not applicable)
 * @param throwsTypes ordered {@code @throws} entries (empty if none)
 * @param since       the {@code @since} value (empty if none)
 * @param deprecation the {@code @deprecated} state ({@link Deprecation#NONE} if not deprecated)
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
        Deprecation deprecation) {

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
    }
}
