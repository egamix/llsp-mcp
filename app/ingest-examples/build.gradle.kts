// ingest.examples -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1). Sample &
// workflow extraction + governance sidecar wired in Epic 3, not here.
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))
}
