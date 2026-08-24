// cli -> config, all ingest.*, mcp (AD-1). The composition root: ingest | serve | validate
// entrypoints; the only module allowed to wire ingest and serving together.
dependencies {
    api(project(":config"))
    api(project(":ingest-javadoc"))
    api(project(":ingest-examples"))
    api(project(":ingest-guides"))
    api(project(":ingest-trainings"))
    api(project(":mcp"))
}
