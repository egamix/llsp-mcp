# Contract freeze — read-tools/2 candidate

Story 2.10 implements durable structured API documentation and package/overview narratives. Scoped contract adoption remains recorded in sign-offs.json; schema/3 technical ratification and other interface/evaluation/release approvals remain pending. Retrieval/MCP handlers remain downstream; Story 2.11 adds coverage reconciliation and an optional independent fidelity audit. Current executed evidence is documented in STORY-2-10-VERIFICATION.md; API-DOCUMENTATION-STORAGE.md records the historical Story 2.9 storage design.

## Identity and independent digests

`SymbolCoordinate` contains product, SDK version, language, namespace, owner, member, ordered qualified erased parameter types and element kind. Its recoverable wire representation is `symbol:1:` plus eight URL-safe, unpadded UTF-8 base64 fields separated by dots: product, SDK version, language, namespace, owner, member, element kind and decimal parameter count, then that many parameter fields. Empty fields encode as an empty segment. Parsing rejects other versions, bad counts and noncanonical encodings. Constructor/method distinctions remain explicit; aliases never establish identity. See `fixtures/portable.properties` and `SymbolCoordinateTest` for a pinned wire value.

Multi-field SHA-256 uses UTF-8 byte length framing `<length>:<bytes>`; null is `-1:`, empty is `0:`. The domain tag is framed first. IDs use `identity/1/record` followed by entity type and ordered identity fields (coordinate wire, SDK version). Edge IDs use `identity/1/relationship`, SDK version, source record ID, SDK relationship type, target record ID and evidence. Source SHA-256 hashes original file bytes; `content/1` hashes the ordered canonical payload field stream; `javadoc-lexical/3` hashes profile plus sorted governed selection; `catalog-runtime/3` hashes schema version, identity version and interface version. These are independent dimensions; a content edit preserves a symbol ID and changes source/content/build input digests. Runtime changes alter runtime and complete build identity; a governance selection or profile change alters recipe and build identity. No timestamps, absolute roots or destination paths enter logical identity.

Partial member occurrence identity is `partial-occurrence/1` over source-relative path, element kind, name and original signature, rather than member enumeration position. Indistinguishable occurrences collide and invalidate the build even under COLLECT_AND_CONTINUE. Narrative IDs use `narrative/1`, product, SDK version, relative path and narrative kind. No guessed endpoint is selected for competing or absent evidence.

`build/1` hashes profile, recipe and runtime digests, optional training presence, explicit SDK binding and sorted root-role/relative-path/source-hash entries. The complete digest is the `b-<64 hex>` build ID. Model roots are ignored by the lexical profile; no model is required. Input/output roots are operational only. Production capabilities are api-documentation, catalog, narrative-documentation, relationships and structured-documentation; lower-level catalog-only fixtures retain their declared capabilities. Lexical search, examples/workflows, embeddings, vectors and activation remain explicitly skipped where unimplemented.

## Storage and validation

SQLite `user_version=3`, identity `identity/1`; `storage-schema.sql` describes the relational contract. Existing schema 0/1/2, unknown/future schema and incompatible identity require rebuild. A read-only preflight runs before writable connections or DDL and leaves unsupported file bytes unchanged. There is no in-place migration or automatic deletion. Tables preserve the existing catalog/edge/reference-evidence families; knowledge dependencies are separately versioned (`knowledge-dependency/1`) and never silently enter SDK traversal.

Records separate physical record type from AUTHORITATIVE_FACT, EXPERT_APPROVED_PRACTICE, RECIPE and CANDIDATE_LEARNING. Candidates cannot activate, including candidates falsely marked approved. Approved practices/recipes require owner, provenance, approval evidence and explicit applicability. GENERAL guidance has no target-version restriction; VERSION_SCOPED applicability lists evidence-backed versions. Applicability is separate from the actual stored source SDK and cannot certify API compatibility from a version label. Required dependencies must exist, match scope, target usable approved knowledge and be acyclic; optional dependencies do not create required-cycle failures. Ordinary unresolved source links are legal evidence. SQLite validates supported API and narrative knowledge metadata, strict source scope, narrative kind/ID/null-symbol invariants and complete documentation associations. Unsupported knowledge producers and nonempty curated dependency tables remain rejected.

`BuildValidator`/`StandardBuildValidator` owns one sorted reason set for CLI, startup, activation, reload, rollback and restore. Existing CLI and pinned serving paths invoke it; lifecycle operations not present in this repository must call the same port when implemented. The existing CLI wiring is the bootstrap seam; no new bootstrap module or lifecycle orchestrator is introduced. Persisted schema/identity/runtime/build/SDK/profile/recipe/capability metadata are read and checked, not substituted with expected constants. Binding a manifest compares existing metadata and cannot overwrite it on resume. Invalidating failures are sticky metadata. A completed resume revalidates persisted metadata. Unsupported stages cannot count as completed activation, and aborted ingestion raises an unsuccessful CLI result.

Validation bounds apply before TEXT materialization: 100,000 catalog records, 1 MiB per stored field, 16 MiB aggregate catalog TEXT; metadata rows are bounded to one row/table and 2 KiB/field. Pinned SQLite snapshots span the metadata-only `octet_length` pass and content reads. SDK traversal retains its stricter per-response and adjacency guards. Domain dependency validation caps 200,000 edges. These are fail-closed admission limits, not claims of unlimited corpus support.

## Durable content handoff

Story 2.10 implements `DocumentationPayload` storage with structured owned API blocks, original links and symbol-less package/overview narratives. Shapes the current DTO cannot represent produce explicit partial issues with retained owned HTML evidence; coverage reconciliation remains Story 2.11. Stable record IDs associate API or symbol-less narrative records atomically with a complete payload. Ordered sections contain paragraph, code, table, list and labeled note blocks, preserved original links, scoped locators, and derived searchable text. Tables preserve ordered headers/rows/cells; lists preserve ordered/unordered state and nested text/children. Do not replace structured cells/items with a flattened string. Maximum nesting is 32, sections 256, blocks 4096 and canonical content bytes 1 MiB. Unsupported format, excessive work or oversize content must fail explicitly; no silent dropping.

Typed source locators carry an actual relative path, optional actual anchor, optional inclusive 1-based line range and optional curated section. SOURCE without an anchor is legal and truthful. ABSENT requires a reason and has no fabricated anchor/range/section. EXTERNAL preserves the original external target. Legacy catalog join anchors are lookup metadata, never proof that a citation exists. Source hash, complete document content hash and section-page content hash are distinct. Canonical payload framing includes section/block counts, all structural data, original links and locator fields, so boundary/ordering changes affect content hashes.

`DocumentationStore.putAtomically` either stores metadata+payload together, reports identical, or reports an identity clash without partial changes. Bounded read/enumeration limit is 1..50. `SectionPage` carries record ID, complete-document hash, selected-page hash, sections and continuation; a section page never pretends to contain the entire document. Reads/restarts retain stable association. Report/2 reconciles disjoint page dispositions (excluded + complete + partial + failed = discovered) separately from logical records and assembled payload sections (emitted = persisted + failed). Missing or invalid coverage is explicitly unavailable; independent expected denominators remain unknown. Story 2.11 supplies the bound accounting sidecar and optional independent fidelity audit described below.

## Six read-only tools

Each `<tool>.request.schema.json` and `<tool>.response.schema.json` is an exact JSON Schema 2020-12 contract with no additional properties; `<tool>.example.json` is a synthetic, explicitly incomplete specimen. `common.schema.json` defines shared proof/error/citation/locator/record contracts. These are future handler contracts, not implemented endpoints.

| Tool | Request | Data |
| --- | --- | --- |
| search_knowledge | query, kind filters, LEXICAL or deterministic BROWSE | ordered hits |
| resolve_symbol | symbol, alias inclusion | exact/ambiguous matches; aliases never establish identity |
| get_record | stable record ID batch, section IDs, content inclusion | records, document hash, page hash, bounded sections |
| get_relationships | seed, explicitly SDK or KNOWLEDGE_DEPENDENCY family, direction, direct/recursive bounds | nodes and typed evidence edges |
| check_symbols | ordered symbol batch | one visible verdict per input; selected ID only for exact proof |
| index_status | coverage inclusion | visible build state/profile/capabilities/ratification |

Search defaults to LEXICAL mode. LEXICAL requires an explicit query containing at least one non-whitespace character; missing, empty or whitespace-only queries are invalid. BROWSE must be explicitly selected and uses an omitted query or the exact empty string, normalized to `""`; nonempty browse queries are invalid. Kind filters default to an empty list (all permitted active kinds). This supports deterministic filter-only or unfiltered browsing without dummy search text. Normalized mode, filters, stable record-ID ordering and the empty browse query participate in continuation binding.

Optional `targetSdkVersion` is user context only; omission needs no SDK prompt. Requests may separately use `sourceSdkVersion` as an explicit restrictive source filter and/or `buildId` to select an immutable build. Without these filters, choose one configured usable source deterministically (operator default first, otherwise configured stable source/build order), within the existing candidate/work/time limits. Missing exact-target docs must not influence that selection into an implicit restrictive filter. If an explicitly selected source is unavailable, report that source limitation; never silently ignore an explicit filter. No new source registry, multi-SDK engine or combined-source query is implemented. One source/build is pinned for the request and continuation, including relationships; selection work consumes the same finite request budget.

Response `sdkVersion` is the actual pinned source SDK and `buildId` is its immutable build; `targetSdkVersion` echoes context or null. Each content citation declares its actual source SDK/build and must match this declared pinned source, never the target merely because it was requested. Records/edges remain source-specific and graph evidence stays within that same source. EXACT proves identity only in the cited source; it makes no tested-compatibility claim about another SDK. Default results provide useful content and ordinary citations: target 2023.1/source 2023.5 yields normal guidance citing 2023.5. Target mismatch, source version labels/proximity, missing exact-target docs or applicability labels alone never create UNKNOWN_SDK, INAPPLICABLE, negative API-existence claims or an automatic UNVERIFIED warning. Missing all usable sources yields BUILD_UNAVAILABLE, and no match in the selected source yields explicit source-scoped not-found/unknown evidence. Ambiguity remains visible.

`versionNotices` defaults to an empty array. Only concrete source evidence of INTRODUCED, REMOVED or BEHAVIOR_CHANGED can support a specific notice. Each notice requires useful factual text, an actual SOURCE citation in the pinned source/build, and a nonempty `relevantTargets` list supported by that evidence and containing the supplied target. Do not infer those targets by numerically comparing version labels. Without a target, or with absent/uncited/irrelevant change evidence, omit the notice. GENERAL guidance remains useful regardless of target while preserving approval and provenance. Neither a notice nor general applicability fabricates API compatibility.

Results retain normalized trace, proof, count relation, coverage reason, empty reason, truncation and continuation. EXACT count means a proven complete admitted window; LOWER_BOUND means a bounded incomplete exploration; UNKNOWN carries null. Empty results never imply target API absence. Batch results preserve input order and an item-level verdict for every admitted input; aggregate payload admission must not silently discard trailing verdicts.

Content snippets/sections require truthful typed citations. A FOUND get_record result with any returned section requires a nonempty record citation list independently of snippet inclusion; setting snippet to null cannot bypass this obligation. Section/citation associations are checked semantically against record ID, section ID and the pinned scope. Metadata-only FOUND results with no snippet and no sections may omit content citations. Verdicts and index status remain visible with no content citations; citations are not required to state unknown/ambiguous/not-applicable status. A match score cannot upgrade proof. Required record metadata carries governance and applicability separately from source citations. Unknown explicit source filter, missing build, incompatible build, malformed request/cursor, oversize payload, work/deadline/cancellation and internal errors use the shared typed codes. Before any immutable build is pinned, errors such as UNKNOWN_SDK (explicit source selection only) and BUILD_UNAVAILABLE use explicit null source `sdkVersion`, `buildId` and `trace.capabilityFingerprint`; never fabricate sentinel IDs or fingerprints. Optional target context remains visible even when no source can be bound. Target context alone can never cause UNKNOWN_SDK. Null binding/fingerprint are paired and allowed only on an error envelope. A successfully pinned response, including a later error after binding, retains its nonempty build ID and capability fingerprint. An error returns null data and cannot claim exact catalog proof; per-item unknown verdicts are successful admitted responses.

Default page is 10, cap 50; batch cap 50; request/query UTF-8 key cap 2 KiB, cursor 4 KiB, serialized response cap 1 MiB. Default candidates/work/time are 1000/5000/1000 ms; caps 10000/50000/5000 ms. Reserve response metadata and charge candidate/scan work before intermediate materialization or filtering; stop with truthful truncation or typed error. Check serialized UTF-8 bytes before delivery. Unbounded total-count scans are forbidden. Content that does not fit must use bounded sections+continuation or PAYLOAD_TOO_LARGE, never truncation disguised as complete content.

Traversal defaults/caps: depth 3/8 (DIRECT forces one hop), visited nodes 1000/10000, adjacency rows 5000/50000, result items 1000/10000, page 10/50, execution 1/5 s and SQL steps 1m/10m. Deterministic cycle-safe traversal charges duplicate adjacency reads before deduplication. Cursor bindings include contract/identity versions, tool, normalized query, all filters/order/work limits, optional target context, explicit source filters, actual source SDK and immutable build. Tokens are authenticated with a runtime secret, finite, and rejected before graph work on query/build/version/key mismatch. No administrative mutation tools are exposed.

## Verification and gates

Run `python tools/contracts/verify_contracts.py`, `python tools/contracts/portable_fixture.py`, `python tools/contracts/verify_mutations.py`, then `app/gradlew.bat --no-daemon check` using the documented local Gradle workaround. Mutation screening restores sources byte-for-byte and requires actual selected-test failures, not compiler errors. `VERIFICATION.md` records executed evidence and limitations; `sign-offs.json` records the still-pending human approvals. Story 10.1 acceptance/private-corpus evaluation is separate and has not run.

Current structured API and narrative persistence uses schema/3; see [Story 2.10 support and verification](STORY-2-10-VERIFICATION.md). [Story 2.9 API storage adoption](API-DOCUMENTATION-STORAGE.md) is historical and scoped to that story. Schema/3 technical ratification and unrelated sign-offs remain pending.


## Ingestion accounting and independent audit (Story 2.11)

Report version 2 separates pages, unique logical record associations and payload sections.
Every discovered HTML page is EXCLUDED, COMPLETE, PARTIAL or FAILED, and the four buckets
sum to discovered. Only positively recognized navigation/non-content is excluded; unknown
content and hand-authored doc-file pages remain unavailable/failed. A complete page has
persisted records and no extraction issues. Partial pages retained some records but have
issues or missing writes; failed pages retained none. Emitted = persisted + failed for
records and sections. Associations are scoped by source-relative page and canonical key,
so a losing conflicting source cannot borrow another page's successful write. Identical puts
and same-source retries never inflate logical counts; a new
catalog attempt replaces the previous ledger. Sections are counted when a payload is
assembled, rather than inferred for unprocessed or rejected records. Expected independent
denominators remain null until separately supplied to the audit.

Outcome cells are sorted by page type, disposition and reason. Each page has one primary
reason (source failure/unknown, extraction issue, payload rejection, identity/storage failure,
unprocessed, preserved or positive non-content); extraction reason precedence is oversized
source, oversized payload, unsupported structure, duplicate identity, generic extraction issue.
Discovery-level disagreements also prevent COMPLETE_FOR_PROFILE. All original extraction
failures remain in the ordinary failure list. Coverage completeness is evidence of the
implemented profile only; technical ratification remains PENDING.

`ingestion-coverage.bin` lives beside the checkpoint under the build staging directory. It
contains coverage/1, build/SDK/input/recipe/runtime binding, counts and ordered outcome cells,
with a SHA-256 checksum. Writes replace the sidecar atomically before catalog checkpointing.
Restores read at most 1 MiB plus one byte, validate framing/checksum/binding/reconciliation,
and return UNAVAILABLE for missing, corrupt or mismatched evidence. A skipped catalog with
unavailable evidence reports PARTIAL and a recoverable coverage failure; it never substitutes
zero or COMPLETE. A catalog retry resets the old sidecar and replaces all totals.

Source admission reads at most 8 MiB plus one byte per HTML/search-index read. At exactly
8 MiB the file is admitted; above that it is explicitly rejected. Input sealing streams
original-byte SHA-256 and preserves existing admitted digests. Before sorting/materializing
an input root, enumeration admits at most 100,000 regular files, 4,096 UTF-8 bytes per
relative path and 16 MiB aggregate relative-path bytes. Boundaries are inclusive. Exceeding
an enumeration budget aborts before the universe is known: the invocation is unsuccessful
and coverage is unavailable, rather than inventing a discovered denominator. Existing
payload section/block/byte, ownership traversal and storage framing caps still apply.
Private traversal work caps also admit at most 200,001 total filesystem entries and depth
128, including empty directories. Explicit attribute-read failures abort enumeration.

`tools/contracts/audit_ingestion.py` accepts an independently authored build/SDK-bound
inventory defined by `ingestion-audit-inventory.schema.json`. Inventory must explicitly
attest independent and complete coverage of the evaluated store. Each record declares its
physical type and whether it is a member; each section declares its ordinal, SHA-256 of
actual encoded payload bytes and ordered block expectations. A block expectation includes
kind, label, SHA-256 of UTF-8 text and SHA-256 of the block's independently framed canonical
fields. Field frames are a big-endian signed 32-bit count followed by signed 32-bit UTF-8
byte lengths (null = -1) and exact field bytes. Section payload frames and canonical block
field order follow DocumentationCodec/schema/3. Use independently derived expectations;
exporting the evaluated store's hashes is not an independent inventory.

The audit opens SQLite in read-only mode, pins a transaction, compares actual section bytes
and decodes actual blocks. It does not accept content_hash columns as fidelity evidence.
Missing/changed sections, notes, blocks, members and narratives remain aggregate discrepancies,
including equal-count mutations. Store SDK scope must match. Limits are 8 MiB inventory,
100,000 records, 256 sections per record, 4,096 blocks per section, 65,536 framed fields,
1,310,724 encoded bytes per record, 100,000 stored section rows, 256 MiB admitted payload,
16 MiB admitted metadata/hash fields and 10 million SQL VM steps. All stored sections,
including unexpected ones, consume byte admission before any payload blob is fetched.
Documentation parents, complete section ordinals and the canonical 19 metadata fields
are checked against stored knowledge columns and usable identity/scope associations.
Inventory section arrays may list ordinals in any order; label length is measured in
Unicode code points, matching JSON Schema maxLength.
Diagnostics contain fixed categories/counts only; identifiers, bodies and raw errors are absent.

Private mode is the default. Before either inventory or store access, it requires explicit
`--approval10_1 --approvalB2 --approvalB6 --corpusOwnerApproved --developmentOnly
--heldOutExcluded --independentInventoryAttested`. Missing gates yield NOT_RUN; invalid or
unreadable inputs yield BLOCKED. These flags record the caller's attestations and do not
create approvals. No private run has been performed. `--synthetic` explicitly attests that
both inputs are synthetic and keeps development verification runnable. Exit 0 means PASS;
all other statuses exit 2. A PASS verifies the supplied independent inventory and does not
ratify the technical contract or claim Story 10.1 acceptance.

Run `python -m unittest discover -s tools/contracts -p test_audit_ingestion.py -v` for the
synthetic discrepancy/gate matrix. The checked-in synthetic inventory and source fixture
were authored from explicit canonical fields and independent build/identity framing.
`CoverageIntegrationTest.independentAuditSpecimenUsesRealStoredPayload` can export the real
Java-ingested store to `LUCIAD_AUDIT_STORE`; then compare it with:

```text
python tools/contracts/audit_ingestion.py --synthetic --inventory docs/contracts/fixtures/ingestion-audit.synthetic.json --store <exported-catalog.sqlite>
```
