package com.luciad.knowledge.domain;

/**
 * Domain port for text embedding (AD-1, AD-21).
 *
 * <p>The {@code embeddings.onnx} module provides the sole implementation. Per AD-21 the
 * embedder accepts raw text plus a {@link Role} (query vs. passage) and returns a dense
 * vector; all preprocessing (normalization, role prefixing, pooling) lives behind this
 * interface. The concrete model and vector dimension are a deferred approval gate (Epic 4).
 */
public interface Embedder {

    /**
     * Embeds the given text under the given role.
     *
     * @param text the raw text to embed
     * @param role whether {@code text} is a query or a passage (AD-21)
     * @return the embedding vector
     */
    float[] embed(String text, Role role);

    /**
     * The role of the text being embedded (AD-21). Asymmetric models embed queries and
     * passages differently, so the role is part of the port contract.
     */
    enum Role {
        QUERY,
        PASSAGE
    }
}
