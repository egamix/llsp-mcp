package com.luciadmcp.knowledge.retrieval;

import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.catalog.sqlite.*;
import com.luciadmcp.knowledge.search.lucene.LuceneLexicalIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CitedRetrievalTest {
    @TempDir Path temp;
    static final String SDK="2023.5", BUILD="b-"+"1".repeat(64);
    static final byte[] KEY=new byte[32];
    Path database(int count) {
        Path path=temp.resolve("source.sqlite");
        try(var store=new SqliteCatalogStore(path);var graph=new SqliteRelationshipStore(path)) {
            store.bindManifest(new IndexManifest(BUILD,SDK,"synthetic"));
            for(int n=0;n<count;n++) {
                String owner="p.Widget"+n;
                var tuple=new CatalogRecord(SDK,owner,"TYPE",owner,"Widget"+n,"p/Widget"+n+".html","");
                String id=IdentityFields.recordId(tuple);var loc=new SourceLocator(SourceLocator.Kind.SOURCE,tuple.relativePath(),Optional.empty(),"");
                var record=new KnowledgeRecord(id,SDK,KnowledgeRecord.RecordType.API,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,
                    Optional.of(SymbolCoordinate.from(tuple)),loc,governance(),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of(SDK),List.of()));
                var sections=List.of(new DocumentationPayload.Section("body","Documentation",loc,List.of(
                    new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"example","drawTerrain(); elevation contours",List.of()),
                    new DocumentationPayload.Block(DocumentationPayload.BlockKind.NOTE,"Implementation Note","Avoid stale tiles",List.of()),
                    new DocumentationPayload.Block(DocumentationPayload.BlockKind.TABLE,"coordinates","",List.of(),Optional.of(new DocumentationPayload.Table(List.of("axis","unit"),List.of(List.of("height","metres")))),List.of(),false),
                    new DocumentationPayload.Block(DocumentationPayload.BlockKind.LIST,"steps","",List.of(),Optional.empty(),List.of(new DocumentationPayload.ListItem("nested raster",List.of())),false))));
                store.putDocumented(tuple,record,payload(id,sections));
            }
            var loc=new SourceLocator(SourceLocator.Kind.SOURCE,"overview.html",Optional.empty(),"");String id=KnowledgeRecord.narrativeId(SDK,loc.relativePath(),"OVERVIEW");
            var narrative=new KnowledgeRecord(id,SDK,KnowledgeRecord.RecordType.NARRATIVE,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,Optional.empty(),loc,governance(),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of(SDK),List.of()),Optional.of(KnowledgeRecord.NarrativeKind.OVERVIEW));
            store.putNarrative(narrative,payload(id,List.of(new DocumentationPayload.Section("overview","Overview",loc,List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH,"description","narrative rasterization",List.of()))))));
            if(count>1) {
                graph.put(new RelationshipEdge("p.Widget0","p.Widget1",RelationshipType.EXTENDS_CLASS,SDK));
                graph.put(new RelationshipEdge("p.Widget1","p.Widget0",RelationshipType.EXTENDS_CLASS,SDK));
            }
        }
        return path;
    }
    static KnowledgeRecord.Governance governance() { return new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","local source","source documentation"); }
    static DocumentationPayload payload(String id,List<DocumentationPayload.Section> sections) { return new DocumentationPayload(id,"a".repeat(64),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections))); }
    static CitedRetrieval.Request request(String text,Map<String,String> filters,CitedRetrieval.Limits limits,String cursor) {
        return new CitedRetrieval.Request(text,filters,Optional.empty(),Optional.empty(),Optional.of("future"),limits,cursor);
    }
    @Test void durableStructuredCitationsAndNarrativeRetainActualSourceAndTargetContext() {
        Path path=database(2);Path lexical=temp.resolve("index");
        try(var source=new SqliteSymbolSource(path)) { LuceneLexicalIndex.build(lexical,source); }
        try(var source=new SqliteSymbolSource(path);var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source))) {
            var service=new CitedRetrieval(source,source,index,KEY);
            for(String text:List.of("elevation","stale","metres","nested","rasterization")) {
                var page=service.search(request(text,Map.of(),CitedRetrieval.Limits.DEFAULT,""));assertFalse(page.items().isEmpty(),text);
                var item=page.items().getFirst();assertEquals(SDK,item.record().sdkVersion());assertEquals(BUILD,item.source().buildId());assertEquals(Optional.of("future"),page.targetSdk());
                assertEquals(item.record().locator(),item.locator());assertFalse(item.sectionId().isBlank());
                if(text.equals("rasterization"))assertTrue(item.record().symbol().isEmpty());
            }
            var exact=service.search(new CitedRetrieval.Request("p.Widget0"));assertEquals(ExactSymbolResult.Proof.EXACT,exact.symbolProof());assertTrue(exact.items().getFirst().exactIdentity());
            assertEquals(exact.items().size(),exact.items().stream().map(i->i.record().id()).distinct().count());
            assertFalse(service.search(new CitedRetrieval.Request("Widget")).items().stream().anyMatch(CitedRetrieval.Item::exactIdentity));
            assertEquals(CitedRetrieval.Outcome.NO_MATCH,service.search(new CitedRetrieval.Request("zzzzabsent")).outcome());
        }
    }
    private static void putApi(CatalogStore store,CatalogRecord tuple,String reason,String text) {
        String id=IdentityFields.recordId(tuple);
        var locator=new SourceLocator(SourceLocator.Kind.SOURCE,tuple.relativePath(),Optional.empty(),reason);
        var record=new KnowledgeRecord(id,SDK,KnowledgeRecord.RecordType.API,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,
                Optional.of(SymbolCoordinate.from(tuple)),locator,governance(),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of(SDK),List.of()));
        var section=new DocumentationPayload.Section("body","Documentation",locator,List.of(
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH,"description",text,List.of())));
        store.putDocumented(tuple,record,payload(id,List.of(section)));
    }
    @Test void anchorlessMemberRetainsStoredLocatorExplanationWithExactProof() {
        Path path=database(1);Path lexical=temp.resolve("index");
        try(var store=new SqliteCatalogStore(path)) {
            putApi(store,new CatalogRecord(SDK,"p.Widget0#paint()","METHOD","p.Widget0","paint","p/Widget0.html",""),
                    "member anchor absent in source","Paints elevation contours");
        }
        try(var source=new SqliteSymbolSource(path)) {
            LuceneLexicalIndex.build(lexical,source);
            try(var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source))) {
                var page=new CitedRetrieval(source,source,index,KEY).search(new CitedRetrieval.Request("p.Widget0#paint()"));
                assertEquals(ExactSymbolResult.Proof.EXACT,page.symbolProof());
                var member=page.items().getFirst();assertTrue(member.exactIdentity());
                assertEquals("member anchor absent in source",member.locator().reason());
                assertTrue(member.locator().anchor().isEmpty());assertEquals("p/Widget0.html",member.locator().relativePath());
            }
        }
    }
    @Test void twoAmbiguousCandidatesExhaustLimitBeforeThirdLexicalCandidate() {
        Path path=database(0);Path lexical=temp.resolve("index");
        try(var store=new SqliteCatalogStore(path)) {
            for(String owner:List.of("alpha.Widget","beta.Widget"))
                putApi(store,new CatalogRecord(SDK,owner,"TYPE",owner,"Widget",owner.replace('.','/')+".html",""),"","Widget guidance");
            putApi(store,new CatalogRecord(SDK,"gamma.Extra","TYPE","gamma.Extra","Extra","gamma/Extra.html",""),"","Widget thirdonly guidance");
        }
        try(var source=new SqliteSymbolSource(path)) {
            LuceneLexicalIndex.build(lexical,source);
            try(var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source))) {
                int[] lexicalCalls={0};
                LexicalIndex thirdCandidate=new LexicalIndex() {
                    public Binding binding() { return index.binding(); }
                    public Page search(Query query) {
                        lexicalCalls[0]++;
                        return index.search(new Query("thirdonly",query.pageSize(),query.candidateLimit(),query.workLimit(),
                                query.timeoutMillis(),query.filters(),query.targetSdkVersion(),query.offset(),query.priorityRecordIds()));
                    }
                    public void close() {}
                };
                var limits=new CitedRetrieval.Limits(2,5000,5000,10,1_048_576);
                var page=new CitedRetrieval(source,source,thirdCandidate,KEY).search(request("Widget",Map.of(),limits,""));
                assertEquals(ExactSymbolResult.Proof.AMBIGUOUS,page.symbolProof());assertEquals(2,page.candidatesExamined());
                assertEquals(0,lexicalCalls[0]);assertTrue(page.items().isEmpty());assertTrue(page.truncated());
                assertEquals("CANDIDATE_LIMIT",page.reason());assertEquals("",page.continuation());
            }
        }
    }
    @Test void overFiftyStablePagesCursorReplayRebindingAndCandidateWindow() {
        Path path=database(65);Path lexical=temp.resolve("index");
        try(var source=new SqliteSymbolSource(path)) {
            LuceneLexicalIndex.build(lexical,source);
            try(var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source))) {
                var service=new CitedRetrieval(source,source,index,KEY);var limits=new CitedRetrieval.Limits(100,5000,5000,10,1_048_576);
                var ids=new ArrayList<String>();String cursor="";
                do { var q=request("",Map.of(),limits,cursor);var page=service.search(q);assertEquals(page,service.search(q));ids.addAll(page.items().stream().map(i->i.record().id()).toList());cursor=page.continuation(); }while(!cursor.isEmpty());
                assertEquals(66,ids.size());assertEquals(ids.size(),new HashSet<>(ids).size());assertEquals(ids.stream().sorted().toList(),ids);
                var first=service.search(request("",Map.of(),limits,""));String token=first.continuation();
                for(var q:List.of(request("changed",Map.of(),limits,token),request("",Map.of("recordType","API"),limits,token),
                        request("",Map.of(),new CitedRetrieval.Limits(99,5000,5000,10,1_048_576),token),
                        new CitedRetrieval.Request("",Map.of(),Optional.empty(),Optional.empty(),Optional.of("other"),limits,token),request("",Map.of(),limits,token+"x")))
                    assertEquals(CitedRetrieval.Failure.INVALID_CONTINUATION,assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(q)).failure());
                var cap=service.search(request("",Map.of(),new CitedRetrieval.Limits(5,5000,5000,10,1_048_576),""));assertEquals(5,cap.items().size());assertTrue(cap.truncated());assertEquals("",cap.continuation());assertEquals("CANDIDATE_WINDOW_EXHAUSTED",cap.reason());
            }
        }
    }
    @Test void scopeBudgetGovernanceAndBoundedPersistedGraph() {
        Path path=database(3);Path lexical=temp.resolve("index");
        try(var source=new SqliteSymbolSource(path)) {
            LuceneLexicalIndex.build(lexical,source);
            try(var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source));var graph=new SqliteRelationshipStore(path,BUILD,new RelationshipScope(SDK),KEY)) {
                var service=new CitedRetrieval(source,source,index,KEY);
                assertEquals(CitedRetrieval.Failure.SOURCE_MISMATCH,assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(request("",Map.of("sourceSdk","unknown"),CitedRetrieval.Limits.DEFAULT,""))).failure());
                assertEquals(CitedRetrieval.Outcome.FILTERED_CONTENT,service.search(request("",Map.of("approval","CANDIDATE"),CitedRetrieval.Limits.DEFAULT,"")).outcome());
                assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(request("",Map.of(),new CitedRetrieval.Limits(100,1,5000,10,1_048_576),"")));
                assertEquals(CitedRetrieval.Failure.PAYLOAD_LIMIT,assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(request("",Map.of(),new CitedRetrieval.Limits(100,5000,5000,10,4096),""))).failure());
                var narrow=new CitedRetrieval.Limits(1,5000,5000,1,1_048_576);
                var retained=service.search(request("Widget0",Map.of(),narrow,""));assertTrue(retained.candidatesExamined()<=1);
                assertEquals(CitedRetrieval.Failure.CANDIDATE_LIMIT,assertThrows(CitedRetrieval.RetrievalException.class,
                    ()->service.searchWithRelated(request("p.Widget0",Map.of(),narrow,""),graph,"p.Widget0")).failure());
                var related=service.searchWithRelated(new CitedRetrieval.Request("p.Widget0"),graph,"p.Widget0");assertFalse(related.related().items().isEmpty());assertEquals(BUILD,related.related().buildId());assertEquals(1,related.related().appliedLimits().depth());
                assertTrue(related.related().scannedRows()+related.related().visitedNodes()+related.primary().workCharged()<=5000);
            }
        }
    }
    @Test void badContentAndReboundCursorFailBeforeContentReads() {
        Path path=database(2);Path lexical=temp.resolve("index");
        try(var source=new SqliteSymbolSource(path)) {
            LuceneLexicalIndex.build(lexical,source);
            try(var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source))) {
                class Broken implements DocumentationSource {
                    int reads;boolean missing=true;
                    public SymbolSourceBinding binding() { return source.binding(); }
                    public String runtimeFingerprint() { return source.runtimeFingerprint(); }
                    public List<String> documentationIdsAfter(String id,int limit) { throw new AssertionError(); }
                    public Optional<KnowledgeRecord> knowledgeRecord(String id) { reads++;return missing?Optional.empty():source.knowledgeRecord(id); }
                    public List<DocumentationPayload.Section> sections(String id,int offset,int limit) { reads++;return List.of(); }
                    public void close() {}
                }
                var broken=new Broken();var service=new CitedRetrieval(broken,source,index,KEY);
                assertEquals(CitedRetrieval.Failure.INVALID_CONTENT,assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(new CitedRetrieval.Request(""))).failure());
                broken.missing=false;assertEquals(CitedRetrieval.Failure.INVALID_CONTENT,assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(new CitedRetrieval.Request(""))).failure());
                var limits=new CitedRetrieval.Limits(100,5000,5000,1,1_048_576);var good=new CitedRetrieval(source,source,index,KEY).search(request("",Map.of(),limits,""));broken.reads=0;
                assertThrows(CitedRetrieval.RetrievalException.class,()->service.search(request("changed",Map.of(),limits,good.continuation())));assertEquals(0,broken.reads);
            }
        }
    }
}

