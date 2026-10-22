package com.luciadmcp.knowledge.cli.pipeline;
import com.luciadmcp.knowledge.domain.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
/** Bootstrap seam wires a domain validator and store port into the existing validation stage. */
public final class BuildValidationStage implements Stage {
    private final BuildValidator validator;
    private final Function<BuildContext, CatalogStore> stores;
    public BuildValidationStage(BuildValidator validator, Function<BuildContext, CatalogStore> stores) { this.validator = validator; this.stores = stores; }
    public void execute(BuildContext context, Consumer<StageFailure> failures) {
        try (CatalogStore store = stores.apply(context)) {
            var result = store.validateBuild(validator, BuildValidator.Operation.CLI, context.buildId(), context.sdkVersion());
            for (var reason : result.reasons()) failures.accept(StageFailure.of(PipelineStage.VALIDATION, FailureSeverity.BUILD_INVALIDATING, reason.name()));
        } catch (RuntimeException e) { failures.accept(StageFailure.of(PipelineStage.VALIDATION, FailureSeverity.BUILD_INVALIDATING, "BUILD_VALIDATION_FAILED: " + e.getMessage())); }
    }
}
