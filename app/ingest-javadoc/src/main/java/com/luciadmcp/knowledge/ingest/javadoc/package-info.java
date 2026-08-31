/**
 * Javadoc ingestion (parse-only, AD-10). Depends inward on {@code domain} and the engine adapter
 * modules (AD-1); reaches HTML parsing through JSoup directly (permitted for this module only).
 *
 * <p>Story 2.1 delivers the first stage: {@link com.luciadmcp.knowledge.ingest.javadoc.JavadocDiscovery}
 * walks a Javadoc directory for {@code *.html} pages (using {@code *-search-index.js} as a corroborating
 * assist where present), and {@link com.luciadmcp.knowledge.ingest.javadoc.JavadocPageClassifier} tags
 * each page by DOM structure — not filename — into a {@link com.luciadmcp.knowledge.ingest.javadoc.PageType}.
 * The output is a deterministic, relative-path-ordered set of
 * {@link com.luciadmcp.knowledge.ingest.javadoc.ClassifiedPage}.
 *
 * <p>Field extraction (Story 2.2), canonical symbols (Story 2.3), and catalog persistence (Story 2.4)
 * build on this. This package mints no {@code domain} records and performs no network I/O (AD-9).
 */
package com.luciadmcp.knowledge.ingest.javadoc;
