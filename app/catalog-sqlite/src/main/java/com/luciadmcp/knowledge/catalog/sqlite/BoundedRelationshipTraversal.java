package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.domain.TraversalResult.*;
import java.sql.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.sqlite.ProgressHandler;

/** Indexed BFS. Each statement fetches at most one adjacency row, including repeated edges. */
final class BoundedRelationshipTraversal {
    private final Connection db;
    private final String build;
    private final RelationshipScope scope;
    private final TraversalCursorCodec cursors;
    private final LongSupplier clock;
    BoundedRelationshipTraversal(Connection db, String build, RelationshipScope scope, byte[] key, LongSupplier clock) {
        this.db = db; this.build = build; this.scope = scope; this.cursors = new TraversalCursorCodec(key); this.clock = clock;
    }
    private record Node(String id, String key, int depth) {}
    private record Row(String id, String sourceId, String targetId, String sourceKey, String targetKey, String type) {}
    private static final class ExecutionLimit extends RuntimeException {}
    private long started;
    private TraversalLimits limits;
    private void check() {
        if (Thread.currentThread().isInterrupted() || clock.getAsLong() - started >= limits.executionNanos())
            throw new ExecutionLimit();
    }
    TraversalResult traverse(TraversalRequest q) {
        Objects.requireNonNull(q, "request");
        String binding = cursors.binding(build, scope.sdkVersion(), q);
        int offset = q.continuation().isEmpty() ? 0 : cursors.decode(q.continuation(), binding);
        if (offset < 0) return result(q, Status.INVALID_CONTINUATION, List.of(), 0, 0, 0, Cutoff.NONE, CountRelation.UNKNOWN, "");
        limits = q.limits(); started = clock.getAsLong();
        try {
            check();
            // A callback per VM instruction gives a strict total across seed and all adjacency SQL.
            ProgressHandler.setHandler(db, 1, new ProgressHandler() {
                int steps;
                @Override protected int progress() {
                    try { check(); return ++steps >= limits.sqlSteps() ? 1 : 0; }
                    catch (ExecutionLimit e) { return 1; }
                }
            });
            return run(q, binding, offset);
        } catch (ExecutionLimit e) {
            return result(q, Status.EXECUTION_LIMIT, List.of(), 0, 0, 0, Cutoff.NONE, CountRelation.UNKNOWN, "");
        } catch (SQLException e) {
            if ((e.getErrorCode() & 255) == 9 || (e.getErrorCode() & 255) == 5) // INTERRUPT/BUSY
                return result(q, Status.EXECUTION_LIMIT, List.of(), 0, 0, 0, Cutoff.NONE, CountRelation.UNKNOWN, "");
            throw new IllegalStateException("bounded traversal SQL failed", e);
        } finally {
            try { ProgressHandler.clearHandler(db); }
            catch (SQLException e) { throw new IllegalStateException("could not clear traversal guard", e); }
        }
    }
    private TraversalResult run(TraversalRequest q, String binding, int offset) throws SQLException {
        String seedId;
        // A seed is a catalog canonical key, or an example record id (example nodes are keyed by record id).
        try (PreparedStatement ps = db.prepareStatement("SELECT record_id FROM records WHERE sdk_version=? AND canonical_key=?"
                + " UNION ALL SELECT record_id FROM knowledge_records WHERE sdk_version=? AND record_id=? AND record_type='EXAMPLE' LIMIT 1")) {
            ps.setString(1, scope.sdkVersion()); ps.setString(2, q.seedKey()); ps.setString(3, scope.sdkVersion()); ps.setString(4, q.seedKey());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return result(q, Status.SEED_NOT_FOUND, List.of(), 0, 0, 0, Cutoff.NONE, CountRelation.EXACT, "");
                seedId = rs.getString(1);
            }
        }
        Set<String> visited = new HashSet<>(); visited.add(seedId);
        Set<String> edges = new HashSet<>();
        List<Item> window = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>(); queue.add(new Node(seedId, q.seedKey(), 0));
        int scanned = 0;
        // Worst-case JSON escaping is six bytes per UTF-16 code unit. Reserve metadata, scope,
        // normalized request, token and punctuation before any graph item is materialized.
        long bytes = 65_536;
        Cutoff cutoff = Cutoff.NONE;
        boolean depthBoundary = false;
        int depth = q.mode() == TraversalRequest.Mode.DIRECT ? 1 : limits.depth();
        outer: while (!queue.isEmpty()) {
            check(); Node node = queue.remove();
            String afterOut = "", afterIn = "";
            Row pendingOut = null, pendingIn = null;
            boolean outDone = q.direction() == TraversalRequest.Direction.IN;
            boolean inDone = q.direction() == TraversalRequest.Direction.OUT;
            while (true) {
                check();
                // Merge at most two indexed cursors. Charge fetched rows, including BOTH's duplicate
                // self/cycle rows, before type filtering or edge deduplication. No uncharged lookahead.
                if (pendingOut == null && !outDone) {
                    if (scanned == limits.adjacencyRows()) { cutoff = Cutoff.ADJACENCY_ROWS; break outer; }
                    pendingOut = seek(node.id(), afterOut, true);
                    if (pendingOut == null) outDone = true;
                    else { scanned++; afterOut = pendingOut.id(); }
                }
                if (pendingIn == null && !inDone) {
                    if (scanned == limits.adjacencyRows()) { cutoff = Cutoff.ADJACENCY_ROWS; break outer; }
                    pendingIn = seek(node.id(), afterIn, false);
                    if (pendingIn == null) inDone = true;
                    else { scanned++; afterIn = pendingIn.id(); }
                }
                if (pendingOut == null && pendingIn == null) break;
                Row row;
                if (pendingOut != null && (pendingIn == null || pendingOut.id().compareTo(pendingIn.id()) <= 0)) {
                    row = pendingOut; pendingOut = null;
                } else { row = pendingIn; pendingIn = null; }
                if (!q.types().isEmpty() && !q.types().contains(RelationshipType.valueOf(row.type()))) continue;
                if (edges.contains(row.id())) continue;
                if (row.sourceKey() == null || row.targetKey() == null) { cutoff = Cutoff.RESPONSE_BYTES; break outer; }
                String otherId = row.sourceId().equals(node.id()) ? row.targetId() : row.sourceId();
                String otherKey = row.sourceId().equals(node.id()) ? row.targetKey() : row.sourceKey();
                boolean newNode = !visited.contains(otherId);
                if (newNode && node.depth() >= depth) { depthBoundary = true; continue; }
                int cost = newNode ? 2 : 1;
                if (newNode && visited.size() == limits.visitedNodes()) { cutoff = Cutoff.VISITED_NODES; break outer; }
                if (window.size() + cost > limits.resultItems()) { cutoff = Cutoff.RESULT_ITEMS; break outer; }
                long itemBytes = 512L + 6L * (row.sourceKey().length() + row.targetKey().length() + row.type().length())
                        + 6L * scope.sdkVersion().length()
                        + (newNode ? 128L + 6L * otherKey.length() : 0);
                if (bytes + itemBytes > limits.responseBytes()) { cutoff = Cutoff.RESPONSE_BYTES; break outer; }
                bytes += itemBytes;
                if (newNode) {
                    visited.add(otherId); window.add(Item.node(otherKey));
                    if (q.mode() == TraversalRequest.Mode.RECURSIVE)
                        queue.add(new Node(otherId, otherKey, node.depth() + 1));
                }
                edges.add(row.id());
                window.add(Item.edge(new StoredRelationshipEdge(row.id(), new RelationshipEdge(
                        row.sourceKey(), row.targetKey(), RelationshipType.valueOf(row.type()), scope.sdkVersion()))));
            }
        }
        if (cutoff == Cutoff.NONE && depthBoundary && q.mode() == TraversalRequest.Mode.RECURSIVE) cutoff = Cutoff.DEPTH;
        check();
        if (offset > window.size() || (offset == window.size() && offset != 0))
            return result(q, Status.INVALID_CONTINUATION, List.of(), 0, 0, 0, Cutoff.NONE, CountRelation.UNKNOWN, "");
        int end = Math.min(window.size(), offset + limits.pageItems());
        String token = end < window.size() ? cursors.encode(binding, end) : "";
        return result(q, Status.OK, window.subList(offset, end), visited.size(), scanned, window.size(), cutoff,
                cutoff == Cutoff.NONE ? CountRelation.EXACT : CountRelation.LOWER_BOUND, token);
    }
    private Row seek(String id, String after, boolean out) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(adjacencySql(out))) {
            ps.setInt(1, limits.responseBytes() / 6); ps.setInt(2, limits.responseBytes() / 6); ps.setInt(3, limits.responseBytes() / 6);
            ps.setString(4, scope.sdkVersion()); ps.setString(5, id); ps.setString(6, after);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() ? null : new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(5),rs.getString(6),rs.getString(4));
            }
        }
    }
    static String adjacencySql(boolean out) {
        // octet_length(column) reads the stored byte count without loading oversized TEXT values.
        // Example-link sources (Story 3.2) are example records, keyed by their record id.
        return "SELECT e.edge_id,e.source_id,e.target_id,e.type,"
                + "CASE WHEN s.record_id IS NOT NULL THEN CASE WHEN octet_length(s.canonical_key)<=? THEN s.canonical_key END"
                + " ELSE CASE WHEN octet_length(x.record_id)<=? THEN x.record_id END END,"
                + "CASE WHEN octet_length(t.canonical_key)<=? THEN t.canonical_key END "
                + "FROM edges e INDEXED BY " + (out ? "edges_out" : "edges_in")
                + " LEFT JOIN records s ON s.record_id=e.source_id AND s.sdk_version=e.sdk_version"
                + " LEFT JOIN knowledge_records x ON x.record_id=e.source_id AND x.sdk_version=e.sdk_version AND x.record_type='EXAMPLE'"
                + " JOIN records t ON t.record_id=e.target_id AND t.sdk_version=e.sdk_version"
                + " WHERE e.sdk_version=? AND e." + (out ? "source_id" : "target_id") + "=? AND e.edge_id>?"
                + " AND (s.record_id IS NOT NULL OR x.record_id IS NOT NULL)"
                + " ORDER BY e.edge_id LIMIT 1";
    }
    private TraversalResult result(TraversalRequest q, Status status, List<Item> items, int visited,
            int scanned, int window, Cutoff cutoff, CountRelation relation, String token) {
        // Do not echo the incoming cursor into the response request metadata.
        var normalized = new TraversalRequest(q.seedKey(), q.mode(), q.direction(), q.types(), q.limits(), "");
        var result = new TraversalResult(build, scope, q.seedKey(), normalized, status, items, visited, scanned, window, cutoff, relation, token);
        if (status == Status.OK) {
            // Exercise the actual canonical serializer under the same deadline, before delivery.
            if (result.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > q.limits().responseBytes())
                throw new IllegalStateException("response admission accounting failed");
            check();
        }
        return result;
    }
}
