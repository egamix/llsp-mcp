// cli -> config, all ingest.*, mcp (AD-1). The composition root: ingest | serve | validate
// entrypoints; the only module allowed to wire ingest and serving together.
dependencies {
    api(project(":config"))
    api(project(":ingest-javadoc"))
    api(project(":ingest-examples"))
    api(project(":ingest-guides"))
    api(project(":ingest-trainings"))
    api(project(":mcp"))

    // Test-only (Story 1.2). AD-1: `cli` has no banned-group entry, so JUnit as a test
    // dependency is permitted; :checkDependencyDirection still passes. These jars never reach
    // any runtime/serving classpath.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
