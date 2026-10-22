package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static com.luciadmcp.knowledge.domain.SymbolSourceException.Code.*;

/** Serving handle: never initializes schemas or opens a writable catalog. */
public final class SqliteSymbolSource implements SymbolSource {
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
    @Override public synchronized void close() {
        try { db.close(); }catch(SQLException e) { throw new SymbolSourceException(BUILD_UNAVAILABLE,"Source close failed",e); }
    }
}
