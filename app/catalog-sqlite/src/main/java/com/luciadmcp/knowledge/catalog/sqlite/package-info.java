/**
 * SQLite adapter implementing the {@code CatalogStore} domain port. Reached only through that port
 * (AD-1). {@code SqliteCatalogStore} is the sole authority that computes record IDs from the
 * {@code domain} {@code IdentityFields} spec (AD-22) and persists version-scoped rows keyed by that ID.
 *
 * <p>The concrete SQLite catalog schema here is a clearly-labeled <b>provisional</b> DDL pending the
 * {@code [NEEDS APPROVAL]} governance sign-off (ARCHITECTURE-SPINE Deferred); it captures identity
 * inputs explicitly so a later approved schema maps on without reshaping.
 *
 * <p>Story 2.5 adds {@code SqliteRelationshipStore}, implementing the {@code RelationshipStore} domain
 * port over the same build-scoped file: a provisional {@code edges} table (adjacency by
 * {@code source_id}/{@code target_id} into the {@code records} table) whose {@code edge_id} is the sole
 * AD-22 edge-identity authority, computed via {@code ContentHashes.relationshipHash}. It is likewise a
 * clearly-labeled provisional schema; the approved adjacency + recursive-CTE traversal representation
 * remains a {@code [NEEDS APPROVAL]} gate (Epic 3).
 */
package com.luciadmcp.knowledge.catalog.sqlite;
