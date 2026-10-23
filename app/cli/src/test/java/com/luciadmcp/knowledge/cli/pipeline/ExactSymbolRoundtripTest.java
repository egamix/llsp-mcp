package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.*;
import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.retrieval.ExactSymbolResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExactSymbolRoundtripTest {
    @TempDir Path temp;
    private static Path repo() {
        Path root=Path.of("").toAbsolutePath();
        while(!Files.isDirectory(root.resolve("docs/contracts")))root=root.getParent();
        return root;
    }
    @Test void importedModernAndLegacyCanonicalSymbolsResolveWithoutSourceTree() throws Exception {
        for(String era:List.of("jdk8","jdk11","jdk17")) {
            Path root=temp.resolve(era),docs=Files.createDirectories(root.resolve("javadoc"));
            Path html=docs.resolve("com/example/Widget.html");Files.createDirectories(html.getParent());
            Files.copy(repo().resolve("app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/"+era+"/Widget.html"),html);
            var args=new IngestArguments(docs,Files.createDirectories(root.resolve("samples")),Files.createDirectories(root.resolve("guides")),root.resolve("unused-model"),root.resolve("out"),Optional.empty(),Optional.of("2023.5"));
            var pipeline=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args).runToResult(args);
            assertFalse(pipeline.aborted(),pipeline.failures().toString());
            List<StoredCatalogRecord> records;
            try(var catalog=new SqliteCatalogStore(pipeline.buildContext().catalogDbPath())) { records=catalog.allRecords(); }
            try(var paths=Files.walk(docs)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path); }
            assertFalse(Files.exists(docs));
            byte[] sealed=Files.readAllBytes(pipeline.buildContext().catalogDbPath());
            for(int reopen=0;reopen<2;reopen++)try(var source=new SqliteSymbolSource(pipeline.buildContext().catalogDbPath())) {
                var resolver=new ExactSymbolResolver(source);
                for(var stored:records) {
                    var q=new ExactSymbolQuery(stored.record().canonicalKey(),true,1,1000,5000,Optional.empty(),Optional.empty(),Optional.of("2023.1"));
                    var result=resolver.resolve(q);
                    assertEquals(ExactSymbolResult.Proof.EXACT,result.proof());
                    assertEquals(stored.recordId(),result.matches().getFirst().recordId());
                    assertEquals(pipeline.manifest().buildId(),result.source().buildId());
                    assertEquals("2023.5",result.source().sdkVersion());
                    assertEquals(Optional.ofNullable(stored.record().anchor().isEmpty()?null:stored.record().anchor()),result.matches().getFirst().locator().anchor());
                }
                var ambiguous=resolver.resolve(new ExactSymbolQuery("com.example.Widget#contains",true,1,1000,5000,Optional.empty(),Optional.empty(),Optional.empty()));
                assertEquals(ExactSymbolResult.Proof.AMBIGUOUS,ambiguous.proof());assertEquals(2,ambiguous.candidatesMatched());
            }
            assertArrayEquals(sealed,Files.readAllBytes(pipeline.buildContext().catalogDbPath()));
        }
    }
}
