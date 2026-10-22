package com.luciadmcp.knowledge.cli;

import com.luciadmcp.knowledge.config.ApplicationConfig;
import com.luciadmcp.knowledge.mcp.*;
import com.luciadmcp.knowledge.cli.pipeline.*;
import java.nio.file.*;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

/** Operator controls stay outside the agent tool schemas. */
final class RuntimeCommands {
    private RuntimeCommands() {}
    static void run(String command,String[] args,PrintStream out) {
        Path configPath=Path.of("application.yaml");int at=0;
        if(args.length>=2 && args[0].equals("--config")) { configPath=Path.of(args[1]);at=2; }
        var config=ApplicationConfig.load(configPath);var repo=new BundleRepository(config);
        switch(command) {
            case "activate","validate" -> {
                if(args.length-at!=2)throw new IllegalArgumentException("Expected SDK and build ID");
                String sdk=args[at],build=args[at+1];
                if(command.equals("validate"))try(var handle=repo.validate(sdk,build)) { out.println("VALID "+sdk+" "+build); }
                else try(var writer=BundleRepository.writer(config.indexRoot())) {
                    repo.activate(sdk,build);out.println("ACTIVATED "+sdk+" "+build);
                }
            }
            case "publish-staged" -> {
                if(args.length-at!=3)throw new IllegalArgumentException("Expected ingestion output root, SDK and build ID");
                Path output=Path.of(args[at]);String sdk=args[at+1],build=args[at+2];
                try(var writer=BundleRepository.writer(output);var publishWriter=output.toAbsolutePath().normalize().equals(config.indexRoot())?null:BundleRepository.writer(config.indexRoot())) {
                    Path staging=output.resolve(".staging").resolve(build);
                    var context=new BuildContext(build,build.startsWith("b-")?build.substring(2):build,staging,sdk);
                    com.fasterxml.jackson.databind.JsonNode report;
                    try(var in=Files.newInputStream(output.resolve(build).resolve("ingestion-report.json"))) { report=Wire.parse(in.readNBytes(8388609)); }
                    catch(java.io.IOException e) { throw new IllegalStateException("BUILD_INVALID: REPORT_UNAVAILABLE"); }
                    var manifest=com.luciadmcp.knowledge.domain.IndexManifest.withDocumentation(build,sdk,report.path("toolchainFingerprint").asText());
                    var result=new PipelineResult(context,manifest,Set.of(),List.of(),false,false);
                    Path path=repo.publish(result);out.println("PUBLISHED "+path.getFileName());
                }
            }
            case "query" -> {
                if(args.length-at!=2 || !ReadTools.NAMES.contains(args[at]))throw new IllegalArgumentException("Expected tool name and JSON request");
                var node=Wire.parse(args[at+1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(!node.isObject())throw new IllegalArgumentException("INVALID_REQUEST");
                Map<String,Object> input=Wire.JSON.convertValue(node,Map.class);
                try(var service=new BundleRepository.Serving(repo);var lease=service.lease()) { out.println(new String(Wire.bytes(lease.tools().call(args[at],input)),java.nio.charset.StandardCharsets.UTF_8)); }
            }
            case "serve" -> {
                if(args.length!=at)throw new IllegalArgumentException("Unexpected serving arguments");
                try(var service=new BundleRepository.Serving(repo)) {
                    var executor=Executors.newSingleThreadScheduledExecutor(r->{ var t=new Thread(r,"build-reload");t.setDaemon(true);return t; });
                    try(var endpoint=new McpEndpoint(config.port(),config.stdio(),(tool,input)-> { try(var lease=service.lease()) { return lease.tools().call(tool,input); } })) {
                        var shutdown=new Thread(endpoint::close,"mcp-shutdown");Runtime.getRuntime().addShutdownHook(shutdown);
                        executor.scheduleWithFixedDelay(()-> { if(!service.reloadIfChanged())System.err.println("Build reload rejected; current validated build retained"); },2,2,TimeUnit.SECONDS);
                        endpoint.start();System.err.println("Luciad MCP listening at http://127.0.0.1:"+config.port()+"/mcp");endpoint.join();
                        try { Runtime.getRuntime().removeShutdownHook(shutdown); }catch(IllegalStateException ignored) { }
                    }finally { executor.shutdownNow(); }
                }
            }
            default -> throw new IllegalArgumentException("Unknown runtime command");
        }
    }
}
