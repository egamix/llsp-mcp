package com.luciadmcp.knowledge.cli.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.catalog.sqlite.SqliteCatalogStore;
import com.luciadmcp.knowledge.catalog.sqlite.SqliteRelationshipStore;
import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.domain.RelationshipEdge;
import com.luciadmcp.knowledge.domain.RelationshipStore;
import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import com.luciadmcp.knowledge.domain.StoredRelationshipEdge;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real pass-two RELATIONSHIPS stage (Story 2.5): over a synthetic tree exercising every one of the
 * 13 Javadoc structural relationship types, it resolves each documented reference against the completed
 * catalog, emits the typed edges, materializes both inverse pairs, and surfaces unresolved references as
 * recoverable failures — never fabricating an edge (AD-4). Fixtures are synthetic HTML.
 */
class RelationshipsStageTest {

    private static final String VERSION = "2024.0";

    // The 13 structural types (everything except the example/workflow types out of scope for 2.5).
    private static final Set<RelationshipType> STRUCTURAL = EnumSet.complementOf(
            EnumSet.of(RelationshipType.EXAMPLE_USES, RelationshipType.WORKFLOW_REQUIRES));

    // --- synthetic documented SDK tree (modern layout), all under com/example -----------------------

    private static final String POINT = page("class-declaration-page", "Class Point",
            "<section class=\"class-description\"><div class=\"block\">A point.</div></section>");

    private static final String WIDGET_EXCEPTION = page("class-declaration-page", "Class WidgetException",
            "<section class=\"class-description\"><div class=\"block\">An error.</div></section>");

    private static final String BASE = page("class-declaration-page", "Class Base", """
            <section class="class-description"><div class="block">A base.</div></section>
            <section class="method-details" id="method-detail"><ul class="member-list">
             <li><section class="detail" id="doThing(int)">
               <h3>doThing</h3>
               <div class="member-signature">void doThing(int count)</div>
               <div class="block">Does.</div>
             </section></li>
            </ul></section>
            """);

    private static final String DRAWABLE = page("interface-declaration-page", "Interface Drawable", """
            <section class="class-description"><div class="block">Drawable.</div></section>
            <section class="method-details" id="method-detail"><ul class="member-list">
             <li><section class="detail" id="draw()">
               <h3>draw</h3>
               <div class="member-signature">void draw()</div>
               <div class="block">Draws.</div>
             </section></li>
            </ul></section>
            """);

    private static final String SHAPE = page("interface-declaration-page", "Interface Shape", """
            <section class="class-description" id="class-description">
              <div class="block">A shape.</div>
              <div class="type-signature"><span class="modifiers">public interface </span><span class="element-name">Shape</span>
               <span class="extends-implements">extends <a href="Drawable.html">Drawable</a></span></div>
            </section>
            """);

    private static final String WIDGET = page("class-declaration-page", "Class Widget", """
            <section class="class-description" id="class-description">
              <div class="block">A widget.</div>
              <div class="type-signature"><span class="modifiers">public class </span><span class="element-name">Widget</span>
               <span class="extends-implements">extends <a href="Base.html">Base</a>
               implements <a href="Drawable.html">Drawable</a></span></div>
              <dl class="notes"><dt>See Also:</dt><dd><a href="Point.html"><code>Point</code></a></dd></dl>
            </section>
            <section class="constructor-details" id="constructor-detail"><ul class="member-list">
             <li><section class="detail" id="&lt;init&gt;(com.example.Point)">
               <h3>Widget</h3>
               <div class="member-signature">public Widget(Point origin)</div>
               <div class="block">Creates.</div>
             </section></li>
            </ul></section>
            <section class="method-details" id="method-detail"><ul class="member-list">
             <li><section class="detail" id="getOrigin()">
               <h3>getOrigin</h3>
               <div class="member-signature"><span class="return-type"><a href="Point.html">Point</a></span>&nbsp;getOrigin()</div>
               <div class="block">Gets it.</div>
             </section></li>
             <li><section class="detail" id="process(com.example.Point)">
               <h3>process</h3>
               <div class="member-signature">void process(Point p) throws WidgetException</div>
               <div class="block">Processes.</div>
               <dl class="notes"><dt>Throws:</dt><dd><code>WidgetException</code> - if bad</dd></dl>
             </section></li>
             <li><section class="detail" id="doThing(int)">
               <h3>doThing</h3>
               <div class="member-signature">void doThing(int count)</div>
               <div class="block">Does.</div>
               <dl class="notes"><dt>Overrides:</dt>
                <dd><code><a href="Base.html#doThing(int)">doThing</a></code> in class <code><a href="Base.html">Base</a></code></dd></dl>
             </section></li>
             <li><section class="detail" id="draw()">
               <h3>draw</h3>
               <div class="member-signature">void draw()</div>
               <div class="block">Draws.</div>
               <dl class="notes"><dt>Specified by:</dt>
                <dd><code><a href="Drawable.html#draw()">draw</a></code> in interface <code><a href="Drawable.html">Drawable</a></code></dd></dl>
             </section></li>
            </ul></section>
            <section class="field-details" id="field-detail"><ul class="member-list">
             <li><section class="detail" id="anchor">
               <h3>anchor</h3>
               <div class="member-signature"><a href="Point.html">Point</a>&nbsp;anchor</div>
               <div class="block">Anchor.</div>
             </section></li>
            </ul></section>
            """);

    private static String page(String bodyClass, String heading, String body) {
        return "<!DOCTYPE html><html><head><title>T</title></head>\n"
                + "<body class=\"" + bodyClass + "\">\n<main>\n"
                + "<h1 class=\"title\">" + heading + "</h1>\n"
                + body + "\n</main></body></html>\n";
    }

    private static void write(Path root, String relativePath, String html) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, html, StandardCharsets.UTF_8);
    }

    /** Writes the full 13-type tree under {@code javadoc}. */
    private static void writeFullTree(Path javadoc) throws Exception {
        write(javadoc, "com/example/Point.html", POINT);
        write(javadoc, "com/example/WidgetException.html", WIDGET_EXCEPTION);
        write(javadoc, "com/example/Base.html", BASE);
        write(javadoc, "com/example/Drawable.html", DRAWABLE);
        write(javadoc, "com/example/Shape.html", SHAPE);
        write(javadoc, "com/example/Widget.html", WIDGET);
    }

    private static BuildContext context(Path tmp) {
        return new BuildContext("b-test", "digest-test", tmp.resolve("staging"), VERSION);
    }

    private static Function<BuildContext, CatalogStore> catalogFactory() {
        return ctx -> new SqliteCatalogStore(ctx.catalogDbPath());
    }

    private static Function<BuildContext, RelationshipStore> relFactory() {
        return ctx -> new SqliteRelationshipStore(ctx.catalogDbPath());
    }

    /** Runs pass one (catalog) then pass two (relationships) over {@code javadoc}, collecting failures. */
    private static List<StageFailure> runBothPasses(Path javadoc, BuildContext ctx) {
        List<StageFailure> failures = new ArrayList<>();
        new CatalogStage(javadoc, new JavadocCatalogSource(), catalogFactory())
                .execute(ctx, failures::add);
        new RelationshipsStage(javadoc, new JavadocCatalogSource(), catalogFactory(), relFactory())
                .execute(ctx, failures::add);
        return failures;
    }

    private static List<StoredRelationshipEdge> readEdges(BuildContext ctx) {
        try (RelationshipStore store = new SqliteRelationshipStore(ctx.catalogDbPath())) {
            return store.allEdges();
        }
    }

    private static boolean hasEdge(
            List<StoredRelationshipEdge> edges, String source, String target, RelationshipType type) {
        return edges.stream().anyMatch(e -> e.edge().sourceSymbol().equals(source)
                && e.edge().targetSymbol().equals(target) && e.edge().type() == type);
    }

    private static Optional<RelationshipEdge> edge(
            List<StoredRelationshipEdge> edges, String source, RelationshipType type) {
        return edges.stream().map(StoredRelationshipEdge::edge)
                .filter(e -> e.sourceSymbol().equals(source) && e.type() == type).findFirst();
    }

    // --- AC 1: every one of the 13 structural types is present after both passes --------------------

    @Test
    void allThirteenStructuralTypesArePresentAfterBothPasses(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeFullTree(javadoc);
        BuildContext ctx = context(tmp);

        List<StageFailure> failures = runBothPasses(javadoc, ctx);
        assertTrue(failures.isEmpty(), () -> "a fully-documented tree should resolve with no failures: "
                + failures.stream().map(StageFailure::message).toList());

        List<StoredRelationshipEdge> edges = readEdges(ctx);
        Set<RelationshipType> present = edges.stream()
                .map(e -> e.edge().type()).collect(Collectors.toCollection(() -> EnumSet.noneOf(RelationshipType.class)));
        assertEquals(STRUCTURAL, present,
                () -> "all 13 structural types must be present, and only those: " + present);
    }

    // --- AC 1: forward⇄inverse consistency for both inverse pairs ------------------------------------

    @Test
    void inversePairsAreReconciledInBothDirections(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeFullTree(javadoc);
        BuildContext ctx = context(tmp);
        runBothPasses(javadoc, ctx);
        List<StoredRelationshipEdge> edges = readEdges(ctx);

        // Every EXTENDS_CLASS(a→b) has a DIRECT_SUBCLASS(b→a), and vice versa.
        for (StoredRelationshipEdge e : edges) {
            if (e.edge().type() == RelationshipType.EXTENDS_CLASS) {
                assertTrue(hasEdge(edges, e.edge().targetSymbol(), e.edge().sourceSymbol(),
                                RelationshipType.DIRECT_SUBCLASS),
                        () -> "EXTENDS_CLASS " + e.edge() + " must have its DIRECT_SUBCLASS inverse");
            }
            if (e.edge().type() == RelationshipType.DIRECT_SUBCLASS) {
                assertTrue(hasEdge(edges, e.edge().targetSymbol(), e.edge().sourceSymbol(),
                                RelationshipType.EXTENDS_CLASS),
                        () -> "DIRECT_SUBCLASS " + e.edge() + " must have its EXTENDS_CLASS inverse");
            }
            if (e.edge().type() == RelationshipType.IMPLEMENTS) {
                assertTrue(hasEdge(edges, e.edge().targetSymbol(), e.edge().sourceSymbol(),
                                RelationshipType.IMPLEMENTED_BY),
                        () -> "IMPLEMENTS " + e.edge() + " must have its IMPLEMENTED_BY inverse");
            }
            if (e.edge().type() == RelationshipType.IMPLEMENTED_BY) {
                assertTrue(hasEdge(edges, e.edge().targetSymbol(), e.edge().sourceSymbol(),
                                RelationshipType.IMPLEMENTS),
                        () -> "IMPLEMENTED_BY " + e.edge() + " must have its IMPLEMENTS inverse");
            }
        }
        assertTrue(hasEdge(edges, "com.example.Widget", "com.example.Base", RelationshipType.EXTENDS_CLASS));
        assertTrue(hasEdge(edges, "com.example.Base", "com.example.Widget", RelationshipType.DIRECT_SUBCLASS));
        assertTrue(hasEdge(edges, "com.example.Widget", "com.example.Drawable", RelationshipType.IMPLEMENTS));
        assertTrue(hasEdge(edges, "com.example.Drawable", "com.example.Widget", RelationshipType.IMPLEMENTED_BY));
    }

    // --- Matrix: linked reference (href) vs plain-name resolution -----------------------------------

    @Test
    void hrefResolvesMemberPreciselyAndPlainNameResolvesByUniqueSimpleName(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeFullTree(javadoc);
        BuildContext ctx = context(tmp);
        runBothPasses(javadoc, ctx);
        List<StoredRelationshipEdge> edges = readEdges(ctx);

        // OVERRIDES resolves via the link href+anchor to the SPECIFIC supertype method (member-precise).
        RelationshipEdge overrides = edge(edges, "com.example.Widget#doThing(int)", RelationshipType.OVERRIDES)
                .orElseThrow(() -> new AssertionError("no OVERRIDES edge from Widget#doThing(int)"));
        assertEquals("com.example.Base#doThing(int)", overrides.targetSymbol(),
                "OVERRIDES must reach the specific supertype method via its link");

        // SPECIFIED_BY likewise reaches the interface method via the link.
        RelationshipEdge specifiedBy = edge(edges, "com.example.Widget#draw()", RelationshipType.SPECIFIED_BY)
                .orElseThrow(() -> new AssertionError("no SPECIFIED_BY edge from Widget#draw()"));
        assertEquals("com.example.Drawable#draw()", specifiedBy.targetSymbol());

        // A PARAMETER_TYPE has no href — it resolves by unique simple name to the documented type.
        RelationshipEdge param = edge(edges, "com.example.Widget#process(Point)", RelationshipType.PARAMETER_TYPE)
                .orElseThrow(() -> new AssertionError("no PARAMETER_TYPE edge from Widget#process(Point)"));
        assertEquals("com.example.Point", param.targetSymbol(), "plain-name param type resolves to the type");

        // A THROWS_TYPE also resolves by name to the documented exception type.
        assertTrue(hasEdge(edges, "com.example.Widget#process(Point)", "com.example.WidgetException",
                RelationshipType.THROWS_TYPE));
    }

    // --- Matrix: reference outside the ingested tree -> RECOVERABLE, no edge ------------------------

    @Test
    void referenceOutsideTheTreeIsRecoverableAndNotFabricated(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        // A lone class whose method returns an undocumented (out-of-tree) type by plain name.
        write(javadoc, "com/example/Widget.html", page("class-declaration-page", "Class Widget", """
                <section class="class-description"><div class="block">A widget.</div></section>
                <section class="method-details" id="method-detail"><ul class="member-list">
                 <li><section class="detail" id="external()">
                   <h3>external</h3>
                   <div class="member-signature">ExternalThing external()</div>
                   <div class="block">Returns an out-of-tree type.</div>
                 </section></li>
                </ul></section>
                """));
        BuildContext ctx = context(tmp);
        List<StageFailure> failures = runBothPasses(javadoc, ctx);

        List<StoredRelationshipEdge> edges = readEdges(ctx);
        assertTrue(edges.isEmpty(), () -> "no edge may be fabricated for an out-of-tree reference: " + edges);
        assertTrue(failures.stream().anyMatch(f -> f.stage() == PipelineStage.RELATIONSHIPS
                        && f.severity() == FailureSeverity.RECOVERABLE
                        && f.message().contains("unresolved") && f.message().contains("ExternalThing")),
                () -> "the unresolved reference must be a recoverable failure: "
                        + failures.stream().map(StageFailure::message).toList());
    }

    // --- Matrix: ambiguous simple name -> left unresolved, no guessed edge --------------------------

    @Test
    void ambiguousSimpleNameIsLeftUnresolved(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        // Two documented types share the simple name "Thing" in different packages.
        write(javadoc, "com/example/Thing.html", page("class-declaration-page", "Class Thing",
                "<section class=\"class-description\"><div class=\"block\">One.</div></section>"));
        write(javadoc, "com/other/Thing.html", page("class-declaration-page", "Class Thing",
                "<section class=\"class-description\"><div class=\"block\">Two.</div></section>"));
        // A method returns "Thing" by plain name (no href) — ambiguous across the two candidates.
        write(javadoc, "com/example/User.html", page("class-declaration-page", "Class User", """
                <section class="class-description"><div class="block">Uses Thing.</div></section>
                <section class="method-details" id="method-detail"><ul class="member-list">
                 <li><section class="detail" id="make()">
                   <h3>make</h3>
                   <div class="member-signature">Thing make()</div>
                   <div class="block">Makes a Thing.</div>
                 </section></li>
                </ul></section>
                """));
        BuildContext ctx = context(tmp);
        List<StageFailure> failures = runBothPasses(javadoc, ctx);

        List<StoredRelationshipEdge> edges = readEdges(ctx);
        assertTrue(edge(edges, "com.example.User#make()", RelationshipType.RETURN_TYPE).isEmpty(),
                "an ambiguous simple name must not be guessed into an edge");
        assertTrue(failures.stream().anyMatch(f -> f.severity() == FailureSeverity.RECOVERABLE
                        && f.message().contains("RETURN_TYPE") && f.message().contains("Thing")),
                () -> "the ambiguous reference must be a recoverable failure: "
                        + failures.stream().map(StageFailure::message).toList());
    }

    // --- AC 3 / Matrix: interrupted-then-resumed yields identical edges/ids, no duplicates ----------

    @Test
    void rerunningPassTwoIsIdempotentAndByteIdentical(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeFullTree(javadoc);
        BuildContext ctx = context(tmp);

        // Pass one once; pass two twice (the second models a resumed, re-run RELATIONSHIPS stage).
        List<StageFailure> failures = new ArrayList<>();
        new CatalogStage(javadoc, new JavadocCatalogSource(), catalogFactory()).execute(ctx, failures::add);
        new RelationshipsStage(javadoc, new JavadocCatalogSource(), catalogFactory(), relFactory())
                .execute(ctx, failures::add);
        List<StoredRelationshipEdge> firstRun = readEdges(ctx);

        new RelationshipsStage(javadoc, new JavadocCatalogSource(), catalogFactory(), relFactory())
                .execute(ctx, failures::add);
        List<StoredRelationshipEdge> secondRun = readEdges(ctx);

        assertTrue(failures.isEmpty(), () -> "re-run must not introduce failures: " + failures);
        assertEquals(firstRun, secondRun, "a resumed pass two must reproduce byte-identical edges and ids");
    }

    // --- Matrix: identity clash from the store -> BUILD_INVALIDATING --------------------------------

    @Test
    void storeIdentityClashIsBuildInvalidating(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        write(javadoc, "com/example/Base.html", page("class-declaration-page", "Class Base",
                "<section class=\"class-description\"><div class=\"block\">Base.</div></section>"));
        write(javadoc, "com/example/Widget.html", page("class-declaration-page", "Class Widget", """
                <section class="class-description" id="class-description">
                  <div class="block">A widget.</div>
                  <div class="type-signature"><span class="modifiers">public class </span><span class="element-name">Widget</span>
                   <span class="extends-implements">extends <a href="Base.html">Base</a></span></div>
                </section>
                """));
        BuildContext ctx = context(tmp);
        List<StageFailure> failures = new ArrayList<>();
        new CatalogStage(javadoc, new JavadocCatalogSource(), catalogFactory()).execute(ctx, failures::add);

        // A relationship store that reports every put as an identity clash.
        RelationshipStore clashing = new ClashingStore();
        new RelationshipsStage(javadoc, new JavadocCatalogSource(), catalogFactory(), c -> clashing)
                .execute(ctx, failures::add);

        assertTrue(failures.stream().anyMatch(f -> f.stage() == PipelineStage.RELATIONSHIPS
                        && f.severity() == FailureSeverity.BUILD_INVALIDATING
                        && f.message().contains("identity clash")),
                () -> "a store identity clash must be build-invalidating: "
                        + failures.stream().map(StageFailure::message).toList());
    }

    /** A relationship store that reports every put as an identity clash (drives the AD-4 clash path). */
    private static final class ClashingStore implements RelationshipStore {
        @Override
        public PutResult put(RelationshipEdge edge) {
            return new PutResult("edge-" + edge.type() + "-" + edge.sourceSymbol() + "-" + edge.targetSymbol(),
                    PutStatus.IDENTITY_CLASH);
        }

        @Override
        public Optional<StoredRelationshipEdge> findByEdgeId(String edgeId) {
            return Optional.empty();
        }

        @Override
        public List<StoredRelationshipEdge> allEdges() {
            return List.of();
        }

        @Override
        public void close() {
        }
    }

    // --- AD-1: cli reaches the stores only through domain ports (no engine type named here) ---------

    @Test
    void catalogRecordsAndEdgesShareTheOneBuildScopedFile(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        writeFullTree(javadoc);
        BuildContext ctx = context(tmp);
        runBothPasses(javadoc, ctx);

        // Records and edges live in the same file (adjacency by record_id into the records table).
        try (CatalogStore catalog = new SqliteCatalogStore(ctx.catalogDbPath())) {
            List<StoredCatalogRecord> records = catalog.allRecords();
            assertFalse(records.isEmpty(), "pass one must have persisted records into the shared file");
        }
        assertFalse(readEdges(ctx).isEmpty(), "pass two must have persisted edges into the same file");
    }
}
