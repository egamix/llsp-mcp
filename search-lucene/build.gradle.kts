// search.lucene -> domain (AD-1). Implements LexicalIndex (BM25) + VectorIndex (KNN). Lucene
// is not wired here yet (Epic 5).
dependencies {
    api(project(":domain"))
}
