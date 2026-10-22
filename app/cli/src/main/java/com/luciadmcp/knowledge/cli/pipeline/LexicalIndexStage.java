package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.catalog.sqlite.SqliteSymbolSource;
import com.luciadmcp.knowledge.domain.LexicalIndex;
import com.luciadmcp.knowledge.search.lucene.LuceneLexicalIndex;
import java.util.function.Consumer;

/** Build only from the admitted SQLite snapshot; completion requires a matching durable commit. */
public final class LexicalIndexStage implements Stage {
    @Override public void execute(BuildContext context, Consumer<StageFailure> failures) {
        try(var source=new SqliteSymbolSource(context.catalogDbPath())) {
            LuceneLexicalIndex.build(context.lexicalIndexPath(),source);
            try(var index=new LuceneLexicalIndex(context.lexicalIndexPath(),LexicalIndex.Binding.of(source))) { index.binding(); }
        }catch(RuntimeException failure) { fail(failures,failure); }
    }
    public void validateCompleted(BuildContext context, Consumer<StageFailure> failures) {
        try(var source=new SqliteSymbolSource(context.catalogDbPath());
            var index=new LuceneLexicalIndex(context.lexicalIndexPath(),LexicalIndex.Binding.of(source))) { index.binding(); }
        catch(RuntimeException failure) { fail(failures,failure); }
    }
    private static void fail(Consumer<StageFailure> failures,RuntimeException failure) {
        failures.accept(StageFailure.of(PipelineStage.LEXICAL_INDEX,FailureSeverity.BUILD_INVALIDATING,"LEXICAL_INDEX_FAILED: "+failure.getMessage()));
    }
}
