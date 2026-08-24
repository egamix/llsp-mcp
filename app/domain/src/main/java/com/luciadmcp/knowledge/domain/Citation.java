package com.luciadmcp.knowledge.domain;

import java.util.Optional;

/**
 * A provenance citation attached to a served result.
 *
 * <p>The canonical symbol is <b>optional</b> (AD-15, amended): symbol-less
 * Guide/Training/Workflow records stay servable, so a citation may resolve to a source
 * document and anchor without a canonical symbol. Do not make it mandatory. Scoped to an
 * SDK version (AD-12). The citation-tuple wire format is a deferred approval gate (Epic 3).
 *
 * @param canonicalSymbol resolved canonical symbol, or empty when unresolved (AD-15)
 * @param sourceDoc       source document identifier
 * @param anchor          location anchor within the source document
 * @param sdkVersion      SDK version this citation belongs to (AD-12)
 */
public record Citation(
        Optional<String> canonicalSymbol,
        String sourceDoc,
        String anchor,
        String sdkVersion) {
    public Citation {
        canonicalSymbol = canonicalSymbol == null ? Optional.empty() : canonicalSymbol;
    }
}
