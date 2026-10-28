---
title: 'Story 2.10: Structured Javadoc and narrative content'
type: 'feature'
created: '2026-10-22'
status: 'done'
review_loop_iteration: 0
baseline_revision: '410c0aad468200bc3f42aa6af1dc5907fd759de9'
followup_review_recommended: false
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/AGENTS.md
warnings: [oversized]
deferred: []
story_key: '2-10-preserve-structured-javadoc-and-narrative-content'
---

<intent-contract>

## Intent

Retain the structure and caveats currently flattened or dropped from Javadoc, including package/overview prose. Extend Story 2.9's real ingestion-to-SQLite-to-bounded-fetch path so reopened content remains useful after source removal.

## Boundaries & Constraints

**Always:** Preserve ordered owned descriptions, code whitespace, simple list nesting/order, rectangular tables, API/implementation note labels, original hrefs/labels and actual/absent anchors. Narrative records use existing narrative/1 IDs and absent symbols, never fabricated API catalog entries. Retain stable API identities, source/build isolation, independent hashes, atomic writes, bounded read/validation and read-tools/2 useful-guidance policy. Derive searchable text from stored blocks. Preserve unrelated work in the original D:/workspace/luciad-mcp checkout; all implementation occurs in this clean managed worktree. Commit each verified substantial slice separately.

**Never:** Fetch links, invent local edges/anchors, merge members/navigation/summary duplicates, silently claim unsupported structures are fully preserved, implement coverage reconciliation/search/MCP/lifecycle handlers, or claim private-corpus acceptance. Unsupported representational shapes need explicit partial/error evidence and fixtures; never discard them silently. Keep completed-story frozen text unchanged and record no invented human sign-off.

## I/O & Edge-Case Matrix

| Input/state | Expected output | Failure handling |
| --- | --- | --- |
| Modern/legacy multiple paragraphs, preformatted code, lists/table | Ordered typed content survives production ingestion, source deletion and reopen | No first-block-only or whitespace flattening |
| API/implementation notes and type parameters | Labels/structured values retained; generic and positional docs separate | No duplicate note-as-prose output |
| Package/overview owned prose | Stable source-specific NARRATIVE, no symbol/catalog API row | Empty/unsupported layouts reported honestly |
| External, escaping and ordinary inline hrefs; absent anchors | Original label/target retained with truthful scope/locator | No network, guessed edge or fabricated anchor |
| Nested members, repeated summaries/navigation | Only each owner's prose retained once | No descendant-selector leakage |
| Ambiguous ownership, spans/ragged tables, unsupported rich list cells | Explicit partial/unsupported issue with source context | No false fidelity claim |
| API+narrative retries, relocation, SDK/build mismatch and transaction failure | Stable IDs/content; scoped paging; atomic rollback | Existing near-cap/corruption guards retained |
| Schema/2 or corrupt narrative ID/kind/symbol/source metadata | CLI and pinned startup reject consistently | Older stores remain byte-identical |

</intent-contract>

## Code Map

Module paths below are under app/, using existing package structure.

- ingest-javadoc/JavadocFieldExtractor:93-225,392-467,520-529,733-808: entry points, owned descriptions/notes, actual anchors and offline href classification. Current norm/first-block path flattens content. Extend ExtractedType/ExtractedMember with retained blocks while preserving semantic compatibility fields.
- JavadocDocumentationAssembler:7-47 and JavadocCatalogSource:31-84: assemble existing payloads; source currently loops only memberPages. DiscoveryResult.narrativePages already separates PACKAGE/OVERVIEW.
- domain/DocumentationPayload:7-128 supports ordered CODE/NOTE/TABLE/LIST, links and canonical hashes; ListItem children lack independent ordered flags and table cells are strings. Preserve current wire shape; unsupported shapes are explicit partial cases.
- domain/KnowledgeRecord:5-30: reuse narrativeId(sdk,path,kind), NARRATIVE and optional symbol. Persist explicit PACKAGE/OVERVIEW kind to validate identity; never infer an API coordinate.
- cli/CatalogStage:91-145 and RelationshipsStage:164-170: preserve API pass-two evidence; add separate domain-port narrative writes.
- catalog-sqlite/SqliteDocumentationStore:21-182: currently requires API tuple/symbol, documentation FK references records, reads join records and validation loops API rows. Generalize the common metadata/content association to knowledge records while retaining API tuple integrity. SqliteCatalogStore validation must reject unsupported governance/scope and corrupt narratives on both CLI/startup paths.
- ContractVersions, IndexManifest, IngestPipelineRunner and IngestionReport contain schema/recipe/runtime/capability declarations; update together with Python oracle and portability fixtures.
- Reuse DocumentationRoundtripTest source-removal harness, DocumentationStoreTest rollback/paging/near-cap tests, era fixtures, discovery narrative fixtures and ApplicationPortabilitySnapshotTest.

## Tasks & Acceptance

- [x] domain, catalog-sqlite, docs/contracts: implement truthful narrative metadata/content association. Keep identity/1, narrative/1, content/1 and read-tools/2. Version schema to 3 for documentation -> knowledge FK and explicit narrative-kind metadata; rebuild-only reject older stores unchanged. Version relevant recipe/runtime/parser fingerprints and declared capabilities consistently. Validate every supported knowledge/document, not only API catalog rows; retain finite aggregate byte/work guards.
- [x] ingest-javadoc: bounded owned-block extraction reused for API/narratives, with deterministic sections, notes and truthful links. Supported: known modern/legacy ownership wrappers, simple nested lists with representable ordering, rectangular column-header tables. Complex spans/row headers/mixed-order nested lists or rich cell structures not representable by the DTO produce explicit partial issues and tested fallback evidence, never claimed full fidelity.
- [x] cli and domain ports: wire classified narratives to the same atomic store via an additive symbol-less domain path, no synthetic CatalogRecord. Preserve API relationship resolution and actual locators. Unsupported ports/content failures cannot report tuple-only success.
- [x] tests, docs/contracts: modern/legacy golden fixtures and real pipeline source-removal/reopen assertions for every matrix row. Check exact recovered blocks/order/whitespace/links/IDs, mixed API+narrative enumeration, retry/rollback, corruption/startup parity, byte caps and relocation. Include narratives and structured sections in platform snapshots; refresh graph and record independent review.

**Acceptance:** Given modern/legacy API, package and overview fixtures with structured prose, when the real CLI ingestion pipeline runs and inputs are removed, then bounded reopened fetches retain the exact expected structure/locators under stable IDs and narratives have no symbol or API catalog row. Given a content edit/relocation, IDs remain stable while appropriate hashes change. Given corrupt narrative associations or unsupported storage, CLI validation and pinned startup both reject; an unsupported source shape is explicitly partial rather than silently complete.

## Design Notes

Narrative kind must be explicit bounded metadata (PACKAGE/OVERVIEW), independent of source locator sections. Extend domain metadata compatibly and schema encoding as needed; retain nullable symbols and approved source provenance. No broad human technical ratification is inferred from automatic implementation authorization. Documentation describes current support and pending unrelated gates accurately.

This is one story, not a schema redesign or another storage engine. Existing DTO fields stay reusable for the next coverage story. Failure/partial issues use existing stage policy; complete reconciled page/record/section coverage is still Story 2.11.

## Spec Change Log

## Review Triage Log

### 2026-10-22 — Review pass
- verdicts: 14 findings — high 0, medium 14, low 0, false 0, maybe-false 0
- findings:
  - `[medium]` `[patch]` Blind: authored headings disappear — walk skips all h1-h6, including owned prose. Retain heading text/links/level evidence while excluding structural member headings; add a reopened regression. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: dotted legacy narrative IDs do not match — CSS #package.description/#overview.description parses ID plus class. Use literal ID selectors and production fixtures. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: nested owner aliases treated as competing owners — size==1 rejects an outer recognized section containing an inner matching wrapper. Collapse nested aliases while retaining rejection of separate owners. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: empty named-note prefix links lost — captured prefix links are attached before the empty-block fallback. Attach them to that fallback and test. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: list br boundaries silently flattened — items accepts inline br and calls prose.text(). Report unsupported evidence for this unrepresentable rich shape. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: authored definition list outside .block is misclassified — notes relies only on .block ancestry and lowercases arbitrary dt labels. Recognize generated notes; retain other owned definition lists as unsupported evidence. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: multiline pre signature whitespace collapsed — signature.text() normalizes pre content. Preserve wholeText for pre signatures and test reopen. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: direct owned example pre skipped — every description-level pre outside .block is treated as signature even after a separate member-signature. Skip the identified signature node only. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: table traversal does not charge work — row/cell loops omit guard calls, so extraction can exceed the declared node budget before payload construction. Charge the traversed table work and test an over-budget table. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Blind: API source read lacks post-read admission — file size can grow between metadata check and readAllBytes, unlike the narrative path's second check. Apply the same bounded byte-array admission consistently. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Edge: authored heading text and links disappear — same root cause as blind heading finding; shared focused repair/regression. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Edge: linked name without description loses href — same root cause as blind empty-note finding; shared focused repair/regression. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Edge: direct authored definition list becomes generated notes — same root cause as blind definition-list finding; shared focused repair/regression. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.
  - `[medium]` `[patch]` Verification gap: fallback assertion accepts generic placeholder — the eight-case test only checks '<'; substituting '<unsupported/>' would pass. Assert fixture-specific subtree text/attributes after source deletion and reopen. Applied in e6ca796; corresponding focused regressions and final Windows/Linux checks passed.

Intent auditor returned descriptive context-dependence rather than a defect: the verbatim 'go' refers to the preceding explicit Story 2.10 clean-worktree continuation. The user-facing target here is durable ingestion/fetch, not an MCP handler. Four review lenses ran; the host's three-child concurrency cap required queueing one, and protected instruction files were copied unchanged to readable temp paths before the edge/gap retries. No layer was skipped.

## Verification

Run focused tests per verified slice, Python contract verifier/oracle, then full app/gradlew.bat --no-daemon check. Audit every matrix row against tests that actually ran. Export fresh content-inclusive Windows/Linux snapshots, including narratives, and compare logical bytes/JSON excluding operational fields. Scoped diff checks, graft build, independent complete-diff review. No private corpus or live handler claims.

Implementation uses this authorized managed worktree exclusively; escalated exec may be needed for writes/cache/Git here. Windows Gradle process environment: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir (keep it nonexistent). Do not invoke another skill renderer; parent owns this active auto workflow. No merge, push or squash.

## Auto Run Result

Completed Story 2.10 on codex/story-2-10-structured-documentation. Owned structured API documentation and package/overview narratives survive production ingestion, source removal and bounded SQLite reopen. Schema/3 provides explicit narrative kind and a common atomic knowledge/content association; older stores are rejected unchanged. Existing identity/content/read-tools guidance policy remains intact.

### Files changed

- `_bmad-output/implementation-artifacts/spec-2-10-structured-javadoc-and-narrative-content.md` - Records frozen intent, per-finding triage and completed automatic run evidence.
- `_bmad-output/implementation-artifacts/sprint-status.yaml` - Moves Story 2.10 to review without changing other story states.
- `app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SchemaCompatibility.java` - Declares schema/3 rebuild-only compatibility.
- `app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteCatalogStore.java` - Adds narrative-kind schema metadata and validates common knowledge associations.
- `app/catalog-sqlite/src/main/java/com/luciadmcp/knowledge/catalog/sqlite/SqliteDocumentationStore.java` - Persists and validates API and symbol-less narrative content atomically with bounded reads.
- `app/catalog-sqlite/src/test/java/com/luciadmcp/knowledge/catalog/sqlite/DocumentationStoreTest.java` - Verifies narrative reopen, corruption parity, rollback and unchanged schema/2 rejection.
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/CatalogStage.java` - Writes classified narratives through the additive atomic store port.
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestPipelineRunner.java` - Versions production structured/narrative capabilities and parser fingerprint.
- `app/cli/src/main/java/com/luciadmcp/knowledge/cli/pipeline/IngestionReport.java` - Reports schema/3 for produced indexes.
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/IngestArgumentContractTest.java` - Shortens a test-only output path for Windows managed worktrees.
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/ApplicationPortabilitySnapshotTest.java` - Exports structured API/narrative payloads and metadata for platform comparison.
- `app/cli/src/test/java/com/luciadmcp/knowledge/cli/pipeline/StructuredDocumentationRoundtripTest.java` - Exercises exact production extraction, source deletion, reopen and adversarial repairs.
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/CatalogStore.java` - Adds the symbol-less narrative write port with unsupported-port failure.
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/ContractVersions.java` - Versions schema, recipe and runtime declarations together.
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/IndexManifest.java` - Declares structured and narrative documentation production capabilities.
- `app/domain/src/main/java/com/luciadmcp/knowledge/domain/KnowledgeRecord.java` - Adds bounded narrative kind metadata while preserving API compatibility.
- `app/domain/src/test/java/com/luciadmcp/knowledge/domain/PortableFixtureTest.java` - Checks the independent narrative identity known answer.
- `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedMember.java` - Carries retained owned blocks alongside semantic member fields.
- `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/ExtractedType.java` - Carries retained owned blocks alongside semantic type fields.
- `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocCatalogSource.java` - Reads bounded API/narrative sources with truthful ownership and actual anchors.
- `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocDocumentationAssembler.java` - Builds ordered API/narrative payload sections from retained blocks.
- `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractor.java` - Connects owned extraction to type/member signatures and semantic notes.
- `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/OwnedDocumentationExtractor.java` - Extracts bounded typed blocks/links and retains unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/DocumentationExtractionTest.java` - Checks semantic extraction and table traversal work admission.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/legacy/p/T.html` - Provides an exact modern/legacy owned documentation fixture.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/legacy/p/overview-summary.html` - Provides an exact modern/legacy owned documentation fixture.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/legacy/p/package-summary.html` - Provides an exact modern/legacy owned documentation fixture.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/modern/p/T.html` - Provides an exact modern/legacy owned documentation fixture.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/modern/p/overview-summary.html` - Provides an exact modern/legacy owned documentation fixture.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/modern/p/package-summary.html` - Provides an exact modern/legacy owned documentation fixture.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/definition-list.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/image.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/line-break-list.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/mixed-list.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/owned-definition-list.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/ragged.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/rich-cell.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/rich-list.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/row-header.html` - Provides adversarial unsupported owned HTML evidence.
- `app/ingest-javadoc/src/test/resources/javadoc-fixtures/structured/unsupported/spans.html` - Provides adversarial unsupported owned HTML evidence.
- `docs/contracts/CHANGELOG.md` - Records schema/3 structured/narrative contract changes.
- `docs/contracts/README.md` - Documents current storage, structured production support and downstream boundaries.
- `_bmad-output/implementation-artifacts/verification/STORY-2-10-VERIFICATION.md` - Records final platform checks, matrix coverage, review repairs and remaining risks.
- `docs/contracts/fixtures/portable.properties` - Pins schema/3 recipe/runtime/capability and narrative identity values.
- `docs/contracts/ingestion-report.example.json` - Aligns the example with schema/3 and production capabilities.
- `docs/contracts/manifest.example.json` - Aligns the example with schema/3 fingerprints and capabilities.
- `docs/contracts/manifest.schema.json` - Updates schema examples to the current contract version.
- `docs/contracts/storage-schema.sql` - Describes narrative kind and documentation-to-knowledge association.
- `tools/contracts/portable_fixture.py` - Computes independent schema/3 and narrative identity oracle values.

### Review outcome

All four review lenses completed. Fourteen individually logged medium findings formed eleven unique patch entries (high 0, medium 11, low 0). All were repaired in e6ca796; three edge-case rows share blind-review root causes. No entries were deferred or rejected, and there are no rejection reasons to enumerate. The intent audit was descriptive and produced no additional defect. Existing implementation commits a2e1abd, c1a507b, 0be8130 and e6ca796 are preserved; the final commit records only remaining review documentation/tracking changes.

`followup_review_recommended: true`: this first pass patched eleven medium entries. The specific unverified risk is interaction among the repaired owner-alias, heading and generated-note classification branches across additional doclet combinations; focused regressions and both full platform suites pass, but the repaired branches have not received a second independent review.

### Verification

- Final Windows and Ubuntu-24.04 native Gradle `check --rerun-tasks`: PASS, 46 tasks each. Both report 251 tests, zero failures/errors; Windows skips one platform-dependent symlink test, Linux skips none. Module counts: catalog-sqlite 42, cli 99, domain 27, ingest-javadoc 83. All 18 extraction/structured roundtrip/documentation storage regression methods ran and passed on both platforms.
- Every intent matrix row maps to executed tests in _bmad-output/implementation-artifacts/verification/STORY-2-10-VERIFICATION.md. Exact fallback subtree assertions now cover ten unsupported fixtures; direct examples/headings/empty linked notes/alias wrappers and table work bounds have focused regressions.
- Python contract verifier: PASS, 122 checks on Windows. Independent portable oracle: PASS on Windows and Linux. Linux full schema verification remains unavailable because that Python environment lacks referencing; Windows supplies the schema check. No host dependency change was made.
- Fresh Windows/Linux source/parser/SQLite/manifest/report snapshots match exactly as bytes and parsed JSON: 20,985 bytes, SHA-256 901144ea306e255590ebc65f525db121319583ebe507037e3d1fad174d681783. They include six API/narrative documents and structured payload bytes.
- Parent read the complete initial diff and focused repair diff, audited the executed XML and final documentation, and refreshed the temporary unified diff after repairs. `git diff --check` passed; implementation agent refreshed Graft after the final code repairs.

### Residual risks and boundaries

Unsupported DTO shapes remain explicitly partial with retained owned HTML evidence. Schema/3 requires rebuilding older indexes; no migration or deletion is attempted. Technical ratification, private-corpus acceptance, coverage reconciliation, search/MCP handlers and unimplemented lifecycle operations are not claimed by this run. The original checkout's unrelated edits remain untouched. No merge, push or squash was performed.

## Follow-up Review Findings — 2026-10-22

Scope: focused repair commit 0be8130..e6ca796, checked against the full current Story 2.10 implementation. Four lenses completed. Ten individual findings: medium 10, high/low/false/maybe-false 0. Eight root-cause entries, all patch; no decisions, deferrals or rejections.

- [x] [Review][Patch] Blind: authored direct member headings disappear — walk exempts every direct h3/h4 by tag and parent. Select only the actual generated heading preceding the signature.
- [x] [Review][Patch] Blind: standard legacy Author/Version notes demote the whole dl — generatedNotes allMatch excludes standard doclet labels. Extend recognition while preserving authored terms as evidence.
- [x] [Review][Patch] Blind: an earlier example prevents explicit signature selection — firstOwned's union follows DOM order. Prefer explicit signature classes before eligible legacy pre.
- [x] [Review][Patch] Blind: unpaired dl.notes term disappears — notes only emits dd and silently consumes trailing dt. Validate term/definition pairing and preserve malformed evidence.
- [x] [Review][Patch] Blind: table inline descendants bypass node/depth limits — row/cell guards do not charge nested spans before select/text. Bound the entire table subtree before those operations.
- [x] [Review][Patch] Blind: dotted anchor-only marker prevents fallback narrative ownership — literal ID selects an empty anchor before div.description. Keep marker separate from prose owner and retain a genuine adjacent anchor.
- [x] [Review][Patch] Edge: direct authored h3 lost — same root cause as blind member-heading finding.
- [x] [Review][Patch] Verification gap: classless legacy narrative notes lack persisted-output coverage — existing narrative fixtures use dl.notes and bypass markup recognition. Add classless labeled PACKAGE/OVERVIEW deletion/reopen assertions.
- [x] [Review][Patch] Acceptance: authored member h3/h4 lost — same root cause as blind member-heading finding.
- [x] [Review][Patch] Acceptance: known-label authored definition list drops term href — generatedNotes accepts linked Since term by text while notes visits only dd. Preserve linked-term definition lists as explicit unsupported HTML evidence.

### Follow-up resolution

All ten findings (eight unique medium patch entries) were fixed in c3403cc. No decisions, deferred entries or rejected findings remain. Modern/legacy direct authored member headings now retain evidence and links; explicit signatures win over examples; standard legacy notes stay structured while malformed/linked-term definition lists retain unsupported evidence; classless narrative notes have persisted-output assertions; dotted markers remain separate from prose ownership; and table inline descendants are bounded before traversal.

Final full Windows/Linux `check --rerun-tasks` passed on c3403cc: 255 tests each, no failures/errors, one Windows-only symlink skip and no Linux skips. All 46 tasks executed per platform. Python contracts: 122 checks passed. Fresh platform snapshots agree byte-for-byte: 20,825 bytes, SHA-256 2ca148f28c340167beff25868170cc3f596dcbea8d95a0a579b91b9c6caac241. Graft refresh and whitespace checks passed.

Follow-up recommendation is false: this was a follow-up pass, patched high entries = 0. The original pass's recommendation and evidence above are retained as history. Story 2.10 is complete; schema/3 technical ratification remains a separate existing gate, not an approval invented by this review. User authorized local-master merge and then Story 2.11 on 2026-10-22.
