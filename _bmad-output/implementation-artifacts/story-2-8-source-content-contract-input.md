# Story 2.8 source-content contract input

Planning integration authorized 2026-10-21. This is a companion to the active 2.8 draft, not a separately executable story and not technical ratification.

## Contract responsibilities

The 2.8 schema/interface slice must define: durable API/narrative payload association by stable domain ID; ordered sections and structured blocks plus derived searchable text; original links with scope and actual/absent locators; independent content hashes; bounded atomic write/read/enumeration; and page/record/section coverage semantics. Keep symbol identity, content and source hashes distinct. Define unsupported-format/rebuild behavior before DDL without deleting existing stores. Exact serialization, finite limits and approvers are settled in the existing 2.8 contract artifacts, not guessed by downstream builders.

Contract tests use compact synthetic specimens for type, overload and symbol-less narrative content, a real absent anchor, code/table/list blocks, labeled notes and external links. Schema/identity/interface sign-offs must record actual human approvals; user authorization of this planning amendment does not supply those technical approvals.

## Implementation ownership

| Work | Owner |
|---|---|
| Shared payload, IDs, locators, hashes, storage/report/request contracts | 2.8 |
| Thin extraction → atomic store → fetch/restart harness for API content | 2.9 |
| Full structured blocks, notes, links and package/overview narratives | 2.10 |
| Reconciled coverage, bounded failures and ingestion fidelity audit | 2.11 |
| Exact and analyzed-content search over persisted records | 4.1 / 4.2 |
| Stable-ID joins, snippets, bounded context and citations | 4.6 |
| Shared envelopes and search/resolve/fetch handlers | 5.7 / 5.1 / 5.3 |
| Sealed-build validation/selection before live serving | 6.1 / 6.2 / 6.5 / 5.6 |
| Evaluation protocol and held-out reservation; later controlled trials | 10.1; 10.2 / 10.3 |

## Sequence and completion rules

2.8 contract ratification → 2.9 → 2.10 → 2.11 → production 4.2 → 4.6 → supported-content MCP integration. 4.1 and shared response-contract implementation can proceed after their own 2.8 gates, independently of parser work. Live user trials also need transport, validated immutable serving, guidance and the existing 10.2 prerequisites.

Define 10.1 early; private corpus selection/tuning waits for its protocol gates, while synthetic parser/storage work does not require held-out access. A later new content producer uses the same contracts. Javadoc-subset delivery never marks the full examples/guides/MCP/evaluation stories done.

The legacy `epics.md`/`sprint-status.yaml` pair remains the system of record. No `tickets.toml` or external tracker is introduced by this amendment. Completed-story files and frozen intent remain unchanged.

## References

- [Epics and forward stories](../planning-artifacts/epics.md)
- [Delivery plan](../planning-artifacts/product-quality-improvement-plan-2026-10-21.md)
- [Original ingestion intent](source-faithful-javadoc-content-input.md)
- [Sprint tracker](sprint-status.yaml)
