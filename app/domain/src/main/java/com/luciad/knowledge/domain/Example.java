package com.luciad.knowledge.domain;

import java.util.List;

/**
 * A code example (sample) extracted during ingestion.
 *
 * <p>One of the four technique-taggable record types (AD-29). The tag vocabulary is a
 * deferred gate — tag values are not hardcoded here. Scoped to an SDK version (AD-12).
 *
 * @param id            stable identity for this example
 * @param title         human-readable title
 * @param sdkVersion    SDK version this example belongs to (AD-12)
 * @param techniqueTags technique-tag placeholders (AD-29); never null (empty when none)
 */
public record Example(String id, String title, String sdkVersion, List<String> techniqueTags) {
    public Example {
        techniqueTags = techniqueTags == null ? List.of() : List.copyOf(techniqueTags);
    }
}
