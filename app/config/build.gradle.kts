dependencies {
 api(project(":domain"))
 implementation(libs.yaml)
 testImplementation(libs.junit.jupiter)
 testRuntimeOnly(libs.junit.platform.launcher)
}
