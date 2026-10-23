package com.luciadmcp.knowledge.retrieval;

import com.luciadmcp.knowledge.domain.*;
import java.util.*;
import static com.luciadmcp.knowledge.domain.ExactSymbolResult.Proof.*;
import static com.luciadmcp.knowledge.domain.SymbolSourceException.Code.*;

/** Canonical identity first; aliases can establish identity only after a complete bounded scan. */
public final class ExactSymbolResolver {
    private final SymbolSource source;
    public ExactSymbolResolver(SymbolSource source) { this.source=Objects.requireNonNull(source); }

    public ExactSymbolResult resolve(ExactSymbolQuery query) { return resolve(query,0); }
    /** Stable candidate paging; offset does not relax proof or work limits. */
    public ExactSymbolResult resolve(ExactSymbolQuery query,int offset) {
        if(offset<0 || offset>=query.candidateLimit())throw new IllegalArgumentException("INVALID_CONTINUATION");
        Objects.requireNonNull(query);
        var binding=source.binding();
        if(query.sourceSdkVersion().isPresent() && !query.sourceSdkVersion().get().equals(binding.sdkVersion()))
            throw new SymbolSourceException(UNKNOWN_SDK,"Requested source SDK is unavailable in this pinned source");
        if(query.buildId().isPresent() && !query.buildId().get().equals(binding.buildId()))
            throw new SymbolSourceException(BUILD_UNAVAILABLE,"Requested build is unavailable in this pinned source");
        String text=query.symbol().strip();
        var canonical=source.canonical(text);
        if(canonical.isPresent()) {
            var exact=match(canonical.get(),"canonical");
            return result(query,fits(List.of(),exact)?List.of(exact):List.of(),1,1,true,true);
        }
        if(!query.includeAliases())return result(query,List.of(),0,1,true,true);
        var matches=new ArrayList<ExactSymbolResult.Match>();
        int count=0,work=1;String cursor="";boolean complete=false,signature=true;
        scan: while(work<query.workLimit()) {
            int batch=Math.min(256,query.workLimit()-work);
            var records=source.after(cursor,batch);
            for(var stored:records) {
                cursor=stored.recordId();work++;
                if(alias(text,stored.record())) {
                    count++;
                    var candidate=match(stored,"alias");
                    if(count>offset && matches.size()<query.pageSize() && fits(matches,candidate))matches.add(candidate);
                    signature &= !callable(stored.record()) || text.endsWith(")");
                    // Candidate work must not grow with page size. Incomplete scans never prove uniqueness.
                    if(count>=query.candidateLimit())break scan;
                }
            }
            if(records.size()<batch) { complete=true;break; }
        }
        return result(query,matches,count,work,complete,signature);
    }
    private ExactSymbolResult result(ExactSymbolQuery q,List<ExactSymbolResult.Match> matches,int count,int work,boolean complete,boolean sufficient) {
        var proof=count>1?AMBIGUOUS:!complete || count==1&&(!sufficient || matches.isEmpty())?INCOMPLETE_EVIDENCE:count==1?EXACT:UNKNOWN_IN_SCOPE;
        String reason=proof==UNKNOWN_IN_SCOPE?"Unknown within this pinned source":proof==INCOMPLETE_EVIDENCE?"Insufficient signature or bounded work/payload exhausted":proof==AMBIGUOUS?"Competing source candidates":"Exact within this source";
        return new ExactSymbolResult(source.binding(),q.targetSdkVersion(),proof,matches,count,work,complete,!complete||count>matches.size(),reason);
    }
    private ExactSymbolResult.Match match(StoredCatalogRecord stored,String by) {
        var r=stored.record();
        var locator=new SourceLocator(SourceLocator.Kind.SOURCE,r.relativePath(),r.anchor().isEmpty()?Optional.empty():Optional.of(r.anchor()),"");
        return new ExactSymbolResult.Match(stored.recordId(),SymbolCoordinate.from(r),locator,source.binding(),by);
    }
    // Conservative JSON framing allowance; transport still enforces its final serialized cap.
    private static boolean fits(List<ExactSymbolResult.Match> existing,ExactSymbolResult.Match next) {
        long bytes=4096+encodedBytes(next);
        for(var match:existing)bytes+=encodedBytes(match);
        return bytes<=1_048_576;
    }
    private static long encodedBytes(ExactSymbolResult.Match match) {
        return 512L+6L*(match.recordId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                +match.symbol().serialize().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                +match.locator().relativePath().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                +match.locator().anchor().orElse("").getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                +match.source().sdkVersion().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                +match.source().buildId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
    private static boolean callable(CatalogRecord r) { return r.kind().equals("METHOD")||r.kind().equals("CONSTRUCTOR"); }
    private static boolean alias(String query,CatalogRecord r) {
        String key=normalize(r.canonicalKey()),q=normalize(query);
        if(q==null || key==null)return false;
        int hash=key.indexOf('#');
        if(hash<0)return r.kind().equals("TYPE") && q.indexOf('.')<0 && q.equals(r.simpleName());
        String owner=key.substring(0,hash),member=key.substring(hash+1);
        int open=member.indexOf('(');String name=r.simpleName();
        member=name+(open<0?"":member.substring(open));
        // Qualified owners stay qualified. A missing owner never searches unrelated members.
        String simpleOwner=owner.substring(owner.lastIndexOf('.')+1);
        for(String prefix:List.of(owner+"#",owner+".",simpleOwner+"#",simpleOwner+".")) {
            if(q.equals(prefix+member))return true;
            if(callable(r) && q.equals(prefix+name))return true;
            if(q.startsWith(prefix+name+"(") && callable(r)) {
                String params=q.substring((prefix+name).length());
                if(params.equals(simpleParameters(member.substring(open))))return true;
            }
        }
        return false;
    }
    private static String simpleParameters(String signature) {
        return signature.replaceAll("(?:[\\p{javaJavaIdentifierPart}$]+\\.)+","");
    }
    /** Erase generic arguments and normalize Java varargs without changing case or owner scope. */
    private static String normalize(String value) {
        StringBuilder out=new StringBuilder();int depth=0;
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='<' && !value.startsWith("<init>",i)) { depth++;continue; }
            if(c=='>' && depth>0) { depth--;continue; }
            if(depth==0 && !Character.isWhitespace(c))out.append(c);
        }
        if(depth!=0)return null;
        return out.toString().replace("...","[]");
    }
}


