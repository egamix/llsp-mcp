// retrieval -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1). Fusion, ranking,
// citation assembly. Must NOT depend on any ingest.* module or on parser libraries — enforced
// by :retrieval:checkDependencyDirection.
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))
}
