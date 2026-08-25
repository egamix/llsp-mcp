// domain — the inward core (AD-1): records, relationship enum, engine ports, manifest.
// Declares NO app-internal dependencies; verified by :domain:checkDependencyDirection.
dependencies {
    // Test-only. AD-1: JUnit is not in domain's banned-group list, so a test-scoped dependency is
    // permitted and :checkDependencyDirection still passes. These jars never reach any runtime.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
