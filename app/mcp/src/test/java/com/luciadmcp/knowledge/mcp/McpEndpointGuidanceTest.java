package com.luciadmcp.knowledge.mcp;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class McpEndpointGuidanceTest {
    @Test void optionalStdioInitializesDiscoversAndDispatchesTheSameThreeTools() throws Exception {
        var originalIn=System.in;var originalOut=System.out;
        try(var input=new PipedInputStream();var requests=new PipedOutputStream(input);
            var responses=new PipedInputStream();var output=new PipedOutputStream(responses);
            var reader=new BufferedReader(new InputStreamReader(responses,StandardCharsets.UTF_8));
            var workers=Executors.newSingleThreadExecutor()) {
            McpEndpoint endpoint;
            try {
                System.setIn(input);System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));
                endpoint=new McpEndpoint(0,true,(name,args)-> {
                    assertEquals("resolve_symbol",name);assertEquals("read-tools/2",args.get("contractVersion"));
                    return ReadTools.error(name,args,"CANCELLED");
                });
            } finally { System.setIn(originalIn);System.setOut(originalOut); }
            try(endpoint) {
                send(requests,Wire.map("jsonrpc","2.0","id",1,"method","initialize","params",Wire.map(
                    "protocolVersion","2025-11-25","capabilities",Map.of(),"clientInfo",Wire.map("name","guidance-test","version","1"))));
                var initialized=Wire.parse(workers.submit(reader::readLine).get(10,TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8));
                assertEquals(AgentGuidance.INSTRUCTIONS,initialized.path("result").path("instructions").asText());
                send(requests,Wire.map("jsonrpc","2.0","method","notifications/initialized"));
                send(requests,Wire.map("jsonrpc","2.0","id",2,"method","tools/list","params",Map.of()));
                var listed=Wire.parse(workers.submit(reader::readLine).get(10,TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8));
                var tools=listed.path("result").path("tools");assertEquals(3,tools.size());
                var names=new ArrayList<String>();
                for(var tool:tools) {
                    var name=tool.path("name").asText();names.add(name);
                    assertEquals(AgentGuidance.description(name),tool.path("description").asText());
                }
                assertEquals(ReadTools.NAMES,names);
                send(requests,Wire.map("jsonrpc","2.0","id",3,"method","tools/call","params",Wire.map(
                    "name","resolve_symbol","arguments",Wire.map("contractVersion","read-tools/2","symbol","unresolved"))));
                var called=Wire.parse(workers.submit(reader::readLine).get(10,TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8));
                assertEquals("CANCELLED",called.path("result").path("structuredContent").path("error").path("code").asText());
            }
        } finally { System.setIn(originalIn);System.setOut(originalOut); }
    }

    private static void send(OutputStream requests,Map<String,Object> message) throws IOException {
        requests.write(Wire.bytes(message));requests.write('\n');requests.flush();
    }
}
