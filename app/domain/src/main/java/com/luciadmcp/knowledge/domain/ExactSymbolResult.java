package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Optional;

public record ExactSymbolResult(SymbolSourceBinding source, Optional<String> targetSdkVersion,
        Proof proof, List<Match> matches, int candidatesMatched, int workCharged,
        boolean complete, boolean truncated, String reason) {
    public enum Proof { EXACT, AMBIGUOUS, UNKNOWN_IN_SCOPE, INCOMPLETE_EVIDENCE }
    public record Match(String recordId, SymbolCoordinate symbol, SourceLocator locator,
            SymbolSourceBinding source, String matchedBy) {}
    public ExactSymbolResult { matches=List.copyOf(matches); }
}
