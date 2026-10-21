package com.luciadmcp.knowledge.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FreezeContractTest {
    private static KnowledgeRecord practice(String id, KnowledgeRecord.Approval approval) {
        return new KnowledgeRecord(id,"2024.0",KnowledgeRecord.RecordType.PRACTICE,KnowledgeRecord.KnowledgeKind.GUIDANCE,
                Optional.empty(),new SourceLocator(SourceLocator.Kind.ABSENT,"",Optional.empty(),"synthetic no anchor"),
                new KnowledgeRecord.Governance(approval,"synthetic-owner","synthetic provenance","synthetic approval evidence"),
                new KnowledgeRecord.Applicability("LuciadLightspeed",List.of("2024.0"),List.of()));
    }
    @Test void identicalReasonsAcrossEveryLifecycleEntryAndRequiredCycles() {
        var input = new BuildValidator.Input("2024.0",ContractVersions.SCHEMA,ContentHashes.runtimeFingerprint(),false,List.of(),
                List.of(practice("a",KnowledgeRecord.Approval.APPROVED), practice("b",KnowledgeRecord.Approval.CANDIDATE)),
                List.of(new KnowledgeDependency("a","b",KnowledgeDependency.Type.REQUIRED,"2024.0"),new KnowledgeDependency("b","a",KnowledgeDependency.Type.REQUIRED,"2024.0")));
        var validator = new StandardBuildValidator();
        var expected = validator.validate(input, BuildValidator.Operation.CLI);
        assertTrue(expected.reasons().containsAll(List.of(BuildValidator.Reason.UNAPPROVED_KNOWLEDGE,BuildValidator.Reason.INVALID_DEPENDENCY,BuildValidator.Reason.REQUIRED_DEPENDENCY_CYCLE)));
        for (var operation : BuildValidator.Operation.values()) assertEquals(expected,validator.validate(input,operation));
        var unknown = new BuildValidator.Input("0.0.0-stub",0,"wrong",true,List.of(),List.of(),List.of());
        assertEquals(List.of(BuildValidator.Reason.UNKNOWN_SDK,BuildValidator.Reason.UNSUPPORTED_SCHEMA,BuildValidator.Reason.INCOMPATIBLE_RUNTIME,BuildValidator.Reason.BUILD_INVALIDATING_OUTCOME),validator.validate(unknown,BuildValidator.Operation.ACTIVATION).reasons());
    }
    @Test void structuredPayloadAndAbsentAnchorHaveIndependentHashes() {
        var absent = new SourceLocator(SourceLocator.Kind.SOURCE,"a/T.html",Optional.empty(),"source has no anchor");
        var external = new DocumentationPayload.Link("link","https://example.test/doc","2024.0",new SourceLocator(SourceLocator.Kind.EXTERNAL,"https://example.test/doc",Optional.empty(),""));
        var sections = List.of(new DocumentationPayload.Section("description","Description",absent,List.of(
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE,"java","int x = 1;",List.of()),
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.TABLE,"","A | B",List.of()),
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.LIST,"","item",List.of()),
                new DocumentationPayload.Block(DocumentationPayload.BlockKind.NOTE,"apiNote","note",List.of(external)))));
        String hash = ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections));
        var payload = new DocumentationPayload("record",ContentHashes.sourceHash("source".getBytes(java.nio.charset.StandardCharsets.UTF_8)),sections,hash);
        assertTrue(payload.searchableText().contains("int x = 1;"));
        assertTrue(absent.anchor().isEmpty());
        assertThrows(IllegalArgumentException.class,() -> new DocumentationPayload("record",payload.sourceHash(),sections,"bad"));
        assertThrows(IllegalArgumentException.class,() -> new SourceLocator(SourceLocator.Kind.SOURCE,"../escape",Optional.empty(),""));
        assertThrows(IllegalArgumentException.class,() -> DocumentationStore.checkLimit(51));
        assertNotEquals(ContentHashes.recipeFingerprint("lexical",List.of()),ContentHashes.recipeFingerprint("other",List.of()));
        assertEquals(ContentHashes.recipeFingerprint("lexical",List.of("b","a")),ContentHashes.recipeFingerprint("lexical",List.of("a","b")));
    }
}
