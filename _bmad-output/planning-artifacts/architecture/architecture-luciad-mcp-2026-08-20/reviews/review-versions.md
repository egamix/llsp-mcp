# Review — Stack Versions & Reality-Check

- **Artifact reviewed:** `ARCHITECTURE-SPINE.md` (LuciadLightspeed Knowledge MCP)
- **Lens:** Was every committed stack decision web-researched / reality-checked, or asserted from training data? Are the pinned library versions current as of 2026-08-20, do the named technologies still exist, and do they fit their stated roles?
- **Reviewer:** versions reviewer
- **Date:** 2026-08-20

## Verdict

**PASS with one caveat.** The Stack table's "Verified current 2026-08-20" claim holds up: all six pinned library versions match the newest releases published on their authoritative registries as of today, every named technology exists and fits its stated role, and the two non-version choices (Java LTS, embedding model) are correctly flagged as open rather than silently asserted. The single caveat is that the Java 21 pin note does not acknowledge that a newer LTS (Java 25) now exists — which the pending approval decision should be told about.

## Method

Each pinned artifact was checked against a live authoritative source (Maven Central via `central.sonatype.com`, GitHub releases, Hugging Face, Wikipedia version history) rather than from memory. Where the `search.maven.org` legacy Solr index disagreed with `central.sonatype.com`, the latter was treated as authoritative (see note under SQLite JDBC).

## Per-item findings

| Stack item | Spine value | Live latest (2026-08-20) | Source | Verdict |
| --- | --- | --- | --- | --- |
| Apache Lucene | 10.5.1 | 10.5.1 | central.sonatype.com/artifact/org.apache.lucene/lucene-core | Current — matches latest |
| MCP Java SDK | 2.0.1 | 2.0.1 | central.sonatype.com (io.modelcontextprotocol.sdk/mcp) + GitHub releases | Current — matches latest |
| ONNX Runtime (Java) | 1.29.0 | 1.29.0 | central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime | Current — matches latest |
| SQLite JDBC (Xerial) | 3.53.2.1 | 3.53.2.1 | central.sonatype.com/artifact/org.xerial/sqlite-jdbc | Current — matches latest |
| JSoup | 1.23.1 | 1.23.1 | central.sonatype.com/artifact/org.jsoup/jsoup | Current — matches latest |
| JavaParser | 3.28.2 | 3.28.2 | central.sonatype.com/artifact/com.github.javaparser/javaparser-core | Current — matches latest |
| Java (JDK) | 21 (LTS), pin `[NEEDS APPROVAL]` | 21 supported LTS; 25 is newest LTS (2025-09-16); 26 non-LTS (2026-03-17) | en.wikipedia.org/wiki/Java_version_history | Valid but note incomplete — see F1 |
| Embedding model | BAAI/bge-small-en-v1.5, 384-dim (CANDIDATE) | Model exists, maintained, 384-dim confirmed | huggingface.co/BAAI/bge-small-en-v1.5 | Exists & fits; correctly flagged candidate — see F3 |
| Maven | multi-module `pom.xml` (no version pinned) | n/a | — | Fine — standard, no version asserted |

## Detailed observations

### F1 (MEDIUM) — Java 21 pin note omits that Java 25 LTS now exists

The Stack row reads `Java 21 (LTS) — pin [NEEDS APPROVAL] per blocker B5 (21 vs org LTS)`. The "21 vs org LTS" framing implies the only tension is Java 21 versus whatever LTS the org has standardized on (potentially an *older* one such as 17). But as of 2026-08-20 the landscape has moved:

- Java 21 (2023-09-19) — still a supported LTS.
- **Java 25 (2025-09-16) — the current/newest LTS.**
- Java 26 (2026-03-17) — latest release, non-LTS.

Java 21 is a perfectly defensible, conservative choice (mature tooling, wide library support), so this is not a blocker. The gap is that a reader taking the "Verified current 2026-08-20" banner at face value would not learn that a newer LTS is available. The B5 approval decision should be made with that fact on the table. **Recommendation:** amend the note to "21 vs org LTS vs current LTS (25)" so the approval gate is informed.

### F2 (INFO) — Six library pins are genuinely current, and evidence of prior reality-checking is present

All six third-party library versions are the exact newest releases available today — not "recent enough" values from training data. Corroborating signals that real checking happened:

- The Lucene row explicitly records a correction: "brief said 10.4.0 — bind latest patch," now 10.5.1. That is the fingerprint of someone re-checking the registry against the brief rather than copying the brief.
- MCP Java SDK 2.0.1 is confirmed both on Maven Central and GitHub releases as the latest 2.x. The 2.x line is also the one that carries the **Streamable HTTP** server transport the spine's `mcp` module and AD-2 depend on, so the version and the architectural role are mutually consistent (a 1.x pin would have been a misfit against the "Streamable HTTP" claim).

_Caveat on sourcing:_ the legacy `search.maven.org` Solr index returned a stale top version for SQLite JDBC (3.49.1.0) while `central.sonatype.com` and the Xerial project show 3.53.2.1. The spine's value (3.53.2.1) matches the authoritative source; the discrepancy is an artifact of the legacy index lagging, not an error in the spine. Flagging so future re-verification uses `central.sonatype.com`, not the legacy Solr endpoint.

### F3 (INFO / LOW) — Embedding model exists and fits, and is correctly held behind a selection gate

`BAAI/bge-small-en-v1.5` exists on Hugging Face, is maintained, and its 384-dim output and the "Represent this sentence for searching relevant passages:" query-prefix convention are confirmed live — the latter matters because AD-8 mandates an identical query/passage prefix convention at index and query time, and this model has exactly such a convention. So the choice is technically coherent with the invariants.

The model dates from 2023 and newer general-purpose embedding models exist (e.g., the bge-m3 / e5 families). The spine does **not** assert this model as final: it is labelled "**CANDIDATE only**, subject to selection gate" in the table and again under Deferred (blockers B2/B3, with dimension/license/fingerprint/quality thresholds all deferred). That is the correct posture — the decision is reality-checked to "exists and is plausible" and explicitly not committed. No action required beyond ensuring the selection gate actually surveys current alternatives rather than defaulting to the candidate.

### F4 (INFO) — Technology-to-role fit spot check

- **Lucene 10.5.1** as both BM25 lexical index and KNN/HNSW `VectorIndex` (AD-7/AD-8, search.lucene): Lucene 10.x has mature HNSW vector search, so one engine legitimately covers both the lexical and vector ports. Fit confirmed.
- **ONNX Runtime (Java) 1.29.0** for in-process bge inference with a session pool (AD-13, embeddings.onnx): appropriate; ONNX Runtime Java is the standard embedded-inference path for a bge ONNX export. Fit confirmed.
- **SQLite JDBC (Xerial) 3.53.2.1** for the catalog + recursive-CTE adjacency store (AD-3/AD-4, catalog.sqlite): SQLite supports recursive CTEs; embedded, single-file, no server — consistent with AD-2 "embedded engines only." Fit confirmed.
- **JSoup 1.23.1 (HTML) + JavaParser 3.28.2 (source AST)** for parse-only ingestion (AD-10): JavaParser's pure-parse mode (no symbol-solver against SDK binaries) is exactly what AD-10 requires. Fit confirmed.

## Bottom line

The Stack table is one of the better-substantiated parts of the spine: every pinned version is live-current, the named technologies all exist and map cleanly onto their invariant-defined roles, and the two genuinely open choices (JDK pin, embedding model) are held behind explicit approval/selection gates rather than asserted. Address F1 (add "current LTS = 25" context to the Java note) and the "Verified current 2026-08-20" claim is fully earned.
