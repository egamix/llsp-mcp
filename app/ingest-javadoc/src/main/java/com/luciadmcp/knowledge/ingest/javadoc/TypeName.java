package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A type's package / simple name / fully-qualified name, derived deterministically from a Javadoc
 * page's {@code /}-separated {@code relativePath} (Story 2.3, AC 7). {@link ExtractedType} carries
 * only the path — no FQN or simple-name field — so it is derived here, offline, with no network and
 * no page-title guessing.
 *
 * <p>Nested types render in the standard Javadoc layout with dots in the final path segment
 * ({@code pkg/Outer.Inner.html}). The chosen, documented nested-type convention is the readable
 * dotted form: the FQN keeps the dots ({@code pkg.Outer.Inner}) and {@link #simpleName()} is the
 * innermost segment ({@code Inner}). (The binary {@code $} form is deliberately not used; downstream
 * 2.4/2.5 rely on this being stable.)
 *
 * @param packageName the dotted package ({@code ""} for the default package)
 * @param simpleName  the bare innermost type name
 * @param fqn         the fully-qualified name (equal to {@code simpleName} in the default package)
 */
public record TypeName(String packageName, String simpleName, String fqn) {

    public TypeName {
        packageName = packageName == null ? "" : packageName;
        if (simpleName == null || simpleName.isBlank()) {
            throw new IllegalArgumentException("simpleName must not be blank");
        }
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
    }

    /**
     * Derives a {@code TypeName} from a {@code /}-separated Javadoc {@code relativePath}. A malformed
     * or degenerate path (no {@code .html} suffix, or an empty final segment) yields
     * {@link Optional#empty()} so the caller can record an issue rather than throw (AC 8). A
     * default-package path ({@code Foo.html}, no {@code /}) is <b>valid</b>, not malformed.
     *
     * @param relativePath the page path, already validated non-blank by {@link ExtractedType}
     * @return the derived name, or empty if the path cannot yield one
     */
    static Optional<TypeName> fromRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        String path = relativePath.trim();
        // Suffix check is Locale.ROOT-independent (ASCII compare, lowercased under ROOT).
        if (!path.toLowerCase(Locale.ROOT).endsWith(".html")) {
            return Optional.empty();
        }
        String noSuffix = path.substring(0, path.length() - ".html".length());

        List<String> segments = new ArrayList<>();
        for (String seg : noSuffix.split("/")) {
            if (!seg.isEmpty()) {
                segments.add(seg);
            }
        }
        if (segments.isEmpty()) {
            return Optional.empty();
        }

        String typeSegment = segments.get(segments.size() - 1); // e.g. "ILcdShape" or "Outer.Inner"
        if (typeSegment.isBlank()) {
            return Optional.empty();
        }

        String packageName = String.join(".", segments.subList(0, segments.size() - 1));

        // The bare innermost simple name is the last dot-separated part of the final segment.
        int lastDot = typeSegment.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? typeSegment.substring(lastDot + 1) : typeSegment;
        if (simpleName.isBlank()) {
            return Optional.empty();
        }

        String fqn = packageName.isEmpty() ? typeSegment : packageName + "." + typeSegment;
        return Optional.of(new TypeName(packageName, simpleName, fqn));
    }
}
