rootProject.name = "luciad-knowledge-mcp"

// Each Structural-Seed package is its own Gradle subproject so the inward-only dependency
// direction (AD-1) can be ENFORCED (see :checkDependencyDirection in the root build), not
// merely documented. Subproject directories match these names.
include(
    "domain",
    "config",
    "catalog-sqlite",
    "search-lucene",
    "embeddings-onnx",
    "retrieval",
    "ingest-javadoc",
    "ingest-examples",
    "ingest-guides",
    "ingest-trainings",
    "mcp",
    "cli",
)
