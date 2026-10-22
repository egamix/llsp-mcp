package com.luciadmcp.knowledge.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SdkGuidancePolicyTest {
    private final SdkGuidancePolicy.SourceScope source = new SdkGuidancePolicy.SourceScope("2023.5", "build-2023.5");
    private final SourceLocator locator = new SourceLocator(SourceLocator.Kind.SOURCE, "changes.html", Optional.empty(), "");
    private SdkGuidancePolicy.ChangeNotice change() {
        return new SdkGuidancePolicy.ChangeNotice(SdkGuidancePolicy.ChangeKind.INTRODUCED,
                "The cited source documents this API as introduced after 2023.1.", List.of("2023.1"),
                new SdkGuidancePolicy.Citation(source, "change-evidence", locator));
    }
    @Test void differentOrOmittedTargetDoesNotCreateNotice() {
        assertEquals(List.of(), SdkGuidancePolicy.notices(Optional.of("2023.1"), source, List.of()));
        assertEquals(List.of(), SdkGuidancePolicy.notices(Optional.empty(), source, List.of(change())));
        assertEquals(List.of(), SdkGuidancePolicy.notices(Optional.of("2023.0"), source, List.of(change())));
        assertEquals(List.of(change()), SdkGuidancePolicy.notices(Optional.of("2023.1"), source, List.of(change())));
        assertEquals("2023.5", change().citation().source().sdkVersion());
    }
    @Test void noticesRequireActualCitedRelevantEvidenceAndPinnedSource() {
        assertThrows(IllegalArgumentException.class, () -> new SdkGuidancePolicy.ChangeNotice(
                SdkGuidancePolicy.ChangeKind.REMOVED, "claim", List.of(), change().citation()));
        assertThrows(IllegalArgumentException.class, () -> new SdkGuidancePolicy.ChangeNotice(
                SdkGuidancePolicy.ChangeKind.REMOVED, "claim", List.of("2023.1"), null));
        assertThrows(IllegalArgumentException.class, () -> SdkGuidancePolicy.notices(Optional.of("2023.1"),
                new SdkGuidancePolicy.SourceScope("2023.1", "foreign"), List.of(change())));
    }
    @Test void generalGuidanceRetainsActualSourceAndGovernanceWithoutTargetAdmission() {
        var general = new KnowledgeRecord.Applicability("LuciadLightspeed", List.of(), List.of(), KnowledgeRecord.ApplicabilityScope.GENERAL);
        var record = new KnowledgeRecord("practice", "2023.5", KnowledgeRecord.RecordType.PRACTICE,
                KnowledgeRecord.KnowledgeKind.EXPERT_APPROVED_PRACTICE, Optional.empty(), locator,
                new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED, "owner", "source", "approval"), general);
        var validator = new StandardBuildValidator();
        for (var operation : BuildValidator.Operation.values()) {
            assertTrue(validator.validate(new BuildValidator.Input("2023.5", ContractVersions.SCHEMA,
                    ContentHashes.runtimeFingerprint(), false, List.of(), List.of(record), List.of()), operation).valid());
            assertTrue(validator.validate(new BuildValidator.Input("2023.1", ContractVersions.SCHEMA,
                    ContentHashes.runtimeFingerprint(), false, List.of(), List.of(record), List.of()), operation).reasons().contains(BuildValidator.Reason.INVALID_SCOPE));
        }
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRecord.Applicability("LuciadLightspeed", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRecord.Applicability("LuciadLightspeed", List.of("2023.5"), List.of(), KnowledgeRecord.ApplicabilityScope.GENERAL));
    }
    @Test void cursorBindsOptionalTargetSeparatelyFromSource() {
        var noTarget = ReadContracts.cursorBinding("search", "Widget", Optional.empty(), "2023.5", "b", List.of());
        assertNotEquals(noTarget, ReadContracts.cursorBinding("search", "Widget", Optional.of("2023.1"), "2023.5", "b", List.of()));
        assertNotEquals(noTarget, ReadContracts.cursorBinding("search", "Widget", Optional.empty(), "2023.1", "b", List.of()));
    }
}
