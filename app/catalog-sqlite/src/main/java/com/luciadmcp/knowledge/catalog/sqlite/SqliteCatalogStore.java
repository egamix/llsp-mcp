package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.CatalogRecord;
import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.domain.ContentHashes;
import com.luciadmcp.knowledge.domain.IdentityFields;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of the {@link CatalogStore} domain port (Story 2.4). It is the sole authority
 * that computes record IDs (AD-22): it applies {@link IdentityFields} to the submitted logical tuple
 * and hashes it with {@link ContentHashes#recordHash(String, java.util.List)} — no other module
 * computes an ID. It opens (creating if absent) a single build-scoped SQLite file and persists
 * version-scoped rows keyed by that computed ID.
 *
 * <h2>Provisional schema — pending governance sign-off</h2>
 *
 * <p>The concrete DDL below is a working, clearly-labeled <b>provisional</b> schema, mirroring the
 * "design-provisionally-then-approve" split Story 2.3 used for the canonical-symbol grammar. The
 * SQLite catalog schema is a {@code [NEEDS APPROVAL]} governance gate (ARCHITECTURE-SPINE Deferred);
 * this is not the approved Epic-3 DDL. Identity inputs are captured explicitly ({@code sdk_version},
 * {@code canonical_key}, {@code kind}, {@code type_fqn}, {@code simple_name}, {@code relative_path},
 * {@code anchor}) so a later approved schema maps on without reshaping. No relationship/adjacency
 * tables are created here — edges are Story 2.5+.
 *
 * <ul>
 *   <li><b>{@code record_id}</b> — TEXT primary key, the AD-22 computed content hash.</li>
 *   <li><b>Version-scoped uniqueness</b> — {@code UNIQUE(sdk_version, canonical_key)} (AD-12): the same
 *       simple name in two SDK versions never collides.</li>
 *   <li><b>Resolvable to source</b> — {@code relative_path} + {@code anchor} reconstruct the source
 *       Javadoc location.</li>
 * </ul>
 *
 * <h2>Idempotent upsert &amp; identity clash (AD-4)</h2>
 *
 * <p>Because the record ID is a pure function of the version-scoped identity, submitting the same
 * logical tuple twice resolves to the same ID and leaves one row ({@link PutStatus#DUPLICATE}).
 * Submitting a <em>different</em> tuple that resolves to the same identity is a real clash: the
 * existing row is left untouched and {@link PutStatus#IDENTITY_CLASH} is returned so the caller can
 * record an issue — never a silent overwrite or drop.
 *
 * <h2>Determinism (NFR-13)</h2>
 *
 * <p>Nothing here reads the clock, a random source, or a locale-dependent transform; IDs are pure
 * content hashes. An interrupted-then-resumed build re-submitting the same tuples yields byte-identical
 * rows and IDs. AD-9: SQLite runs in-process against a local file; no socket is opened.
 */
public final class SqliteCatalogStore implements CatalogStore {

    /** Provisional records-table DDL (pending governance sign-off). */
    static final String CREATE_RECORDS_TABLE =
            "CREATE TABLE IF NOT EXISTS records ("
                    + "record_id TEXT PRIMARY KEY NOT NULL, "
                    + "sdk_version TEXT NOT NULL, "
                    + "canonical_key TEXT NOT NULL, "
                    + "kind TEXT NOT NULL, "
                    + "type_fqn TEXT NOT NULL, "
                    + "simple_name TEXT NOT NULL, "
                    + "relative_path TEXT NOT NULL, "
                    + "anchor TEXT NOT NULL, "
                    + "UNIQUE(sdk_version, canonical_key)"
                    + ")";

    private static final String SELECT_COLUMNS =
            "record_id, sdk_version, canonical_key, kind, type_fqn, simple_name, relative_path, anchor";

    private final Connection connection;

    /**
     * Opens (creating if absent) the build-scoped catalog at {@code dbPath}, creating parent
     * directories and the provisional schema.
     *
     * @param dbPath the build-scoped SQLite file path (coordinator-owned; AD-25)
     */
    public SqliteCatalogStore(Path dbPath) {
        if (dbPath == null) {
            throw new IllegalArgumentException("dbPath must not be null");
        }
        Path absolute = dbPath.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException("could not create catalog directory " + parent, e);
            }
        }
        SchemaCompatibility.check(absolute);
        Connection opened = null;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            SchemaCompatibility.initialize(opened);
            try (Statement stmt = opened.createStatement()) {
                stmt.execute(CREATE_RECORDS_TABLE);
            }
            this.connection = opened;
        } catch (SQLException e) {
            // If the connection opened but the DDL failed, close it so the half-constructed, now
            // unreachable object does not leak the connection.
            closeQuietly(opened);
            throw new IllegalStateException("could not open catalog store at " + absolute, e);
        }
    }

    Connection connection() { return connection; }

    @Override public PutResult putDocumented(CatalogRecord record,
            com.luciadmcp.knowledge.domain.KnowledgeRecord knowledge,
            com.luciadmcp.knowledge.domain.DocumentationPayload payload) {
        return new SqliteDocumentationStore(this).put(record, knowledge, payload);
    }

    @Override public PutResult putNarrative(com.luciadmcp.knowledge.domain.KnowledgeRecord record, com.luciadmcp.knowledge.domain.DocumentationPayload payload) {
        return new SqliteDocumentationStore(this).put(null,record,payload);
    }

    @Override public void markInvalid() {
        try(Statement s=connection.createStatement()) { s.executeUpdate("UPDATE build_metadata SET validation_reasons='[\"BUILD_INVALIDATING_OUTCOME\"]'"); }
        catch(SQLException e) { throw new IllegalStateException("could not preserve validation failure",e); }
    }
    @Override
    public void bindManifest(com.luciadmcp.knowledge.domain.IndexManifest m) {
        try (PreparedStatement check = connection.prepareStatement("SELECT build_id,sdk_version,input_digest,recipe_digest,runtime_digest,profile,capabilities FROM build_metadata")) {
            try (ResultSet r = check.executeQuery()) {
                if (r.next()) {
                    if (!m.buildId().equals(r.getString(1)) || !m.sdkVersion().equals(r.getString(2)) || !m.inputDigest().equals(r.getString(3)) || !m.recipeDigest().equals(r.getString(4)) || !m.runtimeDigest().equals(r.getString(5)) || !m.profile().equals(r.getString(6)) || !m.capabilityFingerprint().equals(r.getString(7))) throw new IllegalStateException("INCOMPATIBLE_BUILD_METADATA: rebuild required");
                    return;
                }
            }
        } catch (SQLException e) { throw new IllegalStateException("could not read build metadata",e); }
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO build_metadata VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setString(1,m.buildId()); p.setString(2,"LuciadLightspeed"); p.setString(3,m.sdkVersion()); p.setString(4,m.profile()); p.setString(5,m.inputDigest()); p.setString(6,m.recipeDigest()); p.setString(7,m.runtimeDigest()); p.setString(8,m.capabilityFingerprint()); p.setString(9,"[]"); p.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("could not persist build metadata",e); }
    }
    @Override
    public com.luciadmcp.knowledge.domain.BuildValidator.Result validateBuild(com.luciadmcp.knowledge.domain.BuildValidator validator,
            com.luciadmcp.knowledge.domain.BuildValidator.Operation operation, String buildId, String sdkVersion) {
        try {
            connection.setAutoCommit(false);
            return validatePersisted(connection,validator,operation,buildId,sdkVersion,validationRecords(connection,null));
        } catch(SQLException e) { throw new IllegalStateException("could not pin validation snapshot",e); }
        finally { try { connection.rollback(); connection.setAutoCommit(true); } catch(SQLException e) { throw new IllegalStateException("could not release validation snapshot",e); } }
    }
    /** Metadata-only admission pass before any TEXT payload is fetched. Must run in one pinned snapshot. */
    static java.util.List<com.luciadmcp.knowledge.domain.StoredCatalogRecord> validationRecords(Connection db,String sdk) throws SQLException {
        String where=sdk==null ? "" : " WHERE sdk_version=?";
        String columns="record_id,sdk_version,canonical_key,kind,type_fqn,simple_name,relative_path,anchor";
        String metadata=java.util.Arrays.stream(columns.split(",")).map(c -> "octet_length("+c+")").collect(java.util.stream.Collectors.joining(","));
        long total=0;int rows=0;
        try(var p=db.prepareStatement("SELECT "+metadata+" FROM records"+where+" LIMIT 100001")) {
            if(sdk!=null)p.setString(1,sdk);
            try(var r=p.executeQuery()) { while(r.next()) {
                if(++rows>com.luciadmcp.knowledge.domain.StandardBuildValidator.MAX_RECORDS) throw new IllegalStateException("WORK_LIMIT: validation rows");
                for(int i=1;i<=8;i++) { long bytes=r.getLong(i); if(r.wasNull() || bytes>1_048_576 || (total+=bytes)>16_777_216) throw new IllegalStateException("WORK_LIMIT: validation payload"); }
            } }
        }
        var records=new java.util.ArrayList<com.luciadmcp.knowledge.domain.StoredCatalogRecord>(rows);
        try(var p=db.prepareStatement("SELECT "+columns+" FROM records"+where+" LIMIT 100000")) {
            if(sdk!=null)p.setString(1,sdk);
            try(var r=p.executeQuery()) { while(r.next()) records.add(new com.luciadmcp.knowledge.domain.StoredCatalogRecord(r.getString(1),new com.luciadmcp.knowledge.domain.CatalogRecord(r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8)))); }
        }
        return records;
    }
    static com.luciadmcp.knowledge.domain.BuildValidator.Result validatePersisted(Connection db,
            com.luciadmcp.knowledge.domain.BuildValidator validator, com.luciadmcp.knowledge.domain.BuildValidator.Operation operation,
            String buildId,String sdkVersion, java.util.List<com.luciadmcp.knowledge.domain.StoredCatalogRecord> records) {
        var reasons = new java.util.ArrayList<com.luciadmcp.knowledge.domain.BuildValidator.Reason>();
        int schema=0; String runtime="";
        try (Statement s=db.createStatement()) {
            for(var entry:java.util.Map.of("contract_metadata", "identity_version,runtime_fingerprint", "build_metadata", "build_id,sdk_product,sdk_version,profile,input_digest,recipe_digest,runtime_digest,capabilities,validation_reasons").entrySet()) {
                String lengths=java.util.Arrays.stream(entry.getValue().split(",")).map(c -> "octet_length("+c+")").collect(java.util.stream.Collectors.joining(","));
                try(ResultSet r=s.executeQuery("SELECT "+lengths+" FROM "+entry.getKey()+" LIMIT 2")) { int rows=0;while(r.next()) { if(++rows>1) throw new IllegalStateException("WORK_LIMIT: metadata rows"); for(int i=1;i<=r.getMetaData().getColumnCount();i++) if(r.getLong(i)>2048) throw new IllegalStateException("WORK_LIMIT: metadata payload"); } }
            }

            try(ResultSet r=s.executeQuery("PRAGMA user_version")) { r.next();schema=r.getInt(1); }
            try(ResultSet r=s.executeQuery("SELECT identity_version,runtime_fingerprint FROM contract_metadata WHERE singleton=1")) {
                if(!r.next()) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.UNSUPPORTED_SCHEMA);
                else { if(!com.luciadmcp.knowledge.domain.ContractVersions.IDENTITY.equals(r.getString(1))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_IDENTITY); runtime=r.getString(2); }
            }
            try(ResultSet r=s.executeQuery("SELECT build_id,sdk_product,sdk_version,profile,input_digest,recipe_digest,runtime_digest,capabilities,validation_reasons FROM build_metadata")) {
                if(!r.next()) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_IDENTITY);
                else {
                    if(!buildId.equals(r.getString(1)) || !buildId.equals("b-"+r.getString(5))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_IDENTITY);
                    if(!"LuciadLightspeed".equals(r.getString(2)) || !sdkVersion.equals(r.getString(3))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_SCOPE);
                    if(!com.luciadmcp.knowledge.domain.ContractVersions.PROFILE.equals(r.getString(4)) || !com.luciadmcp.knowledge.domain.ContentHashes.recipeFingerprint(com.luciadmcp.knowledge.domain.ContractVersions.PROFILE,java.util.List.of()).equals(r.getString(6)) || (!com.luciadmcp.knowledge.domain.ContentHashes.digest("capabilities/1",java.util.List.of("api-documentation","catalog","narrative-documentation","relationships","structured-documentation")).equals(r.getString(8)) && !com.luciadmcp.knowledge.domain.ContentHashes.digest("capabilities/1",java.util.List.of("catalog","relationships")).equals(r.getString(8)))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INCOMPATIBLE_RUNTIME);
                    if(!runtime.equals(r.getString(7))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INCOMPATIBLE_RUNTIME);
                    if(!"[]".equals(r.getString(9))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.BUILD_INVALIDATING_OUTCOME);
                    if(r.next()) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_IDENTITY);
                }
            }
            SqliteDocumentationStore.validate(db,records);
            for(String table:java.util.List.of("knowledge_dependencies")) {
                try(ResultSet r=s.executeQuery("SELECT 1 FROM "+table+" LIMIT 1")) { if(r.next()) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INCOMPATIBLE_RUNTIME); }
            }
        } catch(SQLException | RuntimeException e) { reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.UNSUPPORTED_SCHEMA); }
        try (Statement statement=db.createStatement()) {
            java.util.Set<String> recordIds=records.stream().map(com.luciadmcp.knowledge.domain.StoredCatalogRecord::recordId).collect(java.util.stream.Collectors.toSet());
            int count=0;long bytes=0;
            try(ResultSet r=statement.executeQuery("SELECT octet_length(edge_id),octet_length(sdk_version),octet_length(source_id),octet_length(type),octet_length(target_id),octet_length(evidence) FROM edges LIMIT 200001")) {
                while(r.next()) { if(++count>200000) throw new IllegalStateException("WORK_LIMIT: edges"); for(int i=1;i<=6;i++) { long size=r.getLong(i);if(size>1_048_576 || (bytes+=size)>16_777_216) throw new IllegalStateException("WORK_LIMIT: edge payload"); } }
            }
            try(ResultSet r=statement.executeQuery("SELECT edge_id,sdk_version,source_id,type,target_id,evidence FROM edges LIMIT 200000")) {
                while(r.next()) {
                    if(!sdkVersion.equals(r.getString(2))) { reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_SCOPE); continue; }
                    try {
                        String id=com.luciadmcp.knowledge.domain.ContentHashes.relationshipHash(r.getString(2),r.getString(3),com.luciadmcp.knowledge.domain.RelationshipType.valueOf(r.getString(4)),r.getString(5),r.getString(6));
                        if(!id.equals(r.getString(1)) || !recordIds.contains(r.getString(3)) || !recordIds.contains(r.getString(5))) reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_IDENTITY);
                    } catch(IllegalArgumentException e) { reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INVALID_IDENTITY); }
                }
            }
        } catch(SQLException e) { reasons.add(com.luciadmcp.knowledge.domain.BuildValidator.Reason.UNSUPPORTED_SCHEMA); }
        reasons.addAll(validator.validate(new com.luciadmcp.knowledge.domain.BuildValidator.Input(sdkVersion,schema,runtime,false,records,java.util.List.of(),java.util.List.of()),operation).reasons());
        return new com.luciadmcp.knowledge.domain.BuildValidator.Result(reasons);
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Best-effort cleanup on a failed open; the original failure is what the caller sees.
        }
    }

    @Override
    public PutResult put(CatalogRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        String recordId = ContentHashes.recordHash(
                IdentityFields.entityTypeFor(record), IdentityFields.identityValues(record));

        Optional<StoredCatalogRecord> existing = findByRecordId(recordId);
        if (existing.isPresent()) {
            // Same identity already stored: idempotent no-op if the content matches, otherwise a real
            // identity clash — two distinct logical records claiming one identity (AD-4).
            return existing.get().record().equals(record)
                    ? new PutResult(recordId, PutStatus.DUPLICATE)
                    : new PutResult(recordId, PutStatus.IDENTITY_CLASH);
        }

        String sql = "INSERT INTO records (" + SELECT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.setString(2, record.sdkVersion());
            ps.setString(3, record.canonicalKey());
            ps.setString(4, record.kind());
            ps.setString(5, record.typeFqn());
            ps.setString(6, record.simpleName());
            ps.setString(7, record.relativePath());
            ps.setString(8, record.anchor());
            ps.executeUpdate();
            return new PutResult(recordId, PutStatus.INSERTED);
        } catch (SQLException e) {
            // Defensive: a UNIQUE(sdk_version, canonical_key) violation without a record_id match means
            // a different logical record already occupies this version-scoped key — a real clash, not a
            // silent drop (AD-4). Any other SQL failure is a genuine storage error.
            if (isVersionKeyConstraintViolation(e)) {
                return new PutResult(recordId, PutStatus.IDENTITY_CLASH);
            }
            throw new IllegalStateException("could not persist catalog record " + recordId, e);
        }
    }

    @Override
    public Optional<StoredCatalogRecord> findByKey(String sdkVersion, String canonicalKey) {
        if (sdkVersion == null || canonicalKey == null) {
            throw new IllegalArgumentException("sdkVersion and canonicalKey must not be null");
        }
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM records WHERE sdk_version = ? AND canonical_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sdkVersion);
            ps.setString(2, canonicalKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not query catalog by key", e);
        }
    }

    @Override
    public Optional<StoredCatalogRecord> findByRecordId(String recordId) {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId must not be blank");
        }
        String sql = "SELECT " + SELECT_COLUMNS + " FROM records WHERE record_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not query catalog by record id", e);
        }
    }

    @Override
    public List<StoredCatalogRecord> allRecords() {
        // Ascending record_id gives a deterministic order independent of insertion sequence (NFR-13).
        String sql = "SELECT " + SELECT_COLUMNS + " FROM records ORDER BY record_id ASC";
        List<StoredCatalogRecord> out = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                out.add(read(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not enumerate catalog records", e);
        }
        return out;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("could not close catalog store", e);
        }
    }

    private static StoredCatalogRecord read(ResultSet rs) throws SQLException {
        String recordId = rs.getString("record_id");
        CatalogRecord record = new CatalogRecord(
                rs.getString("sdk_version"),
                rs.getString("canonical_key"),
                rs.getString("kind"),
                rs.getString("type_fqn"),
                rs.getString("simple_name"),
                rs.getString("relative_path"),
                rs.getString("anchor"));
        return new StoredCatalogRecord(recordId, record);
    }

    // SQLite result codes (see sqlite.org/rescode.html). getErrorCode() carries the primary code
    // (19 = SQLITE_CONSTRAINT) or, when the driver surfaces it, the extended code that pinpoints which
    // constraint failed.
    private static final int SQLITE_CONSTRAINT = 19;
    private static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555; // 19 | (6 << 8)
    private static final int SQLITE_CONSTRAINT_UNIQUE = 2067;     // 19 | (8 << 8)

    /**
     * Whether a failed {@code INSERT} is a {@code UNIQUE(sdk_version, canonical_key)} violation — a real
     * identity clash — as opposed to a genuine storage error. Uses the SQLite result code rather than
     * scanning the (driver-text/locale-dependent) message. A {@code record_id} primary-key collision is
     * explicitly <em>not</em> treated as a clash: it cannot occur here because {@link #put} checks
     * {@link #findByRecordId} first, so if it ever surfaced it is a real error and must propagate.
     */
    private static boolean isVersionKeyConstraintViolation(SQLException e) {
        int code = e.getErrorCode();
        if (code == SQLITE_CONSTRAINT_UNIQUE) {
            return true;
        }
        if (code == SQLITE_CONSTRAINT_PRIMARYKEY) {
            return false;
        }
        // Only the primary constraint code is available (no extended detail): our INSERT is guarded by
        // findByRecordId, so a constraint failure here can only be the version-scoped UNIQUE index.
        return (code & 0xFF) == SQLITE_CONSTRAINT;
    }
}
