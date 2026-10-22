package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.ContentHashes;
import com.luciadmcp.knowledge.domain.ContractVersions;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Deterministic accounting evidence, sealed before the catalog checkpoint and scoped to this build. */
record CoverageSnapshot(long discovered, long excluded, long complete, long partial, long failed,
        long emittedRecords, long persistedRecords, long emittedSections, long persistedSections,
        List<Outcome> outcomes, boolean discoveryIssues) {
    static final String FILE = "ingestion-coverage.bin";
    static final int MAX_BYTES = 1024 * 1024;
    record Outcome(String pageType, String disposition, String reason, long count) {}
    CoverageSnapshot {
        outcomes=List.copyOf(outcomes);
        if (discovered<0 || excluded<0 || complete<0 || partial<0 || failed<0 ||
                discovered!=excluded+complete+partial+failed || emittedRecords<0 || persistedRecords<0 ||
                persistedRecords>emittedRecords || emittedSections<0 || persistedSections<0 || persistedSections>emittedSections)
            throw new IllegalArgumentException("COVERAGE_RECONCILIATION_FAILED");
        if(outcomes.stream().anyMatch(o->o.count()<=0) || outcomes.stream().mapToLong(Outcome::count).sum()!=discovered)
            throw new IllegalArgumentException("COVERAGE_OUTCOMES_INVALID");
        long[] counts={excluded,complete,partial,failed};String[] dispositions={"EXCLUDED","COMPLETE","PARTIAL","FAILED"};
        for(int i=0;i<4;i++) {
            String disposition=dispositions[i];
            if(outcomes.stream().filter(o->o.disposition().equals(disposition)).mapToLong(Outcome::count).sum()!=counts[i])
                throw new IllegalArgumentException("COVERAGE_DISPOSITION_MISMATCH");
        }
    }
    boolean completeForProfile() { return partial==0 && failed==0 && emittedRecords==persistedRecords && emittedSections==persistedSections && !discoveryIssues; }
    private static List<String> binding(BuildContext c) {
        return List.of("coverage/1", c.buildId(), c.sdkVersion(), c.sealedInputDigest(),
                ContentHashes.recipeFingerprint(ContractVersions.PROFILE,List.of()),ContentHashes.runtimeFingerprint());
    }
    void write(BuildContext context) {
        try {
            var bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(bytes)) {
                for(String field:binding(context))out.writeUTF(field);
                for(long value:new long[]{discovered,excluded,complete,partial,failed,emittedRecords,persistedRecords,emittedSections,persistedSections})out.writeLong(value);
                out.writeBoolean(discoveryIssues);out.writeInt(outcomes.size());
                for(var cell:outcomes) { out.writeUTF(cell.pageType());out.writeUTF(cell.disposition());out.writeUTF(cell.reason());out.writeLong(cell.count()); }
            }
            byte[] body=bytes.toByteArray();
            if(body.length>MAX_BYTES-64)throw new IOException("COVERAGE_TOO_LARGE");
            Files.createDirectories(context.stagingDir());
            Path target=context.stagingDir().resolve(FILE), temp=Files.createTempFile(context.stagingDir(),"coverage-",".tmp");
            try {
                try(var out=Files.newOutputStream(temp)) { out.write(ContentHashes.sourceHash(body).getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(body); }
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch(IOException e) { throw new UncheckedIOException("COVERAGE_WRITE_FAILED",e); }
    }
    static Optional<CoverageSnapshot> restore(BuildContext context) {
        try(var input=Files.newInputStream(context.stagingDir().resolve(FILE))) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length<64 || bytes.length>MAX_BYTES)return Optional.empty();
            byte[] body=Arrays.copyOfRange(bytes,64,bytes.length);
            if(!ContentHashes.sourceHash(body).equals(new String(bytes,0,64,java.nio.charset.StandardCharsets.US_ASCII)))return Optional.empty();
            try(var in=new DataInputStream(new ByteArrayInputStream(body))) {
                for(String field:binding(context))if(!field.equals(in.readUTF()))return Optional.empty();
                long[] values=new long[9];for(int i=0;i<9;i++)values[i]=in.readLong();
                boolean issues=in.readBoolean();int count=in.readInt();if(count<0 || count>256)return Optional.empty();
                List<Outcome> outcomes=new ArrayList<>();String previous="";
                for(int i=0;i<count;i++) {
                    var cell=new Outcome(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong());
                    String key=cell.pageType()+":"+cell.disposition()+":"+cell.reason();
                    if(key.compareTo(previous)<=0)return Optional.empty();previous=key;
                    com.luciadmcp.knowledge.ingest.javadoc.PageType.valueOf(cell.pageType());
                    if(!Set.of("EXCLUDED","COMPLETE","PARTIAL","FAILED").contains(cell.disposition()))return Optional.empty();
                    outcomes.add(cell);
                }
                if(in.available()!=0)return Optional.empty();
                return Optional.of(new CoverageSnapshot(values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8],outcomes,issues));
            }
        } catch(IOException | RuntimeException e) { return Optional.empty(); }
    }
    String toJson() {
        StringBuilder out=new StringBuilder("{\"availability\":\"AVAILABLE\",\"countingRules\":\"disjoint pages; unique logical record associations; payload sections; retries replace totals\",\"independentExpectations\":null,");
        out.append("\"pages\":{\"discovered\":").append(discovered).append(",\"excluded\":").append(excluded).append(",\"complete\":").append(complete).append(",\"partial\":").append(partial).append(",\"failed\":").append(failed).append("},");
        counts(out,"records",emittedRecords,persistedRecords);out.append(',');counts(out,"sections",emittedSections,persistedSections);
        out.append(",\"discoveryIssues\":").append(discoveryIssues).append(",\"outcomes\":[");
        for(int i=0;i<outcomes.size();i++) { if(i>0)out.append(',');var cell=outcomes.get(i);out.append("{\"pageType\":").append(IngestionReport.quote(cell.pageType())).append(",\"disposition\":").append(IngestionReport.quote(cell.disposition())).append(",\"reason\":").append(IngestionReport.quote(cell.reason())).append(",\"count\":").append(cell.count()).append('}'); }
        return out.append("]}").toString();
    }
    private static void counts(StringBuilder out,String name,long emitted,long persisted) {
        out.append('"').append(name).append("\":{\"emitted\":").append(emitted).append(",\"persisted\":").append(persisted).append(",\"failed\":").append(emitted-persisted).append('}');
    }
}
