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
            var first=store.readSections(record.id(),BUILD,"1",0,1).orElseThrow();assertEquals(List.of(payload.sections().get(0)),first.sections());assertEquals(Optional.of("1"),first.continuation());assertEquals(payload.contentHash(),first.documentContentHash());
            assertEquals(List.of(payload.sections().get(1)),store.readSections(record.id(),BUILD,"1",Integer.parseInt(first.continuation().orElseThrow()),50).orElseThrow().sections());
            assertTrue(store.readSections("missing",BUILD,"1",0,1).isEmpty());
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
        for(String corruption:List.of("UPDATE documentation_sections SET payload=x'00000001'","UPDATE documentation_sections SET payload=zeroblob(2000000)","UPDATE documentation SET content_hash='"+"0".repeat(64)+"'","UPDATE knowledge_records SET approval='CANDIDATE'")) {
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
}
