package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.ingest.javadoc.*;
import java.util.*;

/** One invocation's ledger: retries/identical puts are one logical association, never extra work. */
final class CoverageAccounting {
    private final Map<String,Page> pages=new TreeMap<>();
    private final Map<String,Association> records=new TreeMap<>();
    private final boolean discoveryIssues;
    private String active;
    private static final class Page {
        final String type;String reason="UNPROCESSED";boolean processed;boolean issues;final Set<String> records=new TreeSet<>();
        Page(String type) { this.type=type; }
    }
    private static final class Association { int sections;boolean persisted; }
    CoverageAccounting(JavadocCatalogSource.Result result) {
        discoveryIssues=!result.discoveryWarnings().isEmpty();
        for(var source:result.inventory()) {
            Page page=new Page(source.type().name());pages.put(source.relativePath(),page);
            if(source.type()==PageType.EXCLUDED) { page.processed=true;page.reason="POSITIVE_NON_CONTENT"; }
            if(source.type()==PageType.UNKNOWN) { page.processed=true;page.reason=source.reason().name();page.issues=true; }
        }
        for(var page:result.pages())for(var record:page.records())emit(page.relativePath(),record.canonicalKey());
        for(var page:result.narratives())if(!page.blocks().isEmpty() && page.sourceHash().isPresent())emit(page.relativePath(),"narrative:"+page.relativePath());
    }
    void emit(String path,String key) { pages.computeIfAbsent(path,p->new Page("TYPE")).records.add(key);records.computeIfAbsent(key,k->new Association()); }
    void begin(String path,List<String> issues) {
        active=path;Page page=pages.get(path);page.processed=true;
        page.issues=!issues.isEmpty();page.reason=issues.isEmpty()?"PRESERVED":reason(issues);
    }
    private static String reason(List<String> issues) {
        for(String token:List.of("SOURCE_FILE_TOO_LARGE","PAYLOAD_TOO_LARGE","PARTIAL_UNSUPPORTED","duplicate canonical key"))
            if(issues.stream().anyMatch(issue->issue.contains(token)))return token.equals("duplicate canonical key")?"DUPLICATE_IDENTITY":token;
        return "EXTRACTION_ISSUE";
    }
    void storageFailure() { if(active!=null)failed(active,"STORAGE_FAILED"); }
    void sections(String key,int count) { records.get(key).sections=count; }
    void persisted(String key) { records.get(key).persisted=true; }
    void failed(String path,String reason) { Page page=pages.get(path);page.issues=true;page.reason=reason; }
    CoverageSnapshot snapshot() {
        long excluded=0,complete=0,partial=0,failed=0;Map<String,Long> groups=new TreeMap<>();
        for(Page page:pages.values()) {
            long saved=page.records.stream().filter(key->records.get(key).persisted).count();
            String disposition;
            if(page.type.equals("EXCLUDED")) { disposition="EXCLUDED";excluded++; }
            else if(page.processed && !page.issues && !page.records.isEmpty() && saved==page.records.size()) { disposition="COMPLETE";complete++; }
            else if(saved>0) { disposition="PARTIAL";partial++; }
            else { disposition="FAILED";failed++; }
            groups.merge(page.type+":"+disposition+":"+page.reason,1L,Long::sum);
        }
        List<CoverageSnapshot.Outcome> outcomes=new ArrayList<>();
        groups.forEach((key,count)->{String[] fields=key.split(":");outcomes.add(new CoverageSnapshot.Outcome(fields[0],fields[1],fields[2],count));});
        return new CoverageSnapshot(pages.size(),excluded,complete,partial,failed,records.size(),records.values().stream().filter(r->r.persisted).count(),records.values().stream().mapToLong(r->r.sections).sum(),records.values().stream().filter(r->r.persisted).mapToLong(r->r.sections).sum(),outcomes,discoveryIssues);
    }
}
