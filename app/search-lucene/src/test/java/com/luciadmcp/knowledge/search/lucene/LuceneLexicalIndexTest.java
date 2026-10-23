package com.luciadmcp.knowledge.search.lucene;

import com.luciadmcp.knowledge.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LuceneLexicalIndexTest {
    @TempDir Path temp;
    private static class Source implements DocumentationSource {
        final SymbolSourceBinding binding=new SymbolSourceBinding("1","build", "a".repeat(64));
        final Map<String,KnowledgeRecord> records=new TreeMap<>();
        final Map<String,List<DocumentationPayload.Section>> content=new HashMap<>();
        boolean fail;
        void add(String id,String owner,String text) {
            var locator=new SourceLocator(SourceLocator.Kind.SOURCE,"p/"+id+".html",Optional.empty(),"");
            var symbol=owner.isEmpty()?Optional.<SymbolCoordinate>empty():Optional.of(new SymbolCoordinate("LuciadLightspeed","1","java","p",owner,"",List.of(),"TYPE"));
            records.put(id,new KnowledgeRecord(id,"1",owner.isEmpty()?KnowledgeRecord.RecordType.NARRATIVE:KnowledgeRecord.RecordType.API,
                    KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,symbol,locator,
                    new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","local source","source documentation"),
                    new KnowledgeRecord.Applicability("LuciadLightspeed",List.of("1"),List.of()),owner.isEmpty()?Optional.of(KnowledgeRecord.NarrativeKind.PACKAGE):Optional.empty()));
            content.put(id,List.of(new DocumentationPayload.Section("api","Documentation",locator,List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH,"description",text,List.of())))));
        }
        public SymbolSourceBinding binding() { return binding; }
        public String runtimeFingerprint() { return "runtime"; }
        public List<String> documentationIdsAfter(String after,int limit) { return records.keySet().stream().filter(id->id.compareTo(after)>0).limit(limit).toList(); }
        public Optional<KnowledgeRecord> knowledgeRecord(String id) { return Optional.ofNullable(records.get(id)); }
        public List<DocumentationPayload.Section> sections(String id,int offset,int limit) {
            if(fail && id.equals("b"))throw new IllegalStateException("injected index generation failure");
            var list=content.get(id);return list.subList(Math.min(offset,list.size()),Math.min(offset+limit,list.size()));
        }
        public void close() { }
    }
    private static LexicalIndex.Query query(String text,int page,int candidates,int work,Map<String,String> filters) {
        return new LexicalIndex.Query(text,page,candidates,work,1000,filters,Optional.of("future-sdk"));
    }
    @Test void durableProseSymbolsHighlightsAndExplicitMetadataFilters() {
        var source=new Source();source.add("a","p.FastTerrainPainter","Renders elevation contours using a height grid.");source.add("b","","Package narrative describes rasterization.");
        Path path=temp.resolve("index");LuceneLexicalIndex.build(path,source);
        for(int reopen=0;reopen<2;reopen++)try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
            var prose=index.search(new LexicalIndex.Query("elevation"));assertEquals("a",prose.hits().getFirst().recordId());
            var hit=prose.hits().getFirst();assertEquals("api",hit.sectionId());assertEquals("description",hit.label());assertTrue(hit.excerpt().contains("elevation"));
            assertTrue(hit.highlights().stream().anyMatch(s->hit.excerpt().substring(s.start(),s.end()).equals("elevation")));
            for(String name:List.of("p.FastTerrainPainter","FastTer","terrain","paint"))assertEquals("a",index.search(new LexicalIndex.Query(name)).hits().getFirst().recordId(),name);
            assertEquals("b",index.search(new LexicalIndex.Query("rasterization")).hits().getFirst().recordId());
            assertEquals(1,index.search(query("",10,100,100,Map.of("recordType","NARRATIVE","knowledgeKind","AUTHORITATIVE_FACT","approval","APPROVED","product","LuciadLightspeed","applicableSdk","1"))).hits().size());
            assertTrue(index.search(query("elevation",10,100,100,Map.of("sourceSdk","2"))).hits().isEmpty());
            assertEquals(1,index.search(query("",10,100,100,Map.of("package","p","elementKind","TYPE","deprecated","false","owner","javadoc","provenance","local source","applicabilityScope","VERSION_SCOPED"))).hits().size());
            assertEquals(Optional.of("future-sdk"),index.search(query("elevation",10,100,100,Map.of())).targetSdkVersion());
        }
    }
    @Test void tiesBrowseCandidateAndWorkLimitsAreDeterministicAndTruthful() {
        var source=new Source();for(String id:List.of("c","a","b"))source.add(id,"","identical lexeme");
        Path path=temp.resolve("index");LuceneLexicalIndex.build(path,source);
        try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
            for(String text:List.of("","lexeme")) {
                var page=index.search(query(text,10,100,100,Map.of()));assertEquals(List.of("a","b","c"),page.hits().stream().map(LexicalIndex.Hit::recordId).toList());assertEquals(3,page.matchedCount());assertEquals(LexicalIndex.CountRelation.EXACT,page.countRelation());
                var capped=index.search(query(text,10,1,100,Map.of()));assertEquals(1,capped.hits().size());assertTrue(capped.truncated());assertEquals(LexicalIndex.CountRelation.EXACT,capped.countRelation());assertEquals(3,capped.matchedCount());assertEquals(3,capped.workCharged());
                var work=index.search(query(text,10,100,1,Map.of()));assertEquals(1,work.workCharged());assertEquals(LexicalIndex.CountRelation.LOWER_BOUND,work.countRelation());
                var paged=index.search(query(text,1,100,100,Map.of()));assertTrue(paged.truncated());assertEquals(3,paged.matchedCount());
            }
        }
    }
    @Test void candidateRetentionLimitStillRanksLaterBetterMatchesWithinWorkBudget() {
        var source=new Source();
        for(int n=0;n<10;n++)source.add(String.format("%04d",n),"","needle "+"padding ".repeat(200));
        source.add("9999","","needle");Path path=temp.resolve("index");LuceneLexicalIndex.build(path,source);
        try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
            var page=index.search(query("needle",10,2,100,Map.of()));
            assertEquals(2,page.hits().size());assertEquals("9999",page.hits().getFirst().recordId());
            assertEquals(11,page.matchedCount());assertEquals(11,page.workCharged());
            assertEquals(LexicalIndex.CountRelation.EXACT,page.countRelation());assertTrue(page.truncated());assertEquals("CANDIDATE_LIMIT",page.reason());
            var bounded=index.search(query("needle",10,2,5,Map.of()));
            assertEquals(2,bounded.hits().size());assertEquals(5,bounded.matchedCount());assertEquals(5,bounded.workCharged());
            assertEquals(LexicalIndex.CountRelation.LOWER_BOUND,bounded.countRelation());assertTrue(bounded.truncated());assertEquals("WORK_LIMIT",bounded.reason());
        }
    }
    @Test void mismatchedBindingsMissingIndicesAndFailedGenerationFailClearly() {
        var source=new Source();source.add("a","","original prose");Path path=temp.resolve("index");
        assertThrows(IllegalStateException.class,()->new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source)));assertFalse(Files.exists(path));
        LuceneLexicalIndex.build(path,source);var expected=LexicalIndex.Binding.of(source);
        var wrong=List.of(new LexicalIndex.Binding(new SymbolSourceBinding("2","build","a".repeat(64)),"runtime",LexicalIndex.RECIPE),
                new LexicalIndex.Binding(new SymbolSourceBinding("1","other","a".repeat(64)),"runtime",LexicalIndex.RECIPE),
                new LexicalIndex.Binding(new SymbolSourceBinding("1","build","b".repeat(64)),"runtime",LexicalIndex.RECIPE),
                new LexicalIndex.Binding(source.binding(),"other",LexicalIndex.RECIPE),new LexicalIndex.Binding(source.binding(),"runtime","other"));
        for(var binding:wrong)assertTrue(assertThrows(IllegalStateException.class,()->new LuceneLexicalIndex(path,binding)).getMessage().contains("INCOMPATIBLE"));
        source.add("b","","late record");source.fail=true;assertThrows(IllegalStateException.class,()->LuceneLexicalIndex.build(path,source));
        try(var index=new LuceneLexicalIndex(path,expected)) { assertEquals("a",index.search(new LexicalIndex.Query("original")).hits().getFirst().recordId()); }
        Path failed=temp.resolve("failed");assertThrows(IllegalStateException.class,()->LuceneLexicalIndex.build(failed,source));assertThrows(IllegalStateException.class,()->new LuceneLexicalIndex(failed,expected));
        source.fail=false;LuceneLexicalIndex.build(failed,source);try(var index=new LuceneLexicalIndex(failed,expected)) { assertEquals(2,index.search(new LexicalIndex.Query("")).hits().size()); }
    }
    @Test void boundedBatchesKeepOneRowAndPreserveLateSectionContext() {
        var source=new Source();for(int n=0;n<260;n++)source.add(String.format("%04d",n),"","shared content");
        var record=source.records.get("0000");var sections=new ArrayList<DocumentationPayload.Section>();
        for(int n=0;n<17;n++)sections.add(new DocumentationPayload.Section("section"+n,"Section "+n,record.locator(),List.of(
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.NOTE,n==16?"Implementation Note":"description",n==16?"durabletail":"shared content",List.of()))));
        source.content.put("0000",sections);Path path=temp.resolve("index");LuceneLexicalIndex.build(path,source);
        try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
            assertEquals(260,index.search(new LexicalIndex.Query("")).matchedCount());
            var late=index.search(new LexicalIndex.Query("durabletail")).hits().getFirst();assertEquals("0000",late.recordId());assertEquals("section16",late.sectionId());assertEquals("Implementation Note",late.label());
        }
    }
    @Test void excerptsAndResultPayloadAreBoundedWithoutTruncatingSectionIdentity() {
        var source=new Source();source.add("a","","start "+"x ".repeat(500)+"lateword ending");
        source.add("b","","payloadword");var r=source.records.get("b");source.content.put("b",List.of(new DocumentationPayload.Section("s".repeat(180000),"Title",r.locator(),List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH,"description","payloadword",List.of())))));
        Path path=temp.resolve("index");LuceneLexicalIndex.build(path,source);
        try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
            var excerpt=index.search(new LexicalIndex.Query("lateword")).hits().getFirst();assertTrue(excerpt.excerpt().length()<=512);assertTrue(excerpt.excerpt().contains("lateword"));
            var page=index.search(new LexicalIndex.Query("payloadword"));assertTrue(page.hits().isEmpty());assertTrue(page.truncated());assertEquals("PAYLOAD_LIMIT",page.reason());assertEquals(1,page.matchedCount());
        }
    }
    @Test void unicodeCaseMatchingKeepsOriginalExcerptAndHighlightOffsets() {
        var source=new Source();
        source.add("a","p.Needle","\u0130".repeat(100)+"NeEdLe"+"x".repeat(1000));
        source.add("b","p.Needle","\u0130".repeat(101)+"\uD83D\uDE00".repeat(100)+" NeEdLe "+"\uD83D\uDE00".repeat(1000));
        Path path=temp.resolve("index");LuceneLexicalIndex.build(path,source);
        try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
            var hits=index.search(new LexicalIndex.Query("needle")).hits();assertEquals(2,hits.size());
            for(var hit:hits) {
                String excerpt=hit.excerpt();assertTrue(excerpt.length()<=512);assertTrue(excerpt.contains("NeEdLe"));
                assertFalse(Character.isLowSurrogate(excerpt.charAt(0)));
                assertFalse(Character.isHighSurrogate(excerpt.charAt(excerpt.length()-1)));
                assertEquals(List.of(new LexicalIndex.Span(excerpt.indexOf("NeEdLe"),excerpt.indexOf("NeEdLe")+6)),hit.highlights());
                assertEquals("NeEdLe",excerpt.substring(hit.highlights().getFirst().start(),hit.highlights().getFirst().end()));
                String original=source.content.get(hit.recordId()).getFirst().blocks().getFirst().text();
                assertTrue(original.contains(excerpt));
            }
        }
    }
    @Test void queryUtf8LimitsAndImmutableInputs() {
        assertEquals(10,new LexicalIndex.Query("word").pageSize());assertEquals(1000,new LexicalIndex.Query("word").timeoutMillis());
        assertThrows(IllegalArgumentException.class,()->new LexicalIndex.Query("é".repeat(1025)));
        assertThrows(IllegalArgumentException.class,()->new LexicalIndex.Query("x",51,100,100,1000,Map.of(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new LexicalIndex.Query("x",10,100,100,5001,Map.of(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->query("x",10,0,100,Map.of()));assertThrows(IllegalArgumentException.class,()->query("x",10,100,0,Map.of()));
        assertThrows(IllegalArgumentException.class,()->query("x",10,100,100,Map.of("quality","invented")));
        var filters=new HashMap<String,String>();filters.put("sourceSdk","1");var q=query("",10,100,100,filters);filters.clear();assertEquals("1",q.filters().get("sourceSdk"));
    }
}
