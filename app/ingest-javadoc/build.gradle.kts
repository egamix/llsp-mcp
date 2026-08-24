// ingest.javadoc -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1). JSoup +
// JavaParser parsing is wired in Epic 2, not here.
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))
}
