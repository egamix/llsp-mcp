package com.luciadmcp.knowledge.ingest.javadoc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The pass-one catalog entry point for {@code ingest.javadoc} (Story 2.4): discover and classify a
 * Javadoc tree (2.1), extract each type page's fields (2.2), and mint canonical-symbol records (2.3),
 * returning only {@code ingest.javadoc}/{@code domain} types.
 *
 * <p>This facade exists so a jsoup-free caller (the {@code cli} CATALOG stage) can run the whole
 * discover&rarr;extract&rarr;mint chain without touching a JSoup type. Parsing stays entirely inside
 * this module (AD-1): {@link JavadocFieldExtractor}'s {@code Document} overload never crosses the
 * module boundary. Pure library — reads local bytes only, opens no socket (AD-9), writes nothing, and
 * makes no failure-policy decision (that is the caller's, per AD-1).
 */
public final class JavadocCatalogSource {

    private final JavadocDiscovery discovery = new JavadocDiscovery();
    private final JavadocFieldExtractor extractor = new JavadocFieldExtractor();
    private final CanonicalSymbolMinter minter = new CanonicalSymbolMinter();

    /**
     * Runs discovery, extraction, and minting over every member-bearing ({@link PageType#TYPE}) page
     * under {@code javadocRoot}, in the deterministic relative-path order discovery produces.
     *
     * @param javadocRoot the Javadoc tree root
     * @return per-page minted records plus issues, and the discovery-level warnings
     */
    public Result ingest(Path javadocRoot) {
        DiscoveryResult discovered = discovery.classify(javadocRoot);
        List<PageResult> pages = new ArrayList<>();
        for (ClassifiedPage page : discovered.memberPages()) {
            String relativePath = page.relativePath();
            ExtractionResult extracted = extractor.extract(javadocRoot.resolve(relativePath), relativePath);
            SymbolMintResult minted = minter.mint(extracted.type());

            List<String> issues = new ArrayList<>(extracted.issues());
            issues.addAll(minted.issues());
            pages.add(new PageResult(relativePath, minted.records(), issues, extracted.type(), extracted.sourceHash()));
        }
        List<NarrativePage> narratives=new ArrayList<>();
        for(ClassifiedPage page:discovered.narrativePages())narratives.add(narrative(javadocRoot,page));
        return new Result(discovered.warnings(), pages,narratives);
    }

    public record NarrativePage(String relativePath,PageType kind,List<com.luciadmcp.knowledge.domain.DocumentationPayload.Block> blocks,java.util.Optional<String> anchor,java.util.Optional<String> sourceHash,List<String> issues) {
        public NarrativePage { blocks=List.copyOf(blocks);issues=List.copyOf(issues); }
    }
    private NarrativePage narrative(Path root,ClassifiedPage page) {
        List<String> issues=new ArrayList<>();String path=page.relativePath();
        try {
            if(java.nio.file.Files.size(root.resolve(path))>8_388_608)throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
            byte[] bytes=java.nio.file.Files.readAllBytes(root.resolve(path));
            if(bytes.length>8_388_608)throw new IllegalArgumentException("PAYLOAD_TOO_LARGE");
            var doc=org.jsoup.Jsoup.parse(new java.io.ByteArrayInputStream(bytes),null,"");
            String selector=page.type()==PageType.PACKAGE?"section.package-description, .packageDescription, #package-description, #package.description":"section.overview-description, .overview-description, #overview-description, #overview.description, .overviewDescription";
            var owners=doc.select(selector);
            // Legacy descriptions are wrapped separately from summary tables and navigation.
            if(owners.isEmpty())owners=doc.select("div.description");
            // Doclet root overviews commonly use a single header block as the overview prose.
            if(owners.isEmpty() && page.type()==PageType.OVERVIEW)owners=doc.select("main > .header > .block, .contentContainer > .block");
            if(owners.size()!=1) {
                issues.add(path+": PARTIAL_UNSUPPORTED: "+(owners.isEmpty()?"no known narrative ownership wrapper":"ambiguous narrative ownership wrappers"));
                return new NarrativePage(path,page.type(),List.of(),java.util.Optional.empty(),java.util.Optional.of(com.luciadmcp.knowledge.domain.ContentHashes.sourceHash(bytes)),issues);
            }
            var owner=owners.first();var blocks=OwnedDocumentationExtractor.narrative(owner,path,issues);
            if(blocks.isEmpty())issues.add(path+": PARTIAL_UNSUPPORTED: empty narrative content");
            return new NarrativePage(path,page.type(),blocks,JavadocFieldExtractor.actualAnchor(owner),java.util.Optional.of(com.luciadmcp.knowledge.domain.ContentHashes.sourceHash(bytes)),issues);
        } catch(java.io.IOException | RuntimeException e) {
            issues.add(path+": narrative extraction failed: "+e.getMessage());
            return new NarrativePage(path,page.type(),List.of(),java.util.Optional.empty(),java.util.Optional.empty(),issues);
        }
    }

    /**
     * The minted records and non-fatal issues for one type page.
     *
     * <p>Story 2.5 additionally exposes the page's {@link ExtractedType}, so pass two (the {@code cli}
     * RELATIONSHIPS stage) can read the type's relationship-source references (superclass / interfaces /
     * see-also and each member's return/field type, overrides, specified-by, see-also) without touching
     * a JSoup type (AD-1). The minted {@link #records} supply the source-element canonical keys; the
     * {@code type}'s members line up with the member records by {@link SymbolRecord#memberIndex()}.
     *
     * @param relativePath the page's {@code /}-separated path relative to the Javadoc root
     * @param records      the minted canonical-symbol records (type first, then members)
     * @param issues       extraction + minting issues, each carrying the page's relative path (AD-4)
     * @param type         the extracted type, carrying the relationship-source references for pass two
     */
    public record PageResult(
            String relativePath, List<SymbolRecord> records, List<String> issues, ExtractedType type, java.util.Optional<String> sourceHash) {

        public PageResult {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("relativePath must not be blank");
            }
            records = records == null ? List.of() : List.copyOf(records);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    /**
     * The whole-tree pass-one result.
     *
     * @param discoveryWarnings non-fatal discovery/classification warnings (AD-4)
     * @param pages             per-type-page minted records and issues, in discovery order
     */
    public record Result(List<String> discoveryWarnings, List<PageResult> pages,List<NarrativePage> narratives) {
        public Result(List<String> discoveryWarnings,List<PageResult> pages) { this(discoveryWarnings,pages,List.of()); }

        public Result {
            narratives=List.copyOf(narratives);
            discoveryWarnings = discoveryWarnings == null ? List.of() : List.copyOf(discoveryWarnings);
            pages = pages == null ? List.of() : List.copyOf(pages);
        }
    }
}
