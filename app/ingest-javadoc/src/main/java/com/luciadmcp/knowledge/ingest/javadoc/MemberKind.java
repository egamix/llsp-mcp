package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * The structural kind of a documented member on a type page (Story 2.2). Decided by the page's DOM
 * structure — which detail section the member lives in — never by filename or a text heuristic.
 *
 * <p>Overload distinction and canonical symbol keys are Story 2.3; this enum only records what the
 * HTML says a member is.
 */
public enum MemberKind {

    /** A method (the default when the enclosing detail section is neither constructor nor field). */
    METHOD,

    /** A constructor (enclosing detail section marked constructor). */
    CONSTRUCTOR,

    /** A field (enclosing detail section marked field). */
    FIELD
}
