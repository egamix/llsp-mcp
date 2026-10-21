package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Bounded schema/2 documentation adapter. All association writes use the catalog connection. */
public final class SqliteDocumentationStore implements DocumentationStore {
    static final String DOCUMENTS_DDL = "CREATE TABLE IF NOT EXISTS documentation (record_id TEXT PRIMARY KEY NOT NULL REFERENCES records(record_id), source_hash TEXT NOT NULL, content_hash TEXT NOT NULL, section_count INTEGER NOT NULL, metadata BLOB NOT NULL)";
    static final String SECTIONS_DDL = "CREATE TABLE IF NOT EXISTS documentation_sections (record_id TEXT NOT NULL REFERENCES documentation(record_id), ordinal INTEGER NOT NULL, payload BLOB NOT NULL, content_hash TEXT NOT NULL, PRIMARY KEY(record_id,ordinal))";
    private final SqliteCatalogStore catalog;
    private final Connection db;
    private final boolean ownsCatalog;

    public SqliteDocumentationStore(Path path) {
        catalog=new SqliteCatalogStore(path); db=catalog.connection(); ownsCatalog=true;
    }
    SqliteDocumentationStore(SqliteCatalogStore catalog) { this.catalog=catalog;db=catalog.connection();ownsCatalog=false; }

    @Override public WriteStatus putAtomically(KnowledgeRecord record, DocumentationPayload payload) {
        var symbol=record.symbol().orElseThrow(() -> new IllegalArgumentException("API symbol required"));
        String member=symbol.member();
        boolean callable=symbol.elementKind().equals("METHOD")||symbol.elementKind().equals("CONSTRUCTOR");
        String key=symbol.owner()+(member.isEmpty()?"":"#"+member+(callable?"("+String.join(",",symbol.parameterTypes())+")":""));
        String simple=member.isEmpty()?symbol.owner().substring(symbol.owner().lastIndexOf('.')+1):member;
        if(simple.equals("<init>"))simple=symbol.owner().substring(symbol.owner().lastIndexOf('.')+1);
        if(simple.startsWith("method:"))simple=simple.substring(7);
        var tuple=new CatalogRecord(record.sdkVersion(),key,symbol.elementKind(),symbol.owner(),simple,record.locator().relativePath(),record.locator().anchor().orElse(""));
        var result=put(tuple,record,payload);
        return switch(result.status()) { case INSERTED -> WriteStatus.INSERTED;case DUPLICATE -> WriteStatus.IDENTICAL;case IDENTITY_CLASH -> WriteStatus.IDENTITY_CLASH; };
    }

    CatalogStore.PutResult put(CatalogRecord tuple, KnowledgeRecord record, DocumentationPayload payload) {
        String id=IdentityFields.recordId(tuple);
        validateAssociation(tuple,record,payload);
        byte[] metadata=metadata(record);
        // Encode/validate before opening a transaction. Total framing overhead is bounded too.
        List<byte[]> sections=payload.sections().stream().map(DocumentationCodec::section).toList();
        long encoded=sections.stream().mapToLong(a->a.length).sum();
        if(encoded>DocumentationCodec.MAX_ENCODED_BYTES)throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
        try {
            if(!db.getAutoCommit())throw new IllegalStateException("nested documentation transaction");
            db.setAutoCommit(false);
            try {
                var put=catalog.put(tuple);
                if(put.status()==CatalogStore.PutStatus.IDENTITY_CLASH) { db.rollback();return put; }
                try(var p=db.prepareStatement("SELECT CASE WHEN octet_length(source_hash)=64 THEN source_hash END,CASE WHEN octet_length(content_hash)=64 THEN content_hash END,CASE WHEN octet_length(metadata)<=1310724 THEN metadata END FROM documentation WHERE record_id=?")) {
                    p.setString(1,id);
                    try(var r=p.executeQuery()) {
                        if(r.next()) {
                            boolean same=payload.sourceHash().equals(r.getString(1))&&payload.contentHash().equals(r.getString(2))&&Arrays.equals(metadata,r.getBytes(3));
                            if(same) { var existing=load(db,id);same=existing.equals(payload); }
                            db.rollback();return new CatalogStore.PutResult(id,same?CatalogStore.PutStatus.DUPLICATE:CatalogStore.PutStatus.IDENTITY_CLASH);
                        }
                    }
                }
                try(var p=db.prepareStatement("INSERT INTO knowledge_records VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    p.setString(1,id);p.setString(2,record.applicability().product());p.setString(3,record.sdkVersion());p.setString(4,record.recordType().name());p.setString(5,record.knowledgeKind().name());p.setString(6,record.symbol().orElseThrow().serialize());
                    var loc=record.locator();p.setString(7,loc.kind().name());p.setString(8,loc.relativePath());p.setString(9,loc.anchor().orElse(null));
                    p.setObject(10,loc.lines().map(SourceLocator.LineRange::first).orElse(null));p.setObject(11,loc.lines().map(SourceLocator.LineRange::last).orElse(null));p.setString(12,loc.section().orElse(null));p.setString(13,loc.reason());
                    p.setString(14,record.governance().approval().name());p.setString(15,record.governance().owner());p.setString(16,record.governance().provenance());p.setString(17,record.governance().approvalEvidence());p.setString(18,applicability(record));p.executeUpdate();
                }
                try(var p=db.prepareStatement("INSERT INTO documentation VALUES(?,?,?,?,?)")) {
                    p.setString(1,id);p.setString(2,payload.sourceHash());p.setString(3,payload.contentHash());p.setInt(4,sections.size());p.setBytes(5,metadata);p.executeUpdate();
                }
                try(var p=db.prepareStatement("INSERT INTO documentation_sections VALUES(?,?,?,?)")) {
                    for(int i=0;i<sections.size();i++) { p.setString(1,id);p.setInt(2,i);p.setBytes(3,sections.get(i));p.setString(4,ContentHashes.contentHash(DocumentationPayload.canonicalFields(List.of(payload.sections().get(i)))));p.executeUpdate(); }
                }
                db.commit();return new CatalogStore.PutResult(id,CatalogStore.PutStatus.INSERTED);
            } catch(SQLException|RuntimeException e) { db.rollback();throw e; }
            finally { db.setAutoCommit(true); }
        } catch(SQLException e) { throw new IllegalStateException("atomic documentation write failed",e); }
    }

    static void validateAssociation(CatalogRecord tuple, KnowledgeRecord record, DocumentationPayload payload) {
        if(!IdentityFields.recordId(tuple).equals(record.id()) || !record.id().equals(payload.recordId()) || !record.sdkVersion().equals(tuple.sdkVersion()) || !record.symbol().equals(Optional.of(SymbolCoordinate.from(tuple))))throw new IllegalArgumentException("documentation identity mismatch");
        if(record.recordType()!=KnowledgeRecord.RecordType.API || record.knowledgeKind()!=KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT || record.governance().approval()!=KnowledgeRecord.Approval.APPROVED || !record.applicability().product().equals("LuciadLightspeed") || record.applicability().scope()!=KnowledgeRecord.ApplicabilityScope.VERSION_SCOPED || !record.applicability().sdkVersions().equals(List.of(tuple.sdkVersion())))throw new IllegalArgumentException("unsupported API governance/scope");
        if(record.locator().kind()!=SourceLocator.Kind.SOURCE || !record.locator().relativePath().equals(tuple.relativePath()) || !record.locator().anchor().orElse("").equals(tuple.anchor()))throw new IllegalArgumentException("documentation locator mismatch");
        for(var section:payload.sections())if(!section.locator().equals(record.locator()))throw new IllegalArgumentException("foreign section locator");
    }

    private void scope(String build,String sdk) throws SQLException {
        TraversalRequest.boundedText(build);TraversalRequest.boundedText(sdk);
        try(var p=db.prepareStatement("SELECT 1 FROM build_metadata WHERE build_id=? AND sdk_version=? LIMIT 1")) {
            p.setString(1,build);p.setString(2,sdk);try(var r=p.executeQuery()) { if(!r.next())throw new IllegalArgumentException("INVALID_SCOPE"); }
        }
    }

    @Override public Optional<SectionPage> readSections(String id,String build,String sdk,int first,int limit) {
        DocumentationStore.checkLimit(limit);TraversalRequest.boundedText(id);
        if(first<0||first>DocumentationPayload.MAX_SECTIONS)throw new IllegalArgumentException("INVALID_OFFSET");
        try {
            scope(build,sdk);
            int count;String hash;
            try(var p=db.prepareStatement("SELECT d.section_count,CASE WHEN octet_length(d.content_hash)=64 THEN d.content_hash END FROM documentation d JOIN records r USING(record_id) WHERE d.record_id=? AND r.sdk_version=?")) {
                p.setString(1,id);p.setString(2,sdk);try(var r=p.executeQuery()) { if(!r.next())return Optional.empty();count=r.getInt(1);hash=r.getString(2); }
            }
            if(count<1||count>256||hash==null)throw new IllegalStateException("CORRUPT_CONTENT");
            if(first>=count)throw new IllegalArgumentException("INVALID_OFFSET");
            var sections=read(db,id,first,Math.min(limit,count-first),DocumentationPayload.MAX_BYTES-1024);
            return Optional.of(new SectionPage(id,hash,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)),sections,first+sections.size()<count?Optional.of(Integer.toString(first+sections.size())):Optional.empty()));
        } catch(SQLException e) { throw new IllegalStateException("documentation read failed",e); }
    }

    @Override public List<String> enumerateIds(String build,String sdk,String after,int limit) {
        DocumentationStore.checkLimit(limit);if(after==null)throw new IllegalArgumentException("afterId required");if(!after.isEmpty())TraversalRequest.boundedText(after);
        try {
            scope(build,sdk);List<String> ids=new ArrayList<>();
            try(var p=db.prepareStatement("SELECT CASE WHEN octet_length(d.record_id)=64 THEN d.record_id END FROM documentation d JOIN records r USING(record_id) WHERE r.sdk_version=? AND d.record_id>? ORDER BY d.record_id LIMIT ?")) {
                p.setString(1,sdk);p.setString(2,after);p.setInt(3,limit);try(var r=p.executeQuery()) { while(r.next()) { String id=r.getString(1);if(id==null)throw new IllegalStateException("CORRUPT_CONTENT");ids.add(id); } }
            }
            return List.copyOf(ids);
        } catch(SQLException e) { throw new IllegalStateException("documentation enumeration failed",e); }
    }

    static DocumentationPayload load(Connection db,String id) throws SQLException {
        int count;String source,content;
        try(var p=db.prepareStatement("SELECT CASE WHEN octet_length(source_hash)=64 THEN source_hash END,CASE WHEN octet_length(content_hash)=64 THEN content_hash END,section_count FROM documentation WHERE record_id=?")) {
            p.setString(1,id);try(var r=p.executeQuery()) { if(!r.next())throw new IllegalStateException("MISSING_CONTENT");source=r.getString(1);content=r.getString(2);count=r.getInt(3); }
        }
        if(count<1||count>256)throw new IllegalStateException("CORRUPT_CONTENT");
        return new DocumentationPayload(id,source,read(db,id,0,count,DocumentationCodec.MAX_ENCODED_BYTES),content);
    }

    private static List<DocumentationPayload.Section> read(Connection db,String id,int first,int count,long budget) throws SQLException {
        long total=0;int rows=0;
        // Lengths only first. No JDBC getBytes/getString on untrusted content until all selected rows fit.
        try(var p=db.prepareStatement("SELECT ordinal,octet_length(payload) FROM documentation_sections WHERE record_id=? AND ordinal>=? AND ordinal<? ORDER BY ordinal LIMIT 257")) {
            p.setString(1,id);p.setInt(2,first);p.setInt(3,first+count);try(var r=p.executeQuery()) { while(r.next()) { if(r.getInt(1)!=first+rows++ || r.getLong(2)<4 || r.getLong(2)>DocumentationCodec.MAX_ENCODED_BYTES || (total+=r.getLong(2))>budget)throw new IllegalStateException("PAYLOAD_TOO_LARGE_OR_CORRUPT"); } }
        }
        if(rows!=count)throw new IllegalStateException("CORRUPT_CONTENT");
        List<DocumentationPayload.Section> sections=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT payload,CASE WHEN octet_length(content_hash)=64 THEN content_hash END FROM documentation_sections WHERE record_id=? AND ordinal>=? AND ordinal<? ORDER BY ordinal LIMIT 256")) {
            p.setString(1,id);p.setInt(2,first);p.setInt(3,first+count);try(var r=p.executeQuery()) { while(r.next()) { var section=DocumentationCodec.section(r.getBytes(1));if(!ContentHashes.contentHash(DocumentationPayload.canonicalFields(List.of(section))).equals(r.getString(2)))throw new IllegalStateException("CORRUPT_CONTENT");sections.add(section); } }
        }
        return sections;
    }

    static String applicability(KnowledgeRecord r) {
        List<String> fields=new ArrayList<>(List.of(r.applicability().product(),r.applicability().scope().name(),Integer.toString(r.applicability().sdkVersions().size())));fields.addAll(r.applicability().sdkVersions());fields.addAll(r.applicability().conditions());
        return Base64.getEncoder().encodeToString(DocumentationCodec.encode(fields));
    }
    static byte[] metadata(KnowledgeRecord r) {
        var l=r.locator();var g=r.governance();
        return DocumentationCodec.encode(Arrays.asList(r.id(),r.applicability().product(),r.sdkVersion(),r.recordType().name(),r.knowledgeKind().name(),r.symbol().orElseThrow().serialize(),l.kind().name(),l.relativePath(),l.anchor().orElse(null),l.lines().map(v->Integer.toString(v.first())).orElse(null),l.lines().map(v->Integer.toString(v.last())).orElse(null),l.section().orElse(null),l.reason(),g.approval().name(),g.owner(),g.provenance(),g.approvalEvidence(),applicability(r)));
    }

    static void validate(Connection db,List<StoredCatalogRecord> records) throws SQLException {
        // Supported API rows only; metadata and content are bounded before materialization.
        try(var s=db.createStatement();var r=s.executeQuery("SELECT count(*) FROM documentation_sections")) { r.next();if(r.getLong(1)>100000L*256)throw new IllegalStateException("WORK_LIMIT"); }
        try(var s=db.createStatement();var r=s.executeQuery("SELECT 1 FROM documentation_sections s LEFT JOIN documentation d USING(record_id) WHERE d.record_id IS NULL OR s.ordinal<0 OR s.ordinal>=d.section_count LIMIT 1")) { if(r.next())throw new IllegalStateException("ORPHAN_CONTENT"); }
        try(var s=db.createStatement();var r=s.executeQuery("SELECT 1 FROM documentation d LEFT JOIN records r USING(record_id) LEFT JOIN knowledge_records k USING(record_id) WHERE r.record_id IS NULL OR k.record_id IS NULL LIMIT 1")) { if(r.next())throw new IllegalStateException("ORPHAN_CONTENT"); }
        try(var s=db.createStatement();var r=s.executeQuery("SELECT 1 FROM knowledge_records k LEFT JOIN documentation d USING(record_id) WHERE d.record_id IS NULL LIMIT 1")) { if(r.next())throw new IllegalStateException("ORPHAN_METADATA"); }
        boolean required=false;
        try(var s=db.createStatement();var r=s.executeQuery("SELECT CASE WHEN octet_length(capabilities)=64 THEN capabilities END FROM build_metadata LIMIT 1")) {
            required=r.next() && ContentHashes.digest("capabilities/1",List.of("api-documentation","catalog","relationships")).equals(r.getString(1));
        }
        for(var stored:records) {
            // Tuple-only synthetic catalog fixtures remain valid. Production content capability is checked by the producer.
            try(var p=db.prepareStatement("SELECT octet_length(metadata) FROM documentation WHERE record_id=?")) {
                p.setString(1,stored.recordId());try(var r=p.executeQuery()) { if(!r.next()) { if(required)throw new IllegalStateException("MISSING_CONTENT");continue; }if(r.getLong(1)>DocumentationCodec.MAX_ENCODED_BYTES)throw new IllegalStateException("PAYLOAD_TOO_LARGE"); }
            }
            List<String> fields;
            try(var p=db.prepareStatement("SELECT metadata FROM documentation WHERE record_id=?")) { p.setString(1,stored.recordId());try(var r=p.executeQuery()) { r.next();fields=DocumentationCodec.decode(r.getBytes(1)); } }
            if(fields.size()!=18)throw new IllegalStateException("CORRUPT_METADATA");
            String columns="record_id,sdk_product,sdk_version,record_type,knowledge_kind,symbol_coordinate,locator_kind,source_path,source_anchor,source_first_line,source_last_line,source_section,absent_reason,approval,owner,provenance,approval_evidence,applicability";
            try(var p=db.prepareStatement("SELECT "+Arrays.stream(columns.split(",")).map(c->"octet_length("+c+")").collect(java.util.stream.Collectors.joining(","))+" FROM knowledge_records WHERE record_id=?")) {
                p.setString(1,stored.recordId());try(var r=p.executeQuery()) { if(!r.next())throw new IllegalStateException("MISSING_METADATA");for(int i=1;i<=18;i++)if(r.getLong(i)>DocumentationCodec.MAX_ENCODED_BYTES)throw new IllegalStateException("PAYLOAD_TOO_LARGE"); }
            }
            try(var p=db.prepareStatement("SELECT "+columns+" FROM knowledge_records WHERE record_id=?")) { p.setString(1,stored.recordId());try(var r=p.executeQuery()) { r.next();for(int i=0;i<18;i++)if(!Objects.equals(fields.get(i),r.getString(i+1)))throw new IllegalStateException("CORRUPT_METADATA"); } }
            var coordinate=SymbolCoordinate.parse(fields.get(5));
            var locator=new SourceLocator(SourceLocator.Kind.valueOf(fields.get(6)),fields.get(7),Optional.ofNullable(fields.get(8)),fields.get(12),fields.get(9)==null?Optional.empty():Optional.of(new SourceLocator.LineRange(Integer.parseInt(fields.get(9)),Integer.parseInt(fields.get(10)))),Optional.ofNullable(fields.get(11)));
            List<String> a=DocumentationCodec.decode(Base64.getDecoder().decode(fields.get(17)));int versions=Integer.parseInt(a.get(2));
            var record=new KnowledgeRecord(fields.get(0),fields.get(2),KnowledgeRecord.RecordType.valueOf(fields.get(3)),KnowledgeRecord.KnowledgeKind.valueOf(fields.get(4)),Optional.of(coordinate),locator,new KnowledgeRecord.Governance(KnowledgeRecord.Approval.valueOf(fields.get(13)),fields.get(14),fields.get(15),fields.get(16)),new KnowledgeRecord.Applicability(a.get(0),a.subList(3,3+versions),a.subList(3+versions,a.size()),KnowledgeRecord.ApplicabilityScope.valueOf(a.get(1))));
            validateAssociation(stored.record(),record,load(db,stored.recordId()));
        }
    }

    @Override public void close() { if(ownsCatalog)catalog.close(); }
}
