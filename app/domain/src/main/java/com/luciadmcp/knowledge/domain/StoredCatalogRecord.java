package com.luciadmcp.knowledge.domain;

/**
 * A persisted catalog record read back from {@link CatalogStore}: the store-computed record ID
 * (AD-22) paired with the logical {@link CatalogRecord} that was submitted.
 *
 * <p>The {@code recordId} is assigned solely by the store from the {@link IdentityFields} spec; it is
 * never supplied by ingest. Reads return it so callers can prove that the same logical tuple always
 * resolves to the same ID (NFR-13 determinism) and that {@code relativePath}/{@code anchor}
 * round-trip unchanged.
 *
 * @param recordId the deterministic content-hash ID the store assigned (AD-22)
 * @param record   the logical record tuple as submitted
 */
public record StoredCatalogRecord(String recordId, CatalogRecord record) {

    public StoredCatalogRecord {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId must not be blank");
        }
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
    }
}
