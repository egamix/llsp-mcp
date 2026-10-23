package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.ContentHashes;
import com.luciadmcp.knowledge.domain.ReferenceResolution;
import com.luciadmcp.knowledge.domain.RelationshipScope;
import com.luciadmcp.knowledge.domain.RelationshipEdge;
import com.luciadmcp.knowledge.domain.RelationshipStore;
import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.domain.StoredRelationshipEdge;
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
 * SQLite implementation of the {@link RelationshipStore} domain port (Story 2.5). It is the sole
 * authority that computes edge IDs (AD-22): it maps each submitted endpoint canonical key to its
 * stored {@code record_id} on the shared build-scoped catalog file, then hashes the edge identity with
 * {@link ContentHashes#relationshipHash(String, String, RelationshipType, String, String)} — no other
 * module computes an edge ID. It opens the <em>same</em> build-scoped SQLite file the catalog wrote
 * (Story 2.4), so the {@code records} table it resolves against and the {@code edges} table it writes
 * live together.
 *
 * <h2>Provisional schema — pending governance sign-off</h2>
 *
 * <p>The concrete {@code edges} DDL below is a working, clearly-labeled <b>provisional</b> schema,
 * mirroring the Story-2.4 catalog precedent. The SQLite relationship schema (adjacency +
 * recursive-CTE traversal) is a {@code [NEEDS APPROVAL]} governance gate (ARCHITECTURE-SPINE
 * Deferred, Epic 3); this is not the approved DDL. Identity inputs are captured explicitly
 * ({@code sdk_version}, {@code source_id}, {@code type}, {@code target_id}, {@code evidence}) so a
 * later approved schema maps on without reshaping. {@code evidence} is empty for the 13 Javadoc
 * structural edges (it is an example-link concern, AD-30) but the column exists for Epic-3 reuse.
 *
 * <ul>
 *   <li><b>{@code edge_id}</b> — TEXT primary key, the AD-22 computed content hash.</li>
 *   <li><b>Identity uniqueness</b> — {@code UNIQUE(sdk_version, source_id, type, target_id, evidence)}:
 *       exactly the fields folded into {@code edge_id}, so the index and the hash agree.</li>
 *   <li><b>Version scoping</b> — every edge carries {@code sdk_version} and both endpoints are
 *       resolved from the same version's records (AD-12).</li>
 * </ul>
 *
 * <h2>Idempotent upsert &amp; identity clash (AD-4)</h2>
 *
 * <p>Because the edge ID is a pure function of the version-scoped edge identity, submitting the same
 * logical edge twice resolves to the same ID and leaves one row ({@link PutStatus#DUPLICATE}).
 * Submitting a <em>different</em> logical edge that resolves to the same identity is a real clash: the
 * existing row is left untouched and {@link PutStatus#IDENTITY_CLASH} is returned so the caller can
 * record an issue — never a silent overwrite or drop.
 *
 * <h2>Determinism (NFR-13)</h2>
 *
 * <p>Nothing here reads the clock, a random source, or a locale-dependent transform; IDs are pure
 * content hashes and reads are ordered by ascending {@code edge_id}. An interrupted-then-resumed build
 * re-submitting the same edges yields byte-identical rows and IDs. AD-9: SQLite runs in-process
 * against a local file; no socket is opened.
 */
public final class SqliteRelationshipStore implements RelationshipStore {

    /** Provisional edges-table DDL (pending governance sign-off). */
    static final String CREATE_EDGES_TABLE =
            "CREATE TABLE IF NOT EXISTS edges ("
                    + "edge_id TEXT PRIMARY KEY NOT NULL, "
                    + "sdk_version TEXT NOT NULL, "
                    + "source_id TEXT NOT NULL, "
                    + "type TEXT NOT NULL, "
                    + "target_id TEXT NOT NULL, "
                    + "evidence TEXT NOT NULL, "
                    + "UNIQUE(sdk_version, source_id, type, target_id, evidence)"
                    + ")";

    /** Structural edges carry no evidence (AD-30); the column exists for Epic-3 example reuse. */
    private static final String STRUCTURAL_EVIDENCE = "";

    private final Connection connection;
    private BoundedRelationshipTraversal traversal;
    private java.util.Optional<com.luciadmcp.knowledge.domain.SymbolSourceBinding> servingBinding=java.util.Optional.empty();
    @Override public java.util.Optional<com.luciadmcp.knowledge.domain.SymbolSourceBinding> servingBinding() { return servingBinding; }

    /**
     * Serving handle. The coordinator attests that buildId names this sealed immutable file;
     * identity is never derived from its path. Runtime signing material must be at least 32 bytes.
     * This handle prohibits writes and retains a read snapshot until closed.
     */
    public SqliteRelationshipStore(Path dbPath, String buildId,
            com.luciadmcp.knowledge.domain.RelationshipScope scope, byte[] signingKey) {
        this(requireExisting(dbPath, buildId, scope, signingKey), true);
        try {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA query_only=ON");
                stmt.execute("PRAGMA busy_timeout=0");
            }
            connection.setAutoCommit(false);
            // Establish the snapshot at binding time, before any seed can be resolved.
            try (Statement stmt = connection.createStatement();
                    ResultSet ignored = stmt.executeQuery("SELECT 1 FROM records LIMIT 1")) {
                ignored.next();
            }
            var validation=SqliteCatalogStore.validatePersisted(connection,new com.luciadmcp.knowledge.domain.StandardBuildValidator(),com.luciadmcp.knowledge.domain.BuildValidator.Operation.STARTUP,buildId,scope.sdkVersion());
            if(!validation.valid()) throw new IllegalStateException("BUILD_INVALID: "+validation.reasons());
            try(var stmt=connection.createStatement();var row=stmt.executeQuery("SELECT capabilities FROM build_metadata LIMIT 1")) {
                if(!row.next())throw new IllegalStateException("missing graph binding");
                servingBinding=java.util.Optional.of(new com.luciadmcp.knowledge.domain.SymbolSourceBinding(scope.sdkVersion(),buildId,row.getString(1)));
            }
            traversal = new BoundedRelationshipTraversal(connection, buildId, scope, signingKey, System::nanoTime);
        } catch (SQLException | RuntimeException e) {
            closeQuietly(connection);
            throw new IllegalStateException("could not pin traversal build", e);
        }
    }

    private static Path requireExisting(Path path, String buildId,
            com.luciadmcp.knowledge.domain.RelationshipScope scope, byte[] key) {
        com.luciadmcp.knowledge.domain.TraversalRequest.boundedText(buildId);
        if (scope == null) throw new IllegalArgumentException("scope required");
        com.luciadmcp.knowledge.domain.TraversalRequest.boundedText(scope.sdkVersion());
        if (key == null || key.length < 32) throw new IllegalArgumentException("runtime signing key needs 32 bytes");
        if (path == null || !Files.isRegularFile(path)) throw new IllegalArgumentException("sealed build file required");
        return path;
    }

    @Override
    public synchronized com.luciadmcp.knowledge.domain.TraversalResult traverse(
            com.luciadmcp.knowledge.domain.TraversalRequest request) {
        if (traversal == null) throw new IllegalStateException("traversal requires a pinned immutable build handle");
        return traversal.traverse(request);
    }

    /**
     * Opens (creating if absent) the build-scoped catalog file at {@code dbPath} — the same file the
     * catalog store wrote — and creates the provisional {@code edges} table. The {@code records} table
     * is owned by {@link SqliteCatalogStore} and is expected to already exist (the catalog stage runs
     * first); this store only reads it to resolve endpoint keys.
     *
     * @param dbPath the build-scoped SQLite file path (coordinator-owned; AD-25)
     */
    public SqliteRelationshipStore(Path dbPath) {
        this(dbPath, false);
    }

    private SqliteRelationshipStore(Path dbPath, boolean readOnly) {
        if (dbPath == null) {
            throw new IllegalArgumentException("dbPath must not be null");
        }
        Path absolute = dbPath.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException("could not create relationship store directory " + parent, e);
            }
        }
        SchemaCompatibility.check(absolute);
        Connection opened = null;
        try {
            opened = DriverManager.getConnection(readOnly
                    ? "jdbc:sqlite:" + absolute.toUri() + "?mode=ro"
                    : "jdbc:sqlite:" + absolute);
            if (!readOnly) SchemaCompatibility.initialize(opened);
            if (!readOnly) try (Statement stmt = opened.createStatement()) {
                stmt.execute(CREATE_EDGES_TABLE);
                stmt.execute("CREATE INDEX IF NOT EXISTS edges_out ON edges(sdk_version,source_id,edge_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS edges_in ON edges(sdk_version,target_id,edge_id)");
                // Provisional Story 2.6 occurrence identity; outcome fields deliberately are not identity.
                stmt.execute("CREATE TABLE IF NOT EXISTS reference_resolutions ("
                        + "id INTEGER PRIMARY KEY, sdk_version TEXT NOT NULL, source_id TEXT NOT NULL, "
                        + "source_path TEXT NOT NULL, source_anchor TEXT NOT NULL, type TEXT NOT NULL, "
                        + "token TEXT NOT NULL, locator TEXT NOT NULL, status TEXT NOT NULL, kind TEXT NOT NULL, "
                        + "reason TEXT NOT NULL, selected_id TEXT NOT NULL, "
                        + "UNIQUE(sdk_version,source_id,type,token,locator))");
                stmt.execute("CREATE TABLE IF NOT EXISTS reference_candidates ("
                        + "resolution_id INTEGER NOT NULL, candidate_id TEXT NOT NULL, "
                        + "PRIMARY KEY(resolution_id,candidate_id))");
            }
            this.connection = opened;
        } catch (SQLException e) {
            closeQuietly(opened);
            throw new IllegalStateException("could not open relationship store at " + absolute, e);
        }
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
    public PutResult put(RelationshipEdge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("edge must not be null");
        }
        // The store maps each endpoint canonical key to its stored record_id — it never trusts the
        // caller to supply an id. A key with no matching version-scoped record is an integrity failure:
        // pass two only ever submits resolved catalog symbols, so this must not happen silently.
        String sourceId = resolveRecordId(edge.sdkVersion(), edge.sourceSymbol());
        if (sourceId == null) {
            throw new IllegalStateException("no catalog record for source key '" + edge.sourceSymbol()
                    + "' (SDK version " + edge.sdkVersion() + ")");
        }
        String targetId = resolveRecordId(edge.sdkVersion(), edge.targetSymbol());
        if (targetId == null) {
            throw new IllegalStateException("no catalog record for target key '" + edge.targetSymbol()
                    + "' (SDK version " + edge.sdkVersion() + ")");
        }

        String edgeId = ContentHashes.relationshipHash(
                edge.sdkVersion(), sourceId, edge.type(), targetId, STRUCTURAL_EVIDENCE);

        Optional<StoredRelationshipEdge> existing = findByEdgeId(edgeId);
        if (existing.isPresent()) {
            // Same identity already stored: idempotent no-op if the logical edge matches, otherwise a
            // real identity clash — two distinct logical edges claiming one identity (AD-4).
            return existing.get().edge().equals(edge)
                    ? new PutResult(edgeId, PutStatus.DUPLICATE)
                    : new PutResult(edgeId, PutStatus.IDENTITY_CLASH);
        }

        String sql = "INSERT INTO edges (edge_id, sdk_version, source_id, type, target_id, evidence) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, edgeId);
            ps.setString(2, edge.sdkVersion());
            ps.setString(3, sourceId);
            ps.setString(4, edge.type().name());
            ps.setString(5, targetId);
            ps.setString(6, STRUCTURAL_EVIDENCE);
            ps.executeUpdate();
            return new PutResult(edgeId, PutStatus.INSERTED);
        } catch (SQLException e) {
            // Defensive: a UNIQUE(identity) violation without an edge_id match means a different
            // logical edge already occupies this identity — a real clash, not a silent drop (AD-4).
            if (isIdentityConstraintViolation(e)) {
                return new PutResult(edgeId, PutStatus.IDENTITY_CLASH);
            }
            throw new IllegalStateException("could not persist relationship edge " + edgeId, e);
        }
    }

    @Override
    public Optional<StoredRelationshipEdge> findByEdgeId(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edgeId must not be blank");
        }
        String sql = SELECT_EDGE_JOIN + " WHERE e.edge_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, edgeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not query relationship edge by id", e);
        }
    }

    @Override
    public List<StoredRelationshipEdge> allEdges() {
        // Ascending edge_id gives a deterministic order independent of insertion sequence (NFR-13).
        String sql = SELECT_EDGE_JOIN + " ORDER BY e.edge_id ASC";
        List<StoredRelationshipEdge> out = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                out.add(read(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not enumerate relationship edges", e);
        }
        return out;
    }

    @Override
    public List<StoredRelationshipEdge> edges(RelationshipScope scope) {
        List<StoredRelationshipEdge> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_EDGE_JOIN + " WHERE e.sdk_version=? ORDER BY e.edge_id")) {
            ps.setString(1, scope.sdkVersion());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(read(rs)); }
        } catch (SQLException e) { throw new IllegalStateException("could not read scoped edges", e); }
        return List.copyOf(result);
    }

    @Override
    public void putResolution(ReferenceResolution r) {
        validateResolution(r);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM reference_resolutions WHERE sdk_version=? AND source_id=? AND type=? AND token=? AND locator=?")) {
            String[] identity = {r.sdkVersion(), r.sourceId(), r.relationshipType().name(), r.originalToken(), r.originalLocator()};
            for (int i = 0; i < identity.length; i++) ps.setString(i + 1, identity[i]);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!readResolution(rs, r.sdkVersion()).equals(r)) throw new IllegalStateException("conflicting reference evidence");
                    return;
                }
            }
        } catch (SQLException e) { throw new IllegalStateException("could not query reference occurrence", e); }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO reference_resolutions(sdk_version,source_id,source_path,source_anchor,type,token,locator,status,kind,reason,selected_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                String[] values = {r.sdkVersion(), r.sourceId(), r.sourcePath(), r.sourceAnchor(),
                        r.relationshipType().name(), r.originalToken(), r.originalLocator(),
                        r.status().name(), r.kind().name(), r.reason().name(), r.selectedTargetId()};
                for (int i = 0; i < values.length; i++) ps.setString(i + 1, values[i]);
                ps.executeUpdate();
            }
            long id;
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next(); id = rs.getLong(1);
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO reference_candidates VALUES(?,?)")) {
                for (String candidate : r.candidateIds()) {
                    ps.setLong(1, id); ps.setString(2, candidate); ps.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
            throw new IllegalStateException("could not persist reference evidence", e);
        } finally {
            try { connection.setAutoCommit(true); }
            catch (SQLException e) { throw new IllegalStateException("could not restore evidence transaction", e); }
        }
    }

    private void validateResolution(ReferenceResolution r) {
        if (r == null) throw new IllegalArgumentException("resolution must not be null");
        String sql = "SELECT relative_path, anchor FROM records WHERE sdk_version=? AND record_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, r.sdkVersion()); ps.setString(2, r.sourceId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !r.sourcePath().equals(rs.getString(1)) || !r.sourceAnchor().equals(rs.getString(2)))
                    throw new IllegalStateException("invalid reference source provenance");
            }
            for (String candidate : r.candidateIds()) {
                ps.setString(2, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("candidate outside documented SDK scope");
                }
            }
        } catch (SQLException e) { throw new IllegalStateException("could not validate reference provenance", e); }
    }

    @Override
    public List<ReferenceResolution> resolutions(RelationshipScope scope) {
        List<ReferenceResolution> result = new ArrayList<>();
        String sql = "SELECT r.* FROM reference_resolutions r JOIN records s ON s.record_id=r.source_id "
                + "AND s.sdk_version=r.sdk_version AND s.relative_path=r.source_path AND s.anchor=r.source_anchor "
                + "WHERE r.sdk_version=? ORDER BY r.source_id,r.type,r.token,r.locator";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.sdkVersion());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readResolution(rs, scope.sdkVersion()));
                }
            }
        } catch (SQLException e) { throw new IllegalStateException("could not read reference evidence", e); }
        return List.copyOf(result);
    }

    private ReferenceResolution readResolution(ResultSet rs, String version) throws SQLException {
        List<String> candidates = new ArrayList<>();
        try (PreparedStatement cp = connection.prepareStatement(
                "SELECT c.candidate_id,t.record_id AS verified_id FROM reference_candidates c "
                + "LEFT JOIN records t ON t.record_id=c.candidate_id AND t.sdk_version=? "
                + "WHERE c.resolution_id=? ORDER BY c.candidate_id")) {
            cp.setString(1, version);
            cp.setLong(2, rs.getLong("id"));
            try (ResultSet cr = cp.executeQuery()) {
                while (cr.next()) {
                    if (cr.getString("verified_id") == null)
                        throw new IllegalStateException("stored candidate outside SDK scope");
                    candidates.add(cr.getString(1));
                }
            }
        }
        return new ReferenceResolution(rs.getString("sdk_version"), rs.getString("source_id"),
                rs.getString("source_path"), rs.getString("source_anchor"),
                RelationshipType.valueOf(rs.getString("type")), rs.getString("token"), rs.getString("locator"),
                ReferenceResolution.Status.valueOf(rs.getString("status")),
                ReferenceResolution.Kind.valueOf(rs.getString("kind")),
                ReferenceResolution.Reason.valueOf(rs.getString("reason")), candidates, rs.getString("selected_id"));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("could not close relationship store", e);
        }
    }

    // Reconstruct the logical edge by joining each stored record_id back to its version-scoped
    // canonical key on the records table (Story 2.4). The store persists ids, not keys, but reads
    // return keys so a submitted logical edge round-trips unchanged (NFR-13).
    private static final String SELECT_EDGE_JOIN =
            "SELECT e.edge_id AS edge_id, e.sdk_version AS sdk_version, e.type AS type, "
                    + "s.canonical_key AS source_key, t.canonical_key AS target_key "
                    + "FROM edges e "
                    + "JOIN records s ON s.record_id = e.source_id AND s.sdk_version = e.sdk_version "
                    + "JOIN records t ON t.record_id = e.target_id AND t.sdk_version = e.sdk_version";

    private static StoredRelationshipEdge read(ResultSet rs) throws SQLException {
        String edgeId = rs.getString("edge_id");
        RelationshipEdge edge = new RelationshipEdge(
                rs.getString("source_key"),
                rs.getString("target_key"),
                RelationshipType.valueOf(rs.getString("type")),
                rs.getString("sdk_version"));
        return new StoredRelationshipEdge(edgeId, edge);
    }

    /** The stored {@code record_id} for a version-scoped canonical key, or {@code null} if none. */
    private String resolveRecordId(String sdkVersion, String canonicalKey) {
        String sql = "SELECT record_id FROM records WHERE sdk_version = ? AND canonical_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sdkVersion);
            ps.setString(2, canonicalKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("record_id") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not resolve endpoint record id for key " + canonicalKey, e);
        }
    }

    // SQLite result codes (see sqlite.org/rescode.html). getErrorCode() carries the primary code
    // (19 = SQLITE_CONSTRAINT) or, when the driver surfaces it, the extended code that pinpoints which
    // constraint failed.
    private static final int SQLITE_CONSTRAINT = 19;
    private static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555; // 19 | (6 << 8)
    private static final int SQLITE_CONSTRAINT_UNIQUE = 2067;     // 19 | (8 << 8)

    /**
     * Whether a failed {@code INSERT} is a {@code UNIQUE(identity)} violation — a real identity clash —
     * as opposed to a genuine storage error. Uses the SQLite result code rather than scanning the
     * (driver-text/locale-dependent) message. A primary-key ({@code edge_id}) collision is explicitly
     * <em>not</em> treated as a clash: it cannot occur here because {@link #put} checks
     * {@link #findByEdgeId} first, so if it ever surfaced it is a real error and must propagate.
     */
    private static boolean isIdentityConstraintViolation(SQLException e) {
        int code = e.getErrorCode();
        if (code == SQLITE_CONSTRAINT_UNIQUE) {
            return true;
        }
        if (code == SQLITE_CONSTRAINT_PRIMARYKEY) {
            return false;
        }
        // Only the primary constraint code is available (no extended detail): our INSERT is guarded by
        // findByEdgeId, so a constraint failure here can only be the identity UNIQUE index.
        return (code & 0xFF) == SQLITE_CONSTRAINT;
    }
}
