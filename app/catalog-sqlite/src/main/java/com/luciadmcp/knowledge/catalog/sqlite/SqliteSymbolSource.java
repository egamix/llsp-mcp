package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static com.luciadmcp.knowledge.domain.SymbolSourceException.Code.*;

/** Serving handle: never initializes schemas or opens a writable catalog. */
public final class SqliteSymbolSource implements SymbolSource, DocumentationSource {
    private static final String COLUMNS="record_id,sdk_version,canonical_key,kind,type_fqn,simple_name,relative_path,anchor";
    private final Connection db;
    private final SymbolSourceBinding binding;

    public SqliteSymbolSource(Path path) {
        if(path==null || !Files.isRegularFile(path))throw new SymbolSourceException(BUILD_UNAVAILABLE,"Existing source catalog required");
        Connection opened=null;
        try {
            opened=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath().normalize().toUri()+"?mode=ro");
            try(var s=opened.createStatement()) { s.execute("PRAGMA query_only=ON");s.execute("PRAGMA busy_timeout=0"); }
            opened.setAutoCommit(false);
            // The first read fixes the snapshot before metadata or validation is consulted.
            try(var s=opened.createStatement();var r=s.executeQuery("SELECT 1 FROM records LIMIT 1")) { r.next(); }
            String sdk,build,capabilities;
            try(var s=opened.createStatement();var r=s.executeQuery("SELECT CASE WHEN octet_length(sdk_version)<=2048 THEN sdk_version END,CASE WHEN octet_length(build_id)<=2048 THEN build_id END,CASE WHEN octet_length(capabilities)=64 THEN capabilities END FROM build_metadata LIMIT 2")) {
                if(!r.next())throw new SymbolSourceException(BUILD_INVALID,"Missing build metadata");
                sdk=r.getString(1);build=r.getString(2);capabilities=r.getString(3);
                if(r.next())throw new SymbolSourceException(BUILD_INVALID,"Multiple build identities");
            }
            var records=SqliteCatalogStore.validationRecords(opened,null);
            var validation=SqliteCatalogStore.validatePersisted(opened,new StandardBuildValidator(),BuildValidator.Operation.STARTUP,build,sdk,records);
            if(!validation.valid())throw new SymbolSourceException(BUILD_INVALID,"BUILD_INVALID: "+validation.reasons());
            // Check citation eligibility at admission rather than inventing locators while resolving.
            for(var record:records) locator(record.record());
            binding=new SymbolSourceBinding(sdk,build,capabilities);
            db=opened;
        } catch(SQLException | RuntimeException failure) {
            if(opened!=null)try { opened.close(); }catch(SQLException ignored) { }
            if(failure instanceof SymbolSourceException typed)throw typed;
            throw new SymbolSourceException(BUILD_INVALID,"Cannot admit persisted symbol source",failure);
        }
    }
    private static SourceLocator locator(CatalogRecord r) {
        return new SourceLocator(SourceLocator.Kind.SOURCE,r.relativePath(),r.anchor().isEmpty()?Optional.empty():Optional.of(r.anchor()),"");
    }
    @Override public SymbolSourceBinding binding() { return binding; }
    @Override public synchronized Optional<StoredCatalogRecord> canonical(String key) {
        ExactSymbolQuery.text(key);
        return rows("SELECT "+COLUMNS+" FROM records WHERE sdk_version=? AND canonical_key=? ORDER BY record_id LIMIT 1",key,1).stream().findFirst();
    }
    @Override public synchronized List<StoredCatalogRecord> after(String recordId,int limit) {
        if(recordId==null || limit<1 || limit>256)throw new IllegalArgumentException("bounded cursor and batch required");
        return rows("SELECT "+COLUMNS+" FROM records WHERE sdk_version=? AND record_id>? ORDER BY record_id LIMIT ?",recordId,limit);
    }
    private List<StoredCatalogRecord> rows(String sql,String value,int limit) {
        try(var p=db.prepareStatement(sql)) {
            p.setString(1,binding.sdkVersion());p.setString(2,value);
            if(sql.endsWith("LIMIT ?"))p.setInt(3,limit);
            var rows=new ArrayList<StoredCatalogRecord>();
            try(var r=p.executeQuery()) { while(r.next())rows.add(new StoredCatalogRecord(r.getString(1),new CatalogRecord(r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8)))); }
            return List.copyOf(rows);
        }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Pinned source read failed",e); }
    }
    @Override public synchronized String runtimeFingerprint() {
        try(var s=db.createStatement();var r=s.executeQuery("SELECT runtime_digest FROM build_metadata LIMIT 1")) {
            if(!r.next())throw new IllegalStateException("MISSING_METADATA");return r.getString(1);
        }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Pinned metadata read failed",e); }
    }
    @Override public synchronized List<String> documentationIdsAfter(String after,int limit) {
        boundedBatch(after,limit);
        try(var p=db.prepareStatement("SELECT d.record_id FROM documentation d JOIN knowledge_records k USING(record_id) WHERE k.sdk_version=? AND d.record_id>? ORDER BY d.record_id LIMIT ?")) {
            p.setString(1,binding.sdkVersion());p.setString(2,after);p.setInt(3,limit);
            var ids=new ArrayList<String>();try(var r=p.executeQuery()) { while(r.next())ids.add(r.getString(1)); }return List.copyOf(ids);
        }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Pinned documentation enumeration failed",e); }
    }
    private static void boundedBatch(String cursor,int limit) {
        if(cursor==null || limit<1 || limit>256)throw new IllegalArgumentException("bounded cursor and batch required");
        if(!cursor.isEmpty())ExactSymbolQuery.text(cursor);
    }
    @Override public synchronized Optional<KnowledgeRecord> knowledgeRecord(String id) {
        ExactSymbolQuery.text(id);
        try(var p=db.prepareStatement("SELECT CASE WHEN octet_length(d.metadata)<=1310724 THEN d.metadata END FROM documentation d JOIN knowledge_records k USING(record_id) WHERE d.record_id=? AND k.sdk_version=?")) {
            p.setString(1,id);p.setString(2,binding.sdkVersion());
            try(var r=p.executeQuery()) { if(!r.next())return Optional.empty();return Optional.of(SqliteDocumentationStore.decodeMetadata(DocumentationCodec.decode(r.getBytes(1)))); }
        }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Pinned knowledge read failed",e); }
    }
    @Override public synchronized List<DocumentationPayload.Section> sections(String id,int offset,int limit) {
        boundedBatch(id,limit);
        if(offset<0 || offset>DocumentationPayload.MAX_SECTIONS)throw new IllegalArgumentException("INVALID_OFFSET");
        // Read bounded, verified sections from this connection, never a second SQLite snapshot.
        try(var p=db.prepareStatement("SELECT section_count FROM documentation WHERE record_id=?")) {
            p.setString(1,id);try(var r=p.executeQuery()) {
                if(!r.next())throw new IllegalArgumentException("MISSING_CONTENT");int count=r.getInt(1);
                if(count<1 || count>256)throw new IllegalStateException("CORRUPT_CONTENT");
                if(offset>=count)return List.of();
                return List.copyOf(SqliteDocumentationStore.read(db,id,offset,Math.min(limit,count-offset),DocumentationCodec.MAX_ENCODED_BYTES));
            }
        }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Pinned section read failed",e); }
    }
    @Override public synchronized void close() {
        try { db.close(); }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Source close failed",e); }
    }
}
