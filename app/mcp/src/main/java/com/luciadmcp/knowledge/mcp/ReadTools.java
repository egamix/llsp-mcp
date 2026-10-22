package com.luciadmcp.knowledge.mcp;

import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.retrieval.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static com.luciadmcp.knowledge.mcp.Wire.map;

/** Shared transport/CLI application boundary. Owns envelopes, finite budgets and authenticated cursors. */
public final class ReadTools {
    public static final List<String> NAMES=List.of("search_knowledge","resolve_symbol","get_record");
    private final DocumentationSource content;
    private final SymbolSource symbols;
    private final CitedRetrieval search;
    private final byte[] key;
    private final boolean complete;
    private final String coverageReason;
    public ReadTools(DocumentationSource content,SymbolSource symbols,LexicalIndex index,byte[] key,boolean complete,String coverageReason) {
        this.content=content;this.symbols=symbols;this.key=key.clone();this.complete=complete;this.coverageReason=coverageReason;
        this.search=new CitedRetrieval(content,symbols,index,key);
    }
    public Map<String,Object> call(String name,Map<String,Object> input) {
        Map<String,Object> result;
        try {
            if(!NAMES.contains(name))throw new IllegalArgumentException();
            Wire.validate(name+".request",input);
            if(Wire.bytes(input).length>8388608)throw new IllegalArgumentException();
            var q=Wire.JSON.valueToTree(input);
            for(String field:List.of("query","symbol","sourceSdkVersion","targetSdkVersion","buildId"))
                if(q.hasNonNull(field) && !q.path(field).asText().isEmpty())ExactSymbolQuery.text(q.path(field).asText());
            var binding=content.binding();
            if(q.hasNonNull("sourceSdkVersion") && !q.path("sourceSdkVersion").asText().equals(binding.sdkVersion()))return error(name,input,"UNKNOWN_SDK");
            if(q.hasNonNull("buildId") && !q.path("buildId").asText().equals(binding.buildId()))return error(name,input,"BUILD_UNAVAILABLE");
            int page=q.path("pageSize").asInt(10),work=q.path("limits").path("work").asInt(5000),candidates=q.path("limits").path("candidates").asInt(1000);
            int millis=q.path("limits").path("executionMillis").asInt(1000),bytes=q.path("limits").path("responseBytes").asInt(1048576);
            Budget budget=new Budget(work,millis);
            result=switch(name) {
                case "search_knowledge" -> search(input,page,work,candidates,millis,bytes);
                case "resolve_symbol" -> resolve(input,page,candidates,budget);
                case "get_record" -> fetch(input,page,bytes,budget);
                default -> throw new IllegalArgumentException();
            };
            // Includes SDK result wrapper and JSON-RPC framing allowance, using actual serialized bytes.
            if(wrappedBytes(result)>bytes) {
                if(name.equals("get_record")) {
                    var records=(List<Map<String,Object>>)((Map<?,?>)result.get("data")).get("records");
                    for(int i=records.size()-1;i>=0 && wrappedBytes(result)>bytes;i--)
                        records.set(i,verdict((String)records.get(i).get("inputRecordId"),"PAYLOAD_TOO_LARGE","Serialized response budget"));
                    result.put("truncated",true);result.put("continuation",null);
                } else result=error(name,input,"PAYLOAD_TOO_LARGE");
            }
            Wire.validate(name+".response",result);return result;
        } catch(CitedRetrieval.RetrievalException e) {
            return error(name,input,switch(e.failure()) {
                case SOURCE_MISMATCH -> "UNKNOWN_SDK";case BUILD_MISMATCH,INDEX_UNAVAILABLE -> "BUILD_UNAVAILABLE";
                case INVALID_CONTINUATION -> "INVALID_CONTINUATION";case WORK_LIMIT,CANDIDATE_LIMIT -> "WORK_LIMIT";
                case TIME_LIMIT -> "DEADLINE";case PAYLOAD_LIMIT -> "PAYLOAD_TOO_LARGE";default -> "INCOMPATIBLE_BUILD";
            });
        } catch(Limit e) { return error(name,input,e.code); }
        catch(IllegalArgumentException e) { return error(name,input,"INVALID_REQUEST"); }
        catch(SymbolSourceException e) { return error(name,input,e.code().name().equals("BUILD_INVALID")?"INCOMPATIBLE_BUILD":"BUILD_UNAVAILABLE"); }
        catch(RuntimeException e) { return error(name,input,"INTERNAL_ERROR"); }
    }
    public static int wrappedBytes(Map<String,Object> result) {
        return Wire.bytes(map("jsonrpc","2.0","id","ffffffffffffffffffffffffffffffff","result",
            map("content",List.of(map("type","text","text","Luciad read-tools/2 result")),"structuredContent",result,"isError",result.get("error")!=null))).length;
    }
    private Map<String,Object> search(Map<String,Object> q,int page,int work,int candidates,int millis,int bytes) {
        var kinds=strings(q,"kinds");var filters=new HashMap<String,String>();
        // This Javadoc profile admits only authoritative facts; restrictive kinds never broaden results.
        if(!kinds.isEmpty() && !kinds.contains("AUTHORITATIVE_FACT"))filters.put("knowledgeKind",kinds.getFirst());
        var p=search.search(new CitedRetrieval.Request(text(q,"query",""),filters,opt(q,"sourceSdkVersion"),opt(q,"buildId"),opt(q,"targetSdkVersion"),
            new CitedRetrieval.Limits(candidates,work,millis,page,bytes-8192),text(q,"continuation","")));
        var items=new ArrayList<Map<String,Object>>();
        for(var i:p.items())items.add(item(i.record(),i.exactIdentity()?"EXACT":"NOT_APPLICABLE",i.exactIdentity()?"COORDINATE":text(q,"mode","LEXICAL").equals("BROWSE")?"BROWSE":"LEXICAL",i.snippet(),i.sectionId()));
        var r=envelope("search_knowledge",q,p.symbolProof().name(),map("hits",items));
        finish(r,p.matchedCount(),p.countRelation().name(),p.workCharged(),p.candidatesExamined(),p.truncated(),p.continuation(),items.isEmpty()?p.outcome().name():null,
            items.stream().map(i->(String)i.get("matchedBy")).distinct().toList());return r;
    }
    private Map<String,Object> resolve(Map<String,Object> q,int page,int candidates,Budget budget) {
        int offset=offset(q,"resolve",candidates);
        SymbolSource timed=new SymbolSource() {
            public SymbolSourceBinding binding() { return symbols.binding(); }
            public Optional<StoredCatalogRecord> canonical(String key) { budget.check();return symbols.canonical(key); }
            public List<StoredCatalogRecord> after(String id,int limit) { budget.check();return symbols.after(id,limit); }
            public void close() {}
        };
        var p=new ExactSymbolResolver(timed).resolve(new ExactSymbolQuery(text(q,"symbol",""),!Boolean.FALSE.equals(q.get("includeAliases")),page,candidates,budget.max,
            opt(q,"sourceSdkVersion"),opt(q,"buildId"),opt(q,"targetSdkVersion")),offset);
        budget.used=p.workCharged();budget.check();
        var matches=new ArrayList<Map<String,Object>>();
        for(var m:p.matches()) {
            budget.check();var record=content.knowledgeRecord(m.recordId()).orElseThrow(()->new IllegalStateException("MISSING_CONTENT"));
            matches.add(item(record,p.proof().name(),m.matchedBy().equals("canonical")?"COORDINATE":"ALIAS",null,null));
        }
        int next=offset+matches.size();boolean more=next<p.candidatesMatched();
        String cursor=more && !matches.isEmpty()?cursor(q,"resolve",next):"";
        var r=envelope("resolve_symbol",q,p.proof().name(),map("matches",matches));
        finish(r,p.candidatesMatched(),p.complete()?"EXACT":"LOWER_BOUND",p.workCharged(),p.candidatesMatched(),more||!p.complete(),cursor,matches.isEmpty()?p.proof().name():null,
            matches.stream().map(i->(String)i.get("matchedBy")).distinct().toList());return r;
    }
    private Map<String,Object> fetch(Map<String,Object> q,int page,int bytes,Budget budget) {
        var ids=strings(q,"recordIds");var selected=strings(q,"sectionIds");var offsets=sectionOffsets(q,ids);
        var records=new ArrayList<Map<String,Object>>();var continuing=new ArrayList<Map<String,Object>>();boolean truncated=false;
        for(String id:ids) {
            try {
                budget.charge();var maybe=content.knowledgeRecord(id);
                if(maybe.isEmpty()) { records.add(verdict(id,"NOT_FOUND","No saved record in this source"));offsets.put(id,-1);continue; }
                var record=maybe.get();
                if(record.governance().approval()!=KnowledgeRecord.Approval.APPROVED || record.knowledgeKind()==KnowledgeRecord.KnowledgeKind.CANDIDATE_LEARNING) {
                    records.add(verdict(id,"INAPPLICABLE","Governance excludes this record"));offsets.put(id,-1);continue;
                }
                var sections=new ArrayList<DocumentationPayload.Section>();String hash=null;int at=offsets.get(id);boolean done=at==-1,more=false;
                if(done) { budget.charge();hash=content.sectionPage(id,0,1).orElseThrow().documentContentHash(); }
                else do {
                    budget.charge();var p=content.sectionPage(id,at,1).orElseThrow();hash=p.documentContentHash();
                    var section=p.sections().getFirst();at++;
                    if(selected.isEmpty() || selected.contains(section.id())) {
                        if(!Boolean.TRUE.equals(q.get("includeContent")))section=new DocumentationPayload.Section(section.id(),section.title(),section.locator(),List.of());
                        sections.add(section);
                        if(Wire.bytes(sections.stream().map(ReadTools::section).toList()).length>bytes-20000) {
                            sections.removeLast();at--;more=true;break;
                        }
                    }
                    more=p.continuation().isPresent();
                } while(more && sections.size()<page);
                if(sections.isEmpty() && more) { records.add(verdict(id,"PAYLOAD_TOO_LARGE","One section exceeds response budget"));offsets.put(id,-1);truncated=true;continue; }
                offsets.put(id,more?at:-1);
                var found=map("inputRecordId",id,"status","FOUND","record",item(record,"NOT_APPLICABLE","NONE",null,sections.isEmpty()?null:sections.getFirst().id()),
                    "documentContentHash",hash,"pageContentHash",ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)),
                    "sections",sections.stream().map(ReadTools::section).toList(),"sectionContinuation",null);
                var specimen=envelope("get_record",q,"NOT_APPLICABLE",map("records",List.of(found)));
                try { Wire.validate("get_record.response",specimen); }
                catch(IllegalArgumentException incompatible) { records.add(verdict(id,"INAPPLICABLE","Stored content is not representable under the current wire schema"));offsets.put(id,-1);truncated=true;continue; }
                records.add(found);if(more) { continuing.add(found);truncated=true; }
            } catch(Limit e) { records.add(verdict(id,"INAPPLICABLE",e.code));truncated=true; }
        }
        String next=offsets.values().stream().anyMatch(v->v>=0)?sectionCursor(q,offsets):"";
        for(var found:continuing)found.put("sectionContinuation",nullIfEmpty(next));
        var r=envelope("get_record",q,"NOT_APPLICABLE",map("records",records));
        finish(r,records.size(),"EXACT",budget.used,ids.size(),truncated,next,null,List.of());return r;
    }
    private Map<String,Integer> sectionOffsets(Map<String,Object> q,List<String> ids) {
        var offsets=new LinkedHashMap<String,Integer>();for(String id:ids)offsets.put(id,0);
        String token=text(q,"continuation","");if(token.isEmpty())return offsets;
        try {
            String[] p=new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8).split(":");
            if(p.length!=3 || !p[0].equals(digest(q,"sections")) || !MessageDigest.isEqual(p[2].getBytes(StandardCharsets.UTF_8),sign(p[0]+":"+p[1]).getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
            var values=Wire.JSON.readTree(Base64.getUrlDecoder().decode(p[1]));if(!values.isArray() || values.size()!=ids.size())throw new IllegalArgumentException();
            for(int i=0;i<ids.size();i++) { var value=values.get(i);if(!value.isInt() || value.intValue() < -1 || value.intValue()>=256)throw new IllegalArgumentException();offsets.put(ids.get(i),value.intValue()); }
            return offsets;
        }catch(Exception e) { throw new Limit("INVALID_CONTINUATION"); }
    }
    private String sectionCursor(Map<String,Object> q,Map<String,Integer> offsets) {
        String values=Base64.getUrlEncoder().withoutPadding().encodeToString(Wire.bytes(offsets.values()));
        String payload=digest(q,"sections")+":"+values;
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload+":"+sign(payload)).getBytes(StandardCharsets.UTF_8));
    }
    private static Map<String,Object> verdict(String id,String status,String reason) { return map("inputRecordId",id,"status",status,"reason",reason); }
    private Map<String,Object> item(KnowledgeRecord r,String proof,String matched,String snippet,String section) {
        return map("recordId",r.id(),"recordType",r.recordType().name(),"knowledgeKind",r.knowledgeKind().name(),"symbol",r.symbol().map(SymbolCoordinate::serialize).orElse(null),
            "proof",proof,"matchedBy",matched,"applicability",map("product",r.applicability().product(),"sdkVersions",r.applicability().sdkVersions(),"conditions",r.applicability().conditions(),"scope",r.applicability().scope().name()),
            "governance",map("approval",r.governance().approval().name(),"owner",r.governance().owner(),"provenance",r.governance().provenance(),"approvalEvidence",r.governance().approvalEvidence()),
            "citations",List.of(map("recordId",r.id(),"sdkVersion",content.binding().sdkVersion(),"buildId",content.binding().buildId(),"locator",locator(r.locator()),"sectionId",section)),"snippet",snippet);
    }
    public static Map<String,Object> locator(SourceLocator l) { return map("kind",l.kind().name(),"relativePath",l.relativePath(),"anchor",l.anchor().orElse(null),"reason",l.reason(),
        "lines",l.lines().map(v->map("first",v.first(),"last",v.last())).orElse(null),"section",l.section().orElse(null)); }
    public static Map<String,Object> section(DocumentationPayload.Section s) { return map("id",s.id(),"title",s.title(),"locator",locator(s.locator()),"blocks",s.blocks().stream().map(b->map(
        "kind",b.kind().name(),"label",b.label(),"text",b.text(),"links",b.links().stream().map(l->map("label",l.label(),"originalTarget",l.originalTarget(),"sdkVersion",l.sdkVersion(),"locator",locator(l.locator()))).toList(),
        "table",b.table().map(t->map("headers",t.headers(),"rows",t.rows())).orElse(null),"items",b.items().stream().map(ReadTools::listItem).toList(),"ordered",b.ordered())).toList()); }
    private static Map<String,Object> listItem(DocumentationPayload.ListItem i) { return map("text",i.text(),"children",i.children().stream().map(ReadTools::listItem).toList()); }
    private Map<String,Object> envelope(String name,Map<String,Object> q,String proof,Object data) {
        var b=content.binding();return map("contractVersion","read-tools/2","tool",name,"sdkVersion",b.sdkVersion(),"buildId",b.buildId(),"targetSdkVersion",q.get("targetSdkVersion"),
            "proof",proof,"data",data,"error",null,"counts",map("value",null,"relation","UNKNOWN"),"coverage",map("complete",complete,"reason",coverageReason),"emptyReason",null,
            "truncated",false,"continuation",null,"trace",map("normalizedQuery",text(q,"query",text(q,"symbol","")),"matchedBy",List.of(),"candidatesExamined",0,"workCharged",0,"capabilityFingerprint",b.capabilityFingerprint()),"versionNotices",List.of());
    }
    private void finish(Map<String,Object> r,long count,String relation,int work,int candidates,boolean truncated,String cursor,String empty,List<String> matched) {
        r.put("counts",map("value",relation.equals("UNKNOWN")?null:count,"relation",relation));r.put("truncated",truncated);r.put("continuation",nullIfEmpty(cursor));
        r.put("emptyReason",empty==null?null:Set.of("UNKNOWN_IN_SCOPE","INCOMPLETE_EVIDENCE","NOT_APPLICABLE").contains(empty)?empty:"NO_MATCH");
        var trace=(Map<String,Object>)r.get("trace");trace.put("workCharged",work);trace.put("candidatesExamined",candidates);trace.put("matchedBy",matched);
    }
    public static Map<String,Object> error(String name,Map<String,Object> q,String code) {
        var r=map("contractVersion","read-tools/2","tool",name,"sdkVersion",null,"buildId",null,"proof","NOT_APPLICABLE","data",null,
            "counts",map("value",null,"relation","UNKNOWN"),"truncated",false,"continuation",null,"targetSdkVersion",null,"versionNotices",List.of(),
            "trace",map("normalizedQuery","","matchedBy",List.of(),"candidatesExamined",0,"workCharged",0,"capabilityFingerprint",null));
        if(q!=null && q.get("targetSdkVersion") instanceof String s && s.length()<=2048)r.put("targetSdkVersion",s);
        ((Map<String,Object>)r.get("trace")).put("capabilityFingerprint",null);
        r.put("coverage",map("complete",false,"reason","Request did not produce evidence"));r.put("emptyReason","NOT_APPLICABLE");
        r.put("error",map("code",code,"message",code,"retryable",Set.of("BUILD_UNAVAILABLE","DEADLINE","WORK_LIMIT").contains(code)));return r;
    }
    private String digest(Map<String,Object> q,String purpose) {
        var sorted=new TreeMap<>(q);sorted.remove("continuation");
        return ContentHashes.digest("mcp-cursor/1",List.of(purpose,content.binding().sdkVersion(),content.binding().buildId(),new String(Wire.bytes(sorted),StandardCharsets.UTF_8)));
    }
    private String cursor(Map<String,Object> q,String purpose,int offset) {
        String payload=digest(q,purpose)+":"+offset;return Base64.getUrlEncoder().withoutPadding().encodeToString((payload+":"+sign(payload)).getBytes(StandardCharsets.UTF_8));
    }
    private int offset(Map<String,Object> q,String purpose,int max) {
        String token=text(q,"continuation","");if(token.isEmpty())return 0;
        try {
            String decoded=new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8);String[] parts=decoded.split(":");
            if(parts.length!=3 || !parts[0].equals(digest(q,purpose)) || !MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),sign(parts[0]+":"+parts[1]).getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
            int value=Integer.parseInt(parts[1]);if(value<1 || value>=max)throw new IllegalArgumentException();return value;
        }catch(RuntimeException e) { throw new Limit("INVALID_CONTINUATION"); }
    }
    private String sign(String s) {
        try { var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(s.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private static Object nullIfEmpty(String s) { return s.isEmpty()?null:s; }
    private static String text(Map<String,Object> q,String key,String fallback) { return q.get(key) instanceof String s?s:fallback; }
    private static Optional<String> opt(Map<String,Object> q,String key) { return q.get(key) instanceof String s?Optional.of(s):Optional.empty(); }
    private static List<String> strings(Map<String,Object> q,String key) { return q.get(key) instanceof List<?> l?(List<String>)l:List.of(); }
    private static final class Limit extends RuntimeException { final String code;Limit(String code) { this.code=code; } }
    private static final class Budget {
        final int max;final long deadline;int used;
        Budget(int max,int millis) { this.max=max;deadline=System.nanoTime()+millis*1_000_000L; }
        void check() { if(Thread.currentThread().isInterrupted())throw new Limit("CANCELLED");if(System.nanoTime()>deadline)throw new Limit("DEADLINE"); }
        void charge() { check();if(used>=max)throw new Limit("WORK_LIMIT");used++; }
    }
}
