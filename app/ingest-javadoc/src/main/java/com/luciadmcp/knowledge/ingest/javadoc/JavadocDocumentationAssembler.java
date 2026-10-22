package com.luciadmcp.knowledge.ingest.javadoc;

import com.luciadmcp.knowledge.domain.*;
import java.util.*;

/** Deterministic semantic API content; rich owned HTML blocks are a later producer extension. */
public final class JavadocDocumentationAssembler {
    private JavadocDocumentationAssembler() {}
    public record Documented(KnowledgeRecord record, DocumentationPayload payload) {}

    public static Optional<String> anchor(JavadocCatalogSource.PageResult page, SymbolRecord symbol) {
        return symbol.memberIndex()<0?page.type().anchor():page.type().members().get(symbol.memberIndex()).anchor();
    }

    public static Documented assemble(CatalogRecord tuple,JavadocCatalogSource.PageResult page,SymbolRecord symbol) {
        String id=IdentityFields.recordId(tuple);
        var anchor=anchor(page,symbol);
        var locator=new SourceLocator(SourceLocator.Kind.SOURCE,page.relativePath(),anchor,
                symbol.memberIndex()>=0&&anchor.isEmpty()?"member anchor absent in source":"");
        var record=new KnowledgeRecord(id,tuple.sdkVersion(),KnowledgeRecord.RecordType.API,
                KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,Optional.of(SymbolCoordinate.from(tuple)),locator,
                new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","local Javadoc source","source API documentation"),
                new KnowledgeRecord.Applicability("LuciadLightspeed",List.of(tuple.sdkVersion()),List.of()));
        List<DocumentationPayload.Block> blocks=new ArrayList<>();
        if(symbol.memberIndex()<0) {
            var type=page.type();if(type.documentationBlocks().stream().noneMatch(b->b.label().equals("signature")))add(blocks,"signature",type.signature());if(type.documentationBlocks().isEmpty())add(blocks,"description",type.description());else blocks.addAll(scoped(type.documentationBlocks(),tuple.sdkVersion()));
            if(type.documentationBlocks().isEmpty()) { for(var param:type.typeParameters())add(blocks,"type parameter "+param.name(),param.description());
            add(blocks,"since",type.since()); } deprecation(blocks,type.deprecation());
        } else {
            var member=page.type().members().get(symbol.memberIndex());
            if(member.documentationBlocks().stream().noneMatch(b->b.label().equals("signature")))add(blocks,"signature",member.signature());if(member.documentationBlocks().isEmpty())add(blocks,"description",member.description());else blocks.addAll(scoped(member.documentationBlocks(),tuple.sdkVersion()));
            if(member.documentationBlocks().isEmpty()) { for(var param:member.typeParameters())add(blocks,"type parameter "+param.name(),param.description());
            for(var param:member.params())add(blocks,"parameter "+param.name(),param.description());
            add(blocks,"returns",member.returns());for(var exception:member.throwsTypes())add(blocks,"throws "+exception.type(),exception.description());
            add(blocks,"since",member.since()); } deprecation(blocks,member.deprecation());
        }
        var sections=List.of(new DocumentationPayload.Section("api","API documentation",locator,blocks));
        var payload=new DocumentationPayload(id,page.sourceHash().orElseThrow(()->new IllegalArgumentException("original source bytes unavailable")),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)));
        return new Documented(record,payload);
    }
    static List<DocumentationPayload.Block> scoped(List<DocumentationPayload.Block> blocks,String sdk) {
        return blocks.stream().map(b->new DocumentationPayload.Block(b.kind(),b.label(),b.text(),b.links().stream().map(l->new DocumentationPayload.Link(l.label(),l.originalTarget(),sdk,l.locator())).toList(),b.table(),b.items(),b.ordered())).toList();
    }
    public static Documented assembleNarrative(String sdk,JavadocCatalogSource.NarrativePage page) {
        var kind=KnowledgeRecord.NarrativeKind.valueOf(page.kind().name());
        String id=KnowledgeRecord.narrativeId(sdk,page.relativePath(),kind.name());
        var locator=new SourceLocator(SourceLocator.Kind.SOURCE,page.relativePath(),page.anchor(),"");
        var record=new KnowledgeRecord(id,sdk,KnowledgeRecord.RecordType.NARRATIVE,KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,Optional.empty(),locator,new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED,"javadoc","local Javadoc source","source narrative documentation"),new KnowledgeRecord.Applicability("LuciadLightspeed",List.of(sdk),List.of()),Optional.of(kind));
        var sections=List.of(new DocumentationPayload.Section("narrative",kind==KnowledgeRecord.NarrativeKind.PACKAGE?"Package documentation":"Overview documentation",locator,scoped(page.blocks(),sdk)));
        return new Documented(record,new DocumentationPayload(id,page.sourceHash().orElseThrow(),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections))));
    }
    private static void add(List<DocumentationPayload.Block> blocks,String label,String text) {
        if(!text.isEmpty() || label.startsWith("parameter ") || label.startsWith("type parameter ") || label.startsWith("throws "))blocks.add(new DocumentationPayload.Block(label.equals("signature")?DocumentationPayload.BlockKind.CODE:DocumentationPayload.BlockKind.PARAGRAPH,label,text,List.of()));
    }
    private static void deprecation(List<DocumentationPayload.Block> blocks,Deprecation deprecation) {
        if(deprecation.present() && blocks.stream().noneMatch(b->b.label().equals("deprecated")))blocks.add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.NOTE,"deprecated",deprecation.comment(),List.of()));
    }
}
