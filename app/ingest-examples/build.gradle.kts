// ingest.examples -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1). Story 3.1 parses
// sample sources with JavaParser (syntax only; nothing is compiled, symbol-solved or executed).
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))

    // AD-30 AST parsing. Kept 'implementation' so JavaParser types never leak past this module.
    implementation(libs.javaparser.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
