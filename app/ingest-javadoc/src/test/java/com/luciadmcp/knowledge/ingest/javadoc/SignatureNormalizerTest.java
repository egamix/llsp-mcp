package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Direct proof that {@link SignatureNormalizer} reduces the divergent renderings a JDK 8 legacy
 * {@code <pre>} and a JDK 11/17 modern {@code .member-signature} produce for the <em>same</em>
 * logical method to a byte-equal erased parameter-type list (Story 2.3, AC 5). Uses hand-crafted
 * strings so the exact divergence sources (modifiers, annotations, fully-qualified vs simple param
 * types, U+200B word breaks, {@code &nbsp;} rendered as U+00A0) are controlled explicitly.
 */
class SignatureNormalizerTest {

    private static final String ZWSP = "​"; // zero-width space (modern doclet word break)
    private static final String NBSP = " "; // non-breaking space (&nbsp;)

    private static List<String> types(String signature) {
        Optional<List<String>> r = SignatureNormalizer.parameterTypes(signature);
        assertTrue(r.isPresent(), () -> "expected a recoverable param list from: " + signature);
        return r.get();
    }

    // --- AC 5 headline: legacy and modern renderings of the same method reduce equally -------------

    @Test
    void legacyAndModernSimpleTypeReduceEqually() {
        String legacy = "boolean contains(Point p)";
        // Modern: leading modifiers, U+200B before '(', fully-qualified param type, U+00A0 (&nbsp;).
        String modern = "public final boolean contains" + ZWSP + "(com.example.Point" + NBSP + "p)";
        assertEquals(List.of("Point"), types(legacy));
        assertEquals(types(legacy), types(modern), "legacy vs modern must erase to the same list");
    }

    @Test
    void legacyAndModernGenericReduceEqually() {
        String legacy = "<T> Widget map(Function<A,B> fn)";
        String modern = "Widget" + NBSP + "map" + ZWSP + "(java.util.function.Function<A," + ZWSP + "B>" + NBSP + "fn)";
        assertEquals(List.of("Function"), types(legacy));
        assertEquals(types(legacy), types(modern), "generic args erased; FQN reduced to simple name");
    }

    @Test
    void twoParamOverloadReducesToBothErasedTypes() {
        assertEquals(List.of("double", "double"),
                types("boolean contains(double x, double y)"));
    }

    // --- Array / varargs normalize to one agreed form (`[]`) --------------------------------------

    @Test
    void varargsAndArrayNormalizeToBracketForm() {
        assertEquals(List.of("int[]"), types("void merge(int... values)"));
        assertEquals(List.of("double[]"), types("void scale(double[] factors)"));
        // Varargs and array of the same base collapse to the same token (Java forbids overloading them).
        assertEquals(types("void x(int[] a)"), types("void x(int... a)"));
        // C-style array marker on the parameter name attaches to the type.
        assertEquals(List.of("int[]"), types("void y(int a[])"));
    }

    // --- Modifiers, annotations, nested generics, FQN reduction -----------------------------------

    @Test
    void modifiersAndAnnotationsAreDropped() {
        assertEquals(List.of("String"), types("void f(final @NonNull String s)"));
    }

    @Test
    void nestedGenericsAreFullyErased() {
        assertEquals(List.of("Map"), types("void h(Map<K,List<V>> m)"));
        assertEquals(List.of("List"), types("void i(java.util.List<java.lang.String> l)"));
    }

    @Test
    void noArgYieldsEmptyList() {
        assertEquals(List.of(), types("void g()"));
        assertEquals(List.of(), types("Widget()"));
    }

    // --- Degradation: unrecoverable signatures return empty Optional (never fabricated) -----------

    @Test
    void unparseableSignaturesAreNotRecovered() {
        assertTrue(SignatureNormalizer.parameterTypes("").isEmpty(), "blank -> unrecoverable");
        assertTrue(SignatureNormalizer.parameterTypes(null).isEmpty(), "null -> unrecoverable");
        assertTrue(SignatureNormalizer.parameterTypes("void noParens").isEmpty(),
                "no parentheses -> unrecoverable");
    }
}
