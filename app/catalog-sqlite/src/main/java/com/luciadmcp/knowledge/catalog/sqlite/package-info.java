/**
 * SQLite adapter implementing the {@code CatalogStore} domain port. Reached only through that port
 * (AD-1). {@code SqliteCatalogStore} is the sole authority that computes record IDs from the
 * {@code domain} {@code IdentityFields} spec (AD-22) and persists version-scoped rows keyed by that ID.
 *
 * <p>The concrete SQLite catalog schema here is a clearly-labeled <b>provisional</b> DDL pending the
 * {@code [NEEDS APPROVAL]} governance sign-off (ARCHITECTURE-SPINE Deferred); it captures identity
 * inputs explicitly so a later approved schema maps on without reshaping. Relationship/adjacency
 * storage and the recursive-CTE traversal remain Story 2.5+ / Epic 3.
 */
package com.luciadmcp.knowledge.catalog.sqlite;
