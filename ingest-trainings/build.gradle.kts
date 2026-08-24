// ingest.trainings -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1).
// Scaffolded like every other ingest module at the source level (AC 4). Its packaging
// separateness (ships only in a separate ingest-time-only image, never the serving image) is
// an Epic 8/9 concern (AD-28) and is NOT built here. docling parsing lands in Epic 9.
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))
}
