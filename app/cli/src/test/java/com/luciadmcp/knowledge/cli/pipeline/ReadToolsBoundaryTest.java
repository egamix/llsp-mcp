package com.luciadmcp.knowledge.cli.pipeline;
import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.catalog.sqlite.*;
import com.luciadmcp.knowledge.search.lucene.*;
import com.luciadmcp.knowledge.mcp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.luciadmcp.knowledge.mcp.Wire.map;
class ReadToolsBoundaryTest {
 @TempDir Path temp;
 static final String BUILD="b-"+"a".repeat(64);
 static Map<String,Object> q(Object... pairs) { var m=map("contractVersion","read-tools/2");for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m; }
 static String add(SqliteCatalogStore store,String owner,int count,String text) {
  var tuple=new CatalogRecord("1",owner,"TYPE",owner,"Widget",owner.replace('.','/')+".html","");String id=IdentityFields.recordId(tuple);
  var loc=new SourceLocator(SourceLocator.Kind.SOURCE,tuple.relativePath(),Optional.empty(),"");
  var record=new KnowledgeRecord(id,"1",KnowledgeRecord.RecordType.API,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,Optional.of(SymbolCoordinate.from(tuple)),loc,
    new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","synthetic","source documentation"),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of("1"),List.of()));
  var sections=new ArrayList<DocumentationPayload.Section>();for(int i=0;i<count;i++)sections.add(new DocumentationPayload.Section("s"+i,"Section "+i,loc,List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"code",text,List.of()))));
  store.putDocumented(tuple,record,new DocumentationPayload(id,"a".repeat(64),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections))));return id;
 }
 @Test void exactAmbiguityPagingFetchHashesLimitsAndTypedFailures() throws Exception {
  Path db=temp.resolve("catalog.sqlite"),lexical=temp.resolve("lexical");String paged,small,large;
  try(var store=new SqliteCatalogStore(db);var graph=new SqliteRelationshipStore(db)) {
   store.bindManifest(IndexManifest.withDocumentation(BUILD,"1",ContentHashes.runtimeFingerprint()));
   paged=add(store,"p.Widget",60,"saved();");small=add(store,"q.Widget",1,"small();");large=add(store,"r.Widget",1,"\u0001".repeat(200000));
   for(int i=0;i<57;i++)add(store,"n"+i+".Widget",1,"sample();");
  }
  try(var source=new SqliteSymbolSource(db)) { LuceneLexicalIndex.build(lexical,source); }
  try(var source=new SqliteSymbolSource(db);var index=new LuceneLexicalIndex(lexical,LexicalIndex.Binding.of(source))) {
   var tools=new ReadTools(source,source,index,new byte[32],false,"Partial ingestion specimen");
   var ambiguous=tools.call("resolve_symbol",q("symbol","Widget","pageSize",50));assertNull(ambiguous.get("error"));assertEquals("AMBIGUOUS",ambiguous.get("proof"));
   var next=q("symbol","Widget","pageSize",50,"continuation",ambiguous.get("continuation"));var last=tools.call("resolve_symbol",next);
   assertEquals(10,((List<?>)((Map<?,?>)last.get("data")).get("matches")).size());
   next.put("targetSdkVersion","changed");assertEquals("INVALID_CONTINUATION",((Map<?,?>)tools.call("resolve_symbol",next).get("error")).get("code"));
   var first=tools.call("get_record",q("recordIds",List.of(paged,small,"missing"),"includeContent",true,"pageSize",50));assertNull(first.get("error"));
   var rows=(List<Map<String,Object>>)((Map<?,?>)first.get("data")).get("records");assertEquals(3,rows.size());assertEquals(50,((List<?>)rows.getFirst().get("sections")).size());assertEquals("NOT_FOUND",rows.get(2).get("status"));
   assertEquals(source.sectionPage(paged,0,50).orElseThrow().pageContentHash(),rows.getFirst().get("pageContentHash"));
   var second=tools.call("get_record",q("recordIds",List.of(paged,small,"missing"),"includeContent",true,"pageSize",50,"continuation",first.get("continuation")));
   assertNull(second.get("error"));var secondRows=(List<Map<String,Object>>)((Map<?,?>)second.get("data")).get("records");assertEquals(10,((List<?>)secondRows.getFirst().get("sections")).size());assertTrue(((List<?>)secondRows.get(1).get("sections")).isEmpty());assertNull(second.get("continuation"));
   var huge=tools.call("get_record",q("recordIds",List.of(large,small),"includeContent",true));assertNull(huge.get("error"));assertTrue(ReadTools.wrappedBytes(huge)<=1048576);assertEquals("PAYLOAD_TOO_LARGE",Wire.JSON.valueToTree(huge).path("data").path("records").get(0).path("status").asText());
   var exhausted=tools.call("get_record",q("recordIds",List.of(paged,small),"includeContent",true,"limits",map("work",1)));assertEquals(2,Wire.JSON.valueToTree(exhausted).path("data").path("records").size());
   var browse=tools.call("search_knowledge",q("mode","BROWSE","pageSize",50));assertNotNull(browse.get("continuation"));assertEquals(false,((Map<?,?>)browse.get("coverage")).get("complete"));
   for(var r:List.of(first,second,huge,exhausted))Wire.validate("get_record.response",r);
   assertEquals("INVALID_REQUEST",Wire.JSON.valueToTree(tools.call("resolve_symbol",q("symbol","Widget","path","forbidden"))).path("error").path("code").asText());
   assertThrows(IllegalArgumentException.class,()->Wire.parse("{} {}".getBytes()));
   assertThrows(IllegalArgumentException.class,()->Wire.parse("{\"x\":1,\"x\":2}".getBytes()));
  }
 }
}
