package com.luciadmcp.knowledge.domain;

/** Explicit rebuild-only contract versions. Technical ratification is recorded separately. */
public final class ContractVersions {
    private ContractVersions() {}
    public static final int SCHEMA = 2;
    public static final String IDENTITY = "identity/1";
    public static final String CONTENT = "content/1";
    public static final String RECIPE = "javadoc-lexical/2";
    public static final String RUNTIME = "catalog-runtime/2";
    public static final String PROFILE = "javadoc-lexical";
    public static final String INTERFACE = "read-tools/2";
}
