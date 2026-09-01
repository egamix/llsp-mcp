package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * The four normalized lookup variants carried by every {@link SymbolRecord} (Story 2.3, AC 2). Each
 * is computed deterministically and {@code Locale.ROOT}-independent.
 *
 * <p>These are kept as plain strings and are all module-internal. <b>None</b> of them is stamped as
 * "the" cross-boundary join key: which variant (if any) becomes the AD-18 wire/join variant is a
 * deferred {@code [NEEDS APPROVAL]} gate (Epic 3), decided in {@code domain}, not here.
 *
 * @param simpleName    the element's bare simple name — type {@code ILcdShape}, member {@code contains}
 *                      (no package, no signature; {@code Locale.ROOT})
 * @param dottedMember  {@code SimpleType.member} for members ({@code ILcdShape.contains}); for a type
 *                      it is the simple name itself
 * @param erasedGeneric the identity form with generics erased and parameter types normalized
 *                      ({@code contains(ILcdPoint)}, {@code map(Function)}); for a type it is the raw
 *                      simple type name (types carry no signature)
 * @param camelCase     the CamelHumps abbreviation of the simple name ({@code ILcdShape} &rarr;
 *                      {@code ILS}, {@code getFooBar} &rarr; {@code gFB})
 */
public record LookupVariants(
        String simpleName,
        String dottedMember,
        String erasedGeneric,
        String camelCase) {

    public LookupVariants {
        simpleName = simpleName == null ? "" : simpleName;
        dottedMember = dottedMember == null ? "" : dottedMember;
        erasedGeneric = erasedGeneric == null ? "" : erasedGeneric;
        camelCase = camelCase == null ? "" : camelCase;
    }
}
