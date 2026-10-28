package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.cli.*;
import com.luciadmcp.knowledge.config.ApplicationConfig;
import com.luciadmcp.knowledge.mcp.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.luciadmcp.knowledge.mcp.Wire.map;

class MvpConnectionTest {
    @TempDir Path temp;
    static final String SUBTYPE="<html><body class=\"class-declaration-page\"><h1 class=\"title\">Class U</h1><section class=\"class-description\" id=\"type-doc\">"
        +"<div class=\"type-signature\">public class U extends <a href=\"T.html\">T</a></div><div class=\"block\"><p>Subtype of T.</p></div></section>"
        +"<section class=\"method-details\"><section class=\"detail\" id=\"make()\"><h3>make</h3><div class=\"member-signature\"><a href=\"T.html\">T</a> make()</div>"
        +"<div class=\"block\"><p>Makes a T.</p></div></section></section></body></html>";
    PipelineResult ingest(Path root) throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(root);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        Files.writeString(args.javadoc().resolve("p/U.html"),SUBTYPE);
        Files.createDirectories(args.samples().resolve("demo"));
        Files.writeString(args.samples().resolve("demo/Spinner.java"),"package demo;\nimport p.T;\n/** Spins a widget. */\npublic class Spinner {\n  void spinWidget(T t) {\n    t.run();\n    t.run();\n  }\n}\n");
        // This clean protocol specimen has no empty link target; separate test covers the stored/wire gap.
        try(var files=Files.walk(args.javadoc())) { for(Path p:files.filter(Files::isRegularFile).toList())Files.writeString(p,Files.readString(p).replace("<a href=\"\">Empty</a>","Empty")); }
        var result=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);
        assertFalse(result.aborted(),result.failures().toString());
        StructuredDocumentationRoundtripTest.removeInputs(args.javadoc());return result;
    }
    static Map<String,Object> request(Object... values) { var r=map("contractVersion","read-tools/2");for(int i=0;i<values.length;i+=2)r.put((String)values[i],values[i+1]);return r; }
    static Map<String,Object> call(io.modelcontextprotocol.client.McpSyncClient client,String name,Map<String,Object> request) {
        var result=client.callTool(new McpSchema.CallToolRequest(name,request));
        Map<String,Object> body=Wire.JSON.convertValue(result.structuredContent(),Map.class);
        var text=(McpSchema.TextContent)result.content().getFirst();
        assertEquals(new String(Wire.bytes(body)),new String(Wire.bytes(Wire.parse(text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        assertTrue(Wire.bytes(result).length<=1048576);
        Wire.validate(name+".response",body);assertNull(body.get("error"),body.toString());return body;
    }
    @Test void publishedImportSurvivesDeletionAndRealSdkConnection() throws Exception {
        var result=ingest(temp.resolve("input"));int port;try(var socket=new java.net.ServerSocket(0)) { port=socket.getLocalPort(); }
        var config=new ApplicationConfig(temp.resolve("index"),"synthetic",port,false);var repo=new BundleRepository(config);
        try(var writer=BundleRepository.writer(config.indexRoot())) { repo.publish(result);repo.activate("1",result.manifest().buildId()); }
        try(var service=new BundleRepository.Serving(repo);var endpoint=new McpEndpoint(port,false,(name,input)-> { try(var lease=service.lease()) { return lease.tools().call(name,input); } })) {
            endpoint.start();
            var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp").build();
            try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
                var initialized=client.initialize();
                assertNotNull(initialized.instructions());
                var opening=initialized.instructions().substring(0,Math.min(512,initialized.instructions().length()));
                for(var tool:ReadTools.NAMES)assertTrue(opening.contains(tool),opening);
                assertTrue(opening.contains("read-tools/2"),opening);
                var tools=client.listTools().tools();
                assertEquals(ReadTools.NAMES,tools.stream().map(McpSchema.Tool::name).toList());
                assertEquals(6,tools.stream().map(McpSchema.Tool::description).distinct().count());
                for(var tool:tools)assertFalse(tool.description().isBlank());
                var resolved=call(client,"resolve_symbol",request("symbol","p.T#run()","targetSdkVersion","future"));assertEquals("EXACT",resolved.get("proof"));assertEquals("1",resolved.get("sdkVersion"));assertEquals(List.of(),resolved.get("versionNotices"));
                var matches=(List<Map<String,Object>>)((Map<?,?>)resolved.get("data")).get("matches");String id=(String)matches.getFirst().get("recordId");
                var fetched=call(client,"get_record",request("recordIds",List.of(id,"not-found"),"includeContent",true));
                var records=(List<Map<String,Object>>)((Map<?,?>)fetched.get("data")).get("records");assertEquals(2,records.size());assertEquals("NOT_FOUND",records.get(1).get("status"));
                assertTrue(Wire.JSON.valueToTree(records.getFirst()).toString().contains("run();"));
                var searched=call(client,"search_knowledge",request("query","cache","targetSdkVersion","older"));assertFalse(((List<?>)((Map<?,?>)searched.get("data")).get("hits")).isEmpty());
                try(var lease=service.lease()) { assertEquals(new String(Wire.bytes(searched)),new String(Wire.bytes(lease.tools().call("search_knowledge",request("query","cache","targetSdkVersion","older"))))); }
                var supertypes=call(client,"get_relationships",request("seed","p.U","types",List.of("EXTENDS_CLASS")));
                assertEquals("EXACT",supertypes.get("proof"));
                var edges=(List<Map<String,Object>>)((Map<?,?>)supertypes.get("data")).get("edges");
                assertTrue(edges.stream().anyMatch(e->e.get("sourceId").equals("p.U") && e.get("targetId").equals("p.T") && e.get("type").equals("EXTENDS_CLASS")),edges.toString());
                var subtypes=call(client,"get_relationships",request("seed","p.T","direction","IN"));
                assertTrue(((List<Map<String,Object>>)((Map<?,?>)subtypes.get("data")).get("edges")).stream().anyMatch(e->e.get("sourceId").equals("p.U")),subtypes.toString());
                var unknownSeed=call(client,"get_relationships",request("seed","q.Missing"));
                assertEquals("UNKNOWN_IN_SCOPE",unknownSeed.get("proof"));
                var checked=call(client,"check_symbols",request("symbols",List.of("p.T","p.T#run()","p.Missing")));
                var verdicts=(List<Map<String,Object>>)((Map<?,?>)checked.get("data")).get("verdicts");
                assertEquals(List.of("EXACT","EXACT","UNKNOWN_IN_SCOPE"),verdicts.stream().map(v->v.get("proof")).toList());
                assertNotNull(verdicts.getFirst().get("selectedRecordId"));
                assertEquals("UNKNOWN_IN_SCOPE",checked.get("proof"));
                var status=call(client,"index_status",request());
                var data=(Map<String,Object>)status.get("data");
                assertEquals("ACTIVE",data.get("state"));assertEquals("1",data.get("sdkVersion"));assertEquals(result.manifest().buildId(),data.get("buildId"));
                assertNotNull(((Map<?,?>)data.get("coverage")).get("records"),data.toString());
                var example=call(client,"search_knowledge",request("query","spinWidget"));
                var exampleHits=(List<Map<String,Object>>)((Map<?,?>)example.get("data")).get("hits");
                var method=exampleHits.stream().filter(h->"EXAMPLE".equals(h.get("recordType"))).findFirst().orElseThrow(()->new AssertionError(exampleHits.toString()));
                var cited=(Map<String,Object>)((Map<String,Object>)((List<?>)method.get("citations")).getFirst()).get("locator");
                assertEquals("demo/Spinner.java",cited.get("relativePath"));assertNotNull(cited.get("lines"));
                var exampleRecord=call(client,"get_record",request("recordIds",List.of(method.get("recordId")),"includeContent",true));
                assertTrue(Wire.JSON.valueToTree(exampleRecord).toString().contains("t.run();"),exampleRecord.toString());
                var usedBy=call(client,"get_relationships",request("seed","p.T","direction","IN","types",List.of("CALLS","USES_TYPE")));
                var usage=(List<Map<String,Object>>)((Map<?,?>)usedBy.get("data")).get("edges");
                assertTrue(usage.stream().anyMatch(e->e.get("sourceId").equals(method.get("recordId")) && e.get("type").equals("USES_TYPE") && e.get("targetId").equals("p.T")),usage.toString());
                var calls=call(client,"get_relationships",request("seed",method.get("recordId"),"types",List.of("CALLS")));
                assertEquals(List.of("p.T#run()"),((List<Map<String,Object>>)((Map<?,?>)calls.get("data")).get("edges")).stream().map(e->e.get("targetId")).toList());
                var invalid=client.callTool(new McpSchema.CallToolRequest("search_knowledge",request("query","cache","sourceSdkVersion","missing")));
                var body=Wire.JSON.valueToTree(invalid.structuredContent());assertEquals("UNKNOWN_SDK",body.path("error").path("code").asText());Wire.validate("search_knowledge.response",body);
            }
            var http=HttpClient.newHttpClient();
            var hostile=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).header("Origin","https://foreign.invalid").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,hostile.statusCode());
        }
    }
}
