package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.DocumentationStore;
import com.luciadmcp.knowledge.domain.SourceAdmission;
import com.luciadmcp.knowledge.ingest.examples.JavaExampleExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Story 3.1: stores every {@code .java} file under the samples root as example records in the build
 * catalog, so lexical indexing (which runs next) makes them searchable. Per-file problems are recoverable.
 */
public final class ExamplesStage implements Stage {
    private final Path samples;
    private final Function<BuildContext, DocumentationStore> store;
    private final JavaExampleExtractor extractor = new JavaExampleExtractor();

    public ExamplesStage(Path samples, Function<BuildContext, DocumentationStore> store) {
        this.samples = samples;
        this.store = store;
    }

    @Override public void execute(BuildContext context, Consumer<StageFailure> failures) {
        var files = SourceAdmission.enumerate(samples).stream()
                .filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        if (files.isEmpty()) return;
        try (var out = store.apply(context)) {
            for (Path file : files) {
                String relative = SourceAdmission.relative(samples, file);
                try {
                    var result = extractor.extract(context.sdkVersion(), relative, SourceAdmission.read(file));
                    for (String issue : result.issues())
                        failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file, issue));
                    for (var example : result.examples())
                        if (out.putAtomically(example.record(), example.payload()) == DocumentationStore.WriteStatus.IDENTITY_CLASH)
                            failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file,
                                    relative + ": example identity clash for " + example.payload().sections().getFirst().title()));
                } catch (IOException | RuntimeException e) {
                    failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file,
                            relative + ": example not stored: " + e.getMessage()));
                }
            }
        }
    }
}
