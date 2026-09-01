package com.luciadmcp.knowledge.domain;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for the versioned canonical catalog store (AD-1).
 *
 * <p>The {@code catalog.sqlite} module provides the sole implementation; no other module reaches the
 * storage engine except through this interface. Ingest submits resolved logical {@link CatalogRecord}
 * tuples; the store is the sole authority that computes each record's ID from the {@link IdentityFields}
 * spec (AD-22) and deduplicates by it. Callers never compute an ID and never assume a hash recipe.
 *
 * <p><b>Version scoping (AD-12).</b> Every persisted record carries its {@code sdkVersion};
 * {@code (sdkVersion, canonicalKey)} is unique, so the same simple name in two SDK versions never
 * collides. <b>Determinism (NFR-13).</b> IDs are pure content hashes, so an interrupted-then-resumed
 * build persists byte-identical rows and IDs.
 *
 * <p>The SQLite schema/DDL remains a governance approval gate; the current implementation is a
 * clearly-labeled provisional schema (Story 2.4), not the approved Epic-3 DDL.
 */
public interface CatalogStore extends AutoCloseable {

    /**
     * Persists one logical record, computing its ID from {@link IdentityFields} and deduplicating by
     * it (AD-22). The write is an idempotent upsert: submitting the same logical tuple twice leaves a
     * single row. Submitting a <em>different</em> tuple that resolves to the same identity is an
     * explicit identity clash — never a silent overwrite or drop (AD-4).
     *
     * @param record the resolved logical record tuple
     * @return the computed record ID and the outcome ({@link PutStatus})
     */
    PutResult put(CatalogRecord record);

    /** The stored record for a version-scoped canonical key, or empty if none. */
    Optional<StoredCatalogRecord> findByKey(String sdkVersion, String canonicalKey);

    /** The stored record for a computed record ID, or empty if none. */
    Optional<StoredCatalogRecord> findByRecordId(String recordId);

    /** Every persisted record, in a deterministic order (ascending record ID). */
    List<StoredCatalogRecord> allRecords();

    /** Closes the underlying store. Overridden to declare no checked exception for try-with-resources. */
    @Override
    void close();

    /** The outcome of a {@link #put(CatalogRecord)} call. */
    enum PutStatus {

        /** The record was newly inserted. */
        INSERTED,

        /** An identical record was already present; the upsert was a no-op (idempotent). */
        DUPLICATE,

        /**
         * A different logical record already occupies this identity — a real identity clash. The
         * existing row is left untouched; the caller records an issue per the failure policy (AD-4).
         */
        IDENTITY_CLASH
    }

    /**
     * The result of a {@link #put(CatalogRecord)}: the store-computed record ID (AD-22) and the
     * outcome.
     *
     * @param recordId the computed record ID
     * @param status   whether the record was inserted, already present, or clashed
     */
    record PutResult(String recordId, PutStatus status) {

        public PutResult {
            if (recordId == null || recordId.isBlank()) {
                throw new IllegalArgumentException("recordId must not be blank");
            }
            if (status == null) {
                throw new IllegalArgumentException("status must not be null");
            }
        }
    }
}
