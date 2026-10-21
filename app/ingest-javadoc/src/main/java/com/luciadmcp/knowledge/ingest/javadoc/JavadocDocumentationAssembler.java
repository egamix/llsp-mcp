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
            var type=page.type();add(blocks,"signature",type.signature());add(blocks,"description",type.description());
            for(var param:type.typeParameters())add(blocks,"type parameter "+param.name(),param.description());
            add(blocks,"since",type.since());deprecation(blocks,type.deprecation());
        } else {
            var member=page.type().members().get(symbol.memberIndex());
            add(blocks,"signature",member.signature());add(blocks,"description",member.description());
            for(var param:member.typeParameters())add(blocks,"type parameter "+param.name(),param.description());
            for(var param:member.params())add(blocks,"parameter "+param.name(),param.description());
            add(blocks,"returns",member.returns());for(var exception:member.throwsTypes())add(blocks,"throws "+exception.type(),exception.description());
            add(blocks,"since",member.since());deprecation(blocks,member.deprecation());
        }
        var sections=List.of(new DocumentationPayload.Section("api","API documentation",locator,blocks));
        var payload=new DocumentationPayload(id,page.sourceHash().orElseThrow(()->new IllegalArgumentException("original source bytes unavailable")),sections,ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)));
        return new Documented(record,payload);
    }
    private static void add(List<DocumentationPayload.Block> blocks,String label,String text) {
        if(!text.isEmpty() || label.startsWith("parameter ") || label.startsWith("type parameter ") || label.startsWith("throws "))blocks.add(new DocumentationPayload.Block(label.equals("signature")?DocumentationPayload.BlockKind.CODE:DocumentationPayload.BlockKind.PARAGRAPH,label,text,List.of()));
    }
    private static void deprecation(List<DocumentationPayload.Block> blocks,Deprecation deprecation) {
        if(deprecation.present())blocks.add(new DocumentationPayload.Block(DocumentationPayload.BlockKind.NOTE,"deprecated",deprecation.comment(),List.of()));
    }
}
