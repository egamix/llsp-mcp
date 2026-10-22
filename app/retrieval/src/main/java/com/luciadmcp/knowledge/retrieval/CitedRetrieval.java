package com.luciadmcp.knowledge.retrieval;

import com.luciadmcp.knowledge.domain.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Read-only application service. Counts and continuations describe one bounded candidate window. */
public final class CitedRetrieval {
    public enum Failure { INDEX_UNAVAILABLE, SOURCE_MISMATCH, BUILD_MISMATCH, INVALID_CONTINUATION,
        INVALID_CONTENT, CANDIDATE_LIMIT, WORK_LIMIT, TIME_LIMIT, PAYLOAD_LIMIT, CAPABILITY_UNAVAILABLE }
    public static final class RetrievalException extends RuntimeException {
        private final Failure failure;
        public RetrievalException(Failure failure,String message) { super(message); this.failure=failure; }
        public Failure failure() { return failure; }
    }
    public record Limits(int candidates,int work,int timeoutMillis,int pageSize,int responseBytes) {
        public static final Limits DEFAULT=new Limits(1000,5000,1000,10,1_048_576);
        public Limits {
            if(candidates<1 || candidates>10000 || work<1 || work>50000 || timeoutMillis<1 || timeoutMillis>5000
                    || pageSize<1 || pageSize>50 || responseBytes<4096 || responseBytes>1_048_576)
                throw new IllegalArgumentException("INVALID_REQUEST: retrieval limits");
        }
    }
    public record Request(String text,Map<String,String> filters,Optional<String> sourceSdk,
            Optional<String> buildId,Optional<String> targetSdk,Limits limits,String continuation) {
        public Request(String text) { this(text,Map.of(),Optional.empty(),Optional.empty(),Optional.empty(),Limits.DEFAULT,""); }
        public Request {
            Objects.requireNonNull(text); if(!text.isEmpty())ExactSymbolQuery.text(text);
            filters=Map.copyOf(filters); if(!LexicalIndex.FILTER_FIELDS.containsAll(filters.keySet()))throw new IllegalArgumentException("unsupported filter");
            filters.values().forEach(ExactSymbolQuery::text);
            Objects.requireNonNull(sourceSdk);Objects.requireNonNull(buildId);Objects.requireNonNull(targetSdk);
            sourceSdk.ifPresent(ExactSymbolQuery::text);buildId.ifPresent(ExactSymbolQuery::text);targetSdk.ifPresent(ExactSymbolQuery::text);
            Objects.requireNonNull(limits);Objects.requireNonNull(continuation);
            if(continuation.length()>4096)throw new RetrievalException(Failure.INVALID_CONTINUATION,"cursor too large");
        }
    }
    public record Item(KnowledgeRecord record,SymbolSourceBinding source,String sectionId,SourceLocator locator,
            String label,String snippet,List<LexicalIndex.Span> highlights,float score,boolean exactIdentity,
            boolean snippetTruncated) {
        public Item { highlights=List.copyOf(highlights); }
    }
    public enum CountRelation { EXACT, LOWER_BOUND, UNKNOWN }
    public enum Outcome { MATCHES, NO_MATCH, FILTERED_CONTENT, INSUFFICIENT_EVIDENCE }
    public record Page(SymbolSourceBinding source,Optional<String> targetSdk,List<Item> items,
            ExactSymbolResult.Proof symbolProof,Outcome outcome,long matchedCount,CountRelation countRelation,
            int workCharged,int candidatesExamined,boolean truncated,String reason,String continuation) {
        public Page { items=List.copyOf(items); }
    }
    private final DocumentationSource content;
    private final SymbolSource symbols;
    private final LexicalIndex index;
    private final LexicalIndex.Binding binding;
    private final byte[] key;
    public CitedRetrieval(DocumentationSource content,SymbolSource symbols,LexicalIndex index,byte[] signingKey) {
        this.content=Objects.requireNonNull(content);this.symbols=Objects.requireNonNull(symbols);
        if(index==null)throw error(Failure.INDEX_UNAVAILABLE,"lexical index required");this.index=index;
        if(signingKey==null || signingKey.length<32)throw new IllegalArgumentException("32-byte signing key required");
        key=signingKey.clone();binding=LexicalIndex.Binding.of(content);validateBinding();
    }
    private void validateBinding() {
        if(!binding.source().equals(symbols.binding()) || !binding.equals(index.binding()))
            throw error(Failure.SOURCE_MISMATCH,"catalog/index source, build, runtime or recipe differs");
    }
    public Page search(Request request) {
        Objects.requireNonNull(request);validateBinding();
        var source=binding.source();
        if(request.sourceSdk().filter(s->!s.equals(source.sdkVersion())).isPresent()
                || request.filters().containsKey("sourceSdk") && !request.filters().get("sourceSdk").equals(source.sdkVersion()))
            throw error(Failure.SOURCE_MISMATCH,"unknown source SDK in pinned source");
        if(request.buildId().filter(s->!s.equals(source.buildId())).isPresent())throw error(Failure.BUILD_MISMATCH,"unavailable pinned build");
        String digest=digest(request);int offset=request.continuation().isEmpty()?0:decode(request.continuation(),digest,request.limits().candidates());
        Budget budget=new Budget(request.limits());
        ExactSymbolResult.Proof proof=ExactSymbolResult.Proof.UNKNOWN_IN_SCOPE;Set<String> priority=Set.of();
        if(!request.text().isEmpty()) {
            // Reserve work for lexical discovery and authoritative enrichment. Incomplete aliases never prove identity.
            int exactWork=Math.max(1,Math.min(1000,budget.remaining()/4));budget.charge(exactWork);
            var exact=new ExactSymbolResolver(timedSymbols(budget)).resolve(new ExactSymbolQuery(request.text(),true,50,
                    Math.min(50,request.limits().candidates()),exactWork,Optional.of(source.sdkVersion()),Optional.of(source.buildId()),request.targetSdk()));
            budget.refund(exactWork-exact.workCharged());proof=exact.proof();
            if(proof==ExactSymbolResult.Proof.EXACT) {
                var match=exact.matches().getFirst();budget.charge(1);
                var record=content.knowledgeRecord(match.recordId()).orElseThrow(()->error(Failure.INVALID_CONTENT,"exact symbol has no saved content"));
                if(!record.id().equals(match.recordId()) || !record.sdkVersion().equals(source.sdkVersion())
                        || !record.symbol().equals(Optional.of(match.symbol())) || !record.locator().equals(match.locator()))
                    throw error(Failure.INVALID_CONTENT,"exact identity/content association differs");
                priority=Set.of(match.recordId());
            }
        }
        var filters=new HashMap<>(request.filters());
        if(filters.containsKey("approval") && !filters.get("approval").equals("APPROVED")
                || "CANDIDATE_LEARNING".equals(filters.get("knowledgeKind")))
            return new Page(source,request.targetSdk(),List.of(),proof,Outcome.FILTERED_CONTENT,0,CountRelation.EXACT,budget.used,0,false,"GOVERNANCE_FILTER","");
        filters.put("approval","APPROVED");
        budget.check();
        int lexicalWork=Math.max(1,budget.remaining()-Math.min(request.limits().pageSize()*2,budget.remaining()/2));
        budget.charge(lexicalWork);
        var lexical=index.search(new LexicalIndex.Query(request.text(),request.limits().pageSize(),request.limits().candidates(),
                lexicalWork,budget.millis(),filters,request.targetSdk(),offset,priority));
        if(lexical.workCharged()<0 || lexical.workCharged()>lexicalWork || !lexical.binding().equals(binding)
                || lexical.hits().size()>request.limits().pageSize())throw error(Failure.INVALID_CONTENT,"invalid lexical response");
        budget.refund(lexicalWork-lexical.workCharged());
        var items=new ArrayList<Item>();var seen=new HashSet<String>();int consumed=0,filtered=0;String reason=lexical.reason();
        for(var hit:lexical.hits()) {
            if(!seen.add(hit.recordId()))throw error(Failure.INVALID_CONTENT,"duplicate lexical ID");
            try {
                budget.charge(1);var record=content.knowledgeRecord(hit.recordId()).orElseThrow(()->error(Failure.INVALID_CONTENT,"missing knowledge record"));
                if(!record.id().equals(hit.recordId()) || !record.sdkVersion().equals(source.sdkVersion()))throw error(Failure.INVALID_CONTENT,"foreign knowledge record");
                if(!eligible(record)) { filtered++;consumed++;continue; }
                Item item=verify(record,hit,priority.contains(hit.recordId()),budget);
                budget.payload(item);items.add(item);consumed++;
            } catch(RetrievalException exhausted) {
                if(exhausted.failure()!=Failure.WORK_LIMIT && exhausted.failure()!=Failure.TIME_LIMIT && exhausted.failure()!=Failure.PAYLOAD_LIMIT)throw exhausted;
                if(items.isEmpty())throw exhausted;reason=exhausted.failure().name();break;
            }
        }
        budget.check();int next=offset+consumed;
        boolean more=next<request.limits().candidates() && (next<lexical.matchedCount() || consumed<lexical.hits().size());
        // A bounded/partial ranking cannot be safely replayed as a stable continuation.
        boolean stable=lexical.countRelation()==LexicalIndex.CountRelation.EXACT;
        String cursor=more && stable && consumed>0?encode(digest,next):"";
        boolean truncated=lexical.truncated() || consumed<lexical.hits().size() || filtered>0;
        if(next>=request.limits().candidates() && lexical.matchedCount()>next)reason="CANDIDATE_WINDOW_EXHAUSTED";
        var relation=filtered>0?CountRelation.UNKNOWN:stable?CountRelation.EXACT:CountRelation.LOWER_BOUND;
        var outcome=!items.isEmpty()?Outcome.MATCHES:filtered>0 || !filters.equals(Map.of("approval","APPROVED"))?Outcome.FILTERED_CONTENT:
                proof==ExactSymbolResult.Proof.INCOMPLETE_EVIDENCE || !stable?Outcome.INSUFFICIENT_EVIDENCE:Outcome.NO_MATCH;
        return new Page(source,request.targetSdk(),items,proof,outcome,lexical.matchedCount(),relation,budget.used,consumed,truncated,reason,cursor);
    }
    private SymbolSource timedSymbols(Budget budget) {
        return new SymbolSource() {
            public SymbolSourceBinding binding() { return symbols.binding(); }
            public Optional<StoredCatalogRecord> canonical(String key) { budget.check();var found=symbols.canonical(key);budget.check();return found; }
            public List<StoredCatalogRecord> after(String id,int limit) { budget.check();var rows=symbols.after(id,limit);budget.check();return rows; }
            public void close() {}
        };
    }
    private static boolean eligible(KnowledgeRecord record) {
        var g=record.governance();var a=record.applicability();
        return record.knowledgeKind()!=KnowledgeRecord.KnowledgeKind.CANDIDATE_LEARNING && g.approval()==KnowledgeRecord.Approval.APPROVED
                && !g.owner().isBlank() && !g.provenance().isBlank() && !g.approvalEvidence().isBlank()
                && (a.scope()==KnowledgeRecord.ApplicabilityScope.GENERAL || a.sdkVersions().contains(record.sdkVersion()));
    }
    private Item verify(KnowledgeRecord record,LexicalIndex.Hit hit,boolean exact,Budget budget) {
        if(record.locator().kind()!=SourceLocator.Kind.SOURCE)throw error(Failure.INVALID_CONTENT,"content citation requires source locator");
        if(hit.excerpt().isEmpty() || hit.excerpt().length()>512 || !Float.isFinite(hit.score()))throw error(Failure.INVALID_CONTENT,"invalid excerpt");
        for(int offset=0;offset<DocumentationPayload.MAX_SECTIONS;offset++) {
            budget.charge(1);var sections=content.sections(record.id(),offset,1);
            if(sections.isEmpty())break;
            if(sections.size()!=1)throw error(Failure.INVALID_CONTENT,"unbounded section response");
            var section=sections.getFirst();
            if(!section.locator().equals(record.locator()))throw error(Failure.INVALID_CONTENT,"foreign section locator");
            if(!section.id().equals(hit.sectionId()))continue;
            String projection=null;
            if(hit.label().equals("title") && section.title().contains(hit.excerpt()))projection=section.title();
            for(var block:section.blocks())if(block.label().equals(hit.label())) {
                String text=DocumentationText.block(block);if(text.contains(hit.excerpt())) { projection=text;break; }
            }
            if(projection==null)throw error(Failure.INVALID_CONTENT,"excerpt is not from cited section");
            if(hit.highlights().size()>32 || hit.highlights().stream().anyMatch(s->s.end()>hit.excerpt().length()))throw error(Failure.INVALID_CONTENT,"invalid highlights");
            return new Item(record,binding.source(),section.id(),section.locator(),hit.label(),hit.excerpt(),hit.highlights(),hit.score(),exact,projection.length()>hit.excerpt().length());
        }
        throw error(Failure.INVALID_CONTENT,"missing cited section");
    }
    /** Separate backend operation, sharing request budgets with primary retrieval; ordinary search never traverses. */
    public RelatedPage searchWithRelated(Request request,RelationshipStore relationships,String seedKey) {
        long started=System.nanoTime();
        if(relationships==null)throw error(Failure.CAPABILITY_UNAVAILABLE,"relationship capability unavailable");
        if(relationships.servingBinding().isEmpty())throw error(Failure.CAPABILITY_UNAVAILABLE,"validated relationship handle required");
        if(!relationships.servingBinding().orElseThrow().equals(binding.source()))throw error(Failure.SOURCE_MISMATCH,"foreign relationship handle");
        Page primary=search(request);
        int remaining=request.limits().work()-primary.workCharged();
        int candidates=request.limits().candidates()-primary.candidatesExamined();
        if(candidates<1)throw error(Failure.CANDIDATE_LIMIT,"no remaining relationship candidates");
        long nanos=request.limits().timeoutMillis()*1_000_000L-(System.nanoTime()-started);
        if(remaining<3)throw error(Failure.WORK_LIMIT,"no remaining relationship budget");
        if(nanos<=0)throw error(Failure.TIME_LIMIT,"no remaining relationship deadline");
        int bytes=request.limits().responseBytes()-primaryBytes(primary);
        if(bytes<65536)throw error(Failure.PAYLOAD_LIMIT,"no remaining relationship payload budget");
        var limits=new TraversalLimits(1,Math.min(candidates,Math.min(20,remaining/3)),Math.min(40,remaining/3),Math.min(20,candidates),Math.min(10,candidates),nanos,
                Math.min(100000,remaining/3),bytes);
        var related=relationships.traverse(new TraversalRequest(seedKey,TraversalRequest.Mode.DIRECT,TraversalRequest.Direction.OUT,Set.of(),limits,null));
        if(!related.buildId().equals(binding.source().buildId()) || !related.scope().sdkVersion().equals(binding.source().sdkVersion()))
            throw error(Failure.SOURCE_MISMATCH,"foreign relationship context");
        return new RelatedPage(primary,related);
    }
    public record RelatedPage(Page primary,TraversalResult related) {}
    private static int primaryBytes(Page page) { long size=4096;for(var item:page.items())size+=itemBytes(item);return (int)Math.min(1_048_576,size); }
    private static long itemBytes(Item item) {
        return 1024L+6L*(item.record().toString().getBytes(StandardCharsets.UTF_8).length+item.source().toString().getBytes(StandardCharsets.UTF_8).length
                +item.sectionId().getBytes(StandardCharsets.UTF_8).length+item.label().getBytes(StandardCharsets.UTF_8).length
                +item.snippet().getBytes(StandardCharsets.UTF_8).length)+item.highlights().size()*64L;
    }
    private static final class Budget {
        final Limits limits;final long deadline;int used;long bytes=4096;
        Budget(Limits limits) { this.limits=limits;deadline=System.nanoTime()+limits.timeoutMillis()*1_000_000L; }
        void check() { if(Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline)throw error(Failure.TIME_LIMIT,"retrieval deadline"); }
        int remaining() { return limits.work()-used; }
        void charge(int n) { check();if(n<0 || n>remaining())throw error(Failure.WORK_LIMIT,"retrieval work limit");used+=n; }
        void refund(int n) { used-=n; }
        int millis() { check();return (int)Math.max(1,(deadline-System.nanoTime())/1_000_000L); }
        void payload(Item item) { check();long n=itemBytes(item);if(bytes+n>limits.responseBytes())throw error(Failure.PAYLOAD_LIMIT,"retrieval payload limit");bytes+=n; }
    }
    private String digest(Request request) {
        var values=new ArrayList<String>();values.add("search_knowledge/backend/1");values.add(binding.toString());
        values.add(ContractVersions.INTERFACE);values.add(ContractVersions.IDENTITY);values.add(Integer.toString(ContractVersions.SCHEMA));
        values.add(request.text());values.add(request.sourceSdk().orElse(""));values.add(request.buildId().orElse(""));values.add(request.targetSdk().orElse(""));values.add(request.limits().toString());
        new TreeMap<>(request.filters()).forEach((k,v)->{values.add(k);values.add(v);});
        return ContentHashes.digest("retrieval-cursor/1",values);
    }
    private String encode(String digest,int offset) { String payload="1:"+digest+":"+offset;return payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload)); }
    private int decode(String token,String digest,int candidates) {
        try {
            int dot=token.lastIndexOf('.');String payload=token.substring(0,dot);
            if(!MessageDigest.isEqual(sign(payload),Base64.getUrlDecoder().decode(token.substring(dot+1))) || !payload.startsWith("1:"+digest+":"))throw new IllegalArgumentException();
            int offset=Integer.parseInt(payload.substring(3+digest.length()));if(offset<1 || offset>=candidates)throw new IllegalArgumentException();return offset;
        } catch(IllegalArgumentException | IndexOutOfBoundsException e) { throw error(Failure.INVALID_CONTINUATION,"invalid, stale or rebound continuation"); }
    }
    private byte[] sign(String text) {
        try { var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(text.getBytes(StandardCharsets.UTF_8)); }
        catch(java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private static RetrievalException error(Failure failure,String message) { return new RetrievalException(failure,message); }
}
