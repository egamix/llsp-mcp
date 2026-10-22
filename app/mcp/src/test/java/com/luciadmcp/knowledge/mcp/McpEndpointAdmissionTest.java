package com.luciadmcp.knowledge.mcp;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class McpEndpointAdmissionTest {
 @Test void hostOriginBodyAndConcurrentAdmissionAreBounded() throws Exception {
  int port;try(var socket=new ServerSocket(0)) { port=socket.getLocalPort(); }
  var entered=new CountDownLatch(4);var release=new CountDownLatch(1);
  try(var endpoint=new McpEndpoint(port,false,(name,q)-> {
   entered.countDown();try { release.await(10,TimeUnit.SECONDS); }catch(InterruptedException e) { Thread.currentThread().interrupt(); }
   return ReadTools.error(name,q,"CANCELLED");
  })) {
   endpoint.start();
   var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp").build();
   try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();var workers=Executors.newVirtualThreadPerTaskExecutor()) {
    client.initialize();var q=Map.<String,Object>of("contractVersion","read-tools/2","symbol","Widget");var futures=new ArrayList<Future<?>>();
    for(int i=0;i<4;i++)futures.add(workers.submit(()->client.callTool(new McpSchema.CallToolRequest("resolve_symbol",q))));
    try {
     assertTrue(entered.await(5,TimeUnit.SECONDS));var excess=client.callTool(new McpSchema.CallToolRequest("resolve_symbol",q));
     assertEquals("WORK_LIMIT",Wire.JSON.valueToTree(excess.structuredContent()).path("error").path("code").asText());
    }finally { release.countDown(); }
    for(var f:futures)f.get(10,TimeUnit.SECONDS);
   }
   try(var socket=new Socket("127.0.0.1",port)) {
    socket.setSoTimeout(5000);socket.getOutputStream().write("GET /mcp HTTP/1.1\r\nHost: evil.invalid\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    assertTrue(new String(socket.getInputStream().readNBytes(256)).contains("421"));
   }
   var http=HttpClient.newHttpClient();
   var invalid=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).header("Content-Type","application/json").header("Accept","application/json, text/event-stream")
     .POST(HttpRequest.BodyPublishers.ofString("{} {} ")).build(),HttpResponse.BodyHandlers.ofString());assertTrue(invalid.statusCode()>=400);assertFalse(invalid.body().contains("Users"));
   var hugeId=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).header("Content-Type","application/json").header("Accept","application/json, text/event-stream")
     .POST(HttpRequest.BodyPublishers.ofByteArray(Wire.bytes(Wire.map("jsonrpc","2.0","id","x".repeat(5000),"method","initialize","params",Map.of())))).build(),HttpResponse.BodyHandlers.ofString());assertTrue(hugeId.statusCode()>=400);
   var oversized=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).header("Content-Type","application/json").header("Accept","application/json, text/event-stream")
     .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[8*1024*1024+1])).build(),HttpResponse.BodyHandlers.ofString());assertEquals(413,oversized.statusCode());
  }finally { release.countDown(); }
 }
}
