# Handover: independent re-review of Story 2.2 code review

## Why you're reading this

Story 2.2 (Javadoc field extraction) was implemented **and** code-reviewed by the *same* model (Claude Opus). The review used three adversarial layers (Blind Hunter, Edge Case Hunter, Acceptance Auditor) — but all three ran on the same model family as the implementer, so they share its blind spots. **Your job: independently verify the review, ideally on a different model.** Do not trust the findings below because they're written down — re-derive them from the code. Also actively hunt for anything all-Opus missed.

Nothing has been patched yet. The 7 "patch" findings are recorded as unchecked action items in the story; no code has changed since the implementation commit.

## What to review

- **Repo:** `D:\workspace\luciad-mcp` (Windows; shell is Git Bash; use `python`, not `python3`).
- **Diff under review:** `git -C D:/workspace/luciad-mcp diff 7a3b43a4f70c86ddf9d825e4f9f25e49f7e332f8..HEAD`
  - baseline `7a3b43a` = the create-story commit; HEAD `6e51484` = the implementation commit.
- **Story / spec file (8 ACs, tasks, dev notes, and the recorded findings):**
  `_bmad-output/implementation-artifacts/2-2-parse-modern-and-legacy-javadoc-layouts-into-equivalent-fields.md`
- **The one production file that matters:**
  `app/ingest-javadoc/src/main/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractor.java`
  (plus the small records `ExtractedType/ExtractedMember/ExtractionResult/Param/ThrownException/Deprecation/MemberKind.java` in the same package)
- **Tests + fixtures:**
  `app/ingest-javadoc/src/test/java/com/luciadmcp/knowledge/ingest/javadoc/JavadocFieldExtractorTest.java`
  `app/ingest-javadoc/src/test/resources/javadoc-fixtures/extract/{jdk8,jdk11,jdk17,broken}/…`

## How to build/run tests

```
cd app && GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT ./gradlew :ingest-javadoc:test checkDependencyDirection
```
`checkDependencyDirection` enforces AD-1 (module dependency direction). The `GRADLE_OPTS` flag works around a proxy PKIX issue in this environment.

## Architectural constraints (flag violations, don't flag the constraints themselves)

- **AD-1:** `ingest.javadoc` must NOT depend on `cli` — no import of `FailurePolicy`/`StageFailure`/`FailureSeverity` or any `com.luciadmcp.knowledge.cli.*`. Degradation is expressed only as a `List<String>` issues channel on `ExtractionResult`.
- **AD-9:** zero egress — `Jsoup.connect` must never appear; local-bytes parse only. (`NoJsoupConnectSourceTest` scans the module source.)
- **AD-4:** never fabricate — missing/unparseable data is recorded as an explicit issue, never guessed.
- **Scope:** this story must NOT mint canonical symbols, persist to SQLite, or wire into the pipeline (those are Stories 2.3/2.4). Do not flag their absence.

## What the code does (1-paragraph orientation)

`JavadocFieldExtractor.extract(Document|Path, relativePath)` guards that the page classifies as `PageType.TYPE` (via the Story 2.1 `JavadocPageClassifier`), then extracts type-level `description`/`@since`/`@deprecated` from a `section.class-description`/`div.description` scope, and per-member fields from member "detail" blocks (modern `section.detail`; legacy `li.blockList` with a direct `<h4>`). Member kind is inferred structurally; tag notes (`Parameters:`/`Returns:`/`Throws:`/`Since:`) come from a `dl.notes`/`dl`. Prose is normalized via `Element.text().trim()`. Broken markup is recorded as issues, never thrown. The headline AC-1 test asserts the whole `ExtractedType` is equal across three fixtures (JDK 8, JDK 11–16, JDK 17+) authored over the same API surface.

---

## Findings to verify (from the same-model review)

For each: confirm it's real by reading the code, judge severity for the real consumer (Story 2.3/2.4 catalog building), and decide patch/defer/dismiss. **Challenge them** — some may be wrong, over/under-rated, or have a better fix.

### Claimed patches

1. **[HIGH] Member-kind self-id match.** `memberKind` (JavadocFieldExtractor.java:175) runs `block.closest("[id*=constructor]/[class*=constructor]")` then `[id*=field]/[class*=field]`. `closest` includes `block` itself, and for modern eras `block` is `<section class="detail" id="<member-signature>">`. So a real method whose signature/anchor contains the substring "field" or "constructor" (e.g. `getField(int)`, `getConstructor()`) self-matches and is tagged `FIELD`/`CONSTRUCTOR`. Legacy classifies the same member correctly via its `<h3>` section heading, so this breaks the AC-1 modern==legacy guarantee on real trees and corrupts Story 2.3 identity.
   - **Verify:** Is `closest` self-inclusive in JSoup 1.23.1? Is JSoup `[attr*=val]` case-insensitive (so `getField` matches `field`)? Are Luciad method names actually likely to contain these substrings? Proposed fix: search from `block.parent()` so the member section's own id is excluded and only the enclosing details-group section (`method-details`/`constructor-details`/`field-details`) is matched. **Does that fix hold for both JDK 11–16 and JDK 17+ container structures, and leave legacy untouched?**

2. **[MED] `Type Parameters:` folded into value params.** `dispatchNote` (line 255) maps both `"parameters"` and `"type parameters"` to `notes.params`, so a generic method's `<T>` becomes positional param slot 0.
   - **Verify:** Does real Javadoc emit a separate `Type Parameters:` `dt`? Is dropping it (map only `"parameters"`) the right call for this story, or should type params be a separate field? (AC lists only "params".)

3. **[MED] Notes `dl` fallback hijacked by an author `<dl>`.** `parseNotes` (line 222) and `notesValue` (line 298) do `selectFirst("dl.notes")` then fall back to `selectFirst("dl")`, which can grab a definition list an author wrote inside the description `.block` → spurious "truncated tag" issue and/or wrong/dropped `@since`.
   - **Verify:** Can a description `.block` contain a `<dl>` in real Javadoc? Is "fall back only to a `<dl>` whose `closest(\".block\") == null`" correct and safe for legacy (where the real notes `dl` is a direct child, not inside `.block`)?

4. **[MED] Signature/prose divergence on U+00A0.** Modern `.member-signature` uses `&nbsp;` (U+00A0) between tokens; `norm()` (line 360, `text().trim()`) neither collapses nor strips U+00A0, while legacy `<pre>` uses ordinary spaces → unequal `signature` strings on real pages.
   - **Verify (empirical):** Does JSoup 1.23.1 `Element.text()` preserve U+00A0 (not collapse it to a normal space)? Does `String.trim()` leave it? If yes, the fix is to normalize U+00A0→space and collapse in `norm()`. **Note:** `signature` is NOT one of AC-1's listed fields (description/params/returns/throws/since/deprecated) — decide whether this is an AC-1 violation or just a robustness improvement for the extra `signature` field / Story 2.3 identity.

5. **[MED] Type-description scope misses `section.description`.** `TYPE_DESC_SCOPE` (line 41) = `"section.class-description, div.description"`. A `<section class="description">` matches neither branch (not a `div`; class is `description` not `class-description`).
   - **Verify:** Does any real Javadoc doclet emit `<section class="description">` for the type description? (Reviewer claimed an intermediate HTML5-era doclet, ~JDK 13–15 — confirm or refute.) If the element is real, the fix (add `section.description`) is trivial and safe; if no doclet emits it, this is a dismiss.

6. **[MED/LOW] Unwrapped deprecation comment dropped; camelCase `deprecationBlock` token absent.** `findDeprecation` (line 331) reads the comment only from a `.deprecation-comment`/`.deprecationComment` element; deprecation prose rendered as bare following text yields `comment=""`. Also `DEPRECATION_BLOCK` (line 46) lacks the camelCase `deprecationBlock` (currently masked because presence is also detected via the label selector).
   - **Verify:** Which form does real Luciad Javadoc use? Is "container text minus the label element's text" a safe fallback, or does it risk swallowing the description on the combined-block case (finding D4 below)?

7. **[LOW] Spurious "missing description" issue for `@deprecated`-only members.** `extractMember` (line 162) always records "missing its description" when the description block is empty, even for a member documented solely via `@deprecated`.
   - **Verify:** Is suppressing that issue when a non-empty deprecation comment is present the right rule, or does it hide genuinely-undocumented members?

### Claimed deferrals (confirm these are safe to defer, not patch)

- Multi-paragraph description across multiple `.block` — only first kept (JavadocFieldExtractor.java:318).
- Nameless `@param`/`@throws` (no `<code>`, no `" - "`) emitted with empty name, no AD-4 issue (line 269) — claim: real Javadoc always wraps names in `<code>`.
- Enum constants classify as `METHOD` (line 175) — `MemberKind` has no enum-constant value; claimed a Story 2.3 model decision.
- Legacy single-block deprecation+description not cleanly separated (line 318).

### Claimed dismissals (confirm these are truly noise)

- JDK 17+ `ul.member-list` "not load-bearing" — `section.detail` is used directly; claimed correct/by-design.
- Fixtures in a new `extract/` tree instead of extending `modern/`/`legacy/` — claimed deliberate.
- AC-5 "unparseable fragment" not represented — claimed N/A because JSoup is lenient.

---

## Specific things the same-model review may have gotten wrong (look here first)

- **JSoup behavior claims (findings 1 and 4) are empirical, not read from the code.** Confirm `closest` self-inclusion + `[attr*=]` case sensitivity, and `text()`'s U+00A0 handling, against JSoup 1.23.1 actual behavior (write a tiny throwaway test if unsure). If either claim is wrong, the finding collapses.
- **Finding 5 depends on a doclet that may not exist.** If no real Javadoc emits `<section class="description">`, downgrade to dismiss.
- **AC-1 equality strength.** The three fixtures use *identical* signature strings and no field-named methods, so the headline equality test passes even though findings 1 and 4 show real trees would diverge. Decide whether that makes the test misleading and whether a regression fixture (a field-named method; an nbsp signature) should be added.
- **Anything not found at all.** All three layers were same-model. Re-scan for correctness bugs, missed edge cases, and AC gaps with fresh eyes.

## When you're done

Report back, per finding: confirmed / rejected / re-rated, with a one-line reason, and your recommended patch or defer. If you confirm the High, note whether the proposed `block.parent()` fix is correct or if you'd fix it differently. That verdict decides which patches get applied.
