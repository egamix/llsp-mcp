package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.SqliteSymbolSource;
import com.luciadmcp.knowledge.domain.*;
import com.luciadmcp.knowledge.search.lucene.LuceneLexicalIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LexicalIndexRoundtripTest {
    @TempDir Path temp;
    @Test void actualImportStructuredDiscoveryDeletionReopenAndResume() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);
        StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args);var first=runner.runToResult(args);
        assertFalse(first.aborted(),first.failures().toString());assertTrue(first.completedStages().contains(PipelineStage.LEXICAL_INDEX));assertFalse(first.activationOccurred());
        var resumed=runner.runToResult(args);assertFalse(resumed.aborted(),resumed.failures().toString());
        Path db=first.buildContext().catalogDbPath(),path=first.buildContext().lexicalIndexPath();byte[] before=Files.readAllBytes(db);
        StructuredDocumentationRoundtripTest.removeInputs(args.javadoc());
        try(var source=new SqliteSymbolSource(db)) {
            var ids=source.documentationIdsAfter("",128);assertEquals(5,ids.size());
            for(String id:ids)assertTrue(source.knowledgeRecord(id).isPresent());
            assertThrows(IllegalArgumentException.class,()->source.documentationIdsAfter("",257));assertThrows(IllegalArgumentException.class,()->source.sections(ids.getFirst(),0,257));
            for(int reopen=0;reopen<2;reopen++)try(var index=new LuceneLexicalIndex(path,LexicalIndex.Binding.of(source))) {
                for(String term:List.of("caveat","cache","order","Nested","Meaning","count","n++","run"))assertFalse(index.search(new LexicalIndex.Query(term)).hits().isEmpty(),term);
                var narrative=index.search(new LexicalIndex.Query("caveat",10,1000,5000,1000,Map.of("recordType","NARRATIVE"),Optional.of("future")));
                assertEquals(2,narrative.hits().size());assertTrue(narrative.hits().stream().allMatch(h->source.knowledgeRecord(h.recordId()).orElseThrow().symbol().isEmpty()));
                assertEquals(ids,index.search(new LexicalIndex.Query("")).hits().stream().map(LexicalIndex.Hit::recordId).toList());
                assertEquals(1,index.search(new LexicalIndex.Query("p.T#run()")).hits().stream().filter(h->source.knowledgeRecord(h.recordId()).orElseThrow().symbol().map(s->s.member().equals("run")).orElse(false)).count());
            }
            LuceneLexicalIndex.build(path,source);
        }
        assertArrayEquals(before,Files.readAllBytes(db));
    }
    @Test void indexFailureRemainsUncheckpointedAndRetryCompletes() throws Exception {
        var args=StructuredDocumentationRoundtripTest.args(temp);StructuredDocumentationRoundtripTest.copy(StructuredDocumentationRoundtripTest.fixtures().resolve("modern"),args.javadoc());
        var context=BuildContext.forInputs(args);Files.createDirectories(context.lexicalIndexPath().getParent());Files.writeString(context.lexicalIndexPath(),"obstruction");
        var runner=IngestPipelineRunner.withCatalogStage(FailurePolicy.COLLECT_AND_CONTINUE,args);var failed=runner.runToResult(args);
        assertTrue(failed.aborted());assertFalse(new CheckpointStore(context).isCompleted(PipelineStage.LEXICAL_INDEX));assertTrue(failed.failures().stream().anyMatch(f->f.stage()==PipelineStage.LEXICAL_INDEX));
        Files.delete(context.lexicalIndexPath());var retry=runner.runToResult(args);assertFalse(retry.aborted(),retry.failures().toString());assertTrue(retry.completedStages().contains(PipelineStage.LEXICAL_INDEX));
        // A checkpoint cannot make a missing or tampered index count as a completed derived build.
        try(var source=new SqliteSymbolSource(context.catalogDbPath())) {
            assertDoesNotThrow(()->{try(var index=new LuceneLexicalIndex(context.lexicalIndexPath(),LexicalIndex.Binding.of(source))) { index.binding(); }});
        }
        try(var paths=Files.list(context.lexicalIndexPath())) { for(Path file:paths.toList())Files.delete(file); }
        byte[] beforeMissing=Files.readAllBytes(context.catalogDbPath());
        var missing=runner.runToResult(args);assertTrue(missing.aborted());assertFalse(missing.completedStages().contains(PipelineStage.LEXICAL_INDEX));assertArrayEquals(beforeMissing,Files.readAllBytes(context.catalogDbPath()));assertTrue(missing.failures().stream().anyMatch(f->f.stage()==PipelineStage.LEXICAL_INDEX));
    }
}
