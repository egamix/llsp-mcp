package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * One extracted {@code @throws}/{@code @exception} entry: the thrown type's name (as written in the
 * doc) and its prose (Story 2.2).
 *
 * <p>Parsed the same way as {@link Param} from a {@code Throws:} {@code dd} ({@code <code>Type</code>
 * - prose}). No catalog resolution happens here — this is the raw documented text; resolving the type
 * name to a canonical symbol is later work (Story 2.3+).
 *
 * @param type        the exception type name as documented (may be empty if omitted)
 * @param description the prose describing when it is thrown (whitespace-normalized; may be empty)
 */
public record ThrownException(String type, String description) {

    public ThrownException {
        type = type == null ? "" : type;
        description = description == null ? "" : description;
    }
}
