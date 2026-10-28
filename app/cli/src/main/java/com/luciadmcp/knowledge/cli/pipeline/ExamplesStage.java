package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.DocumentationStore;
import com.luciadmcp.knowledge.domain.RelationshipScope;
import com.luciadmcp.knowledge.domain.RelationshipStore;
import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.domain.SourceAdmission;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import com.luciadmcp.knowledge.ingest.examples.ExampleLinker;
import com.luciadmcp.knowledge.ingest.examples.JavaExampleExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Stories 3.1/3.2: stores every {@code .java} file under the samples root as example records, then links
 * each example to the catalog symbols it provably uses (USES_TYPE, CONSTRUCTS, CALLS). Runs after the
 * catalog and relationship stages and before lexical indexing. Per-file problems are recoverable.
 */
public final class ExamplesStage implements Stage {
    private final Path samples;
    private final Function<BuildContext, DocumentationStore> store;
    private final Function<BuildContext, List<StoredCatalogRecord>> catalog;
    private final Function<BuildContext, RelationshipStore> relationships;
    private final JavaExampleExtractor extractor = new JavaExampleExtractor();
    private static final Set<RelationshipType> SUPERTYPE = EnumSet.of(RelationshipType.EXTENDS_CLASS, RelationshipType.EXTENDS_INTERFACE, RelationshipType.IMPLEMENTS);

    private record Extracted(Path file, JavaExampleExtractor.Result result) {}

    public ExamplesStage(Path samples, Function<BuildContext, DocumentationStore> store,
            Function<BuildContext, List<StoredCatalogRecord>> catalog, Function<BuildContext, RelationshipStore> relationships) {
        this.samples = samples;
        this.store = store;
        this.catalog = catalog;
        this.relationships = relationships;
    }

    @Override public void execute(BuildContext context, Consumer<StageFailure> failures) {
        var files = SourceAdmission.enumerate(samples).stream()
                .filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        if (files.isEmpty()) return;
        var stored = new ArrayList<Extracted>();
        try (var out = store.apply(context)) {
            for (Path file : files) {
                String relative = SourceAdmission.relative(samples, file);
                try {
                    var result = extractor.extract(context.sdkVersion(), relative, SourceAdmission.read(file));
                    for (String issue : result.issues())
                        failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file, issue));
                    var kept = new ArrayList<JavaExampleExtractor.Example>();
                    for (var example : result.examples()) {
                        if (out.putAtomically(example.record(), example.payload()) == DocumentationStore.WriteStatus.IDENTITY_CLASH)
                            failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file,
                                    relative + ": example identity clash for " + example.payload().sections().getFirst().title()));
                        else kept.add(example);
                    }
                    stored.add(new Extracted(file, new JavaExampleExtractor.Result(kept, List.of(), result.packageName(), result.imports())));
                } catch (IOException | RuntimeException e) {
                    failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file,
                            relative + ": example not stored: " + e.getMessage()));
                }
            }
        }
        link(context, stored, failures);
    }

    private void link(BuildContext context, List<Extracted> stored, Consumer<StageFailure> failures) {
        var keys = catalog.apply(context).stream().map(r -> r.record().canonicalKey()).toList();
        try (var graph = relationships.apply(context)) {
            var supertypes = new HashMap<String, List<String>>();
            for (var edge : graph.edges(new RelationshipScope(context.sdkVersion())))
                if (SUPERTYPE.contains(edge.edge().type()))
                    supertypes.computeIfAbsent(edge.edge().sourceSymbol(), k -> new ArrayList<>()).add(edge.edge().targetSymbol());
            var linker = new ExampleLinker(keys, supertypes);
            for (var file : stored) {
                for (var example : file.result().examples()) {
                    try {
                        for (var link : linker.link(file.result().packageName(), file.result().imports(), example.usages()))
                            if (graph.putExampleLink(context.sdkVersion(), example.record().id(), link.type(), link.targetKey(), link.evidence())
                                    .status() == RelationshipStore.PutStatus.IDENTITY_CLASH)
                                failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file.file(),
                                        "example link identity clash: " + link.evidence()));
                    } catch (RuntimeException e) {
                        failures.accept(StageFailure.ofFile(PipelineStage.EXAMPLES_WORKFLOWS, FailureSeverity.RECOVERABLE, file.file(),
                                "example links not stored: " + e.getMessage()));
                    }
                }
            }
        }
    }
}
