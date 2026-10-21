---
title: 'Fix five Epic 2 review findings'
type: 'bugfix'
created: '2026-10-21'
status: 'done'
route: 'oneshot'
context: []
---

<frozen-after-approval reason="user requested all five reviewed fixes">

## Intent

Fix the five accepted review findings: recognize real legacy help and custom-titled overview pages; distinguish constructors from same-named methods; correctly skip quoted annotation arguments; preserve locator metadata on superclass and interface references. Add targeted regressions and preserve existing unaffected identities and unrelated work.

</frozen-after-approval>

## Implementation Notes

- Work is isolated on `codex/epic2-review-fixes`, based on master `4b5e7678546f687b0cc16f6b57fe000e833a1189`. The shared checkout's Story 2.7 and documentation edits remain separate.
- No migration or persisted-build mutation: corrected ingest behavior applies to newly built catalogs. The canonical key distinction will be limited to methods named after their enclosing type; ordinary keys remain unchanged.
- Verification covers ingest-javadoc, catalog persistence, and relationship pipeline tests, including locator provenance and absence of fabricated edges.
- Legacy help heading and overview package-table markers now classify correctly even when pages are renamed. Fixtures use actual JDK 8 structures.
- Same-named methods use the reserved `method:` identity discriminator; lookup variants retain source spelling. Constructor locators use the modern `<init>` spelling, independently of canonical identity.
- Annotation stripping tracks string/character literals and escapes. Header references carry their original locator and kind through renaming.
- Direct Java 21 compilation and JUnit Platform execution passed all 152 tests in ingest-javadoc, catalog-sqlite, and CLI pipeline packages. Gradle could not establish its daemon connection on this Windows host; direct testing used the same cached JUnit 5.11.4 dependencies and test resources.
- Final independent focused review found no additional actionable issues. No deferred items were added.
