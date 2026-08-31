package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * The extracted {@code @deprecated} state of a type or member (Story 2.2). A deprecation carries a
 * flag plus its prose comment — the comment is often present but may be empty when the source only
 * marks the element deprecated without explanation.
 *
 * <p>Modern Javadoc renders this as {@code div.deprecation-block}/{@code span.deprecated-label};
 * legacy renders it as {@code span.deprecatedLabel} + a deprecation comment. Both normalize to this
 * layout-agnostic value so a modern and legacy page describing the same element compare equal (AC 1).
 *
 * @param present whether the element is deprecated
 * @param comment the deprecation prose (whitespace-normalized), or empty string when none
 */
public record Deprecation(boolean present, String comment) {

    /** The state of an element that is not deprecated. */
    public static final Deprecation NONE = new Deprecation(false, "");

    public Deprecation {
        comment = comment == null ? "" : comment;
    }

    /** A deprecation with the given comment (present == true). */
    public static Deprecation of(String comment) {
        return new Deprecation(true, comment == null ? "" : comment);
    }
}
