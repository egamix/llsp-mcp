package com.luciadmcp.knowledge.domain;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PortableFixtureTest {
    @Test void javaMatchesIndependentPortableKnownAnswers() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("docs/contracts/fixtures"))) root=root.getParent();
        Properties expected=new Properties();try(var reader=Files.newBufferedReader(root.resolve("docs/contracts/fixtures/portable.properties"))) { expected.load(reader); }
        assertEquals(expected.getProperty("narrativeId"),KnowledgeRecord.narrativeId("1","p/package-summary.html","PACKAGE"));
        var symbol=new SymbolCoordinate("p","1","java","a","a.T","m",List.of("x.Value[]","y.Value"),"METHOD");
        assertEquals(expected.getProperty("symbol"),symbol.serialize());
        assertEquals(expected.getProperty("recordId"),ContentHashes.recordHash("MethodElement",List.of(symbol.serialize(),"1")));
        assertEquals(expected.getProperty("edgeId"),ContentHashes.relationshipHash("1","source",RelationshipType.SEE_ALSO,"target",""));
        assertEquals(expected.getProperty("contentHash"),ContentHashes.contentHash(List.of("paragraph","synthetic")));
        assertEquals(expected.getProperty("recipeDigest"),ContentHashes.recipeFingerprint(ContractVersions.PROFILE,List.of()));
        assertEquals(expected.getProperty("runtimeDigest"),ContentHashes.runtimeFingerprint());
        var manifest=IndexManifest.withDocumentation("b-"+"a".repeat(64),"1","runtime");
        assertEquals(expected.getProperty("capabilityFingerprint"),manifest.capabilityFingerprint());
        assertEquals(expected.getProperty("profile"),manifest.profile());
        assertEquals(Integer.parseInt(expected.getProperty("manifestVersion")),manifest.manifestVersion());
    }
}
