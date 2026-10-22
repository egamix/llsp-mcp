package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationStoreTest {
    @TempDir Path temp;
    static final String BUILD="b-"+"a".repeat(64);
    static CatalogRecord tuple(String version) { return new CatalogRecord(version,"p.T#m(int)","METHOD","p.T","m","p/T.html","legacy-m-int-"); }
    static KnowledgeRecord knowledge(CatalogRecord tuple) {
        return new KnowledgeRecord(IdentityFields.recordId(tuple),tuple.sdkVersion(),KnowledgeRecord.RecordType.API,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,Optional.of(SymbolCoordinate.from(tuple)),new SourceLocator(SourceLocator.Kind.SOURCE,tuple.relativePath(),Optional.of(tuple.anchor()),""),new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","local source","source documentation"),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of(tuple.sdkVersion()),List.of()));
    }
    static DocumentationPayload payload(KnowledgeRecord record) {
        var locator=record.locator();
        var blocks=List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH,"description","café Ω",List.of(new DocumentationPayload.Link("link","#legacy",record.sdkVersion(),locator))),new DocumentationPayload.Block(DocumentationPayload.BlockKind.TABLE,"table","",List.of(),Optional.of(new DocumentationPayload.Table(List.of("h"),List.of(List.of("v")))),List.of(),false),new DocumentationPayload.Block(DocumentationPayload.BlockKind.LIST,"list","",List.of(),Optional.empty(),List.of(new DocumentationPayload.ListItem("one",List.of(new DocumentationPayload.ListItem("nested",List.of())))),true),new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"code","  a\n b",List.of()));
        var sections=List.of(new DocumentationPayload.Section("description","Description",locator,blocks),new DocumentationPayload.Section("returns","Returns",locator,List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.NOTE,"returns","answer",List.of()))));
        return new DocumentationPayload(record.id(),ContentHashes.sourceHash(new byte[]{1,2,3}),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)));
    }
    Path seed() {
        Path path=temp.resolve("catalog.db");
        try(var catalog=new SqliteCatalogStore(path)) { catalog.bindManifest(IndexManifest.withDocumentation(BUILD,"1","synthetic"));var tuple=tuple("1");var record=knowledge(tuple);assertEquals(CatalogStore.PutStatus.INSERTED,catalog.putDocumented(tuple,record,payload(record)).status()); }
        try(var edges=new SqliteRelationshipStore(path)) { }
        return path;
    }
    @Test void structuredReopenPagingScopesAndRetry() {
        Path path=seed();var tuple=tuple("1");var record=knowledge(tuple);var payload=payload(record);
        try(var store=new SqliteDocumentationStore(path)) {
            assertEquals(DocumentationStore.WriteStatus.IDENTICAL,store.putAtomically(record,payload));
            assertEquals(List.of(record.id()),store.enumerateIds(BUILD,"1","",50));
            var first=store.readSections(record.id(),BUILD,"1",0,1).orElseThrow();assertEquals(List.of(payload.sections().get(0)),first.sections());assertTrue(first.continuation().orElseThrow().startsWith("1:"));assertEquals(payload.contentHash(),first.documentContentHash());
            assertEquals(List.of(payload.sections().get(1)),store.readSections(record.id(),BUILD,"1",first.continuation().orElseThrow(),1).orElseThrow().sections());
            assertTrue(store.readSections("missing",BUILD,"1",0,1).isEmpty());
            assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),BUILD,"1",first.continuation().orElseThrow(),2));
            assertThrows(IllegalArgumentException.class,()->store.readSections("other",BUILD,"1",first.continuation().orElseThrow(),1));
            assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),"other","1",first.continuation().orElseThrow(),1));
            assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),BUILD,"other",first.continuation().orElseThrow(),1));
            for(int limit:new int[]{0,51})assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),BUILD,"1",0,limit));
            for(int offset:new int[]{-1,2,257})assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),BUILD,"1",offset,1));
            assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),"wrong","1",0,1));assertThrows(IllegalArgumentException.class,()->store.enumerateIds(BUILD,"2","",1));
            var changed=new DocumentationPayload(record.id(),ContentHashes.sourceHash(new byte[]{4}),payload.sections(),payload.contentHash());
            assertEquals(DocumentationStore.WriteStatus.IDENTITY_CLASH,store.putAtomically(record,changed));
        }
        try(var catalog=new SqliteCatalogStore(path)) { assertTrue(catalog.validateBuild(new StandardBuildValidator(),BuildValidator.Operation.CLI,BUILD,"1").valid()); }
    }
    @Test void rollsBackAtMetadataDocumentAndSectionBoundaries() throws Exception {
        for(String table:List.of("knowledge_records","documentation","documentation_sections")) {
            Path path=temp.resolve(table+".db");
            try(var catalog=new SqliteCatalogStore(path)) {
                try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var s=db.createStatement()) { s.execute("CREATE TRIGGER fail_write BEFORE INSERT ON "+table+" BEGIN SELECT RAISE(ABORT,'injected'); END"); }
                var tuple=tuple("1");var record=knowledge(tuple);assertThrows(IllegalStateException.class,()->catalog.putDocumented(tuple,record,payload(record)));assertTrue(catalog.allRecords().isEmpty());
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var s=db.createStatement()) { for(String check:List.of("records","knowledge_records","documentation","documentation_sections"))try(var r=s.executeQuery("SELECT count(*) FROM "+check)) { assertTrue(r.next());assertEquals(0,r.getInt(1)); } }
        }
    }
    @Test void corruptAndOversizeRejectCliAndPinnedStartup() throws Exception {
        Path path=seed();
        for(String corruption:List.of("UPDATE documentation_sections SET payload=x'00000001'","UPDATE documentation_sections SET payload=zeroblob(2000000)","UPDATE documentation SET content_hash='"+"0".repeat(64)+"'","UPDATE knowledge_records SET approval='CANDIDATE'",
                "UPDATE documentation SET metadata=zeroblob(2000000)","UPDATE documentation SET source_hash='bad'",
                "UPDATE documentation SET section_count=257","UPDATE documentation_sections SET ordinal=ordinal+300",
                "DELETE FROM documentation","DELETE FROM knowledge_records","UPDATE knowledge_records SET applicability='bad'")) {
            Path copy=temp.resolve("bad"+Math.abs(corruption.hashCode())+".db");Files.copy(path,copy);
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+copy);var s=db.createStatement()) { s.executeUpdate(corruption); }
            try(var catalog=new SqliteCatalogStore(copy)) { assertFalse(catalog.validateBuild(new StandardBuildValidator(),BuildValidator.Operation.CLI,BUILD,"1").valid()); }
            assertThrows(IllegalStateException.class,()->new SqliteRelationshipStore(copy,BUILD,new RelationshipScope("1"),new byte[32]));
        }
    }
    @Test void rejectsSchemaOneWithoutChangingBytes() throws Exception {
        Path path=temp.resolve("old.db");try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var s=db.createStatement()) { s.execute("PRAGMA user_version=1");s.execute("CREATE TABLE original(value)"); }
        byte[] before=Files.readAllBytes(path);assertThrows(IllegalStateException.class,()->new SqliteCatalogStore(path));assertArrayEquals(before,Files.readAllBytes(path));
    }

    @Test void admittedNearCapSectionRemainsReadableAfterReopen() {
        var tuple=tuple("1");var record=knowledge(tuple);
        String text="x".repeat(1_048_000);
        var section=new DocumentationPayload.Section("api","API documentation",record.locator(),List.of(
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"signature",text,List.of())));
        var sections=List.of(section);
        var payload=new DocumentationPayload(record.id(),"0".repeat(64),sections,
                ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)));
        int encodedBytes=DocumentationCodec.section(section).length;
        assertTrue(encodedBytes>DocumentationPayload.MAX_BYTES-1024);
        assertTrue(encodedBytes<=DocumentationCodec.MAX_ENCODED_BYTES);
        Path path=temp.resolve("near-cap.db");
        try(var catalog=new SqliteCatalogStore(path)) {
            catalog.bindManifest(IndexManifest.withDocumentation(BUILD,"1","synthetic"));
            assertEquals(CatalogStore.PutStatus.INSERTED,catalog.putDocumented(tuple,record,payload).status());
        }
        try(var store=new SqliteDocumentationStore(path)) {
            var page=store.readSections(record.id(),BUILD,"1",0,1).orElseThrow();
            assertEquals(sections,page.sections());
            assertEquals(payload.contentHash(),page.documentContentHash());
            assertEquals(payload.contentHash(),page.pageContentHash());
            assertTrue(page.continuation().isEmpty());
        }
    }

    @Test void exactSectionAndBlockCapsPagingAndEncodedAdmission() throws Exception {
        var tuple=tuple("1");var record=knowledge(tuple);var locator=record.locator();
        var block=new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH,"","v",List.of());
        List<DocumentationPayload.Section> sections=new ArrayList<>();
        for(int i=0;i<256;i++)sections.add(new DocumentationPayload.Section("s"+i,"",locator,Collections.nCopies(16,block)));
        var payload=new DocumentationPayload(record.id(),"0".repeat(64),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)));
        Path path=temp.resolve("caps.db");
        try(var catalog=new SqliteCatalogStore(path)) { catalog.bindManifest(IndexManifest.withDocumentation(BUILD,"1","synthetic"));assertEquals(CatalogStore.PutStatus.INSERTED,catalog.putDocumented(tuple,record,payload).status()); }
        try(var store=new SqliteDocumentationStore(path)) {
            int returned=0;var page=store.readSections(record.id(),BUILD,"1",0,50).orElseThrow();
            while(true) { returned+=page.sections().size();if(page.continuation().isEmpty())break;page=store.readSections(record.id(),BUILD,"1",page.continuation().orElseThrow(),50).orElseThrow(); }
            assertEquals(256,returned);
            for(String malformed:List.of("", "-1:bad", "256:bad", "abc", "1:bad", "1:"+"a".repeat(200)))assertThrows(IllegalArgumentException.class,()->store.readSections(record.id(),BUILD,"1",malformed,50));
        }
        var extra=new ArrayList<>(sections);extra.add(new DocumentationPayload.Section("extra","",locator,List.of()));
        assertThrows(IllegalArgumentException.class,()->DocumentationPayload.canonicalFields(extra));
        var tooManyBlocks=List.of(new DocumentationPayload.Section("s","",locator,Collections.nCopies(4097,block)));
        assertThrows(IllegalArgumentException.class,()->new DocumentationPayload(record.id(),"0".repeat(64),tooManyBlocks,"0".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->DocumentationCodec.decode(new byte[]{127,127,127,127}));
        assertThrows(IllegalArgumentException.class,()->DocumentationCodec.decode(new byte[DocumentationCodec.MAX_ENCODED_BYTES+1]));
        var tooMuchText=List.of(new DocumentationPayload.Section("s","",locator,List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"","x".repeat(DocumentationPayload.MAX_BYTES+1),List.of()))));
        assertThrows(IllegalArgumentException.class,()->DocumentationPayload.canonicalFields(tooMuchText));
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var s=db.createStatement()) { s.executeUpdate("UPDATE documentation_sections SET payload=zeroblob(2000000) WHERE ordinal=0"); }
        try(var store=new SqliteDocumentationStore(path)) { assertThrows(IllegalStateException.class,()->store.readSections(record.id(),BUILD,"1",0,1)); }
    }

    static KnowledgeRecord narrative(String path,KnowledgeRecord.NarrativeKind kind) {
        return new KnowledgeRecord(KnowledgeRecord.narrativeId("1",path,kind.name()),"1",KnowledgeRecord.RecordType.NARRATIVE,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,Optional.empty(),new SourceLocator(SourceLocator.Kind.SOURCE,path,Optional.empty(),""),new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","local Javadoc source","source narrative documentation"),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of("1"),List.of()),Optional.of(kind));
    }
    @Test void narrativesShareAtomicContentButNeverCatalogRows() throws Exception {
        Path path=seed();var record=narrative("p/package-summary.html",KnowledgeRecord.NarrativeKind.PACKAGE);var payload=payload(record);
        try(var store=new SqliteDocumentationStore(path)) {
            assertEquals(DocumentationStore.WriteStatus.INSERTED,store.putAtomically(record,payload));
            assertEquals(DocumentationStore.WriteStatus.IDENTICAL,store.putAtomically(record,payload));
            assertEquals(2,store.enumerateIds(BUILD,"1","",50).size());
            assertEquals(payload.sections(),store.readSections(record.id(),BUILD,"1",0,50).orElseThrow().sections());
        }
        try(var catalog=new SqliteCatalogStore(path)) { assertEquals(1,catalog.allRecords().size());assertTrue(catalog.validateBuild(new StandardBuildValidator(),BuildValidator.Operation.CLI,BUILD,"1").valid()); }
        for(String corruption:List.of("narrative_kind='OVERVIEW'","symbol_coordinate='invented'","source_path='other.html'","sdk_version='2'","record_type='API'","record_id='"+"b".repeat(64)+"'","owner=''")) {
            Path copy=temp.resolve("narrative"+Math.abs(corruption.hashCode())+".db");Files.copy(path,copy);
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+copy);var st=db.createStatement()) { st.executeUpdate("UPDATE knowledge_records SET "+corruption+" WHERE record_type='NARRATIVE'"); }
            try(var catalog=new SqliteCatalogStore(copy)) { assertFalse(catalog.validateBuild(new StandardBuildValidator(),BuildValidator.Operation.CLI,BUILD,"1").valid(),corruption); }
            assertThrows(IllegalStateException.class,()->new SqliteRelationshipStore(copy,BUILD,new RelationshipScope("1"),new byte[32]),corruption);
        }
    }
    @Test void narrativeRollbackAndSchemaTwoRejection() throws Exception {
        Path path=seed();var record=narrative("overview-summary.html",KnowledgeRecord.NarrativeKind.OVERVIEW);
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var st=db.createStatement()) { st.execute("CREATE TRIGGER fail_narrative BEFORE INSERT ON documentation_sections BEGIN SELECT RAISE(ABORT,'injected'); END"); }
        try(var store=new SqliteDocumentationStore(path)) { assertThrows(IllegalStateException.class,()->store.putAtomically(record,payload(record)));assertEquals(1,store.enumerateIds(BUILD,"1","",50).size()); }
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var st=db.createStatement();var r=st.executeQuery("SELECT count(*) FROM knowledge_records WHERE record_type='NARRATIVE'")) { r.next();assertEquals(0,r.getInt(1)); }
        Path old=temp.resolve("schema2.db");try(var db=DriverManager.getConnection("jdbc:sqlite:"+old);var st=db.createStatement()) { st.execute("PRAGMA user_version=2"); }
        byte[] before=Files.readAllBytes(old);assertThrows(IllegalStateException.class,()->new SqliteCatalogStore(old));assertArrayEquals(before,Files.readAllBytes(old));
    }
}
