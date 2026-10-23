package com.luciadmcp.knowledge.mcp;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.JsonFactory;
import com.networknt.schema.*;
import java.util.*;
import java.io.*;

/** Canonical packaged schemas are used for both validation and SDK tool declarations. */
public final class Wire {
    private Wire() {}
    public static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String,Schema> VALIDATORS=new java.util.concurrent.ConcurrentHashMap<>();
    public static LinkedHashMap<String,Object> map(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    public static byte[] bytes(Object value) {
        try { return JSON.writeValueAsBytes(value); }catch(IOException e) { throw new IllegalStateException("SERIALIZATION_FAILED"); }
    }
    public static JsonNode parse(byte[] bytes) {
        if(bytes.length>8388608)throw new IllegalArgumentException("INVALID_REQUEST");
        try { return JSON.readTree(bytes); }catch(IOException e) { throw new IllegalArgumentException("INVALID_REQUEST"); }
    }
    private static JsonNode load(String name) {
        try(var in=Wire.class.getResourceAsStream("/contracts/"+name+".schema.json")) {
            if(in==null)throw new IllegalStateException("MISSING_CONTRACT");return JSON.readTree(in);
        } catch(IOException e) { throw new IllegalStateException("MISSING_CONTRACT"); }
    }
    public static ObjectNode schema(String name) {
        ObjectNode node=(ObjectNode)load(name);node.remove("$id");
        node.set("$defs",load("common").get("$defs"));rewrite(node);return node;
    }
    private static void rewrite(JsonNode node) {
        if(node.isObject()) {
            var o=(ObjectNode)node;
            if(o.has("$ref"))o.put("$ref",o.get("$ref").asText().replace("common.schema.json#","#"));
            o.elements().forEachRemaining(Wire::rewrite);
        } else if(node.isArray())node.elements().forEachRemaining(Wire::rewrite);
    }
    public static Map<String,Object> schemaMap(String name) { return JSON.convertValue(schema(name),Map.class); }
    public static void validate(String name,Object value) {
        var validator=VALIDATORS.computeIfAbsent(name,n->SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schema(n).toString(),InputFormat.JSON));
        if(!validator.validate(new String(bytes(value),java.nio.charset.StandardCharsets.UTF_8),InputFormat.JSON).isEmpty())throw new IllegalArgumentException("INVALID_REQUEST");
    }
}
