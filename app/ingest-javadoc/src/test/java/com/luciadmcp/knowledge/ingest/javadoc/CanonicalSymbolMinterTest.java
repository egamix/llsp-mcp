package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Canonical-symbol minting: one record per API element, overloads never merged, four normalized
 * lookup variants, era-independent overload keys, deterministic order, and graceful degradation
 * (Story 2.3, AC 1–8). Consumes the Story-2.2 {@link ExtractedType} model; touches no HTML.
 * Fixtures are synthetic (no proprietary content).
 */
class CanonicalSymbolMinterTest {

    private final CanonicalSymbolMinter minter = new CanonicalSymbolMinter();
    private final JavadocFieldExtractor extractor = new JavadocFieldExtractor();

    private static final String PATH = "com/example/Widget.html";
    private static final String FQN = "com.example.Widget";

    // --- AC 1 (headline): overloads become distinct records with distinct keys --------------------

    @Test
    void overloadedMethodsBecomeDistinctRecordsWithDistinctKeys() throws Exception {
        List<SymbolRecord> records = mint("jdk8/Widget.html", PATH).records();

        List<SymbolRecord> contains = records.stream()
                .filter(r -> r.simpleName().equals("contains"))
                .toList();
        assertEquals(2, contains.size(), "both contains(...) overloads must be present as records");

        List<String> keys = contains.stream().map(SymbolRecord::canonicalKey).toList();
        assertEquals(
                List.of(FQN + "#contains(Point)", FQN + "#contains(double,double)"),
                keys,
                "the two overloads must have two distinct canonical keys, in source order");

        // A non-overloaded member is a single record.
        assertEquals(1, records.stream().filter(r -> r.simpleName().equals("doThing")).count());

        // No two records ever share a canonical key (AC 1 / AC 3): keys are unique across all records.
        List<String> allKeys = records.stream().map(SymbolRecord::canonicalKey).toList();
        assertEquals(allKeys.size(), new HashSet<>(allKeys).size(), "canonical keys must be unique");
    }

    // --- AC 3: exactly one record per element (type + N members), correct kinds -------------------

    @Test
    void oneRecordPerElementWithCorrectKindsAndKeys() throws Exception {
        List<SymbolRecord> records = mint("jdk8/Widget.html", PATH).records();

        // 1 type + 9 members (Widget ctor, doThing, oldThing, contains x2, map, merge, scale, MAX).
        assertEquals(10, records.size());

        SymbolRecord type = records.get(0);
        assertEquals(SymbolKind.TYPE, type.kind());
        assertEquals(FQN, type.canonicalKey());
        assertEquals("Widget", type.simpleName());
        assertTrue(type.erasedParameterTypes().isEmpty(), "the type record carries no params");
        assertEquals(-1, type.memberIndex(), "the type record is not a member");

        assertEquals(SymbolKind.CONSTRUCTOR, keyed(records, FQN + "#Widget(int)").kind());
        assertEquals(SymbolKind.METHOD, keyed(records, FQN + "#doThing(int,String)").kind());
        assertEquals(SymbolKind.METHOD, keyed(records, FQN + "#oldThing()").kind());
        assertEquals(SymbolKind.METHOD, keyed(records, FQN + "#map(Function)").kind());
        assertEquals(SymbolKind.METHOD, keyed(records, FQN + "#merge(int[])").kind());
        assertEquals(SymbolKind.METHOD, keyed(records, FQN + "#scale(double[])").kind());

        // A field carries no parameter list and a name-only key (name is unique within the type).
        SymbolRecord max = keyed(records, FQN + "#MAX");
        assertEquals(SymbolKind.FIELD, max.kind());
        assertTrue(max.erasedParameterTypes().isEmpty());
    }

    @Test
    void enumConstantsAreDistinctFieldLikeRecords() throws Exception {
        List<SymbolRecord> records = mint("jdk11/Direction.html", "com/example/Direction.html").records();

        SymbolRecord north = keyed(records, "com.example.Direction#NORTH");
        SymbolRecord south = keyed(records, "com.example.Direction#SOUTH");
        assertEquals(SymbolKind.ENUM_CONSTANT, north.kind());
        assertEquals(SymbolKind.ENUM_CONSTANT, south.kind());
        assertTrue(north.erasedParameterTypes().isEmpty());
    }

    // --- AC 2: all four lookup variants present and correct; camel-case pinned --------------------

    @Test
    void lookupVariantsAreComputedForEachRecord() throws Exception {
        List<SymbolRecord> records = mint("jdk8/Widget.html", PATH).records();

        LookupVariants type = records.get(0).variants();
        assertEquals("Widget", type.simpleName());
        assertEquals("Widget", type.dottedMember());
        assertEquals("Widget", type.erasedGeneric());
        assertEquals("W", type.camelCase());

        LookupVariants doThing = keyed(records, FQN + "#doThing(int,String)").variants();
        assertEquals("doThing", doThing.simpleName());
        assertEquals("Widget.doThing", doThing.dottedMember());
        assertEquals("doThing(int,String)", doThing.erasedGeneric());
        assertEquals("dT", doThing.camelCase());

        LookupVariants map = keyed(records, FQN + "#map(Function)").variants();
        assertEquals("map(Function)", map.erasedGeneric());
    }

    @Test
    void camelCaseIsPinnedByKnownAnswers() {
        assertEquals("ILS", CanonicalSymbolMinter.camelCase("ILcdShape"));
        assertEquals("gFB", CanonicalSymbolMinter.camelCase("getFooBar"));
        assertEquals("W", CanonicalSymbolMinter.camelCase("Widget"));
        assertEquals("MAX", CanonicalSymbolMinter.camelCase("MAX"));
        assertEquals("", CanonicalSymbolMinter.camelCase(""));
    }

    // --- AC 5: canonical keys and erased param lists are equal across the three eras --------------

    @Test
    void overloadKeysAreEqualAcrossEras() throws Exception {
        Set<String> jdk8 = keysOf(mint("jdk8/Widget.html", PATH));
        Set<String> jdk11 = keysOf(mint("jdk11/Widget.html", PATH));
        Set<String> jdk17 = keysOf(mint("jdk17/Widget.html", PATH));

        assertEquals(jdk8, jdk11, "JDK 8 legacy vs JDK 11–16 modern keys must match");
        assertEquals(jdk11, jdk17, "JDK 11–16 modern vs JDK 17+ modern keys must match");

        // The divergently-rendered overloads specifically reduce to the same erased lists.
        assertTrue(jdk8.contains(FQN + "#contains(Point)"));
        assertTrue(jdk8.contains(FQN + "#contains(double,double)"));
        assertTrue(jdk8.contains(FQN + "#map(Function)"));
        assertTrue(jdk8.contains(FQN + "#merge(int[])"));
        assertTrue(jdk8.contains(FQN + "#scale(double[])"));

        // Erased param lists compare equal for a divergently-rendered method across all eras.
        List<String> p8 = keyed(mint("jdk8/Widget.html", PATH).records(), FQN + "#map(Function)")
                .erasedParameterTypes();
        List<String> p17 = keyed(mint("jdk17/Widget.html", PATH).records(), FQN + "#map(Function)")
                .erasedParameterTypes();
        assertEquals(List.of("Function"), p8);
        assertEquals(p8, p17);
    }

    // --- AC 6: deterministic order + keys independent of overload count ----------------------------

    @Test
    void mintingIsDeterministicAcrossRuns() throws Exception {
        ExtractedType type = extractor.extract(fixture("jdk17/Widget.html"), PATH).type();
        assertEquals(minter.mint(type).records(), minter.mint(type).records(),
                "same input -> same records in the same order");
    }

    @Test
    void keyForAnElementIsIndependentOfOverloadCount() {
        ExtractedMember containsPoint = method("contains", "boolean contains(Point p)");
        ExtractedMember containsCoords = method("contains", "boolean contains(double x, double y)");

        ExtractedType withOne = new ExtractedType(PATH, "d", "", Deprecation.NONE, List.of(containsPoint));
        ExtractedType withBoth =
                new ExtractedType(PATH, "d", "", Deprecation.NONE, List.of(containsPoint, containsCoords));

        String keyAlone = keyed(minter.mint(withOne).records(), FQN + "#contains(Point)").canonicalKey();
        String keyAmongOverloads =
                keyed(minter.mint(withBoth).records(), FQN + "#contains(Point)").canonicalKey();
        assertEquals(keyAlone, keyAmongOverloads, "a key must not depend on sibling overloads");
    }

    // --- AC 7: FQN derivation from relativePath (normal / default package / nested) ----------------

    @Test
    void fqnIsDerivedFromRelativePath() {
        TypeName normal = TypeName.fromRelativePath("com/luciad/shape/ILcdShape.html").orElseThrow();
        assertEquals("com.luciad.shape", normal.packageName());
        assertEquals("ILcdShape", normal.simpleName());
        assertEquals("com.luciad.shape.ILcdShape", normal.fqn());

        TypeName defaultPkg = TypeName.fromRelativePath("Foo.html").orElseThrow();
        assertEquals("", defaultPkg.packageName());
        assertEquals("Foo", defaultPkg.simpleName());
        assertEquals("Foo", defaultPkg.fqn());

        // Nested type: dotted convention — FQN keeps the dot, simple name is the innermost segment.
        TypeName nested = TypeName.fromRelativePath("com/example/Outer.Inner.html").orElseThrow();
        assertEquals("com.example", nested.packageName());
        assertEquals("Inner", nested.simpleName());
        assertEquals("com.example.Outer.Inner", nested.fqn());
    }

    @Test
    void malformedRelativePathYieldsNoTypeName() {
        assertTrue(TypeName.fromRelativePath("com/example/").isEmpty(), "no .html -> empty");
        assertTrue(TypeName.fromRelativePath("notes.txt").isEmpty(), "non-.html suffix -> empty");
    }

    @Test
    void malformedPathIsRecordedAsAnIssueNotAThrow() {
        ExtractedType bad = new ExtractedType("com/example/", "d", "", Deprecation.NONE, List.of());
        SymbolMintResult result = minter.mint(bad);
        assertTrue(result.records().isEmpty(), "nothing minted for an underivable path");
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("cannot derive a type name")),
                () -> "expected a recorded issue: " + result.issues());
    }

    // --- AC 8: graceful degradation on an unparseable member signature -----------------------------

    @Test
    void unparseableMemberSignatureRecordsIssueAndMintsNameOnlyKeyWithoutThrowing() {
        ExtractedMember empty = method("brokenA", "");
        ExtractedMember garbage = method("brokenB", "void brokenB"); // no parentheses
        ExtractedType type = new ExtractedType(PATH, "d", "", Deprecation.NONE, List.of(empty, garbage));

        SymbolMintResult result = minter.mint(type);

        // Name-only keys minted (no fabricated parameter list, no silent drop).
        SymbolRecord a = keyed(result.records(), FQN + "#brokenA");
        SymbolRecord b = keyed(result.records(), FQN + "#brokenB");
        assertTrue(a.erasedParameterTypes().isEmpty());
        assertTrue(b.erasedParameterTypes().isEmpty());
        assertEquals(2, result.issues().size(), () -> "one issue per broken member: " + result.issues());
        assertTrue(result.issues().stream().allMatch(i -> i.contains("unparseable signature")));
        assertTrue(result.issues().stream().allMatch(i -> i.startsWith(PATH + ":")),
                "issues carry the relative path (AD-4)");
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> minter.mint(null));
    }

    // --- AD-1 guard: the minter references no cli type ---------------------------------------------

    @Test
    void newSourceReferencesNoCliType() throws Exception {
        Path mainSrc = locateMainSource();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainSrc)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("com.luciadmcp.knowledge.cli")) {
                    offenders.add(file.toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "ingest.javadoc source must not reference cli (AD-1): " + offenders);
    }

    // --- helpers -----------------------------------------------------------------------------------

    private SymbolMintResult mint(String fixtureName, String relativePath) throws Exception {
        ExtractionResult extracted = extractor.extract(fixture(fixtureName), relativePath);
        assertTrue(extracted.issues().isEmpty(),
                () -> fixtureName + " extraction issues: " + extracted.issues());
        return minter.mint(extracted.type());
    }

    private static ExtractedMember method(String name, String signature) {
        return new ExtractedMember(MemberKind.METHOD, name, signature, "desc",
                List.of(), "", List.of(), "", Deprecation.NONE);
    }

    private static SymbolRecord keyed(List<SymbolRecord> records, String key) {
        Optional<SymbolRecord> found =
                records.stream().filter(r -> r.canonicalKey().equals(key)).findFirst();
        assertTrue(found.isPresent(), () -> "no record with key " + key + " in " + keysOf(records));
        return found.get();
    }

    private static Set<String> keysOf(SymbolMintResult result) {
        return keysOf(result.records());
    }

    private static Set<String> keysOf(List<SymbolRecord> records) {
        return records.stream().map(SymbolRecord::canonicalKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private Path fixture(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource("javadoc-fixtures/extract/" + name);
        assertNotNull(url, "missing test fixture: " + name);
        return Path.of(url.toURI());
    }

    private static Path locateMainSource() throws Exception {
        URL anchor = CanonicalSymbolMinterTest.class.getClassLoader()
                .getResource("javadoc-fixtures/extract");
        assertNotNull(anchor, "missing anchor resource javadoc-fixtures/extract");
        for (Path dir = Path.of(anchor.toURI()); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not locate src/main/java above " + anchor);
    }
}
