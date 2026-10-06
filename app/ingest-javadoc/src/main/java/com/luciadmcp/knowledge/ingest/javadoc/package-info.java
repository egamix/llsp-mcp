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
 * <p>Story 2.2 adds {@link com.luciadmcp.knowledge.ingest.javadoc.JavadocFieldExtractor}, which mines a
 * {@link com.luciadmcp.knowledge.ingest.javadoc.PageType#TYPE} page into a layout-agnostic
 * {@link com.luciadmcp.knowledge.ingest.javadoc.ExtractedType} — type-level description/{@code @since}/
 * {@code @deprecated} plus per-member fields — identically for modern (JDK 11+) and legacy (JDK 8)
 * generators. Broken/incomplete markup degrades into explicit issues on the
 * {@link com.luciadmcp.knowledge.ingest.javadoc.ExtractionResult} rather than crashing.
 *
 * <p>Story 2.3 adds {@link com.luciadmcp.knowledge.ingest.javadoc.CanonicalSymbolMinter}, which turns
 * an {@link com.luciadmcp.knowledge.ingest.javadoc.ExtractedType} into a deterministic list of
 * {@link com.luciadmcp.knowledge.ingest.javadoc.SymbolRecord}s — one per API element (the type plus
 * each member), overloads never merged — each carrying the four normalized
 * {@link com.luciadmcp.knowledge.ingest.javadoc.LookupVariants} and a <b>provisional, module-internal</b>
 * canonical key. That key is deliberately <em>not</em> the {@code domain} AD-18 wire/join key and not
 * the {@code [NEEDS APPROVAL]} Epic-3 serialization grammar; it captures the identity inputs (type
 * FQN from the page path, kind, name, era-normalized erased parameter types) so a later post-approval
 * story can map onto the {@code domain} symbol. Signatures it cannot parse degrade into explicit
 * issues on {@link com.luciadmcp.knowledge.ingest.javadoc.SymbolMintResult} (AD-4).
 *
 * <p>Catalog persistence (Story 2.4) builds on this. Story 2.5 extends
 * {@link com.luciadmcp.knowledge.ingest.javadoc.JavadocFieldExtractor} to also capture the
 * relationship-source references — a type's superclass / super-interfaces / implemented-interfaces /
 * see-also, and each member's return/field type, overrides, specified-by, and see-also — as
 * {@link com.luciadmcp.knowledge.ingest.javadoc.TypeRef}s carrying their link hrefs where the page
 * rendered one. These ride on {@link com.luciadmcp.knowledge.ingest.javadoc.ExtractedType} /
 * {@link com.luciadmcp.knowledge.ingest.javadoc.ExtractedMember} and are exposed per page by
 * {@link com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource} so the jsoup-free {@code cli}
 * RELATIONSHIPS stage can resolve them against the completed catalog in pass two. Resolution and
 * canonical-symbol minting never happen here (AD-4).
 *
 * <p>This package mints no {@code domain} records and performs no network I/O (AD-9).
 */
package com.luciadmcp.knowledge.ingest.javadoc;
