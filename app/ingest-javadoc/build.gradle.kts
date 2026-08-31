// ingest.javadoc -> domain, catalog.sqlite, search.lucene, embeddings.onnx (AD-1). JSoup HTML
// parsing is wired here (Story 2.1). JavaParser (example ASTs) is deferred to Epic 3.
dependencies {
    api(project(":domain"))
    api(project(":catalog-sqlite"))
    api(project(":search-lucene"))
    api(project(":embeddings-onnx"))

    // AD-10 static HTML parsing. AD-1: 'org.jsoup' is NOT banned for this module (only for
    // domain/retrieval/mcp), so a direct dependency is permitted and :checkDependencyDirection passes.
    // Kept 'implementation' so JSoup types never leak past this module's public API.
    implementation(libs.jsoup)

    // Test-only; JUnit platform is wired for every module in the root build (useJUnitPlatform()).
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
