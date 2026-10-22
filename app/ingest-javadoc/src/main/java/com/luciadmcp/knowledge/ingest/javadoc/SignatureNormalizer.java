package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Normalizes a raw {@link ExtractedMember#signature() signature} into an ordered list of erased,
 * simple parameter-type names — the identity list used to distinguish overloads (Story 2.3, AC 5).
 *
 * <p>This is the era-independence crux of the story. The <em>same</em> logical method rendered as a
 * JDK 8 legacy {@code <pre>} (plain spaces, simple types) and as a JDK 11/17 modern
 * {@code .member-signature} ({@code &nbsp;}, possibly fully-qualified param types, U+200B word
 * breaks) must yield the <b>same</b> erased list. The normalization is a deterministic, pure function
 * of the input string with no locale-dependent behavior beyond {@code Locale.ROOT} casing (none is
 * applied here — tokens are copied verbatim).
 *
 * <p>Normalization steps, in order:
 * <ol>
 *   <li>strip U+200B (zero-width space), map U+00A0 ({@code &nbsp;}) to a normal space, and collapse
 *       all whitespace runs to a single space;</li>
 *   <li>strip annotations everywhere — method-level and per-parameter — including their balanced
 *       {@code (...)} argument groups, so an annotation's parentheses can never be mistaken for the
 *       real parameter list;</li>
 *   <li>isolate the parenthesized parameter list (depth-matched from the first remaining {@code (});</li>
 *   <li>erase generic type arguments ({@code List<String>} &rarr; {@code List}, balanced
 *       {@code <...>} removal);</li>
 *   <li>split the parameter list on top-level commas;</li>
 *   <li>per parameter: drop the {@code final} modifier and the parameter <em>name</em>, normalize any
 *       array/varargs dimensionality to a consistent bracket form ({@code T...} and {@code T[]} both
 *       &rarr; {@code T[]}; {@code T[][]} / {@code T[]...} &rarr; {@code T[][]}), and reduce a
 *       fully-qualified type to its last dotted segment ({@code java.lang.String} &rarr; {@code String}).</li>
 * </ol>
 *
 * <p>Degradation is never silent (AD-4): an unbalanced parameter parenthesis, an unbalanced generic
 * {@code <}, an empty parameter slot, or a parameter that reduces to no type name is reported as an
 * explicit {@link Parsed#problems() problem} rather than being dropped without trace.
 */
final class SignatureNormalizer {

    private static final String ZERO_WIDTH_SPACE = "​";
    private static final char NBSP = ' '; // non-breaking space (&nbsp;)

    private SignatureNormalizer() {
    }

    /**
     * The outcome of normalizing one signature.
     *
     * @param parameterTypes the ordered erased simple type names (possibly empty for a no-arg
     *                       element), or {@link Optional#empty()} if no parameter list could be
     *                       recovered at all (blank signature, no parentheses, or unbalanced parens)
     * @param problems       human-readable problem fragments (no path/member prefix) for anything that
     *                       degraded — surfaced by the caller as issues rather than dropped silently
     */
    record Parsed(Optional<List<String>> parameterTypes, List<String> problems) {

        Parsed {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }
    }

    /**
     * Convenience view returning only the erased parameter-type list (discarding problem details).
     *
     * @param signature the raw signature text (any era's rendering)
     * @return the erased list, or empty if no parameter list could be recovered
     */
    static Optional<List<String>> parameterTypes(String signature) {
        return parse(signature).parameterTypes();
    }

    /**
     * The erased simple <em>leading type</em> of a signature: a method's return type or a field's
     * declared type (Story 2.5). This is the token immediately before the element name — the last
     * token before the parameter list for a method (e.g. {@code boolean contains(Point p)} &rarr;
     * {@code boolean}), or the second-to-last token of a field declaration (e.g. {@code int MAX}
     * &rarr; {@code int}). Generics are erased, a fully-qualified type keeps its package qualifier,
     * and array/varargs dimensionality is preserved as {@code []} — the same normalization
     * {@link #parameterType(String)} applies.
     *
     * <p>Returns {@link Optional#empty()} when no leading type can be recovered: a blank signature, a
     * constructor (only the type name precedes the parameter list), or a declaration with a single
     * token. The caller decides whether the recovered name denotes a documented type (a primitive or
     * {@code void} names none and yields no edge).
     *
     * @param signature the raw signature text (any era's rendering)
     * @return the erased simple leading-type name, or empty if none is recoverable
     */
    static Optional<String> leadingType(String signature) {
        if (signature == null) {
            return Optional.empty();
        }
        String cleaned = stripAnnotations(collapse(signature)).trim();
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        // Everything before the parameter list for a method/constructor; the whole for a field.
        int open = cleaned.indexOf('(');
        String head = open >= 0 ? cleaned.substring(0, open) : cleaned;
        head = eraseGenerics(head).text().trim();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        String[] tokens = head.split("\\s+");
        if (tokens.length < 2) {
            // Only the element name is present (a constructor, or a bare declaration): no leading type.
            return Optional.empty();
        }
        String typeToken = tokens[tokens.length - 2];
        String nameToken = tokens[tokens.length - 1];
        int[] typeDims = new int[1];
        String base = stripArrayMarkers(typeToken, typeDims);
        int[] nameDims = new int[1];
        stripArrayMarkers(nameToken, nameDims); // C-style array on the name: `int foo[]`
        int dims = typeDims[0] + nameDims[0];
        // The package qualifier is kept: pass two resolves a qualified name by FQN only, so a JDK type
        // is never mistaken for a same-simple-name catalog type.
        if (base.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(base + "[]".repeat(dims));
    }

    /**
     * Parses {@code signature} into its erased parameter-type list plus any degradation problems.
     *
     * @param signature the raw signature text (any era's rendering)
     * @return the parse outcome (never null)
     */
    static Parsed parse(String signature) {
        return parse(signature, false);
    }

    static Parsed parseQualified(String signature) { return parse(signature, true); }

    static List<String> qualifiedParameterTypes(String signature) {
        return parse(signature, true).parameterTypes().orElse(List.of());
    }

    static List<String> qualifiedThrowsTypes(String signature) {
        if (signature == null) return List.of();
        String cleaned = stripAnnotations(collapse(signature));
        int close = cleaned.indexOf(')');
        if (close < 0) return List.of();
        String tail = cleaned.substring(close + 1).trim();
        if (!tail.startsWith("throws ")) return List.of();
        return splitTopLevel(eraseGenerics(tail.substring(7)).text()).stream()
                .map(String::trim).filter(token -> !token.isEmpty()).toList();
    }

    private static Parsed parse(String signature, boolean preserveQualification) {
        if (signature == null) {
            return new Parsed(Optional.empty(), List.of());
        }
        String cleaned = stripAnnotations(collapse(signature)).trim();
        if (cleaned.isEmpty()) {
            return new Parsed(Optional.empty(), List.of());
        }

        List<String> problems = new ArrayList<>();

        ParamList list = parameterList(cleaned);
        if (!list.found()) {
            if (list.unbalanced()) {
                problems.add("has an unbalanced parameter-list parenthesis in its signature");
            }
            return new Parsed(Optional.empty(), problems);
        }

        // Erase generic arguments before splitting so a comma inside <...> never splits a parameter
        // (e.g. Function<A,B> is one parameter, not two).
        Erased erased = eraseGenerics(list.content());
        if (erased.unbalanced()) {
            problems.add("has an unbalanced generic '<' in its signature");
        }
        String params = erased.text().trim();
        if (params.isEmpty()) {
            return new Parsed(Optional.of(List.of()), problems); // genuine no-arg element
        }

        List<String> types = new ArrayList<>();
        for (String raw : splitTopLevel(params)) {
            if (raw.trim().isEmpty()) {
                problems.add("has an empty parameter slot in its signature");
                continue;
            }
            String type = parameterType(raw, preserveQualification);
            if (type.isEmpty()) {
                problems.add("has a parameter that reduced to no type name in its signature");
                continue;
            }
            types.add(type);
        }
        return new Parsed(Optional.of(List.copyOf(types)), problems);
    }

    /** Strips U+200B, maps U+00A0 to a space, and collapses whitespace runs to single spaces. */
    private static String collapse(String s) {
        String normalized = s.replace(ZERO_WIDTH_SPACE, "").replace(NBSP, ' ');
        return normalized.trim().replaceAll("\\s+", " ");
    }

    /**
     * Removes every annotation — {@code @Name} and {@code @Name(args)} with balanced {@code (...)} —
     * wherever it appears (method-level or per-parameter). Each removed annotation leaves a single
     * space so adjacent tokens do not merge. This runs before the parameter list is located so a
     * method- or parameter-level annotation's parentheses can never be mistaken for the parameter
     * list (e.g. {@code @Size(max=5) void f(int x)}).
     */
    private static String stripAnnotations(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c != '@') {
                out.append(c);
                i++;
                continue;
            }
            i++; // consume '@'
            // Annotation type name: identifier characters and dots (a possibly-qualified name).
            while (i < n && isNameChar(s.charAt(i))) {
                i++;
            }
            // Optional balanced (...) argument group (allowing whitespace before the '(').
            int j = i;
            while (j < n && s.charAt(j) == ' ') {
                j++;
            }
            if (j < n && s.charAt(j) == '(') {
                int depth = 0;
                int k = j;
                char quote = 0;
                for (; k < n; k++) {
                    char cc = s.charAt(k);
                    if (quote != 0) {
                        if (cc == '\\') {
                            k++; // escaped quote/backslash (or any escaped literal character)
                        } else if (cc == quote) {
                            quote = 0;
                        }
                    } else if (cc == '"' || cc == '\'') {
                        quote = cc;
                    } else if (cc == '(') {
                        depth++;
                    } else if (cc == ')') {
                        depth--;
                        if (depth == 0) {
                            k++;
                            break;
                        }
                    }
                }
                i = k; // consume the argument group (to end if unbalanced)
            }
            out.append(' '); // keep a separator in place of the removed annotation
        }
        return out.toString();
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    /** The first balanced {@code (...)} pair, matched by parenthesis depth. */
    private static ParamList parameterList(String s) {
        int open = s.indexOf('(');
        if (open < 0) {
            return new ParamList(false, "", false);
        }
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return new ParamList(true, s.substring(open + 1, i), false);
                }
            }
        }
        return new ParamList(false, "", true); // opened but never closed
    }

    /** Removes all balanced {@code <...>} generic-argument groups, at any nesting depth. */
    private static Erased eraseGenerics(String s) {
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
        return new Erased(out.toString(), depth > 0);
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
     * Reduces one generics-erased, annotation-free parameter declaration to its erased simple type
     * name. Drops {@code final} and the parameter name, preserves array/varargs dimensionality as a
     * consistent bracket form, and reduces a fully-qualified type to its last dotted segment. Returns
     * {@code ""} if nothing type-like remains (caller records a problem, never fabricates a type).
     */
    private static String parameterType(String erasedParam, boolean preserveQualification) {
        String[] tokens = erasedParam.trim().split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String t : tokens) {
            if (t.isEmpty() || t.equals("final")) {
                continue; // modifier (annotations were already stripped globally)
            }
            kept.add(t);
        }
        if (kept.isEmpty()) {
            return "";
        }

        String typeToken;
        String nameToken = "";
        if (kept.size() == 1) {
            typeToken = kept.get(0); // parameter name omitted
        } else {
            typeToken = kept.get(kept.size() - 2);
            nameToken = kept.get(kept.size() - 1);
        }

        // Array/varargs dimensionality can be written on the type (`int[]`, `int...`, `int[]...`) and,
        // C-style, on the parameter name (`int a[][]`). Sum both so every dimension is preserved.
        int[] typeDims = new int[1];
        String base = stripArrayMarkers(typeToken, typeDims);
        int[] nameDims = new int[1];
        stripArrayMarkers(nameToken, nameDims);
        int dims = typeDims[0] + nameDims[0];

        // Reduce a fully-qualified type to its last dotted segment.
        int lastDot = base.lastIndexOf('.');
        if (!preserveQualification && lastDot >= 0) {
            base = base.substring(lastDot + 1);
        }
        if (base.isEmpty()) {
            return "";
        }
        return base + "[]".repeat(dims);
    }

    /**
     * Strips every trailing array {@code []} and a single trailing varargs {@code ...} marker from a
     * token, counting the total number of dimensions into {@code dimsOut[0]}, and returns the bare
     * base token. {@code int[]...} counts as two dimensions.
     */
    private static String stripArrayMarkers(String token, int[] dimsOut) {
        String t = token;
        int dims = 0;
        if (t.endsWith("...")) {
            dims++;
            t = t.substring(0, t.length() - 3);
        }
        while (t.endsWith("[]")) {
            dims++;
            t = t.substring(0, t.length() - 2);
        }
        dimsOut[0] = dims;
        return t;
    }

    /** The located parameter list: whether a balanced pair was {@code found}, its {@code content}. */
    private record ParamList(boolean found, String content, boolean unbalanced) {
    }

    /** Generic-erased text plus whether an unclosed {@code <} was seen (unbalanced generics). */
    private record Erased(String text, boolean unbalanced) {
    }
}
