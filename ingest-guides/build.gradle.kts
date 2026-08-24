// ingest.guides -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1). JSoup guide/
// tutorial parsing + catalog linking wired in Epic 3, not here.
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))
}
