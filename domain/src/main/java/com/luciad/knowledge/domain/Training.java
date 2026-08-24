package com.luciad.knowledge.domain;

import java.util.List;

/**
 * A training-deck record (PPTX/PDF derived) extracted during ingestion.
 *
 * <p>One of the four technique-taggable record types (AD-29). The tag vocabulary is a
 * deferred gate. Servable even without a resolved canonical symbol (AD-15). Scoped to an
 * SDK version (AD-12). Training material is ingested via a separate ingest-time-only image
 * (AD-28); this record type is served like any other.
 *
 * @param id            stable identity for this training record
 * @param title         human-readable title
 * @param sdkVersion    SDK version this training record belongs to (AD-12)
 * @param techniqueTags technique-tag placeholders (AD-29); never null (empty when none)
 */
public record Training(String id, String title, String sdkVersion, List<String> techniqueTags) {
    public Training {
        techniqueTags = techniqueTags == null ? List.of() : List.copyOf(techniqueTags);
    }
}
