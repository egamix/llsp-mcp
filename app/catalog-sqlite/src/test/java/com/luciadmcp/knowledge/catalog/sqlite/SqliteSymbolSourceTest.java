package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SqliteSymbolSourceTest {
    @TempDir Path temp;
    private Path catalog(String name) {
        Path path=temp.resolve(name);
        try(var store=new SqliteCatalogStore(path);var edges=new SqliteRelationshipStore(path)) {
            store.bindManifest(new IndexManifest("b-"+"a".repeat(64),"2023.5","synthetic"));
            store.put(new CatalogRecord("2023.5","test.Widget","TYPE","test.Widget","Widget","test/Widget.html",""));
        }
        return path;
    }
    @Test void missingNeverCreatesAndMalformedNeverChangesBytes() throws Exception {
        Path missing=temp.resolve("absent/does-not-exist.sqlite");
        assertEquals(SymbolSourceException.Code.BUILD_UNAVAILABLE,assertThrows(SymbolSourceException.class,()->new SqliteSymbolSource(missing)).code());
        assertFalse(Files.exists(missing.getParent()));
        Path junk=temp.resolve("junk.sqlite");Files.writeString(junk,"Not SQLite");byte[] before=Files.readAllBytes(junk);
        assertEquals(SymbolSourceException.Code.BUILD_INVALID,assertThrows(SymbolSourceException.class,()->new SqliteSymbolSource(junk)).code());assertArrayEquals(before,Files.readAllBytes(junk));
    }
    @Test void schemaIdentityRuntimeBuildAndScopeFailuresNeverMutate() throws Exception {
        int n=0;
        for(String sql:List.of("PRAGMA user_version=999","UPDATE contract_metadata SET identity_version='other'","UPDATE contract_metadata SET runtime_fingerprint='other'","UPDATE build_metadata SET build_id='wrong'","UPDATE build_metadata SET sdk_version='unknown'","UPDATE records SET record_id='wrong'","UPDATE build_metadata SET validation_reasons='[\"invalid\"]'","UPDATE records SET sdk_version='2023.1'","DELETE FROM build_metadata","UPDATE build_metadata SET capabilities='other'","UPDATE records SET relative_path='../escape.html'")) {
            Path path=catalog("invalid"+(n++)+".sqlite");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var s=db.createStatement()) { s.execute(sql); }
            byte[] before=Files.readAllBytes(path);
            assertEquals(SymbolSourceException.Code.BUILD_INVALID,assertThrows(SymbolSourceException.class,()->new SqliteSymbolSource(path),sql).code());
            assertArrayEquals(before,Files.readAllBytes(path),sql);
        }
    }
    @Test void pinnedSnapshotSurvivesConcurrentWalMutationAndEnforcesBatchBounds() throws Exception {
        Path path=catalog("snapshot.sqlite");
        try(var writer=DriverManager.getConnection("jdbc:sqlite:"+path);var s=writer.createStatement()) {
            try(var mode=s.executeQuery("PRAGMA journal_mode=WAL")) { assertTrue(mode.next()); }
            try(var source=new SqliteSymbolSource(path)) {
                s.executeUpdate("DELETE FROM records");
                assertTrue(source.canonical("test.Widget").isPresent());
                assertEquals(1,source.after("",1).size());
                assertThrows(IllegalArgumentException.class,()->source.after("",257));
            }
        }
    }
}

