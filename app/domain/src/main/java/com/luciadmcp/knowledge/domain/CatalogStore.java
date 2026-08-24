package com.luciadmcp.knowledge.domain;

/**
 * Domain port for the canonical catalog store (AD-1).
 *
 * <p>The {@code catalog.sqlite} module provides the sole implementation; no other module
 * reaches the storage engine except through this interface. Methods are intentionally
 * absent in this skeleton story — the SQLite schema/DDL and the store operations are a
 * deferred approval gate (Epic 3). This interface fixes the boundary, not the behavior.
 */
public interface CatalogStore {
}
