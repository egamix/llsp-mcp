// Engine adapter depends only on domain ports.
dependencies {
    api(project(":domain"))
    implementation(libs.lucene.core)
    implementation(libs.lucene.analysis.common)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
