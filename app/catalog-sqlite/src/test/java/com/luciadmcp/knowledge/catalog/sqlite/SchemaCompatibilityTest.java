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
}
