package com.luciadmcp.knowledge.mcp;

import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.*;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.ee11.servlet.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import java.time.Duration;

/** Official SDK protocol implementation hosted in the standard Jakarta EE11 servlet container. */
public final class McpEndpoint implements AutoCloseable {
    private final Server http;
    private final List<McpSyncServer> servers=new ArrayList<>();
    private final Semaphore admission=new Semaphore(4);
    public McpEndpoint(int port,boolean stdio,BiFunction<String,Map<String,Object>,Map<String,Object>> dispatcher) {
        var mapper=new BoundedMcpMapper(new JacksonMcpJsonMapper(tools.jackson.databind.json.JsonMapper.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()));
        var specs=new ArrayList<McpServerFeatures.SyncToolSpecification>();
        for(String name:ReadTools.NAMES) {
            var tool=new McpSchema.Tool(name,name,AgentGuidance.description(name),
                Wire.schemaMap(name+".request"),Wire.schemaMap(name+".response"),new McpSchema.ToolAnnotations(name,true,false,true,false,false),Map.of());
            specs.add(new McpServerFeatures.SyncToolSpecification(tool,(exchange,request)-> {
                Map<String,Object> result;
                if(!admission.tryAcquire())result=ReadTools.error(name,request.arguments(),"WORK_LIMIT");
                else try { result=dispatcher.apply(name,request.arguments()); }
                catch(RuntimeException e) { result=ReadTools.error(name,request.arguments(),"INTERNAL_ERROR"); }
                finally { admission.release(); }
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(new String(Wire.bytes(result),java.nio.charset.StandardCharsets.UTF_8))),result.get("error")!=null,result,null);
            }));
        }
        var security=DefaultServerTransportSecurityValidator.builder()
            .allowedHosts(List.of("127.0.0.1:"+port,"localhost:"+port))
            .allowedOrigins(List.of("http://127.0.0.1:"+port,"http://localhost:"+port)).build();
        var transport=HttpServletStreamableServerTransportProvider.builder().jsonMapper(mapper).mcpEndpoint("/mcp")
            .maxRequestSize(8*1024*1024).securityValidator(security).build();
        servers.add(McpServer.sync(transport).jsonMapper(mapper).serverInfo("luciad-javadoc","0.1.0").instructions(AgentGuidance.INSTRUCTIONS)
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).validateToolInputs(false)
            .requestTimeout(Duration.ofSeconds(10)).tools(specs).build());
        http=new Server(new org.eclipse.jetty.util.thread.QueuedThreadPool(32,4,60000,new java.util.concurrent.ArrayBlockingQueue<>(64)));var connector=new ServerConnector(http);connector.setHost("127.0.0.1");connector.setPort(port);connector.setIdleTimeout(15000);http.addConnector(connector);
        var context=new ServletContextHandler();context.setContextPath("/");var holder=new ServletHolder(transport);holder.setAsyncSupported(true);context.addServlet(holder,"/mcp");http.setHandler(context);
        if(stdio) {
            var standard=new StdioServerTransportProvider(mapper,System.in,System.out,8*1024*1024);
            servers.add(McpServer.sync(standard).jsonMapper(mapper).serverInfo("luciad-javadoc","0.1.0").instructions(AgentGuidance.INSTRUCTIONS)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).validateToolInputs(false).requestTimeout(Duration.ofSeconds(10)).tools(specs).build());
        }
    }
    public void start() { try { http.start(); }catch(Exception e) { close();throw new IllegalStateException("HTTP_START_FAILED"); } }
    public void join() { try { http.join(); }catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
    public void close() { for(var server:servers)server.closeGracefully();try { http.stop(); }catch(Exception e) { throw new IllegalStateException("HTTP_STOP_FAILED"); } }
}
