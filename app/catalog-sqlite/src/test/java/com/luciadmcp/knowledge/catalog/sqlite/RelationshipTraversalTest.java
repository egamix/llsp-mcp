package com.luciadmcp.knowledge.catalog.sqlite;

import static org.junit.jupiter.api.Assertions.*;
import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.domain.TraversalRequest.*;
import com.luciadmcp.knowledge.domain.TraversalResult.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelationshipTraversalTest {
    @TempDir Path dir;
    private final byte[] key = new byte[32];
    private void nodes(Path db, String sdk, String... keys) {
        try (var catalog = new SqliteCatalogStore(db)) {
            for (String name : keys) catalog.put(new CatalogRecord(sdk,name,"TYPE",name,name,"Synthetic.html",""));
        }
    }
    private void edges(Path db, String... pairs) {
        try (var store = new SqliteRelationshipStore(db)) {
            for (String pair : pairs) store.put(new RelationshipEdge(pair.substring(0,1),pair.substring(1),RelationshipType.SEE_ALSO,"1"));
        }
    }
    private SqliteRelationshipStore serving(Path db) { return new SqliteRelationshipStore(db,"trusted-build",new RelationshipScope("1"),key); }
    private TraversalLimits limits(int depth, int nodes, int rows, int items, int page, int steps) {
        return new TraversalLimits(depth,nodes,rows,items,page,5_000_000_000L,steps,1_048_576);
    }
    private TraversalRequest recursive(TraversalLimits limits) { return new TraversalRequest("A",Mode.RECURSIVE,Direction.OUT,Set.of(),limits,""); }
    private Set<String> nodeKeys(TraversalResult r) {
        Set<String> result = new HashSet<>();
        for (var item : r.items()) if (item.nodeKey() != null) result.add(item.nodeKey());
        return result;
    }
    @Test void chainDirectionsTypesIsolatedAndMissing() {
        Path db = dir.resolve("chain.db"); nodes(db,"1","A","B","C","I"); nodes(db,"2","A","Z");
        edges(db,"AB","BC");
        try (var old = new SqliteRelationshipStore(db)) {
            old.put(new RelationshipEdge("A","Z",RelationshipType.SEE_ALSO,"2"));
            old.put(new RelationshipEdge("A","C",RelationshipType.RETURN_TYPE,"1"));
            assertThrows(IllegalStateException.class, () -> old.traverse(new TraversalRequest("A")));
        }
        try (var store = serving(db)) {
            var direct = store.traverse(new TraversalRequest("A"));
            assertEquals(Set.of("B","C"),nodeKeys(direct));
            assertEquals(1,direct.appliedLimits().depth());
            assertEquals(Cutoff.NONE,direct.cutoff());
            var filtered = store.traverse(new TraversalRequest("A",null,null,Set.of(RelationshipType.SEE_ALSO),null,null));
            assertEquals(Set.of("B"),nodeKeys(filtered)); assertEquals(2,filtered.scannedRows());
            var incoming = store.traverse(new TraversalRequest("B",null,Direction.IN,null,null,null));
            assertEquals(Set.of("A"),nodeKeys(incoming));
            var both = store.traverse(new TraversalRequest("B",null,Direction.BOTH,null,null,null));
            assertEquals(Set.of("A","C"),nodeKeys(both));
            assertEquals(Status.SEED_NOT_FOUND,store.traverse(new TraversalRequest("Z")).status());
            var isolated = store.traverse(new TraversalRequest("I"));
            assertEquals(1,isolated.visitedNodes()); assertEquals(0,isolated.windowItems());
            assertEquals(CountRelation.EXACT,isolated.countRelation());
            assertThrows(IllegalStateException.class, () -> store.put(new RelationshipEdge("A","B",RelationshipType.RETURN_TYPE,"1")));
        }
    }
    @Test void cyclesDiamondSelfLoopStableAcrossInsertionAndReopen() {
        var query = recursive(limits(8,100,500,100,50,1_000_000));
        List<Item> reference = null;
        for (int reverse = 0; reverse < 2; reverse++) {
            Path db = dir.resolve("cycle" + reverse + ".db"); nodes(db,"1","A","B","C","D");
            String[] pairs = {"AA","AB","AC","BD","CD","DA"};
            if (reverse == 1) Collections.reverse(Arrays.asList(pairs)); edges(db,pairs);
            try (var store = serving(db)) {
                var result = store.traverse(query);
                assertEquals(Status.OK,result.status()); assertEquals(4,result.visitedNodes());
                assertEquals(9,result.windowItems()); assertEquals(6,result.scannedRows());
                assertEquals(Cutoff.NONE,result.cutoff());
                assertEquals(result.items().size(),new HashSet<>(result.items()).size());
                assertFalse(nodeKeys(result).contains("A"));
                if (reference == null) reference = result.items(); else assertEquals(reference,result.items());
            }
            try (var reopened = serving(db)) { assertEquals(reference,reopened.traverse(query).items()); }
        }
    }
    @Test void depthAndEveryWorkBoundaryAreHonestAndAtomic() {
        Path db = dir.resolve("bounds.db"); nodes(db,"1","A","B","C"); edges(db,"AB","BC");
        try (var store = serving(db)) {
            var depth = store.traverse(recursive(limits(1,100,100,100,50,1_000_000)));
            assertEquals(Set.of("B"),nodeKeys(depth)); assertEquals(Cutoff.DEPTH,depth.cutoff());
            var exactDepth = store.traverse(recursive(limits(2,3,100,4,50,1_000_000)));
            assertEquals(Cutoff.NONE,exactDepth.cutoff()); assertEquals(CountRelation.EXACT,exactDepth.countRelation());
            assertEquals(4,exactDepth.windowItems());
            var nodes = store.traverse(recursive(limits(8,1,100,100,50,1_000_000)));
            assertEquals(Cutoff.VISITED_NODES,nodes.cutoff()); assertEquals(0,nodes.windowItems());
            var items = store.traverse(recursive(limits(8,100,100,1,50,1_000_000)));
            assertEquals(Cutoff.RESULT_ITEMS,items.cutoff()); assertEquals(1,items.visitedNodes());
            var rows = store.traverse(recursive(limits(8,100,1,100,50,1_000_000)));
            assertEquals(Cutoff.ADJACENCY_ROWS,rows.cutoff()); assertEquals(1,rows.scannedRows());
            assertEquals(CountRelation.LOWER_BOUND,rows.countRelation());
            var steps = store.traverse(recursive(limits(8,100,100,100,50,1)));
            assertEquals(Status.EXECUTION_LIMIT,steps.status()); assertEquals("",steps.continuation());
            assertEquals(Status.OK,store.traverse(new TraversalRequest("A")).status());
        }
    }
    @Test void bothChargesDuplicateRowsAndEmitsEdgesOnce() {
        Path db = dir.resolve("both.db"); nodes(db,"1","A","B"); edges(db,"AA","AB","BA");
        try (var store = serving(db)) {
            var result = store.traverse(new TraversalRequest("A",Mode.RECURSIVE,Direction.BOTH,null,limits(3,100,100,100,50,1_000_000),null));
            assertEquals(6,result.scannedRows()); assertEquals(4,result.windowItems());
            assertEquals(3,result.items().stream().filter(i -> i.edge() != null).count());
        }
    }
    @Test void cancellationAndDeadlineNeedNoSleeps() throws Exception {
        Path db = dir.resolve("time.db"); nodes(db,"1","A","B"); edges(db,"AB");
        try (var store = serving(db)) {
            Thread.currentThread().interrupt();
            try { assertEquals(Status.EXECUTION_LIMIT,store.traverse(new TraversalRequest("A")).status()); }
            finally { Thread.interrupted(); }
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            AtomicLong time = new AtomicLong();
            var engine = new BoundedRelationshipTraversal(connection,"build",new RelationshipScope("1"),key,
                    () -> time.getAndAdd(1_000_000_000L));
            var result = engine.traverse(new TraversalRequest("A"));
            assertEquals(Status.EXECUTION_LIMIT,result.status()); assertEquals("",result.continuation());
        }
    }
    @Test void longEscapedStoredKeysAndHighDegreeStayBounded() {
        Path db = dir.resolve("payload.db"); String longKey = "\u0001\"\\界".repeat(50_000);
        nodes(db,"1","A",longKey);
        try (var store = new SqliteRelationshipStore(db)) { store.put(new RelationshipEdge("A",longKey,RelationshipType.SEE_ALSO,"1")); }
        try (var store = serving(db)) {
            var result = store.traverse(new TraversalRequest("A"));
            assertEquals(Cutoff.RESPONSE_BYTES,result.cutoff()); assertEquals(1,result.visitedNodes());
            assertEquals(0,result.windowItems());
            assertTrue(result.toJson().getBytes(StandardCharsets.UTF_8).length <= result.appliedLimits().responseBytes());
        }
        Path high = dir.resolve("degree.db"); nodes(high,"1","A");
        for (int i = 0; i < 120; i++) {
            String node = "\u0001\"\\界".repeat(180) + i; nodes(high,"1",node);
            try (var store = new SqliteRelationshipStore(high)) { store.put(new RelationshipEdge("A",node,RelationshipType.SEE_ALSO,"1")); }
        }
        try (var store = serving(high)) {
            var result = store.traverse(new TraversalRequest("A",null,null,null,limits(1,1000,7,1000,50,1_000_000),null));
            assertEquals(7,result.scannedRows()); assertEquals(Cutoff.ADJACENCY_ROWS,result.cutoff());
            assertTrue(result.toJson().getBytes(StandardCharsets.UTF_8).length <= result.appliedLimits().responseBytes());
        }
    }
}
