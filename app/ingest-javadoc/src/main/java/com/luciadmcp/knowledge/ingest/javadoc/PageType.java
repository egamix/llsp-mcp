package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * Structural classification of a single Javadoc HTML page (Story 2.1, FR-4). The type is decided by
 * page structure, never by filename, and drives what later stages do with the page:
 *
 * <ul>
 *   <li>{@link #TYPE} — a class/interface/enum/annotation-type page; the only member-bearing kind,
 *       from which Story 2.2 extracts members.</li>
 *   <li>{@link #PACKAGE}, {@link #OVERVIEW} — narrative pages kept for their prose, not parsed for
 *       members.</li>
 *   <li>{@link #EXCLUDED} — navigation/tree/help/index/all-classes/deprecated/constants/serialized-form
 *       pages, excluded from member ingestion entirely.</li>
 *   <li>{@link #UNKNOWN} — a page whose structure could not be classified; recorded, never guessed
 *       (AD-4), so a later stage can decide policy.</li>
 * </ul>
 */
public enum PageType {
    TYPE,
    PACKAGE,
    OVERVIEW,
    EXCLUDED,
    UNKNOWN;

    /** True only for {@link #TYPE}: the page carries API members to extract downstream. */
    public boolean isMemberBearing() {
        return this == TYPE;
    }

    /** True for {@link #PACKAGE} and {@link #OVERVIEW}: kept as narrative, not as member records. */
    public boolean isNarrative() {
        return this == PACKAGE || this == OVERVIEW;
    }
}
