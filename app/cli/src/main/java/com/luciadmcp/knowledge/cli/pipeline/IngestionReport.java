package com.luciadmcp.knowledge.cli.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The machine-readable summary of one ingestion run (Story 1.4), written to a fixed path at the end
 * of every run — including aborted runs — so a failure is diagnosable without reading logs by hand.
 *
 * <p>The JSON is deterministic: field order is fixed, lists preserve their given order, and nothing
 * time- or run-varying is included, so two runs over identical inputs produce byte-identical report
 * content. It is a diagnostic artifact only; it does not implement activation or the active-index
 * pointer (Epic 6).
 *
 * @param buildId              the build identity
 * @param sdkVersion           the SDK version this build was produced from
 * @param toolchainFingerprint the AD-6 compatibility fingerprint carried by the manifest
 * @param aborted              whether the run stopped early (fail-fast, or a build-invalidating failure)
 * @param activationOccurred   whether the ACTIVATION stage ran to completion
 * @param stages               per-stage throughput (ran / skipped-on-resume / completed / not-run)
 * @param failures             every failure collected during the run
 * @param relationshipResolution exact/ambiguous/unresolved relationship counts, grouped by type and
 *                                resolution kind (structurally present; zero under stub stages)
 */
public record IngestionReport(
        String buildId,
        String sdkVersion,
        String toolchainFingerprint,
        boolean aborted,
        boolean activationOccurred,
        List<StageEntry> stages,
        List<FailureEntry> failures,
        ResolutionSummary relationshipResolution) {

    public IngestionReport {
        stages = List.copyOf(stages);
        failures = List.copyOf(failures);
    }

    /** One stage's throughput line. */
    public record StageEntry(PipelineStage stage, StageStatus status) {
    }

    /** One failure: which stage, how severe, and the offending file (null when not file-attributable). */
    public record FailureEntry(PipelineStage stage, FailureSeverity severity, String file, String message) {
    }

    /**
     * Relationship-resolution counts. Totals plus two independent groupings — by relationship type
     * and by resolution kind. With Story-1.3 stub stages there are no relationships, so the totals
     * are zero and both groupings are empty; the shape is fixed here for Epics 2–3 to populate.
     *
     * @param exact            count of exactly-resolved relationships
     * @param ambiguous        count of ambiguously-resolved relationships
     * @param unresolved       count of unresolved relationships
     * @param byType           per-relationship-type breakdown (empty under stubs)
     * @param byResolutionKind per-resolution-kind breakdown (empty under stubs)
     */
    public record ResolutionSummary(
            long exact,
            long ambiguous,
            long unresolved,
            List<ResolutionGroup> byType,
            List<ResolutionGroup> byResolutionKind) {

        public ResolutionSummary {
            byType = List.copyOf(byType);
            byResolutionKind = List.copyOf(byResolutionKind);
        }

        /** The zero summary used while stages are stubs and no relationships exist yet. */
        public static ResolutionSummary empty() {
            return new ResolutionSummary(0, 0, 0, List.of(), List.of());
        }
    }

    /** One grouping bucket: a key (relationship type name, or resolution kind) and its status counts. */
    public record ResolutionGroup(String key, long exact, long ambiguous, long unresolved) {
    }

    /** Writes the deterministic JSON to {@code path}, creating parent directories as needed. */
    public void writeTo(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write ingestion report to " + path, e);
        }
    }

    /** Serializes to deterministic JSON: fixed field order, ordered lists, escaped strings. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        field(sb, "  ", "buildId", buildId).append(",\n");
        field(sb, "  ", "sdkVersion", sdkVersion).append(",\n");
        field(sb, "  ", "toolchainFingerprint", toolchainFingerprint).append(",\n");
        sb.append("  \"aborted\": ").append(aborted).append(",\n");
        sb.append("  \"activationOccurred\": ").append(activationOccurred).append(",\n");

        sb.append("  \"stages\": [");
        for (int i = 0; i < stages.size(); i++) {
            StageEntry s = stages.get(i);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("    {\"stage\": ").append(quote(s.stage().name()))
                    .append(", \"status\": ").append(quote(s.status().name())).append("}");
        }
        sb.append(stages.isEmpty() ? "],\n" : "\n  ],\n");

        sb.append("  \"failures\": [");
        for (int i = 0; i < failures.size(); i++) {
            FailureEntry f = failures.get(i);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("    {\"stage\": ").append(quote(f.stage().name()))
                    .append(", \"severity\": ").append(quote(f.severity().name()))
                    .append(", \"file\": ").append(f.file() == null ? "null" : quote(f.file()))
                    .append(", \"message\": ").append(quote(f.message())).append("}");
        }
        sb.append(failures.isEmpty() ? "],\n" : "\n  ],\n");

        ResolutionSummary r = relationshipResolution;
        sb.append("  \"relationshipResolution\": {\n");
        sb.append("    \"exact\": ").append(r.exact()).append(",\n");
        sb.append("    \"ambiguous\": ").append(r.ambiguous()).append(",\n");
        sb.append("    \"unresolved\": ").append(r.unresolved()).append(",\n");
        groups(sb, "byType", r.byType()).append(",\n");
        groups(sb, "byResolutionKind", r.byResolutionKind()).append("\n");
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static StringBuilder groups(StringBuilder sb, String name, List<ResolutionGroup> list) {
        sb.append("    ").append(quote(name)).append(": [");
        for (int i = 0; i < list.size(); i++) {
            ResolutionGroup g = list.get(i);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("      {\"key\": ").append(quote(g.key()))
                    .append(", \"exact\": ").append(g.exact())
                    .append(", \"ambiguous\": ").append(g.ambiguous())
                    .append(", \"unresolved\": ").append(g.unresolved()).append("}");
        }
        sb.append(list.isEmpty() ? "]" : "\n    ]");
        return sb;
    }

    private static StringBuilder field(StringBuilder sb, String indent, String key, String value) {
        return sb.append(indent).append(quote(key)).append(": ").append(quote(value));
    }

    /** JSON string literal with the mandatory escapes. */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
