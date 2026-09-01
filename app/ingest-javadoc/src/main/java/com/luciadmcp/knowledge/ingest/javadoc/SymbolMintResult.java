package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.List;

/**
 * The outcome of minting canonical-symbol records from one {@link ExtractedType} (Story 2.3): the
 * ordered {@link SymbolRecord}s plus any non-fatal issues encountered while minting them.
 *
 * <p>Modeled exactly on {@link ExtractionResult} (2.2) and {@link DiscoveryResult} (2.1): same
 * immutability discipline ({@link List#copyOf}), same "issues are human-readable strings with the
 * offending page's relative path baked in." A signature that cannot be parsed into a parameter-type
 * list is recorded here, never fabricated and never silently dropped (AD-4).
 *
 * <p>This module expresses degradation in its own terms only. It does <b>not</b> reference the
 * {@code cli} failure policy (AD-1 forbids {@code ingest.javadoc} &rarr; {@code cli}); mapping issues
 * onto {@code fail-fast | collect-and-continue} is Story 2.4's CATALOG wiring.
 *
 * @param records the minted symbol records, in {@link ExtractedType#members()} (document) order with
 *                the type record first (never null)
 * @param issues  non-fatal minting issues, each carrying the page's relative path (never null)
 */
public record SymbolMintResult(List<SymbolRecord> records, List<String> issues) {

    public SymbolMintResult {
        records = records == null ? List.of() : List.copyOf(records);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
