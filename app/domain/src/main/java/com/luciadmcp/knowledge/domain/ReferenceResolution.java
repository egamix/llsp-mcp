package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Objects;

/** Provisional, explicit evidence for a source reference, independent of edge identity. */
public record ReferenceResolution(
        String sdkVersion, String sourceId, String sourcePath, String sourceAnchor,
        RelationshipType relationshipType, String originalToken, String originalLocator,
        Status status, Kind kind, Reason reason, List<String> candidateIds, String selectedTargetId) {
    public enum Status { EXACT, AMBIGUOUS, UNRESOLVED }
    public enum Kind { LOCAL_LOCATOR, EXTERNAL_LOCATOR, ROOT_ESCAPE, QUALIFIED_NAME, SIMPLE_NAME, MEMBER_LOCATOR }
    public enum Reason { MATCHED, EXTERNAL_LOCATOR, ROOT_ESCAPE, MISSING_LOCATOR,
        MISSING_QUALIFIED_NAME, MISSING_NAME, COMPETING_CANDIDATES, LOCATOR_REQUIRED }

    public ReferenceResolution {
        for (String value : new String[] {sdkVersion, sourceId, sourcePath}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("required reference provenance/token is blank");
        }
        if (sourcePath.startsWith("/") || sourcePath.contains("\\") || sourcePath.contains(":")
                || java.util.Arrays.asList(sourcePath.split("/")).contains("..")
                || java.util.Arrays.asList(sourcePath.split("/")).contains("."))
            throw new IllegalArgumentException("source path must stay within documented SDK");
        sourceAnchor = Objects.requireNonNull(sourceAnchor);
        originalToken = Objects.requireNonNull(originalToken);
        originalLocator = Objects.requireNonNull(originalLocator);
        if (originalToken.isBlank() && originalLocator.isBlank())
            throw new IllegalArgumentException("reference needs a token or locator");
        Objects.requireNonNull(relationshipType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(reason);
        candidateIds = Objects.requireNonNull(candidateIds).stream().peek(id -> {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("blank candidate ID");
        }).distinct().sorted().toList();
        selectedTargetId = Objects.requireNonNull(selectedTargetId);
        if (status == Status.EXACT) {
            if (candidateIds.size() != 1 || !candidateIds.get(0).equals(selectedTargetId) || reason != Reason.MATCHED)
                throw new IllegalArgumentException("exact reference must select its single candidate");
        } else if (!selectedTargetId.isEmpty()
                || (status == Status.AMBIGUOUS && (candidateIds.size() < 2 || reason != Reason.COMPETING_CANDIDATES))
                || (status == Status.UNRESOLVED && (!candidateIds.isEmpty()
                        || reason == Reason.MATCHED || reason == Reason.COMPETING_CANDIDATES))) {
            throw new IllegalArgumentException("non-exact reference cannot select a target");
        }
    }

    public RelationshipScope scope() { return new RelationshipScope(sdkVersion); }
}
