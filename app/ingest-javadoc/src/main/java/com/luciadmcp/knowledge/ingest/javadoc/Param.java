package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * One extracted {@code @param} entry: the parameter (or type-parameter) name and its prose (Story 2.2).
 *
 * <p>In both modern ({@code dl.notes}) and legacy ({@code dl}) layouts a parameter {@code dd} reads
 * {@code <code>name</code> - prose}; the name comes from the leading code element and the prose from
 * the remaining text, so the two layouts produce equal {@code Param}s for the same source (AC 1).
 *
 * @param name        the parameter name (may be empty if the source omitted it)
 * @param description the parameter prose (whitespace-normalized; may be empty)
 */
public record Param(String name, String description) {

    public Param {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
    }
}
