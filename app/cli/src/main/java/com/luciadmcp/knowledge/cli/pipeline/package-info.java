/**
 * The multi-stage, resumable ingestion pipeline and its failure policy (Story 1.3).
 *
 * <p>This package lives in {@code cli} because {@code cli ingest} is the sole build coordinator
 * (AD-17): it computes the single {@code buildId} over the complete input set, owns the build's
 * staging directory, and passes an opaque {@link com.luciadmcp.knowledge.cli.pipeline.BuildContext}
 * to stages. Ingest modules never mint a {@code buildId}.
 *
 * <p>The pipeline here is orchestration only. The eight {@link
 * com.luciadmcp.knowledge.cli.pipeline.PipelineStage}s are stubs ({@link
 * com.luciadmcp.knowledge.cli.pipeline.Stage#noOp()}); real parsing, storage, embedding, and
 * indexing arrive in Epic 2 onward. Deterministic content hashes and the machine-readable JSON
 * report are Story 1.4. Real immutable-build and atomic-activation semantics are Epic 6. No path in
 * this package performs a network request (AD-9); the training-materials exception (AD-28) is not
 * exercised here.
 */
package com.luciadmcp.knowledge.cli.pipeline;
