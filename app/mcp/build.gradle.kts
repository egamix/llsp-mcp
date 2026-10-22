dependencies {
 api(project(":domain"))
 api(project(":retrieval"))
 api(libs.mcp.sdk)
 implementation(libs.jetty.server)
 implementation(libs.jetty.servlet)
 api(libs.jackson)
 implementation(libs.json.schema)
 testImplementation(libs.junit.jupiter)
 testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.processResources { from(rootProject.file("../docs/contracts")) { include("*.schema.json"); into("contracts") } }
