package com.luciadmcp.knowledge.ingest.javadoc;

/**
 * One relationship-source reference extracted from a Javadoc page (Story 2.5): the documented target
 * text plus the original href and its local, external, or root-escape classification.
 *
 * <p>An {@code ingest.javadoc}-internal contract like {@link ExtractedType}/{@link ExtractedMember} —
 * not a {@code domain} record. It carries only what the page states: the target's documented
 * {@link #name} (a type name — simple or fully-qualified — or a member display text) and, for a linked
 * reference, the link's {@link #relativePath} (root-relative, {@code /}-separated) and {@link #anchor}.
 * Pass two (the {@code cli} RELATIONSHIPS stage) resolves these against the completed catalog: a
 * link-bearing reference resolves via the catalog's persisted {@code (relativePath, anchor)} — reliable
 * and member-precise — and a plain name falls back to FQN then unique simple-name. No resolution or
 * canonical-symbol minting happens here (AD-4 two-pass discipline).
 *
 * <p>{@link #relativePath} is empty when the page did not link the reference (or linked it outside the
 * tree, e.g. an absolute URL); {@link #anchor} is empty for a type target or a same-page member link's
 * bare page. The {@code relativePath} is already resolved relative to the referring page (so it matches
 * the root-relative path the catalog persists), never the raw page-relative href.
 *
 * @param name         the documented target text (never null; may be empty for a bare link)
 * @param relativePath the link target's root-relative {@code /}-separated path, or {@code ""} if none
 * @param anchor       the link's on-page fragment, or {@code ""} if none
 * @param originalLocator the verbatim href, or {@code ""} for an unlinked name
 * @param locatorKind the classification of the explicit locator
 */
public record TypeRef(String name, String relativePath, String anchor, String originalLocator, LocatorKind locatorKind) {

    public enum LocatorKind { NONE, LOCAL, EXTERNAL, ROOT_ESCAPE }

    public TypeRef(String name, String relativePath, String anchor) {
        this(name, relativePath, anchor, relativePath == null || relativePath.isBlank() ? "" : relativePath + (anchor == null || anchor.isBlank() ? "" : "#" + anchor),
                relativePath == null || relativePath.isBlank() ? LocatorKind.NONE : LocatorKind.LOCAL);
    }

    public TypeRef {
        name = name == null ? "" : name;
        relativePath = relativePath == null ? "" : relativePath;
        anchor = anchor == null ? "" : anchor;
        originalLocator = originalLocator == null ? "" : originalLocator;
        if (locatorKind == null) throw new IllegalArgumentException("locatorKind must not be null");
    }

    /** A plain (unlinked) reference to a documented target by its {@code name}. */
    public static TypeRef ofName(String name) {
        return new TypeRef(name, "", "");
    }

    /** Whether this reference carries any explicit locator, including external or escaping links. */
    public boolean hasHref() {
        return locatorKind != LocatorKind.NONE;
    }
}
