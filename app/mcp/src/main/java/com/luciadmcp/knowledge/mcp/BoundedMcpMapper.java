package com.luciadmcp.knowledge.mcp;
import io.modelcontextprotocol.json.*;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.io.IOException;
/** Bounds echoed request IDs so the final serialized result framing allowance is finite. */
final class BoundedMcpMapper implements McpJsonMapper {
 private final McpJsonMapper delegate;
 BoundedMcpMapper(McpJsonMapper delegate) { this.delegate=delegate; }
 private static <T> T admit(T value) {
  Object id=null;
  if(value instanceof McpSchema.JSONRPCRequest request)id=request.id();
  else if(value instanceof Map<?,?> map && map.containsKey("jsonrpc"))id=map.get("id");
  if(id!=null && Wire.bytes(id).length>4096)throw new IllegalArgumentException("Request ID exceeds framing budget");
  return value;
 }
 public <T> T readValue(String value,Class<T> type) throws IOException { return admit(delegate.readValue(value,type)); }
 public <T> T readValue(byte[] value,Class<T> type) throws IOException { return admit(delegate.readValue(value,type)); }
 public <T> T readValue(String value,TypeRef<T> type) throws IOException { return admit(delegate.readValue(value,type)); }
 public <T> T readValue(byte[] value,TypeRef<T> type) throws IOException { return admit(delegate.readValue(value,type)); }
 public <T> T convertValue(Object value,Class<T> type) { return admit(delegate.convertValue(value,type)); }
 public <T> T convertValue(Object value,TypeRef<T> type) { return admit(delegate.convertValue(value,type)); }
 public String writeValueAsString(Object value) throws IOException { return delegate.writeValueAsString(value); }
 public byte[] writeValueAsBytes(Object value) throws IOException { return delegate.writeValueAsBytes(value); }
}
