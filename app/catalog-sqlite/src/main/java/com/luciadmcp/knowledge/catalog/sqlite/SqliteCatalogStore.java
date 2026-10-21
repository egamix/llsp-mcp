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
