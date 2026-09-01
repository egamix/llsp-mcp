package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Normalizes a raw {@link ExtractedMember#signature() signature} into an ordered list of erased,
 * simple parameter-type names — the identity list used to distinguish overloads (Story 2.3, AC 5).
 *
 * <p>This is the era-independence crux of the story. The <em>same</em> logical method rendered as a
 * JDK 8 legacy {@code <pre>} (plain spaces, simple types) and as a JDK 11/17 modern
 * {@code .member-signature} ({@code &nbsp;}, possibly fully-qualified param types, U+200B word
 * breaks) must yield the <b>same</b> erased list. The normalization is a deterministic, pure function
 * of the input string with no locale-dependent behavior beyond {@code Locale.ROOT}.
 *
 * <p>Normalization steps, in order:
 * <ol>
 *   <li>strip U+200B (zero-width space); replace U+00A0 (already collapsed to a normal space by
 *       JSoup {@code Element.text()}, handled here too for direct-string robustness) and collapse all
 *       whitespace runs to a single space;</li>
 *   <li>isolate the parenthesized parameter list (depth-matched from the first {@code (});</li>
 *   <li>erase generic type arguments ({@code List<String>} &rarr; {@code List}, balanced
 *       {@code <...>} removal);</li>
 *   <li>split the parameter list on top-level commas (commas inside remaining parentheses do not
 *       split);</li>
 *   <li>per parameter: drop leading annotations ({@code @Foo}) and the {@code final} modifier, drop
 *       the parameter <em>name</em>, normalize varargs {@code ...} and C-style trailing {@code []} to
 *       a single agreed array form ({@code ...} &rarr; {@code []}), and reduce a fully-qualified type
 *       to its last dotted segment ({@code java.lang.String} &rarr; {@code String}).</li>
 * </ol>
 *
 * <p>Array vs varargs: {@code T...} and {@code T[]} normalize to the same {@code T[]} form (Java does
 * not permit overloading them apart, so collapsing is correct).
 */
final class SignatureNormalizer {

    private SignatureNormalizer() {
    }

    /**
     * Parses {@code signature} into its erased parameter-type list.
     *
     * @param signature the raw signature text (any era's rendering)
     * @return the ordered erased simple type names (possibly empty for a no-arg element), or
     *         {@link Optional#empty()} if no parameter list could be recovered (blank signature or no
     *         parentheses) — the caller records an issue rather than fabricating a list (AD-4)
     */
    static Optional<List<String>> parameterTypes(String signature) {
        if (signature == null) {
            return Optional.empty();
        }
        String cleaned = collapse(signature);
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }

        String params = parameterList(cleaned);
        if (params == null) {
            return Optional.empty(); // no recoverable parenthesized parameter list
        }
        // Erase generic arguments before splitting so a comma inside <...> never splits a parameter
        // (e.g. Function<A,B> is one parameter, not two).
        params = eraseGenerics(params).trim();
        if (params.isEmpty()) {
            return Optional.of(List.of()); // genuine no-arg element
        }

        List<String> types = new ArrayList<>();
        for (String raw : splitTopLevel(params)) {
            String type = parameterType(raw);
            if (!type.isEmpty()) {
                types.add(type);
            }
        }
        return Optional.of(List.copyOf(types));
    }

    /** Strips U+200B, maps U+00A0 to a space, and collapses whitespace runs to single spaces. */
    private static String collapse(String s) {
        String noZeroWidth = s.replace("​", "").replace(' ', ' ');
        return noZeroWidth.trim().replaceAll("\\s+", " ");
    }

    /**
     * The substring inside the first top-level {@code (...)} pair, matched by parenthesis depth, or
     * {@code null} if there is no balanced pair. Depth matching tolerates nested parentheses (e.g. an
     * annotation argument such as {@code @Size(max=5)}).
     */
    private static String parameterList(String s) {
        int open = s.indexOf('(');
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return s.substring(open + 1, i);
                }
            }
        }
        return null; // unbalanced
    }

    /** Removes all balanced {@code <...>} generic-argument groups, at any nesting depth. */
    private static String eraseGenerics(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (depth > 0) {
                    depth--;
                }
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Splits a parameter list on commas that are not nested inside parentheses. */
    private static List<String> splitTopLevel(String params) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
            } else if (c == ',' && depth == 0) {
                out.add(params.substring(start, i));
                start = i + 1;
            }
        }
        out.add(params.substring(start));
        return out;
    }

    /**
     * Reduces one generics-erased parameter declaration to its erased simple type name. Drops leading
     * annotations and {@code final}, drops the parameter name, normalizes varargs/array markers, and
     * reduces a fully-qualified type to its last dotted segment.
     */
    private static String parameterType(String erasedParam) {
        String[] tokens = erasedParam.trim().split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String t : tokens) {
            if (t.isEmpty() || t.equals("final") || t.startsWith("@")) {
                continue; // modifier / annotation
            }
            kept.add(t);
        }
        if (kept.isEmpty()) {
            return "";
        }

        String type;
        String nameToken = "";
        if (kept.size() == 1) {
            // Only a type is present (parameter name omitted).
            type = kept.get(0);
        } else {
            // Last token is the parameter name; the token before it is the type.
            type = kept.get(kept.size() - 2);
            nameToken = kept.get(kept.size() - 1);
        }

        // C-style array on the parameter name (`int arr[]`) attaches the array to the type.
        boolean cStyleArray = nameToken.endsWith("[]");
        boolean varargs = type.endsWith("...");
        boolean bracketArray = type.endsWith("[]");

        String base = type;
        if (varargs) {
            base = base.substring(0, base.length() - 3);
        } else if (bracketArray) {
            base = base.substring(0, base.length() - 2);
        }

        // Reduce a fully-qualified type to its last dotted segment (Locale.ROOT-independent).
        int lastDot = base.lastIndexOf('.');
        if (lastDot >= 0) {
            base = base.substring(lastDot + 1);
        }

        boolean isArray = varargs || bracketArray || cStyleArray;
        return isArray ? base + "[]" : base;
    }
}
