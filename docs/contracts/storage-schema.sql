-- SQLite schema/2; source API storage adoption approved 2026-10-21. Other gates remain pending.

PRAGMA user_version=2;

CREATE TABLE IF NOT EXISTS records (record_id TEXT PRIMARY KEY NOT NULL, sdk_version TEXT NOT NULL, canonical_key TEXT NOT NULL, kind TEXT NOT NULL, type_fqn TEXT NOT NULL, simple_name TEXT NOT NULL, relative_path TEXT NOT NULL, anchor TEXT NOT NULL, UNIQUE(sdk_version, canonical_key));

CREATE TABLE IF NOT EXISTS edges (edge_id TEXT PRIMARY KEY NOT NULL, sdk_version TEXT NOT NULL, source_id TEXT NOT NULL, type TEXT NOT NULL, target_id TEXT NOT NULL, evidence TEXT NOT NULL, UNIQUE(sdk_version, source_id, type, target_id, evidence));

CREATE INDEX IF NOT EXISTS edges_out ON edges(sdk_version,source_id,edge_id);

CREATE INDEX IF NOT EXISTS edges_in ON edges(sdk_version,target_id,edge_id);

CREATE TABLE IF NOT EXISTS reference_resolutions (id INTEGER PRIMARY KEY, sdk_version TEXT NOT NULL, source_id TEXT NOT NULL, source_path TEXT NOT NULL, source_anchor TEXT NOT NULL, type TEXT NOT NULL, token TEXT NOT NULL, locator TEXT NOT NULL, status TEXT NOT NULL, kind TEXT NOT NULL, reason TEXT NOT NULL, selected_id TEXT NOT NULL, UNIQUE(sdk_version,source_id,type,token,locator));

CREATE TABLE IF NOT EXISTS reference_candidates (resolution_id INTEGER NOT NULL, candidate_id TEXT NOT NULL, PRIMARY KEY(resolution_id,candidate_id));

CREATE TABLE IF NOT EXISTS knowledge_records (record_id TEXT PRIMARY KEY NOT NULL, sdk_product TEXT NOT NULL, sdk_version TEXT NOT NULL, record_type TEXT NOT NULL, knowledge_kind TEXT NOT NULL, symbol_coordinate TEXT, locator_kind TEXT NOT NULL CHECK(locator_kind IN ('SOURCE','ABSENT','EXTERNAL')), source_path TEXT NOT NULL, source_anchor TEXT, source_first_line INTEGER, source_last_line INTEGER, source_section TEXT, absent_reason TEXT NOT NULL, approval TEXT NOT NULL CHECK(approval IN ('CANDIDATE','APPROVED')), owner TEXT NOT NULL, provenance TEXT NOT NULL, approval_evidence TEXT NOT NULL, applicability TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS knowledge_dependencies (sdk_version TEXT NOT NULL, source_id TEXT NOT NULL, target_id TEXT NOT NULL, dependency_type TEXT NOT NULL CHECK(dependency_type IN ('REQUIRED','OPTIONAL','SUPERSEDES')), PRIMARY KEY(sdk_version,source_id,target_id,dependency_type));

CREATE TABLE IF NOT EXISTS build_metadata (build_id TEXT PRIMARY KEY NOT NULL, sdk_product TEXT NOT NULL, sdk_version TEXT NOT NULL, profile TEXT NOT NULL, input_digest TEXT NOT NULL, recipe_digest TEXT NOT NULL, runtime_digest TEXT NOT NULL, capabilities TEXT NOT NULL, validation_reasons TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS contract_metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), identity_version TEXT NOT NULL, runtime_fingerprint TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS documentation (record_id TEXT PRIMARY KEY NOT NULL REFERENCES records(record_id), source_hash TEXT NOT NULL, content_hash TEXT NOT NULL, section_count INTEGER NOT NULL, metadata BLOB NOT NULL);

CREATE TABLE IF NOT EXISTS documentation_sections (record_id TEXT NOT NULL REFERENCES documentation(record_id), ordinal INTEGER NOT NULL, payload BLOB NOT NULL, content_hash TEXT NOT NULL, PRIMARY KEY(record_id,ordinal));
