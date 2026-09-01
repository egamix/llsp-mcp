package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mints canonical-symbol records from a Story-2.2 {@link ExtractedType} (Story 2.3, FR): one
 * {@link SymbolRecord} per semantic API element — the type itself plus each documented member —
 * with overloads never merged. Each record carries a <b>provisional, module-internal</b> canonical
 * key and the four normalized {@link LookupVariants}.
 *
 * <p>This is a pure library. It reads the already-extracted model only: it touches no HTML (no JSoup,
 * AD-9/AD-10), opens no socket, writes nothing, and mints or extends no {@code domain} record
 * (the {@code domain} field set stays a deferred Epic-2 gate). It imports no {@code com.luciadmcp
 * .knowledge.cli.*} type (AD-1): a signature it cannot parse is recorded as a module-local issue on
 * {@link SymbolMintResult}, never fabricated and never silently dropped (AD-4); it makes no
 * fail-fast/continue decision and is not wired into any pipeline stage (that is Story 2.4).
 *
 * <p><b>Key grammar (provisional):</b>
 * <ul>
 *   <li>type — {@code com.luciad.shape.ILcdShape}</li>
 *   <li>method / constructor — {@code com.luciad.shape.ILcdShape#contains(ILcdPoint)} (erased param
 *       types joined by {@code ,} with no spaces; a no-arg element renders {@code name()})</li>
 *   <li>field / enum constant — {@code com.luciad.shape.ILcdShape#SIZE}</li>
 *   <li>degraded method/constructor (unparseable signature) —
 *       {@code com.luciad.shape.ILcdShape#name?METHOD@3} (a best-effort key that folds in the kind
 *       and source index so it can never collide with a field/enum key of the same name nor another
 *       degraded overload) plus a recorded issue</li>
 * </ul>
 * A final uniqueness pass guarantees no two records in a result share a {@code canonicalKey}; a
 * collision (two genuinely-identical extracted elements) is recorded as an explicit issue (AD-4).
 * Overloads differ only in the {@code (...)} erased list, so distinct overloads get distinct keys
 * automatically (AC 1). The key is a pure function of (type FQN, kind, name, erased parameter types)
 * — independent of overload count, iteration order, or which page-era produced the input (AC 6,
 * NFR-13). This grammar is explicitly <em>not</em> the AD-18 wire/join variant and <em>not</em> the
 * {@code [NEEDS APPROVAL]} Epic-3 serialization grammar.
 */
public final class CanonicalSymbolMinter {

    /**
     * Mints the symbol records for one extracted type.
     *
     * @param type the Story-2.2 extracted type (never null)
     * @return the ordered records (type first, then members in document order) and any issues
     */
    public SymbolMintResult mint(ExtractedType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }

        String relativePath = type.relativePath();
        List<String> issues = new ArrayList<>();

        Optional<TypeName> maybeName = TypeName.fromRelativePath(relativePath);
        if (maybeName.isEmpty()) {
            issues.add(relativePath + ": cannot derive a type name from the relative path; "
                    + "no symbol records minted");
            return new SymbolMintResult(List.of(), issues);
        }
        TypeName typeName = maybeName.get();

        List<SymbolRecord> records = new ArrayList<>();

        // --- Type-level record ---
        records.add(new SymbolRecord(
                typeName.fqn(),
                SymbolKind.TYPE,
                typeName.fqn(),
                typeName.simpleName(),
                List.of(),
                typeVariants(typeName.simpleName()),
                relativePath,
                -1));

        // --- One record per member, in document order ---
        List<ExtractedMember> members = type.members();
        for (int i = 0; i < members.size(); i++) {
            records.add(mintMember(typeName, members.get(i), i, relativePath, issues));
        }

        // --- Uniqueness invariant (AC 1 / AC 3): no two records may share a canonical key. Keys are
        // unique by construction (overloads differ in their erased list; the degraded path folds in
        // kind + source index), so a collision means two genuinely-identical elements were extracted
        // from the page. Flag it explicitly rather than let it pass silently (AD-4).
        Set<String> seenKeys = new HashSet<>();
        for (SymbolRecord record : records) {
            if (!seenKeys.add(record.canonicalKey())) {
                issues.add(relativePath + ": duplicate canonical key '" + record.canonicalKey()
                        + "' minted for more than one element");
            }
        }

        return new SymbolMintResult(records, issues);
    }

    private static SymbolRecord mintMember(
            TypeName typeName, ExtractedMember member, int index, String relativePath,
            List<String> issues) {

        SymbolKind kind = SymbolKind.fromMemberKind(member.kind());
        String name = member.name();

        // Fields and enum constants have a unique name within the type — no signature to parse.
        if (kind == SymbolKind.FIELD || kind == SymbolKind.ENUM_CONSTANT) {
            String key = typeName.fqn() + "#" + name;
            return new SymbolRecord(key, kind, typeName.fqn(), name, List.of(),
                    memberVariants(typeName.simpleName(), name, name), relativePath, index);
        }

        // Methods and constructors: derive the erased parameter-type list for overload distinction.
        SignatureNormalizer.Parsed parsed = SignatureNormalizer.parse(member.signature());
        // Surface every degradation the normalizer flagged (unbalanced parens/generics, empty
        // parameter slots, ...) as an explicit issue — never a silent drop (AD-4).
        for (String problem : parsed.problems()) {
            issues.add(relativePath + ": member '" + name + "' " + problem);
        }
        Optional<List<String>> params = parsed.parameterTypes();
        if (params.isEmpty()) {
            // AD-4: never fabricate a parameter list. Record an issue and mint a best-effort key.
            issues.add(relativePath + ": member '" + name
                    + "' has an unparseable signature; minted a name-only key");
            // The degraded key must never collide with a field/enum key of the same name (fqn#name)
            // nor with another degraded overload of the same method name — fold in the kind and the
            // member's source index so it is unique by construction. It is deliberately not a
            // well-formed key: the trailing "?<kind>@<index>" marks it as an unresolved signature.
            String key = typeName.fqn() + "#" + name + "?" + kind + "@" + index;
            return new SymbolRecord(key, kind, typeName.fqn(), name, List.of(),
                    memberVariants(typeName.simpleName(), name, name), relativePath, index);
        }

        List<String> erased = params.get();
        String paramList = "(" + String.join(",", erased) + ")";
        String key = typeName.fqn() + "#" + name + paramList;
        String erasedGeneric = name + paramList;
        return new SymbolRecord(key, kind, typeName.fqn(), name, erased,
                memberVariants(typeName.simpleName(), name, erasedGeneric), relativePath, index);
    }

    /** The four variants for the type-level record: simple/dotted/erased all reduce to the name. */
    private static LookupVariants typeVariants(String simpleName) {
        return new LookupVariants(simpleName, simpleName, simpleName, camelCase(simpleName));
    }

    /** The four variants for a member record. */
    private static LookupVariants memberVariants(
            String typeSimpleName, String memberName, String erasedGeneric) {
        return new LookupVariants(
                memberName,
                typeSimpleName + "." + memberName,
                erasedGeneric,
                camelCase(memberName));
    }

    /**
     * The CamelHumps abbreviation of a simple name (AC 2): the first character verbatim, then every
     * subsequent upper-case letter — {@code ILcdShape} &rarr; {@code ILS}, {@code getFooBar} &rarr;
     * {@code gFB}. Deterministic; no locale-dependent transform is applied (characters are copied
     * as-is, classified with {@link Character#isUpperCase(char)}).
     */
    static String camelCase(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append(simpleName.charAt(0));
        for (int i = 1; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (Character.isUpperCase(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
