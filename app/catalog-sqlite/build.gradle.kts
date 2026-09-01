// catalog.sqlite -> domain (AD-1). Implements the CatalogStore port. This is the first module to
// wire the Xerial SQLite JDBC driver; 'org.xerial' is NOT banned for this module (see the root
// build's bannedExternalGroups), so a direct dependency is permitted and :checkDependencyDirection
// still passes. Kept 'implementation' so the JDBC types never leak past this module's public API —
// callers reach the store only through the domain CatalogStore port.
dependencies {
    api(project(":domain"))

    implementation(libs.sqlite.jdbc)

    // Test-only; the JUnit platform is wired for every module in the root build (useJUnitPlatform()).
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
