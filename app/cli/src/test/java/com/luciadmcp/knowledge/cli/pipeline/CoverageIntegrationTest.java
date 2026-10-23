package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.ingest.javadoc.*;
import com.luciadmcp.knowledge.catalog.sqlite.*;
import com.luciadmcp.knowledge.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoverageIntegrationTest {
    @TempDir Path temp;
    @Test void reconcilesMixedPagesAndRetryWithoutInflation() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        Files.writeString(args.javadoc().resolve("nav.html"),"<html><body class='help-page'>Navigation</body></html>");
        Files.writeString(args.javadoc().resolve("unknown.html"),"<html><body>Unrecognized content</body></html>");
        var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args);
        var first=runner.runToResult(args);var before=CoverageSnapshot.restore(first.buildContext()).orElseThrow();
        assertEquals(5,before.discovered());assertEquals(1,before.excluded());assertEquals(3,before.complete());assertEquals(1,before.failed());
        assertEquals(5,before.emittedRecords());assertEquals(5,before.persistedRecords());assertEquals(5,before.persistedSections());assertFalse(before.completeForProfile());
        runner.runToResult(args);assertEquals(before,CoverageSnapshot.restore(first.buildContext()).orElseThrow());
        String report=Files.readString(args.out().resolve(first.manifest().buildId()).resolve("ingestion-report.json"));
        assertTrue(report.contains("\"reportVersion\": 2"));assertTrue(report.contains("\"completion\": \"PARTIAL\""));assertTrue(report.contains("UNKNOWN_CLASSIFICATION"));
    }
    @Test void cleanResumeRestoresIdenticalCoverageAndCorruptionIsUnavailable() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args);var first=runner.runToResult(args);
        var context=first.buildContext();var coverage=CoverageSnapshot.restore(context).orElseThrow();assertTrue(coverage.completeForProfile());
        byte[] saved=Files.readAllBytes(context.stagingDir().resolve(CoverageSnapshot.FILE));
        runner.runToResult(args);assertArrayEquals(saved,Files.readAllBytes(context.stagingDir().resolve(CoverageSnapshot.FILE)));
        assertEquals(coverage,CoverageSnapshot.restore(context).orElseThrow());
        assertTrue(CoverageSnapshot.restore(new BuildContext(context.buildId(),"wrong",context.stagingDir(),context.sdkVersion())).isEmpty());
        Files.write(context.stagingDir().resolve(CoverageSnapshot.FILE),new byte[]{0});
        var resumed=runner.runToResult(args);assertFalse(resumed.failures().isEmpty());
        String report=Files.readString(args.out().resolve(first.manifest().buildId()).resolve("ingestion-report.json"));
        assertTrue(report.contains("UNAVAILABLE"));assertTrue(report.contains("\"pages\":null"));assertFalse(report.contains("COMPLETE_FOR_PROFILE"));
        Files.delete(context.stagingDir().resolve(CoverageSnapshot.FILE));assertTrue(CoverageSnapshot.restore(context).isEmpty());
    }
    @Test void realAtomicRollbackLeavesFailedAssociationsAndUnprocessedPagesExplicit() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        var context=BuildContext.forInputs(args);
        // Real SQLite failure after the catalog tuple insertion exercises transaction rollback.
        try(var store=new SqliteCatalogStore(context.catalogDbPath());var db=DriverManager.getConnection("jdbc:sqlite:"+context.catalogDbPath());var statement=db.createStatement()) {
            statement.execute("CREATE TRIGGER reject_payload BEFORE INSERT ON documentation BEGIN SELECT RAISE(ABORT,'synthetic'); END");
        }
        List<StageFailure> failures=new ArrayList<>();new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath())).execute(context,failures::add);
        assertTrue(failures.stream().anyMatch(f->f.severity()==FailureSeverity.BUILD_INVALIDATING));
        var coverage=CoverageSnapshot.restore(context).orElseThrow();assertEquals(3,coverage.failed());assertEquals(5,coverage.emittedRecords());assertEquals(0,coverage.persistedRecords());
        assertTrue(coverage.outcomes().stream().anyMatch(o->o.reason().equals("UNPROCESSED")));
        try(var store=new SqliteCatalogStore(context.catalogDbPath())) { assertEquals(0,store.allRecords().size()); }
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+context.catalogDbPath());var statement=db.createStatement()) { statement.execute("DROP TRIGGER reject_payload"); }
        failures.clear();new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath())).execute(context,failures::add);
        assertTrue(failures.isEmpty());assertEquals(5,CoverageSnapshot.restore(context).orElseThrow().persistedRecords());
    }
    @Test void oversizedAndUnsupportedPagesCannotBeComplete() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        Files.write(args.javadoc().resolve("large.html"),new byte[SourceAdmission.MAX_FILE_BYTES+1]);
        Files.writeString(args.javadoc().resolve("package-summary.html"),"<body class='package-declaration-page'><section class='package-description'><p>Retained.</p><img src='x'></section></body>");
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);
        var coverage=CoverageSnapshot.restore(result.buildContext()).orElseThrow();assertEquals(2,coverage.discovered());assertEquals(1,coverage.failed());assertEquals(1,coverage.partial());assertEquals(0,coverage.complete());
        assertTrue(coverage.outcomes().stream().anyMatch(o->o.reason().equals("SOURCE_FILE_TOO_LARGE")));
    }
    @Test void independentAuditSpecimenUsesRealStoredPayload() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        Path fixture=StructuredDocumentationRoundtripTest.repo().resolve("docs/contracts/fixtures/ingestion-audit-source.html");
        Files.writeString(args.javadoc().resolve("package-summary.html"),Files.readString(fixture).replace("\r\n","\n"));
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);
        assertTrue(result.failures().isEmpty(),result.failures().toString());
        assertTrue(CoverageSnapshot.restore(result.buildContext()).orElseThrow().completeForProfile());
        String export=System.getenv("LUCIAD_AUDIT_STORE");
        if(export!=null && !export.isBlank()) {
            Path target=Path.of(export).toAbsolutePath();Files.createDirectories(target.getParent());
            Files.copy(result.buildContext().catalogDbPath(),target,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    @Test void realIdentityClashCountsFailedAssociationAndPreservesOriginalContent() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);
        String original;
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+result.buildContext().catalogDbPath());var st=db.createStatement();var rows=st.executeQuery("SELECT group_concat(hex(payload),'') FROM documentation_sections ORDER BY record_id,ordinal")) { rows.next();original=rows.getString(1); }
        Path file=args.javadoc().resolve("p/T.html");Files.writeString(file,Files.readString(file)+"\n<!-- changed source bytes -->\n");
        List<StageFailure> failures=new ArrayList<>();
        new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath())).execute(result.buildContext(),failures::add);
        assertTrue(failures.stream().anyMatch(f->f.severity()==FailureSeverity.BUILD_INVALIDATING));
        var coverage=CoverageSnapshot.restore(result.buildContext()).orElseThrow();
        assertEquals(5,coverage.emittedRecords());assertEquals(2,coverage.persistedRecords());assertEquals(3,coverage.emittedRecords()-coverage.persistedRecords());
        assertTrue(coverage.outcomes().stream().anyMatch(o->o.reason().equals("IDENTITY_CLASH")));
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+result.buildContext().catalogDbPath());var st=db.createStatement();var rows=st.executeQuery("SELECT group_concat(hex(payload),'') FROM documentation_sections ORDER BY record_id,ordinal")) { rows.next();assertEquals(original,rows.getString(1)); }
    }
    @Test void authoredDocFileHeadingCannotBeExcludedOrPromotedToApi() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        Files.writeString(args.javadoc().resolve("authored.html"),"<body class='doc-file-page'><h1>Class Tutorial</h1><p>Authored content.</p></body>");
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);
        var coverage=CoverageSnapshot.restore(result.buildContext()).orElseThrow();
        assertEquals(1,coverage.failed());assertEquals(0,coverage.excluded());assertEquals(0,coverage.complete());
        assertEquals("UNKNOWN",coverage.outcomes().getFirst().pageType());
    }
    @Test void conflictingPagesHaveIndependentLosingAssociations() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        String type=Files.readString(StructuredDocumentationRoundtripTest.fixtures().resolve("modern/p/T.html"));
        Files.createDirectories(args.javadoc().resolve("p"));
        Files.writeString(args.javadoc().resolve("p/T.html"),type);
        Files.writeString(args.javadoc().resolve("p.T.html"),type);
        var context=BuildContext.forInputs(args);List<StageFailure> failures=new ArrayList<>();
        var stage=new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath()));
        stage.execute(context,failures::add);var first=CoverageSnapshot.restore(context).orElseThrow();
        assertEquals(6,first.emittedRecords());assertEquals(3,first.persistedRecords());
        assertEquals(1,first.complete());assertEquals(1,first.failed());assertEquals(0,first.partial());
        assertEquals(6,first.emittedSections());assertEquals(3,first.persistedSections());
        stage.execute(context,failures::add);assertEquals(first,CoverageSnapshot.restore(context).orElseThrow());
    }
    @Test void narrativeCanonicalOverflowIsLocalAndLaterNarrativePersists() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        String prefix="<body class='package-declaration-page'><section class='package-description'><p>",suffix="</p></section></body>";
        Files.writeString(args.javadoc().resolve("a.html"),prefix+"a".repeat(DocumentationPayload.MAX_BYTES-1)+suffix);
        Files.writeString(args.javadoc().resolve("z.html"),prefix+"Valid later narrative."+suffix);
        var context=BuildContext.forInputs(args);List<StageFailure> failures=new ArrayList<>();
        new CatalogStage(args.javadoc(),new JavadocCatalogSource(),c->new SqliteCatalogStore(c.catalogDbPath())).execute(context,failures::add);
        assertEquals(1,failures.size());assertEquals(FailureSeverity.RECOVERABLE,failures.getFirst().severity());assertEquals("PAYLOAD_REJECTED",failures.getFirst().message());
        var coverage=CoverageSnapshot.restore(context).orElseThrow();assertEquals(1,coverage.failed());assertEquals(1,coverage.complete());assertEquals(2,coverage.emittedRecords());assertEquals(1,coverage.persistedRecords());
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+context.catalogDbPath());var st=db.createStatement();var rows=st.executeQuery("SELECT count(*) FROM documentation_sections WHERE length(payload)>0")) { rows.next();assertEquals(1,rows.getInt(1)); }
    }
    @Test void missingAndCorruptCompletedCoverageHonorFailFastBeforeLaterStages() throws Exception {
        for(boolean corrupt:List.of(false,true)) {
            var args=StructuredDocumentationRoundtripTest.args(temp.resolve(Boolean.toString(corrupt)));
            Files.copy(StructuredDocumentationRoundtripTest.repo().resolve("docs/contracts/fixtures/ingestion-audit-source.html"),args.javadoc().resolve("package-summary.html"));
            var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.FAIL_FAST,args);var first=runner.runToResult(args);
            assertFalse(first.aborted());Path sidecar=first.buildContext().stagingDir().resolve(CoverageSnapshot.FILE);
            if(corrupt)Files.write(sidecar,new byte[]{0});else Files.delete(sidecar);
            var resumed=runner.runToResult(args);assertTrue(resumed.aborted());
            String report=Files.readString(args.out().resolve(first.manifest().buildId()).resolve("ingestion-report.json"));
            assertTrue(report.contains("COVERAGE_UNAVAILABLE"));assertTrue(report.contains("\"stage\": \"RELATIONSHIPS\", \"status\": \"NOT_RUN\""));
        }
    }
}
