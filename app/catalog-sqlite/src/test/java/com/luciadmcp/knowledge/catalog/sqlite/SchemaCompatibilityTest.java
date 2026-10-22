package com.luciadmcp.knowledge.catalog.sqlite;
import org.junit.jupiter.api.Test;
import com.luciadmcp.knowledge.domain.*;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class SchemaCompatibilityTest {
    @Test void provisionalAndFutureStoresAreRejectedWithoutChangingBytes(@TempDir Path root) throws Exception {
        for (int version : new int[]{0,999}) {
            Path file = root.resolve("store-" + version + ".sqlite");
            try (var db = DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE provisional(value TEXT)"); s.execute("PRAGMA user_version="+version);
            }
            byte[] before=Files.readAllBytes(file);
            assertThrows(IllegalStateException.class,()->new SqliteCatalogStore(file));
            assertArrayEquals(before,Files.readAllBytes(file));
            assertThrows(IllegalStateException.class,()->new SqliteRelationshipStore(file));
            assertArrayEquals(before,Files.readAllBytes(file));
        }
    }
    @Test void newSchemaReopens(@TempDir Path root) {
        Path file=root.resolve("new.sqlite");
        try(var ignored=new SqliteCatalogStore(file)) {}
        try(var ignored=new SqliteCatalogStore(file);var edges=new SqliteRelationshipStore(file)) {}
    }
    @Test void persistedMetadataCorruptionBlocksCliAndPinnedServing(@TempDir Path root) throws Exception {
        Path file=root.resolve("build.sqlite");
        String build="b-"+"a".repeat(64);
        try(var catalog=new SqliteCatalogStore(file)) {
            catalog.bindManifest(new com.luciadmcp.knowledge.domain.IndexManifest(build,"2024.0",com.luciadmcp.knowledge.domain.ContentHashes.runtimeFingerprint()));
            catalog.put(new com.luciadmcp.knowledge.domain.CatalogRecord("2024.0","a.T","TYPE","a.T","T","a/T.html",""));
        }
        try(var edges=new SqliteRelationshipStore(file)) {}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) { s.execute("UPDATE build_metadata SET runtime_digest='corrupt'"); }
        try(var catalog=new SqliteCatalogStore(file)) {
            var validation=catalog.validateBuild(new com.luciadmcp.knowledge.domain.StandardBuildValidator(),com.luciadmcp.knowledge.domain.BuildValidator.Operation.CLI,build,"2024.0");
            assertFalse(validation.valid());
            assertTrue(validation.reasons().contains(com.luciadmcp.knowledge.domain.BuildValidator.Reason.INCOMPATIBLE_RUNTIME));
            assertThrows(IllegalStateException.class,()->catalog.bindManifest(new com.luciadmcp.knowledge.domain.IndexManifest(build,"2024.0","runtime")));
        }
        assertThrows(IllegalStateException.class,()->new SqliteRelationshipStore(file,build,new com.luciadmcp.knowledge.domain.RelationshipScope("2024.0"),new byte[32]));
    }
    @Test void unrelatedOversizedCatalogRowIsRejectedBeforeTextFetch(@TempDir Path root) throws Exception {
        Path file=root.resolve("oversized.sqlite");String build="b-"+"a".repeat(64);
        try(var catalog=new SqliteCatalogStore(file)) { catalog.bindManifest(new com.luciadmcp.knowledge.domain.IndexManifest(build,"2024.0","runtime")); catalog.put(new com.luciadmcp.knowledge.domain.CatalogRecord("2024.0","safe.T","TYPE","safe.T","T","safe/T.html","")); }
        try(var edges=new SqliteRelationshipStore(file)) {}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) { s.execute("UPDATE records SET simple_name=printf('%.*c', 10000000, 'X')"); }
        var failure=assertThrows(IllegalStateException.class,()->new SqliteRelationshipStore(file,build,new com.luciadmcp.knowledge.domain.RelationshipScope("2024.0"),new byte[32]));
        assertTrue(failure.getCause().getMessage().contains("WORK_LIMIT"));
    }
    @Test void mixedScopeCatalogAndForeignEdgesRejectCliAndPinnedStartupIdentically(@TempDir Path root) throws Exception {
        for (boolean foreignCatalog : new boolean[] {true, false}) {
            Path file = root.resolve(foreignCatalog ? "foreign-record.sqlite" : "foreign-edge.sqlite");
            String build = "b-" + "a".repeat(64);
            String firstId, secondId;
            try (var catalog = new SqliteCatalogStore(file)) {
                catalog.bindManifest(new IndexManifest(build, "2024.0", ContentHashes.runtimeFingerprint()));
                firstId = catalog.put(new CatalogRecord("2024.0", "a.First", "TYPE", "a.First", "First", "a/First.html", "")).recordId();
                secondId = catalog.put(new CatalogRecord("2024.0", "a.Second", "TYPE", "a.Second", "Second", "a/Second.html", "")).recordId();
                if (foreignCatalog) catalog.put(new CatalogRecord("2025.0", "a.Foreign", "TYPE", "a.Foreign", "Foreign", "a/Foreign.html", ""));
            }
            try (var edges = new SqliteRelationshipStore(file)) {}
            if (!foreignCatalog) {
                try (var db = DriverManager.getConnection("jdbc:sqlite:" + file);
                        var insert = db.prepareStatement("INSERT INTO edges VALUES(?,?,?,?,?,?)")) {
                    insert.setString(1, ContentHashes.relationshipHash("2025.0", firstId, RelationshipType.SEE_ALSO, secondId, ""));
                    insert.setString(2, "2025.0"); insert.setString(3, firstId); insert.setString(4, "SEE_ALSO");
                    insert.setString(5, secondId); insert.setString(6, ""); insert.executeUpdate();
                }
            }
            BuildValidator.Result cli;
            try (var catalog = new SqliteCatalogStore(file)) {
                cli = catalog.validateBuild(new StandardBuildValidator(), BuildValidator.Operation.CLI, build, "2024.0");
                assertEquals(List.of(BuildValidator.Reason.INVALID_SCOPE), cli.reasons());
                assertEquals(cli, catalog.validateBuild(new StandardBuildValidator(), BuildValidator.Operation.STARTUP, build, "2024.0"));
            }
            var startup = assertThrows(IllegalStateException.class,
                    () -> new SqliteRelationshipStore(file, build, new RelationshipScope("2024.0"), new byte[32]));
            assertEquals("BUILD_INVALID: " + cli.reasons(), startup.getCause().getMessage());
        }
    }
}
