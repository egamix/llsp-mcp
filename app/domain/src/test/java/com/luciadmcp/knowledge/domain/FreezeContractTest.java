package com.luciadmcp.knowledge.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FreezeContractTest {
    private static KnowledgeRecord practice(String id, KnowledgeRecord.Approval approval) {
        return new KnowledgeRecord(id,"2024.0",KnowledgeRecord.RecordType.PRACTICE,KnowledgeRecord.KnowledgeKind.EXPERT_APPROVED_PRACTICE,
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
    @Test void independentPracticeAndRecipeTypesRequireGovernanceEvenWhenMarkedAsFacts() {
        var validator = new StandardBuildValidator();
        for (var type : List.of(KnowledgeRecord.RecordType.PRACTICE, KnowledgeRecord.RecordType.RECIPE)) {
            var record = new KnowledgeRecord("synthetic-" + type, "2024.0", type,
                    KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT, Optional.empty(),
                    new SourceLocator(SourceLocator.Kind.ABSENT, "", Optional.empty(), "synthetic specimen"),
                    new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED, "", "", ""),
                    new KnowledgeRecord.Applicability("LuciadLightspeed", List.of("2024.0"), List.of()));
            var input = new BuildValidator.Input("2024.0", ContractVersions.SCHEMA, ContentHashes.runtimeFingerprint(),
                    false, List.of(), List.of(record), List.of());
            for (var operation : BuildValidator.Operation.values())
                assertEquals(List.of(BuildValidator.Reason.INVALID_GOVERNANCE), validator.validate(input, operation).reasons());
        }
        var fact = new KnowledgeRecord("synthetic-api-fact", "2024.0", KnowledgeRecord.RecordType.API,
                KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT, Optional.empty(),
                new SourceLocator(SourceLocator.Kind.SOURCE, "a/Fact.html", Optional.empty(), ""),
                new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED, "", "synthetic vendor source", ""),
                new KnowledgeRecord.Applicability("LuciadLightspeed", List.of("2024.0"), List.of()));
        assertTrue(validator.validate(new BuildValidator.Input("2024.0", ContractVersions.SCHEMA,
                ContentHashes.runtimeFingerprint(), false, List.of(), List.of(fact), List.of()), BuildValidator.Operation.CLI).valid());
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
        var table=new DocumentationPayload.Table(List.of("A","B"),List.of(List.of("1","2")));
        var nested=new DocumentationPayload.ListItem("parent",List.of(new DocumentationPayload.ListItem("child",List.of())));
        var structured=List.of(new DocumentationPayload.Section("structured","Structured",new SourceLocator(SourceLocator.Kind.SOURCE,"guide.md",Optional.empty(),"",Optional.of(new SourceLocator.LineRange(2,4)),Optional.of("intro")),List.of(new DocumentationPayload.Block(DocumentationPayload.BlockKind.TABLE,"","",List.of(),Optional.of(table),List.of(),false),new DocumentationPayload.Block(DocumentationPayload.BlockKind.LIST,"","",List.of(),Optional.empty(),List.of(nested),true))));
        assertTrue(DocumentationPayload.canonicalFields(structured).contains("child"));
        assertNotEquals(hash,ContentHashes.contentHash(DocumentationPayload.canonicalFields(structured)));
        assertTrue(payload.searchableText().contains("int x = 1;"));
        assertTrue(absent.anchor().isEmpty());
        assertThrows(IllegalArgumentException.class,() -> new DocumentationPayload("record",payload.sourceHash(),sections,"bad"));
        assertThrows(IllegalArgumentException.class,() -> new SourceLocator(SourceLocator.Kind.SOURCE,"../escape",Optional.empty(),""));
        assertThrows(IllegalArgumentException.class,() -> DocumentationStore.checkLimit(51));
        assertNotEquals(ContentHashes.recipeFingerprint("lexical",List.of()),ContentHashes.recipeFingerprint("other",List.of()));
        assertEquals(ContentHashes.recipeFingerprint("lexical",List.of("b","a")),ContentHashes.recipeFingerprint("lexical",List.of("a","b")));
    }
}
