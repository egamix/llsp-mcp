package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.*;
import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.ingest.javadoc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuredDocumentationRoundtripTest {
    @TempDir Path temp;
    static Path repo() { Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("docs/contracts")))root=root.getParent();return root; }
    static Path fixtures() { return repo().resolve("app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured"); }
    static IngestArguments args(Path root) throws Exception {
        return new IngestArguments(Files.createDirectories(root.resolve("javadoc")),Files.createDirectories(root.resolve("samples")),Files.createDirectories(root.resolve("guides")),root.resolve("unused-model"),root.resolve("out"),Optional.empty(),Optional.of("1"));
    }
    static void copy(Path from,Path to) throws Exception {
        try(var paths=Files.walk(from)) { for(Path path:paths.filter(Files::isRegularFile).toList()) { Path target=to.resolve(from.relativize(path));Files.createDirectories(target.getParent());Files.writeString(target,Files.readString(path).replace("\r\n","\n")); } }
    }
    static void removeInputs(Path root) throws Exception { try(var paths=Files.walk(root)) { for(Path path:paths.filter(Files::isRegularFile).toList())Files.delete(path); } }
    static List<DocumentationPayload.Block> blocks(DocumentationStore store,String id,String build) {
        return store.readSections(id,build,"1",0,50).orElseThrow().sections().get(0).blocks();
    }
    static void assertStructured(List<DocumentationPayload.Block> b) {
        assertEquals(List.of(DocumentationPayload.BlockKind.PARAGRAPH,DocumentationPayload.BlockKind.PARAGRAPH,DocumentationPayload.BlockKind.CODE,DocumentationPayload.BlockKind.LIST,DocumentationPayload.BlockKind.TABLE,DocumentationPayload.BlockKind.PARAGRAPH,DocumentationPayload.BlockKind.NOTE,DocumentationPayload.BlockKind.NOTE,DocumentationPayload.BlockKind.NOTE),b.stream().map(DocumentationPayload.Block::kind).toList());
        assertEquals("First Local External Escape Empty.",b.get(0).text());assertEquals("Second caveat.",b.get(1).text());assertEquals("  int n = 1;\n    n++;\n",b.get(2).text());
        assertTrue(b.get(3).ordered());assertEquals(List.of(new DocumentationPayload.ListItem("One",List.of(new DocumentationPayload.ListItem("Nested",List.of()))),new DocumentationPayload.ListItem("Two",List.of())),b.get(3).items());
        assertEquals(new DocumentationPayload.Table(List.of("Name","Meaning"),List.of(List.of("n","count"))),b.get(4).table().orElseThrow());assertEquals("Last caveat.",b.get(5).text());
        assertEquals(List.of("API Note","Implementation Requirements","Implementation Note"),b.subList(6,9).stream().map(DocumentationPayload.Block::label).toList());
        assertEquals(List.of("API caveat.","Must keep order.","May cache."),b.subList(6,9).stream().map(DocumentationPayload.Block::text).toList());
        var links=b.get(0).links();assertEquals(List.of("Local","External","Escape","Empty"),links.stream().map(DocumentationPayload.Link::label).toList());
        assertEquals(List.of("T.html#run-real","https://example.invalid/reference#x","../../escape.html",""),links.stream().map(DocumentationPayload.Link::originalTarget).toList());
        assertTrue(links.stream().allMatch(l->l.sdkVersion().equals("1")));
        assertEquals(new SourceLocator(SourceLocator.Kind.SOURCE,"p/T.html",Optional.of("run-real"),""),links.get(0).locator());
        assertEquals(SourceLocator.Kind.EXTERNAL,links.get(1).locator().kind());assertEquals(SourceLocator.Kind.ABSENT,links.get(2).locator().kind());assertEquals(SourceLocator.Kind.ABSENT,links.get(3).locator().kind());
    }
    @Test void modernAndLegacyApiAndNarrativesSurviveDeletionExactly() throws Exception {
        for(String era:List.of("modern","legacy")) {
            var args=args(temp.resolve(era));copy(fixtures().resolve(era),args.javadoc());
            var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args);var result=runner.runToResult(args);
            assertFalse(result.aborted(),result.failures().toString());assertTrue(result.failures().isEmpty(),result.failures().toString());
            assertEquals(result.manifest(),runner.runToResult(args).manifest());
            Map<String,StoredCatalogRecord> api=new HashMap<>();
            try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath())) { catalog.allRecords().forEach(r->api.put(r.record().canonicalKey(),r));assertEquals(3,api.size());assertTrue(catalog.validateBuild(new StandardBuildValidator(),BuildValidator.Operation.CLI,result.manifest().buildId(),"1").valid()); }
            removeInputs(args.javadoc());
            try(var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
                var type=blocks(store,api.get("p.T").recordId(),result.manifest().buildId());assertEquals("signature",type.get(0).label());assertStructured(type.subList(1,type.size()));
                var method=store.readSections(api.get("p.T#run()").recordId(),result.manifest().buildId(),"1",0,50).orElseThrow();
                assertEquals(Optional.of("run-real"),method.sections().get(0).locator().anchor());assertEquals(List.of("void run()","Run first.","  run();\n","Run last."),method.sections().get(0).blocks().stream().map(DocumentationPayload.Block::text).toList());
                assertEquals(List.of("void nested()","Only nested."),blocks(store,api.get("p.T#nested()").recordId(),result.manifest().buildId()).stream().map(DocumentationPayload.Block::text).toList());
                String pkg=KnowledgeRecord.narrativeId("1","p/package-summary.html","PACKAGE"),overview=KnowledgeRecord.narrativeId("1","p/overview-summary.html","OVERVIEW");
                assertStructured(blocks(store,pkg,result.manifest().buildId()));assertStructured(blocks(store,overview,result.manifest().buildId()));
                assertEquals(Optional.of("package-description"),store.readSections(pkg,result.manifest().buildId(),"1",0,50).orElseThrow().sections().get(0).locator().anchor());
                assertEquals(Optional.empty(),store.readSections(overview,result.manifest().buildId(),"1",0,50).orElseThrow().sections().get(0).locator().anchor());
                var first=store.enumerateIds(result.manifest().buildId(),"1","",2);var second=store.enumerateIds(result.manifest().buildId(),"1",first.getLast(),50);assertEquals(5,first.size()+second.size());
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+result.buildContext().catalogDbPath());var st=db.createStatement();var r=st.executeQuery("SELECT record_id,symbol_coordinate,narrative_kind FROM knowledge_records WHERE record_type='NARRATIVE'")) { int n=0;while(r.next()) { assertNull(r.getString(2));assertNotNull(r.getString(3));assertFalse(api.values().stream().anyMatch(a->a.recordId().equals(readId(r))));n++; }assertEquals(2,n); }
        }
    }
    private static String readId(ResultSet r) { try{return r.getString(1);}catch(SQLException e){throw new IllegalStateException(e);} }
    @Test void unsupportedStructuresPersistEvidenceAndReportPartial() throws Exception {
        var args=args(temp);copy(fixtures().resolve("unsupported"),args.javadoc());
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);assertFalse(result.aborted());
        assertEquals(8,result.failures().stream().filter(f->f.message().contains("PARTIAL_UNSUPPORTED")).count(),result.failures().toString());
        removeInputs(args.javadoc());
        try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath());var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
            assertTrue(catalog.allRecords().isEmpty());var ids=store.enumerateIds(result.manifest().buildId(),"1","",50);assertEquals(8,ids.size());
            for(String id:ids) { var b=blocks(store,id,result.manifest().buildId());assertEquals(1,b.size());assertEquals("unsupported HTML",b.get(0).label());assertEquals(DocumentationPayload.BlockKind.CODE,b.get(0).kind());assertTrue(b.get(0).text().contains("<")); }
        }
    }
    @Test void unknownEmptyAndAmbiguousNarrativeLayoutsHaveExplicitIssues() throws Exception {
        var args=args(temp);
        Files.writeString(args.javadoc().resolve("empty.html"),"<body class='package-declaration-page'><section class='package-description'></section>");
        Files.writeString(args.javadoc().resolve("unknown.html"),"<body class='package-declaration-page'><main>Unknown wrapper</main>");
        Files.writeString(args.javadoc().resolve("ambiguous.html"),"<body class='package-declaration-page'><section class='package-description'>one</section><section class='package-description'>two</section>");
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);assertFalse(result.aborted());assertEquals(3,result.failures().stream().filter(f->f.message().contains("PARTIAL_UNSUPPORTED")).count());
        try(var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) { assertTrue(store.enumerateIds(result.manifest().buildId(),"1","",50).isEmpty()); }
    }

    @Test void sourceSpecificNarrativeIdsAndIndependentHashesAcrossEditsAndRelocation() throws Exception {
        var observed=new ArrayList<List<String>>();
        for(int n=0;n<5;n++) {
            var base=args(temp.resolve("case"+n));String sdk=n==4?"2":"1";
            var input=new IngestArguments(base.javadoc(),base.samples(),base.guides(),base.model(),base.out(),base.trainings(),Optional.of(sdk));
            String html="<html><body class='package-declaration-page'><section class='package-description'><p>"+(n==2?"Changed":"Original")+" caveat.</p><pre>  code();\r\n</pre></section>"+(n==3?"<!-- source-only edit -->":"")+"</body></html>";
            Files.writeString(input.javadoc().resolve("package-summary.html"),html);
            var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,input).runToResult(input);assertTrue(result.failures().isEmpty(),result.failures().toString());
            String id=KnowledgeRecord.narrativeId(sdk,"package-summary.html","PACKAGE");removeInputs(input.javadoc());
            try(var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath());var db=DriverManager.getConnection("jdbc:sqlite:"+result.buildContext().catalogDbPath());var st=db.createStatement();var rows=st.executeQuery("SELECT source_hash,content_hash FROM documentation")) {
                assertTrue(rows.next());observed.add(List.of(id,rows.getString(1),rows.getString(2)));
                var b=store.readSections(id,result.manifest().buildId(),sdk,0,50).orElseThrow().sections().get(0).blocks();assertEquals("  code();\r\n",b.get(1).text());
            }
        }
        assertEquals(observed.get(0),observed.get(1));assertEquals(observed.get(0).get(0),observed.get(2).get(0));assertNotEquals(observed.get(0).get(1),observed.get(2).get(1));assertNotEquals(observed.get(0).get(2),observed.get(2).get(2));
        assertEquals(observed.get(0).get(0),observed.get(3).get(0));assertNotEquals(observed.get(0).get(1),observed.get(3).get(1));assertEquals(observed.get(0).get(2),observed.get(3).get(2));assertNotEquals(observed.get(0).get(0),observed.get(4).get(0));
    }
    @Test void signatureDeprecationAndNamedNotesRetainHrefAndLabels() throws Exception {
        var input=args(temp);Files.writeString(input.javadoc().resolve("T.html"),"""
            <html><body class='class-declaration-page'><section class='class-description'><div class='type-signature'>class T extends <a href='Base.html'>Base</a></div><div class='deprecation-block'><span class='deprecated-label'>Deprecated.</span><div class='deprecation-comment'>Use <a href='New.html'>New</a>.</div></div><div class='block'>Owned.</div><dl class='notes'><dt>Throws:</dt><dd><code><a href='Failure.html'>Failure</a></code> - if it fails</dd></dl></section></body></html>
            """);
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,input).runToResult(input);assertFalse(result.aborted());removeInputs(input.javadoc());
        try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath());var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
            var b=blocks(store,catalog.findByKey("1","T").orElseThrow().recordId(),result.manifest().buildId());
            assertEquals(List.of("signature","deprecated","description","throws Failure"),b.stream().map(DocumentationPayload.Block::label).toList());
            assertEquals(List.of("Base.html","New.html","Failure.html"),b.stream().flatMap(v->v.links().stream()).map(DocumentationPayload.Link::originalTarget).toList());
            assertEquals(1,b.stream().filter(v->v.label().equals("deprecated")).count());
        }
    }
}
