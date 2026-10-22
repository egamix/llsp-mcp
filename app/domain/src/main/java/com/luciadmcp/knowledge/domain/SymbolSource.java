package com.luciadmcp.knowledge.domain;

/** Read-only, snapshot-bound catalog port. Implementations bound every returned batch. */
public interface SymbolSource extends AutoCloseable {
    SymbolSourceBinding binding();
    java.util.Optional<StoredCatalogRecord> canonical(String key);
    java.util.List<StoredCatalogRecord> after(String recordId, int limit);
    @Override void close();
}
