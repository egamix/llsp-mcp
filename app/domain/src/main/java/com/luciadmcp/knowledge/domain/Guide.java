package com.luciadmcp.knowledge.domain;

import java.util.List;

/**
 * A guide/tutorial article extracted during ingestion.
 *
 * <p>One of the four technique-taggable record types (AD-29). The tag vocabulary is a
 * deferred gate. Servable even without a resolved canonical symbol (AD-15). Scoped to an
 * SDK version (AD-12).
 *
 * @param id            stable identity for this guide
 * @param title         human-readable title
 * @param sdkVersion    SDK version this guide belongs to (AD-12)
 * @param techniqueTags technique-tag placeholders (AD-29); never null (empty when none)
 */
public record Guide(String id, String title, String sdkVersion, List<String> techniqueTags) {
    public Guide {
        techniqueTags = techniqueTags == null ? List.of() : List.copyOf(techniqueTags);
    }
}
