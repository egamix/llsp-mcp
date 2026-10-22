package com.luciadmcp.knowledge.domain;

/** Scope selection failures are distinct from unknown symbols in an admitted source. */
public final class SymbolSourceException extends IllegalStateException {
    public enum Code { UNKNOWN_SDK, BUILD_UNAVAILABLE, BUILD_INVALID }
    private final Code code;
    public SymbolSourceException(Code code, String message) { super(message); this.code=code; }
    public SymbolSourceException(Code code, String message, Throwable cause) { super(message,cause); this.code=code; }
    public Code code() { return code; }
}
