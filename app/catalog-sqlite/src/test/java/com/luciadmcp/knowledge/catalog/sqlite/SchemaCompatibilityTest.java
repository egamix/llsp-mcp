package com.luciadmcp.knowledge.catalog.sqlite;
import org.junit.jupiter.api.Test;
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
}
