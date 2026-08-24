import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency

// Root aggregator. No external parent/starter template (AC 3).
group = "com.luciad.knowledge"
version = "0.1.0-SNAPSHOT"

// ---------------------------------------------------------------------------------------
// AD-1 dependency graph. Each module may declare ONLY these inward project edges. `domain`
// declares none. Enforced by :checkDependencyDirection below.
// ---------------------------------------------------------------------------------------
val allowedProjectDeps = mapOf(
    "domain" to emptySet<String>(),
    "config" to setOf("domain"),
    "catalog-sqlite" to setOf("domain"),
    "search-lucene" to setOf("domain"),
    "embeddings-onnx" to setOf("domain"),
    "retrieval" to setOf("domain", "catalog-sqlite", "search-lucene", "embeddings-onnx"),
    "ingest-javadoc" to setOf("domain", "catalog-sqlite", "search-lucene", "embeddings-onnx"),
    "ingest-examples" to setOf("domain", "catalog-sqlite", "search-lucene", "embeddings-onnx"),
    "ingest-guides" to setOf("domain", "catalog-sqlite", "search-lucene", "embeddings-onnx"),
    "ingest-trainings" to setOf("domain", "catalog-sqlite", "search-lucene", "embeddings-onnx"),
    "mcp" to setOf("domain", "retrieval"),
    "cli" to setOf("config", "ingest-javadoc", "ingest-examples", "ingest-guides", "ingest-trainings", "mcp"),
)

// AD-1: engines (SQLite/Lucene/ONNX) and parsers (JSoup/JavaParser) are reached ONLY through
// domain ports. These external groups are banned as DIRECT dependencies of the listed modules
// (mirrors the intent of Maven enforcer's banned-dependencies with searchTransitive=false).
val bannedExternalGroups = mapOf(
    "domain" to listOf(
        "org.apache.lucene", "io.modelcontextprotocol.sdk", "com.microsoft.onnxruntime",
        "org.xerial", "org.jsoup", "com.github.javaparser",
    ),
    "retrieval" to listOf(
        "org.apache.lucene", "com.microsoft.onnxruntime", "org.xerial",
        "org.jsoup", "com.github.javaparser",
    ),
    "mcp" to listOf(
        "org.apache.lucene", "com.microsoft.onnxruntime", "org.xerial",
        "org.jsoup", "com.github.javaparser",
    ),
)

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    // AD-1 enforcement: fail the build on any forbidden module dependency edge.
    val checkDependencyDirection = tasks.register("checkDependencyDirection") {
        group = "verification"
        description = "Fails the build on any module dependency edge forbidden by AD-1."
        doLast {
            val moduleName = project.name
            val allowed = allowedProjectDeps[moduleName]
                ?: throw GradleException(
                    "AD-1: module '$moduleName' is missing from the allow-list in the root build; add it."
                )
            val banned = bannedExternalGroups[moduleName].orEmpty()
            // Cover every declarable configuration a forbidden edge could hide in, including
            // test and annotation-processor scopes (a test-scoped forbidden edge is still forbidden).
            listOf(
                "api", "implementation", "compileOnly", "runtimeOnly",
                "testImplementation", "testCompileOnly", "testRuntimeOnly",
                "annotationProcessor", "testAnnotationProcessor",
            ).forEach { cfgName ->
                val cfg = project.configurations.findByName(cfgName) ?: return@forEach
                cfg.dependencies.forEach { dep ->
                    when (dep) {
                        is ProjectDependency -> {
                            if (dep.name !in allowed) {
                                throw GradleException(
                                    "AD-1 violation: ':$moduleName' must not depend on project ':${dep.name}' " +
                                        "(allowed inward edges: $allowed)."
                                )
                            }
                        }
                        is ExternalModuleDependency -> {
                            val grp = dep.group.orEmpty()
                            if (banned.any { grp == it || grp.startsWith("$it.") }) {
                                throw GradleException(
                                    "AD-1 violation: ':$moduleName' must not directly depend on external " +
                                        "'${dep.group}:${dep.name}'; reach engines/parsers only through domain ports."
                                )
                            }
                        }
                        is FileCollectionDependency -> {
                            // A raw jar (files("lucene.jar")) can smuggle a banned engine past the
                            // group check. Modules with a banned-group list must never use file deps.
                            if (banned.isNotEmpty()) {
                                throw GradleException(
                                    "AD-1 violation: ':$moduleName' declares a file/collection dependency " +
                                        "(${dep.files.files}); banned-group modules must reach engines/parsers " +
                                        "only through domain ports, not raw jars."
                                )
                            }
                        }
                        else -> {
                            // Fail closed: an unrecognized dependency type on a banned-group module
                            // is refused rather than silently allowed past the AD-1 check.
                            if (banned.isNotEmpty()) {
                                throw GradleException(
                                    "AD-1: ':$moduleName' declares an unrecognized dependency type " +
                                        "'${dep.javaClass.name}'; refusing to allow it past the AD-1 check."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(checkDependencyDirection)
    }
}
