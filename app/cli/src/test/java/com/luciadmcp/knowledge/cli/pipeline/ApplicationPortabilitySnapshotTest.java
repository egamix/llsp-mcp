package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.SqliteCatalogStore;
import com.luciadmcp.knowledge.catalog.sqlite.SqliteRelationshipStore;
import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.domain.*;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real application output, not an independent recipe oracle. Export is opt-in for platform comparison. */
class ApplicationPortabilitySnapshotTest {
    @Test void realPipelineProducesPortableLogicalSnapshot(@TempDir Path temporary) throws Exception {
        String first = snapshot(temporary.resolve("first"), false);
        String relocated = snapshot(temporary.resolve("relocated"), true);
        assertEquals(first, relocated, "relocation and input creation order must preserve every logical field");
        assertFalse(first.contains(temporary.toString()));
        String export = System.getenv("LUCIAD_CONTRACT_SNAPSHOT");
        if (export != null && !export.isBlank()) {
            Path destination = Path.of(export).toAbsolutePath();
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, first, StandardCharsets.UTF_8);
        }
    }

    private static String snapshot(Path root, boolean reverseCreation) throws Exception {
        Path javadoc = Files.createDirectories(root.resolve("javadoc/com/example"));
        String base = page("Base", "<section class='class-description'><div class='block'>Synthetic base.</div></section>");
        String widget = page("Widget", """
            <section class="class-description"><div class="block">Synthetic widget.</div>
            <div class="type-signature"><span class="modifiers">public class </span><span class="element-name">Widget</span>
            <span class="extends-implements">extends <a href="Base.html">Base</a></span></div></section>
            <section class="method-details" id="method-detail"><ul class="member-list">
            <li><section class="detail" id="read(a.Value)"><h3>read</h3>
            <div class="member-signature">public void read(a.Value value)</div><div class="block">Reads A.</div></section></li>
            <li><section class="detail" id="read(b.Value)"><h3>read</h3>
            <div class="member-signature">public void read(b.Value value)</div><div class="block">Reads B.</div></section></li>
            </ul></section>
            """);
        // Files.writeString uses explicit LF-only fixture bytes on every host.
        if (reverseCreation) {
            Files.writeString(javadoc.resolve("Widget.html"), widget, StandardCharsets.UTF_8);
            Files.writeString(javadoc.resolve("Base.html"), base, StandardCharsets.UTF_8);
        } else {
            Files.writeString(javadoc.resolve("Base.html"), base, StandardCharsets.UTF_8);
            Files.writeString(javadoc.resolve("Widget.html"), widget, StandardCharsets.UTF_8);
        }
        var arguments = new IngestArguments(root.resolve("javadoc"), Files.createDirectories(root.resolve("samples")),
                Files.createDirectories(root.resolve("guides")), root.resolve("unused-model"), root.resolve("out"),
                Optional.empty(), Optional.of("2024.0"));
        var result = IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE, arguments).runToResult(arguments);
        assertFalse(result.aborted(), result.failures().toString());
        assertTrue(result.failures().isEmpty(), result.failures().toString());
        assertFalse(result.activationOccurred(), "the production activation capability is still unimplemented");
        assertTrue(result.completedStages().contains(PipelineStage.VALIDATION));
        Map<String,Object> logical = new TreeMap<>();
        logical.put("manifest", result.manifest());
        logical.put("completedStages", result.completedStages().stream().map(Enum::name).sorted().toList());
        try (var catalog = new SqliteCatalogStore(result.buildContext().catalogDbPath())) {
            var rows = catalog.allRecords();
            assertEquals(4, rows.size());
            assertTrue(catalog.validateBuild(new StandardBuildValidator(), BuildValidator.Operation.CLI,
                    result.manifest().buildId(), "2024.0").valid());
            assertEquals(2, rows.stream().filter(r -> r.record().kind().equals("METHOD")).count());
            logical.put("catalogRows", rows);
            logical.put("symbols", rows.stream().map(r -> SymbolCoordinate.from(r.record()).serialize()).sorted().toList());
        }
        try (var relationships = new SqliteRelationshipStore(result.buildContext().catalogDbPath())) {
            var edges = relationships.allEdges();
            assertEquals(2, edges.size(), "one inheritance relation plus its inverse");
            logical.put("relationships", edges);
            var evidence = relationships.resolutions(new RelationshipScope("2024.0"));
            assertTrue(evidence.stream().anyMatch(r -> r.status() == ReferenceResolution.Status.EXACT));
            assertTrue(evidence.stream().anyMatch(r -> r.status() == ReferenceResolution.Status.UNRESOLVED));
            logical.put("referenceEvidence", evidence.stream().sorted(Comparator.comparing(Object::toString)).toList());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + result.buildContext().catalogDbPath())) {
            logical.put("schemaVersion", query(connection, "PRAGMA user_version"));
            logical.put("schemaMetadata", query(connection, "SELECT singleton,identity_version,runtime_fingerprint FROM contract_metadata ORDER BY singleton"));
            logical.put("buildMetadata", query(connection, "SELECT build_id,sdk_product,sdk_version,profile,input_digest,recipe_digest,runtime_digest,capabilities,validation_reasons FROM build_metadata ORDER BY build_id"));
        }
        String report = Files.readString(arguments.out().resolve(result.manifest().buildId()).resolve(IngestPipelineRunner.REPORT_FILE), StandardCharsets.UTF_8);
        assertTrue(report.contains("SKIPPED_UNSUPPORTED"));
        assertTrue(report.contains("\"activationOccurred\": false"));
        // Report has no timestamp and this successful fixture has no absolute diagnostic locations.
        // Preserve every actual report field verbatim, including unknown coverage and pending ratification.
        String prefix = json(logical);
        return prefix.substring(0, prefix.length()-1) + ",\"report\":" + report.strip() + "}\n";
    }

    private static String page(String name, String body) {
        return "<!doctype html><html><body class='class-declaration-page'><main><h1 class='title'>Class " + name + "</h1>" + body + "</main></body></html>\n";
    }
    private static List<List<String>> query(Connection db, String sql) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (var statement=db.createStatement(); var result=statement.executeQuery(sql)) {
            while (result.next()) {
                List<String> row=new ArrayList<>();
                for(int i=1;i<=result.getMetaData().getColumnCount();i++) row.add(result.getString(i));
                rows.add(row);
            }
        }
        return rows;
    }
    /** Small deterministic test serializer: records retain component order, maps retain sorted keys. */
    private static String json(Object value) throws Exception {
        if(value==null) return "null";
        if(value instanceof String text) return quote(text);
        if(value instanceof Enum<?> item) return quote(item.name());
        if(value instanceof Boolean || value instanceof Number) return value.toString();
        if(value instanceof Collection<?> values) {
            List<String> parts=new ArrayList<>();for(Object item:values) parts.add(json(item));
            return "["+String.join(",",parts)+"]";
        }
        if(value instanceof Map<?,?> values) {
            List<String> parts=new ArrayList<>();for(var entry:values.entrySet()) parts.add(quote(entry.getKey().toString())+":"+json(entry.getValue()));
            return "{"+String.join(",",parts)+"}";
        }
        if(value.getClass().isRecord()) {
            List<String> parts=new ArrayList<>();
            for(RecordComponent field:value.getClass().getRecordComponents()) parts.add(quote(field.getName())+":"+json(field.getAccessor().invoke(value)));
            return "{"+String.join(",",parts)+"}";
        }
        throw new IllegalArgumentException("unsupported snapshot value "+value.getClass());
    }
    private static String quote(String value) {
        StringBuilder text=new StringBuilder("\"");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\' || c=='\"') text.append('\\').append(c);
            else if(c<32) text.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
            else text.append(c);
        }
        return text.append('\"').toString();
    }
}
