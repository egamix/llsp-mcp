package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.List;

/**
 * The outcome of extracting one type page (Story 2.2): the {@link ExtractedType} plus any non-fatal
 * issues encountered while parsing it.
 *
 * <p>Modeled on {@link DiscoveryResult}'s {@code warnings} channel. Each issue is a human-readable
 * string with the offending page's relative path baked in — a member with no recoverable name, a
 * truncated {@code dl} tag list, an empty required description, etc. Issues are recorded, never a
 * silent drop and never a fabricated value (AD-4).
 *
 * <p>This module expresses degradation in its own terms only. It does <b>not</b> reference the
 * {@code cli} failure policy (AD-1 forbids {@code ingest.javadoc} &rarr; {@code cli}); the CATALOG
 * stage maps these issues onto the {@code fail-fast | collect-and-continue} policy in Story 2.4.
 *
 * @param type   the extracted fields (never null; members may be empty)
 * @param issues non-fatal extraction issues, each carrying the page's relative path
 */
public record ExtractionResult(ExtractedType type, List<String> issues) {

    public ExtractionResult {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
