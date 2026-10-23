package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.ContractVersions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** Read-only preflight runs before a writable connection or DDL. Unsupported files remain unchanged. */
final class SchemaCompatibility {
    private SchemaCompatibility() {}
    static void check(Path path) {
        if (!Files.exists(path)) return;
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath().toUri() + "?mode=ro");
             Statement s = db.createStatement()) {
            int version;
            try (ResultSet r = s.executeQuery("PRAGMA user_version")) { r.next(); version = r.getInt(1); }
            if (version != ContractVersions.SCHEMA)
                throw new IllegalStateException("UNSUPPORTED_SCHEMA: rebuild required (found " + version + ")");
            try (ResultSet r = s.executeQuery("SELECT CASE WHEN octet_length(identity_version)<=128 THEN identity_version ELSE NULL END FROM contract_metadata WHERE singleton=1")) {
                if (!r.next() || !ContractVersions.IDENTITY.equals(r.getString(1)))
                    throw new IllegalStateException("INCOMPATIBLE_IDENTITY: rebuild required");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("UNSUPPORTED_SCHEMA: rebuild required", e);
        }
    }
    static void initialize(Connection db) throws SQLException {
        initialize(db, false);
    }

    static void initialize(Connection db, boolean includeCatalogRecords) throws SQLException {
        if (!db.getAutoCommit()) throw new SQLException("schema initialization requires an idle connection");
        db.setAutoCommit(false);
        boolean transactionClosed = false;
        try {
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA user_version=" + ContractVersions.SCHEMA);
                if (includeCatalogRecords) s.execute(SqliteCatalogStore.CREATE_RECORDS_TABLE);
                s.execute(SqliteDocumentationStore.DOCUMENTS_DDL);
                s.execute(SqliteDocumentationStore.SECTIONS_DDL);
                s.execute("CREATE TABLE IF NOT EXISTS knowledge_records (record_id TEXT PRIMARY KEY NOT NULL, sdk_product TEXT NOT NULL, sdk_version TEXT NOT NULL, record_type TEXT NOT NULL, knowledge_kind TEXT NOT NULL, symbol_coordinate TEXT, locator_kind TEXT NOT NULL CHECK(locator_kind IN ('SOURCE','ABSENT','EXTERNAL')), source_path TEXT NOT NULL, source_anchor TEXT, source_first_line INTEGER, source_last_line INTEGER, source_section TEXT, absent_reason TEXT NOT NULL, approval TEXT NOT NULL CHECK(approval IN ('CANDIDATE','APPROVED')), owner TEXT NOT NULL, provenance TEXT NOT NULL, approval_evidence TEXT NOT NULL, applicability TEXT NOT NULL, narrative_kind TEXT CHECK(narrative_kind IN ('PACKAGE','OVERVIEW')))");
                s.execute("CREATE TABLE IF NOT EXISTS knowledge_dependencies (sdk_version TEXT NOT NULL, source_id TEXT NOT NULL, target_id TEXT NOT NULL, dependency_type TEXT NOT NULL CHECK(dependency_type IN ('REQUIRED','OPTIONAL','SUPERSEDES')), PRIMARY KEY(sdk_version,source_id,target_id,dependency_type))");
                s.execute("CREATE TABLE IF NOT EXISTS build_metadata (build_id TEXT PRIMARY KEY NOT NULL, sdk_product TEXT NOT NULL, sdk_version TEXT NOT NULL, profile TEXT NOT NULL, input_digest TEXT NOT NULL, recipe_digest TEXT NOT NULL, runtime_digest TEXT NOT NULL, capabilities TEXT NOT NULL, validation_reasons TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS contract_metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), identity_version TEXT NOT NULL, runtime_fingerprint TEXT NOT NULL)");
            }
            try (PreparedStatement s = db.prepareStatement("INSERT OR IGNORE INTO contract_metadata VALUES(1,?,?)")) {
                s.setString(1, ContractVersions.IDENTITY); s.setString(2, com.luciadmcp.knowledge.domain.ContentHashes.runtimeFingerprint()); s.executeUpdate();
            }
            db.commit();
            transactionClosed = true;
        } catch (SQLException | RuntimeException e) {
            try {
                db.rollback();
                transactionClosed = true;
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            if (transactionClosed) db.setAutoCommit(true);
        }
    }
}
