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
    PipelineResult ingest(Path root) throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(root);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
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
                client.initialize();assertEquals(ReadTools.NAMES,client.listTools().tools().stream().map(McpSchema.Tool::name).toList());
                var resolved=call(client,"resolve_symbol",request("symbol","p.T#run()","targetSdkVersion","future"));assertEquals("EXACT",resolved.get("proof"));assertEquals("1",resolved.get("sdkVersion"));assertEquals(List.of(),resolved.get("versionNotices"));
                var matches=(List<Map<String,Object>>)((Map<?,?>)resolved.get("data")).get("matches");String id=(String)matches.getFirst().get("recordId");
                var fetched=call(client,"get_record",request("recordIds",List.of(id,"not-found"),"includeContent",true));
                var records=(List<Map<String,Object>>)((Map<?,?>)fetched.get("data")).get("records");assertEquals(2,records.size());assertEquals("NOT_FOUND",records.get(1).get("status"));
                assertTrue(Wire.JSON.valueToTree(records.getFirst()).toString().contains("run();"));
                var searched=call(client,"search_knowledge",request("query","cache","targetSdkVersion","older"));assertFalse(((List<?>)((Map<?,?>)searched.get("data")).get("hits")).isEmpty());
                try(var lease=service.lease()) { assertEquals(new String(Wire.bytes(searched)),new String(Wire.bytes(lease.tools().call("search_knowledge",request("query","cache","targetSdkVersion","older"))))); }
                var invalid=client.callTool(new McpSchema.CallToolRequest("search_knowledge",request("query","cache","sourceSdkVersion","missing")));
                var body=Wire.JSON.valueToTree(invalid.structuredContent());assertEquals("UNKNOWN_SDK",body.path("error").path("code").asText());Wire.validate("search_knowledge.response",body);
            }
            var http=HttpClient.newHttpClient();
            var hostile=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).header("Origin","https://foreign.invalid").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,hostile.statusCode());
        }
    }
}
