// catalog.sqlite -> domain (AD-1). Implements CatalogStore. The SQLite JDBC library is not
// wired here yet (Epic 3).
dependencies {
    api(project(":domain"))
}
