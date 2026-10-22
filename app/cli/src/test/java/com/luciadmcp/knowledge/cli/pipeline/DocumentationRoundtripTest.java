package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.*;
import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.ingest.javadoc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Shared source-removal harness, reused across actual modern and legacy extraction fixtures. */
class DocumentationRoundtripTest {
    @TempDir Path temp;
    private static Path repo() { Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("docs/contracts")))root=root.getParent();return root; }
    private static IngestArguments args(Path root,String sdk) throws Exception {
        return new IngestArguments(Files.createDirectories(root.resolve("javadoc")),Files.createDirectories(root.resolve("samples")),Files.createDirectories(root.resolve("guides")),root.resolve("unused-model"),root.resolve("out"),Optional.empty(),Optional.of(sdk));
    }
    private static Map<String,String> fields(DocumentationStore.SectionPage page) {
        Map<String,String> fields=new LinkedHashMap<>();page.sections().forEach(s->s.blocks().forEach(b->fields.put(b.label(),b.text())));return fields;
    }
    @Test void modernAndLegacyEverySemanticFieldSurvivesSourceRemoval() throws Exception {
        Map<String,String> baseline=null;
        for(String era:List.of("jdk8","jdk11","jdk17")) {
            var args=args(temp.resolve(era),"2024.0");Path file=args.javadoc().resolve("com/example/Widget.html");Files.createDirectories(file.getParent());
            Files.copy(repo().resolve("app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/"+era+"/Widget.html"),file);
            String sourceHash=ContentHashes.sourceHash(Files.readAllBytes(file));
            var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);assertFalse(result.aborted(),result.failures().toString());
            Map<String,StoredCatalogRecord> catalog=new TreeMap<>();
            try(var store=new SqliteCatalogStore(result.buildContext().catalogDbPath())) { for(var record:store.allRecords())catalog.put(record.record().canonicalKey(),record); }
            assertEquals(10,catalog.size());
            Files.delete(file);assertFalse(Files.exists(file));
            try(var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
                var method=catalog.get("com.example.Widget#doThing(int,String)");assertNotNull(method);
                var page=store.readSections(method.recordId(),result.manifest().buildId(),"2024.0",0,50).orElseThrow();
                Map<String,String> actual=fields(page);
                assertEquals(Map.of("signature","void doThing(int count, String label)","description","Does the thing.","parameter count","how many","parameter label","the label","returns","the result","throws IOException","if it fails","since","1.1"),actual);
                if(baseline==null)baseline=actual;else assertEquals(baseline,actual);
                assertEquals(era.equals("jdk8")?Optional.empty():Optional.of("doThing(int,java.lang.String)"),page.sections().get(0).locator().anchor());
                assertEquals("Use NewWidget instead.",fields(store.readSections(catalog.get("com.example.Widget").recordId(),result.manifest().buildId(),"2024.0",0,50).orElseThrow()).get("deprecated"));
                assertEquals("Use doThing instead.",fields(store.readSections(catalog.get("com.example.Widget#oldThing()").recordId(),result.manifest().buildId(),"2024.0",0,50).orElseThrow()).get("deprecated"));
                assertEquals("the initial size",fields(store.readSections(catalog.get("com.example.Widget#Widget(int)").recordId(),result.manifest().buildId(),"2024.0",0,50).orElseThrow()).get("parameter size"));
                assertEquals("Tests point containment.",fields(store.readSections(catalog.get(era.equals("jdk8")?"com.example.Widget#contains(Point)":"com.example.Widget#contains(com.example.Point)").recordId(),result.manifest().buildId(),"2024.0",0,50).orElseThrow()).get("description"));
                assertEquals("Tests coordinate containment.",fields(store.readSections(catalog.get("com.example.Widget#contains(double,double)").recordId(),result.manifest().buildId(),"2024.0",0,50).orElseThrow()).get("description"));
                for(var record:catalog.values())assertTrue(store.readSections(record.recordId(),result.manifest().buildId(),"2024.0",0,50).isPresent());
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+result.buildContext().catalogDbPath());var s=db.createStatement();var r=s.executeQuery("SELECT source_hash FROM documentation")) { while(r.next())assertEquals(sourceHash,r.getString(1)); }
        }
    }
    static String generic(String description) {
        return """
            <!doctype html><html><head><meta charset="ISO-8859-1"></head><body class="class-declaration-page"><main>
            <h1 class="title">Class Generic</h1><section class="class-description" id="class-description">
            <div class="type-signature">public class Generic&lt;T&gt;</div><div class="block">TYPE_DESCRIPTION</div>
            <dl class="notes"><dt>Type Parameters:</dt><dd><code>T</code> - the type</dd></dl></section>
            <section class="method-details"><ul class="member-list"><li><section class="detail" id="legacy-map-anchor">
            <h3>map</h3><div class="member-signature">public &lt;R&gt; R map(int n)</div><div class="block">Maps café.</div>
            <dl class="notes"><dt>Type Parameters:</dt><dd><code>R</code> - result type</dd><dt>Parameters:</dt><dd><code>n</code> - value</dd><dt>Returns:</dt><dd>the result</dd></dl>
            </section></li></ul></section></main></body></html>
            """.replace("TYPE_DESCRIPTION",description);
    }
    @Test void typeParametersCharsetRetryContentEditRelocationAndSdkIsolation() throws Exception {
        List<PipelineResult> results=new ArrayList<>();List<Map<String,String>> contents=new ArrayList<>();
        for(int i=0;i<4;i++) {
            String sdk=i==3?"2025.0":"2024.0";var args=args(temp.resolve("root"+i),sdk);
            Files.write(args.javadoc().resolve("Generic.html"),generic(i==2?"Changed café.":"Generic café.").getBytes(StandardCharsets.ISO_8859_1));
            var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args);var result=runner.runToResult(args);assertFalse(result.aborted(),result.failures().toString());
            assertEquals(result.manifest(),runner.runToResult(args).manifest());results.add(result);
            Files.delete(args.javadoc().resolve("Generic.html"));
            try(var catalog=new SqliteCatalogStore(result.buildContext().catalogDbPath());var store=new SqliteDocumentationStore(result.buildContext().catalogDbPath())) {
                var type=catalog.findByKey(sdk,"Generic").orElseThrow();var method=catalog.findByKey(sdk,"Generic#map(int)").orElseThrow();
                var typePage=store.readSections(type.recordId(),result.manifest().buildId(),sdk,0,50).orElseThrow();var methodPage=store.readSections(method.recordId(),result.manifest().buildId(),sdk,0,50).orElseThrow();
                assertEquals("the type",fields(typePage).get("type parameter T"));assertEquals("result type",fields(methodPage).get("type parameter R"));assertEquals("value",fields(methodPage).get("parameter n"));assertEquals("Maps café.",fields(methodPage).get("description"));assertEquals(Optional.of("legacy-map-anchor"),methodPage.sections().get(0).locator().anchor());
                assertThrows(IllegalArgumentException.class,()->store.readSections(method.recordId(),result.manifest().buildId(),sdk.equals("2024.0")?"2025.0":"2024.0",0,1));
                contents.add(Map.of("id",type.recordId(),"content",typePage.documentContentHash(),"source",sourceHash(result.buildContext().catalogDbPath(),type.recordId())));
            }
        }
        assertEquals(results.get(0).manifest(),results.get(1).manifest());assertEquals(contents.get(0),contents.get(1));
        assertEquals(contents.get(0).get("id"),contents.get(2).get("id"));assertNotEquals(contents.get(0).get("content"),contents.get(2).get("content"));assertNotEquals(contents.get(0).get("source"),contents.get(2).get("source"));assertNotEquals(results.get(0).manifest().buildId(),results.get(2).manifest().buildId());assertNotEquals(contents.get(0).get("id"),contents.get(3).get("id"));
    }
    private static String sourceHash(Path path,String id) throws Exception { try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var p=db.prepareStatement("SELECT source_hash FROM documentation WHERE record_id=?")) { p.setString(1,id);try(var r=p.executeQuery()) { assertTrue(r.next());return r.getString(1); } } }

    @Test void unsupportedContentPortCannotReportTupleOnlySuccess() throws Exception {
        var args=args(temp,"1");Files.writeString(args.javadoc().resolve("Generic.html"),generic("Generic."));
        var tuples=new ArrayList<CatalogRecord>();
        CatalogStore unsupported=new CatalogStore() {
            public PutResult put(CatalogRecord tuple) { tuples.add(tuple);return new PutResult(IdentityFields.recordId(tuple),PutStatus.INSERTED); }
            public Optional<StoredCatalogRecord> findByKey(String v,String k) { return Optional.empty(); }
            public Optional<StoredCatalogRecord> findByRecordId(String id) { return Optional.empty(); }
            public List<StoredCatalogRecord> allRecords() { return List.of(); }
            public void close() {}
        };
        List<StageFailure> failures=new ArrayList<>();new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->unsupported).execute(BuildContext.forInputs(args),failures::add);
        assertTrue(tuples.isEmpty());assertTrue(failures.stream().anyMatch(f->f.severity()==FailureSeverity.BUILD_INVALIDATING&&f.message().contains("atomic documentation")));
    }

    @Test void productionStageRollsBackFailedContentWrites() throws Exception {
        var args=args(temp,"2024.0");Files.writeString(args.javadoc().resolve("Generic.html"),generic("Generic."));
        var context=BuildContext.forInputs(args);
        try(var catalog=new SqliteCatalogStore(context.catalogDbPath())) { catalog.bindManifest(IndexManifest.withDocumentation(context.buildId(),context.sdkVersion(),"synthetic")); }
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+context.catalogDbPath());var s=db.createStatement()) { s.execute("CREATE TRIGGER content_failure BEFORE INSERT ON documentation_sections BEGIN SELECT RAISE(ABORT,'injected production failure'); END"); }
        List<StageFailure> failures=new ArrayList<>();new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath())).execute(context,failures::add);
        assertTrue(failures.stream().anyMatch(f->f.severity()==FailureSeverity.BUILD_INVALIDATING&&f.message().contains("atomic documentation write failed")));
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+context.catalogDbPath());var s=db.createStatement()) {
            for(String table:List.of("records","knowledge_records","documentation","documentation_sections"))try(var r=s.executeQuery("SELECT count(*) FROM "+table)) { assertTrue(r.next());assertEquals(0,r.getInt(1)); }
            s.execute("DROP TRIGGER content_failure");
        }
        failures.clear();new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath())).execute(context,failures::add);assertTrue(failures.isEmpty(),failures.toString());
        Files.delete(args.javadoc().resolve("Generic.html"));
        try(var catalog=new SqliteCatalogStore(context.catalogDbPath());var store=new SqliteDocumentationStore(context.catalogDbPath())) { assertEquals(2,catalog.allRecords().size());for(var record:catalog.allRecords())assertTrue(store.readSections(record.recordId(),context.buildId(),context.sdkVersion(),0,50).isPresent()); }
    }
}
