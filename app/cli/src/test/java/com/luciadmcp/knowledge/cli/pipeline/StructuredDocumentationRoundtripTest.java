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
        Map<String,List<String>> expected=Map.of(
                "spans",List.of("<table>","<th colspan=\"2\">h</th>","<td>a</td>","<td>b</td>"),
                "ragged",List.of("<th>a</th>","<th>b</th>","<td>v</td>"),
                "row-header",List.of("<th>a</th>","<th>v</th>"),
                "rich-cell",List.of("<td>","<ul>","<li>rich</li>"),
                "mixed-list",List.of("<ol>","<li>a", "<ul>","<li>b</li>"),
                "rich-list",List.of("<ul>","<p>paragraph</p>","<pre>code</pre>"),
                "definition-list",List.of("<dl>","<dt>term</dt>","<dd>definition</dd>"),
                "image",List.of("<img src=\"local.png\" alt=\"diagram\">"),
                "line-break-list",List.of("<ul>","<li>Before", "<br>","After</li>"),
                "owned-definition-list",List.of("<dl>","<dt><a href=\"Term.html\">Term Label</a></dt>","<dd>Definition Value</dd>"));
        var args=args(temp);copy(fixtures().resolve("unsupported"),args.javadoc());
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);assertFalse(result.aborted());
        assertEquals(expected.size(),result.failures().stream().filter(f->f.message().contains("PARTIAL_UNSUPPORTED")).count(),result.failures().toString());
        removeInputs(args.javadoc());
        try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath());var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
            assertTrue(catalog.allRecords().isEmpty());assertEquals(expected.size(),store.enumerateIds(result.manifest().buildId(),"1","",50).size());
            for(var fixture:expected.entrySet()) {
                String id=KnowledgeRecord.narrativeId("1",fixture.getKey()+".html","PACKAGE");
                var b=blocks(store,id,result.manifest().buildId());assertEquals(1,b.size());assertEquals("unsupported HTML",b.get(0).label());assertEquals(DocumentationPayload.BlockKind.CODE,b.get(0).kind());
                String evidence=b.get(0).text().replaceAll("\\s+"," ").replaceAll(">\\s+<","><");
                for(String fragment:fixture.getValue())assertTrue(evidence.contains(fragment),fixture.getKey()+": missing "+fragment+" in "+evidence);
                if(fixture.getKey().equals("owned-definition-list")) {
                    assertEquals(List.of("Term Label"),b.get(0).links().stream().map(DocumentationPayload.Link::label).toList());
                    assertEquals(List.of("Term.html"),b.get(0).links().stream().map(DocumentationPayload.Link::originalTarget).toList());
                }
            }
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

    @Test void authoredHeadingsAndDirectExamplesSurviveWithPreformattedSignatures() throws Exception {
        var input=args(temp);
        String typeSignature="public class T {\n  // signature\n}\n",memberSignature="public void run(\n    int n)\n";
        Files.writeString(input.javadoc().resolve("T.html"),"<html><body class='class-declaration-page'><section class='class-description'><pre>"+typeSignature+"</pre><h2 id='owned-heading'><a href='Guide.html'>Authored caveat</a></h2><pre>  example();\n    next();\n</pre><div class='block'>Owned.</div></section><section class='method-details'><section class='detail' id='run-real'><h3>run</h3><pre>"+memberSignature+"</pre><div class='block'>Run.</div><pre>  run(1);\n</pre></section></section></body></html>");
        Files.writeString(input.javadoc().resolve("package-summary.html"),"<html><body class='package-declaration-page'><section class='package-description'><h3 id='level-three'><a href='Topic.html'>Narrative caveat</a></h3><p>Narrative.</p></section></body></html>");
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,input).runToResult(input);assertFalse(result.aborted(),result.failures().toString());
        assertEquals(2,result.failures().stream().filter(f->f.message().contains("PARTIAL_UNSUPPORTED: authored heading")).count(),result.failures().toString());removeInputs(input.javadoc());
        try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath());var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
            var type=blocks(store,catalog.findByKey("1","T").orElseThrow().recordId(),result.manifest().buildId());
            assertEquals(List.of("signature","unsupported HTML","description","description"),type.stream().map(DocumentationPayload.Block::label).toList());
            assertEquals(typeSignature,type.get(0).text());assertEquals("  example();\n    next();\n",type.get(2).text());
            assertTrue(type.get(1).text().contains("<h2 id=\"owned-heading\">"));assertTrue(type.get(1).text().contains("Authored caveat"));assertEquals("Guide.html",type.get(1).links().getFirst().originalTarget());
            var member=blocks(store,catalog.findByKey("1","T#run(int)").orElseThrow().recordId(),result.manifest().buildId());
            assertEquals(List.of(memberSignature,"Run.","  run(1);\n"),member.stream().map(DocumentationPayload.Block::text).toList());assertTrue(member.stream().noneMatch(b->b.label().equals("unsupported HTML")));
            var narrative=blocks(store,KnowledgeRecord.narrativeId("1","package-summary.html","PACKAGE"),result.manifest().buildId());
            assertTrue(narrative.get(0).text().contains("<h3 id=\"level-three\">"));assertTrue(narrative.get(0).text().contains("Narrative caveat"));assertEquals("Topic.html",narrative.get(0).links().getFirst().originalTarget());assertEquals("Narrative.",narrative.get(1).text());
        }
    }
    @Test void literalDottedIdsAndNestedNarrativeAliasesShareOneOwner() throws Exception {
        var input=args(temp);
        for(String kind:List.of("package","overview"))for(boolean nested:List.of(false,true)) {
            String name=kind+(nested?"-nested":"-literal")+".html";
            String owner="<div id='"+kind+".description' class='"+kind+"Description'><p>"+name+" prose.</p></div>";
            if(nested)owner="<section class='"+kind+"-description'>"+owner+"</section>";
            Files.writeString(input.javadoc().resolve(name),"<html><body class='"+(kind.equals("package")?"package-declaration-page":"package-index-page")+"'>"+owner+"</body></html>");
        }
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,input).runToResult(input);assertTrue(result.failures().isEmpty(),result.failures().toString());removeInputs(input.javadoc());
        try(var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
            assertEquals(4,store.enumerateIds(result.manifest().buildId(),"1","",50).size());
            for(String kind:List.of("package","overview"))for(boolean nested:List.of(false,true)) {
                String name=kind+(nested?"-nested":"-literal")+".html",id=KnowledgeRecord.narrativeId("1",name,kind.toUpperCase(Locale.ROOT));
                var page=store.readSections(id,result.manifest().buildId(),"1",0,50).orElseThrow();assertEquals(Optional.of(kind+".description"),page.sections().getFirst().locator().anchor());
                assertEquals(List.of(name+" prose."),page.sections().getFirst().blocks().stream().map(DocumentationPayload.Block::text).toList());
            }
        }
    }
    @Test void emptyNamedDocumentationRetainsPrefixLinkLabelsAndTargets() throws Exception {
        var input=args(temp);Files.writeString(input.javadoc().resolve("T.html"),"""
            <html><body class='class-declaration-page'><section class='class-description'><div class='type-signature'>class T</div><div class='block'>Owned.</div></section><section class='method-details'><section class='detail' id='run'><h3>run</h3><div class='member-signature'>void run(int value)</div><div class='block'>Run.</div><dl class='notes'><dt>Type Parameters:</dt><dd><code><a href='#T'>T</a></code></dd><dt>Parameters:</dt><dd><code><a href='#value'>value</a></code></dd><dt>Throws:</dt><dd><code><a href='Failure.html'>Failure</a></code></dd></dl></section></section></body></html>
            """);
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,input).runToResult(input);assertFalse(result.aborted(),result.failures().toString());removeInputs(input.javadoc());
        try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath());var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
            var b=blocks(store,catalog.findByKey("1","T#run(int)").orElseThrow().recordId(),result.manifest().buildId()).subList(2,5);
            assertEquals(List.of("type parameter T","parameter value","throws Failure"),b.stream().map(DocumentationPayload.Block::label).toList());assertTrue(b.stream().allMatch(v->v.text().isEmpty()));
            assertEquals(List.of("T","value","Failure"),b.stream().flatMap(v->v.links().stream()).map(DocumentationPayload.Link::label).toList());assertEquals(List.of("#T","#value","Failure.html"),b.stream().flatMap(v->v.links().stream()).map(DocumentationPayload.Link::originalTarget).toList());
        }
    }
}
