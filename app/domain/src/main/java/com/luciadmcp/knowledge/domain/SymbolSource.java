package com.luciadmcp.knowledge.domain;

/** Read-only, snapshot-bound catalog port. Implementations bound every returned batch. */
public interface SymbolSource extends AutoCloseable {
    SymbolSourceBinding binding();
    java.util.Optional<StoredCatalogRecord> canonical(String key);
    java.util.List<StoredCatalogRecord> after(String recordId, int limit);
    default java.util.Optional<StoredCatalogRecord> byRecordId(String recordId) { return java.util.Optional.empty(); }
    /** Records whose simple name equals {@code name}, in record-id order; empty when the source cannot look up by name. */
    default java.util.Optional<java.util.List<StoredCatalogRecord>> bySimpleName(String name, int limit) { return java.util.Optional.empty(); }
    @Override void close();
}
