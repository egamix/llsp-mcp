// embeddings.onnx -> domain (AD-1). Implements Embedder (AD-21). ONNX Runtime is not wired
// here yet (Epic 4).
dependencies {
    api(project(":domain"))
}
