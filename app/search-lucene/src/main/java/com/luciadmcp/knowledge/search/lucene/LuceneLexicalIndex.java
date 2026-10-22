package com.luciadmcp.knowledge.search.lucene;

import com.luciadmcp.knowledge.domain.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

/** Persistent, source-bound derived BM25 projection. SQLite retains authoritative content. */
public final class LuceneLexicalIndex implements LexicalIndex {
    private final Directory directory;
    private final DirectoryReader reader;
    private final Binding binding;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private static final int SECTION_BATCH=16, ID_BATCH=128, EXCERPT_CHARS=512;

    private static Map<String,String> metadata(Binding b) {
        return Map.of("sdk",b.source().sdkVersion(),"build",b.source().buildId(),
                "capabilities",b.source().capabilityFingerprint(),"runtime",b.runtimeFingerprint(),
                "recipe",b.recipe(),"lucene",Version.LATEST.toString());
    }
    public LuceneLexicalIndex(Path path, Binding expected) {
        Directory opened=null;DirectoryReader admitted=null;
        try {
            if(!Files.isDirectory(path))throw new IllegalStateException("INDEX_UNAVAILABLE: lexical directory required");
            opened=FSDirectory.open(path);admitted=DirectoryReader.open(opened);
            if(!metadata(expected).equals(admitted.getIndexCommit().getUserData()))
                throw new IllegalStateException("INCOMPATIBLE_LEXICAL_INDEX: source/runtime/recipe mismatch; rebuild required");
            directory=opened;reader=admitted;binding=expected;
        } catch(IOException | RuntimeException failure) {
            try { if(admitted!=null)admitted.close(); }catch(IOException ignored) { }
            try { if(opened!=null)opened.close(); }catch(IOException ignored) { }
            throw new IllegalStateException("LEXICAL_INDEX_UNAVAILABLE: "+failure.getMessage(),failure);
        }
    }
    /** CREATE commits atomically; rollback leaves any prior committed generation intact. */
    public static void build(Path path, DocumentationSource source) {
        Objects.requireNonNull(source);Binding binding=Binding.of(source);
        try(var directory=FSDirectory.open(path);var analyzer=new StandardAnalyzer()) {
            var config=new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE).setCommitOnClose(false);
            config.setSimilarity(new BM25Similarity());
            var writer=new IndexWriter(directory,config);
            try {
                String cursor="";
                while(true) {
                    var ids=source.documentationIdsAfter(cursor,ID_BATCH);
                    if(ids.size()>ID_BATCH)throw new IllegalStateException("SOURCE_BATCH_LIMIT");
                    for(String id:ids) {
                        if(id.compareTo(cursor)<=0)throw new IllegalStateException("SOURCE_CURSOR_ORDER");
                        var record=source.knowledgeRecord(id).orElseThrow(()->new IllegalStateException("MISSING_METADATA"));
                        if(!record.id().equals(id) || !record.sdkVersion().equals(binding.source().sdkVersion()))throw new IllegalStateException("FOREIGN_SOURCE_RECORD");
                        writer.addDocument(project(record,source));cursor=id;
                    }
                    if(ids.size()<ID_BATCH)break;
                }
                if(!binding.equals(Binding.of(source)))throw new IllegalStateException("SOURCE_BINDING_CHANGED");
                writer.setLiveCommitData(metadata(binding).entrySet());writer.commit();
            } catch(IOException | RuntimeException | Error failure) {
                try { writer.rollback(); }catch(IOException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            } finally { writer.close(); }
        } catch(IOException e) { throw new UncheckedIOException("LEXICAL_BUILD_FAILED",e); }
    }
    private static void exact(Document d,String field,String value) { d.add(new StringField(field,value,Field.Store.NO)); }
    private static Document project(KnowledgeRecord r,DocumentationSource source) {
        var d=new Document();d.add(new StringField("id",r.id(),Field.Store.YES));
        d.add(new SortedDocValuesField("idOrder",new BytesRef(r.id())));
        exact(d,"sourceSdk",r.sdkVersion());exact(d,"recordType",r.recordType().name());exact(d,"knowledgeKind",r.knowledgeKind().name());
        var g=r.governance();exact(d,"approval",g.approval().name());exact(d,"owner",g.owner());exact(d,"provenance",g.provenance());exact(d,"approvalEvidence",g.approvalEvidence());
        var a=r.applicability();exact(d,"product",a.product());exact(d,"applicabilityScope",a.scope().name());
        a.sdkVersions().forEach(v->exact(d,"applicableSdk",v));a.conditions().forEach(v->exact(d,"condition",v));
        r.narrativeKind().ifPresent(v->exact(d,"narrativeKind",v.name()));
        r.symbol().ifPresent(s->{
            exact(d,"package",s.namespace());exact(d,"elementKind",s.elementKind());
            boolean callable=s.elementKind().equals("METHOD")||s.elementKind().equals("CONSTRUCTOR");
            String canonical=s.owner()+(s.member().isEmpty()?"":"#"+s.member()+(callable?"("+String.join(",",s.parameterTypes())+")":""));
            exact(d,"canonical",canonical);exact(d,"normalized",normalize(canonical));
            String simple=s.owner().substring(s.owner().lastIndexOf('.')+1);
            for(String name:List.of(canonical,s.owner(),simple,s.member()))if(!name.isEmpty()) {
                exact(d,"symbolPrefix",normalize(name));
                for(String token:camel(name))exact(d,"camel",token);
                d.add(new TextField("prose",name,Field.Store.NO));
            }
        });
        int offset=0,totalBytes=0;boolean deprecated=false;
        while(true) {
            var sections=source.sections(r.id(),offset,SECTION_BATCH);
            if(sections.size()>SECTION_BATCH || offset+sections.size()>DocumentationPayload.MAX_SECTIONS)throw new IllegalStateException("SOURCE_SECTION_LIMIT");
            for(var section:sections) {
                addSegment(d,section.id(),"title",section.title());
                for(var block:section.blocks()) {
                    // Canonical projection includes code, link labels, structured tables and nested lists.
                    String text=blockText(block);
                    totalBytes+=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    if(totalBytes>DocumentationPayload.MAX_BYTES)throw new IllegalStateException("PAYLOAD_TOO_LARGE");
                    addSegment(d,section.id(),block.label(),text);
                    deprecated|=block.label().toLowerCase(Locale.ROOT).startsWith("deprecated");
                }
            }
            offset+=sections.size();if(sections.size()<SECTION_BATCH)break;
        }
        if(offset==0)throw new IllegalStateException("MISSING_CONTENT");
        exact(d,"deprecated",Boolean.toString(deprecated));return d;
    }
    private static void addSegment(Document d,String id,String label,String text) {
        d.add(new TextField("prose",label+"\n"+text,Field.Store.NO));
        d.add(new StoredField("section",id));d.add(new StoredField("label",label));d.add(new StoredField("projection",text));
    }
    private static String blockText(DocumentationPayload.Block b) {
        var parts=new ArrayList<String>();parts.add(b.text());b.links().forEach(l->parts.add(l.label()));
        b.table().ifPresent(t->{parts.addAll(t.headers());t.rows().forEach(parts::addAll);});items(parts,b.items());return String.join("\n",parts);
    }
    private static void items(List<String> parts,List<DocumentationPayload.ListItem> items) { for(var item:items) { parts.add(item.text());items(parts,item.children()); } }
    private static String normalize(String s) { return s.replaceAll("\\s+","").toLowerCase(Locale.ROOT); }
    private static List<String> camel(String s) {
        return Arrays.stream(s.replaceAll("([a-z0-9])([A-Z])","$1 $2").replaceAll("([A-Z])([A-Z][a-z])","$1 $2").split("[^A-Za-z0-9]+"))
                .filter(v->!v.isEmpty()).map(v->v.toLowerCase(Locale.ROOT)).toList();
    }
    private List<String> tokens(String text) throws IOException {
        var result=new ArrayList<String>();
        try(var stream=analyzer.tokenStream("prose",text)) {
            var term=stream.addAttribute(CharTermAttribute.class);stream.reset();
            while(stream.incrementToken()) { if(result.size()>=128)throw new IllegalArgumentException("INVALID_REQUEST: too many query tokens");result.add(term.toString()); }
            stream.end();
        }return result;
    }
    private org.apache.lucene.search.Query query(Query request,List<String> tokens) {
        var all=new BooleanQuery.Builder();
        if(request.text().isEmpty())all.add(new MatchAllDocsQuery(),BooleanClause.Occur.MUST);
        else {
            var words=new BooleanQuery.Builder();
            words.add(new BoostQuery(new TermQuery(new Term("canonical",request.text())),8),BooleanClause.Occur.SHOULD);
            words.add(new BoostQuery(new TermQuery(new Term("normalized",normalize(request.text()))),6),BooleanClause.Occur.SHOULD);
            words.add(new BoostQuery(new PrefixQuery(new Term("symbolPrefix",normalize(request.text()))),3),BooleanClause.Occur.SHOULD);
            for(String token:tokens) {
                words.add(new TermQuery(new Term("prose",token)),BooleanClause.Occur.SHOULD);
                words.add(new BoostQuery(new PrefixQuery(new Term("camel",token)),2),BooleanClause.Occur.SHOULD);
            }
            words.setMinimumNumberShouldMatch(1);all.add(words.build(),BooleanClause.Occur.MUST);
        }
        request.filters().forEach((k,v)->all.add(new TermQuery(new Term(k,v)),BooleanClause.Occur.FILTER));return all.build();
    }
    private record Candidate(int doc,String id,float score) { }
    private static final Comparator<Candidate> ORDER=Comparator.comparingDouble((Candidate c)->-c.score()).thenComparing(Candidate::id);
    private static final class BudgetReached extends RuntimeException { }
    @Override public Binding binding() { return binding; }
    @Override public Page search(Query request) {
        Objects.requireNonNull(request);long deadline=System.nanoTime()+request.timeoutMillis()*1_000_000L;
        try {
            var terms=tokens(request.text());var searcher=new IndexSearcher(reader);searcher.setSimilarity(new BM25Similarity());
            searcher.setTimeout(()->System.nanoTime()>=deadline);
            var heap=new PriorityQueue<Candidate>(request.pageSize(),ORDER.reversed());
            int[] work={0};long[] matched={0};boolean[] bounded={false};String[] reason={"COMPLETE"};
            var collector=new SimpleCollector() {
                Scorable scorer;int base;SortedDocValues ids;
                @Override protected void doSetNextReader(LeafReaderContext context) throws IOException { base=context.docBase;ids=DocValues.getSorted(context.reader(),"idOrder"); }
                @Override public void setScorer(Scorable scorer) { this.scorer=scorer; }
                @Override public ScoreMode scoreMode() { return ScoreMode.COMPLETE; }
                @Override public void collect(int doc) throws IOException {
                    if(System.nanoTime()>=deadline || work[0]>=request.workLimit() || matched[0]>=request.candidateLimit()) {
                        bounded[0]=true;reason[0]=System.nanoTime()>=deadline?"TIME_LIMIT":"WORK_OR_CANDIDATE_LIMIT";throw new BudgetReached();
                    }
                    work[0]++;matched[0]++;if(!ids.advanceExact(doc))throw new IllegalStateException("CORRUPT_INDEX_ID");
                    var c=new Candidate(base+doc,ids.lookupOrd(ids.ordValue()).utf8ToString(),request.text().isEmpty()?0:scorer.score());
                    if(heap.size()<request.pageSize())heap.add(c);else if(ORDER.compare(c,heap.peek())<0) { heap.poll();heap.add(c); }
                }
            };
            try { searcher.search(query(request,terms),collector); }catch(BudgetReached ignored) { }
            if(searcher.timedOut()) { bounded[0]=true;reason[0]="TIME_LIMIT"; }
            var ordered=new ArrayList<>(heap);ordered.sort(ORDER);var hits=new ArrayList<Hit>();
            var stored=reader.storedFields();
            int payloadBytes=32768;boolean payloadLimited=false;
            for(var c:ordered) {
                var hit=hit(c,stored.document(c.doc()),terms);
                // Conservative JSON escaping allowance; reserve binding/context/framing before hits.
                long bytes=256L+6L*(hit.recordId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        +hit.sectionId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        +hit.label().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        +hit.excerpt().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)+hit.highlights().size()*64L;
                if(payloadBytes+bytes>1_048_576) { payloadLimited=true;break; }
                payloadBytes+=(int)bytes;hits.add(hit);
            }
            if(payloadLimited)reason[0]="PAYLOAD_LIMIT";
            else if(!bounded[0] && matched[0]>hits.size())reason[0]="PAGE_LIMIT";
            return new Page(binding,request.targetSdkVersion(),hits,matched[0],bounded[0]?CountRelation.LOWER_BOUND:CountRelation.EXACT,
                    work[0],bounded[0]||payloadLimited||matched[0]>hits.size(),reason[0]);
        }catch(IOException e) { throw new UncheckedIOException("LEXICAL_SEARCH_FAILED",e); }
    }
    private static Hit hit(Candidate c,Document d,List<String> terms) {
        String[] texts=d.getValues("projection"),ids=d.getValues("section"),labels=d.getValues("label");
        var patterns=terms.stream().map(term->java.util.regex.Pattern.compile(term,
                java.util.regex.Pattern.LITERAL | java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)).toList();
        int chosen=texts.length>1?1:0,position=0,best=0;
        for(int i=0;i<texts.length;i++) {
            int count=0,first=-1;
            for(var pattern:patterns) {
                var match=pattern.matcher(texts[i]);
                if(match.find()) { count++;int at=match.start();if(first<0 || at<first)first=at; }
                else if(pattern.matcher(labels[i]).find())count++;
            }
            if(count>best) { best=count;chosen=i;position=Math.max(0,first-80); }
        }
        String text=texts[chosen];position=Math.min(position,Math.max(0,text.length()-EXCERPT_CHARS));
        // Keep offsets in Java UTF-16 coordinates without splitting surrogate pairs.
        if(position>0 && Character.isLowSurrogate(text.charAt(position)))position--;
        int end=Math.min(text.length(),position+EXCERPT_CHARS);if(end<text.length() && Character.isHighSurrogate(text.charAt(end-1)))end--;
        String excerpt=text.substring(position,end);var spans=new ArrayList<Span>();
        for(var pattern:patterns) {
            var match=pattern.matcher(excerpt);
            if(match.find() && spans.size()<32)spans.add(new Span(match.start(),match.end()));
        }
        return new Hit(c.id(),c.score(),ids[chosen],labels[chosen],excerpt,spans);
    }
    @Override public void close() {
        analyzer.close();try { reader.close(); }catch(IOException e) { throw new UncheckedIOException(e); }
        finally { try { directory.close(); }catch(IOException e) { throw new UncheckedIOException(e); } }
    }
}
