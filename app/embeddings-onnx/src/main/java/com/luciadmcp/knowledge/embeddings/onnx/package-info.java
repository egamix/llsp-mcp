/**
 * ONNX Runtime adapter implementing the {@code Embedder} domain port (AD-21), including
 * the session pool. Reached only through that port (AD-1). Model and dimension are
 * deferred (candidate {@code bge-small-en-v1.5}/384-dim, not locked).
 */
package com.luciadmcp.knowledge.embeddings.onnx;
