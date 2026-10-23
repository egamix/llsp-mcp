---
title: 'Story 5.8 / 7.1 Javadoc agent guidance'
type: feature
created: '2026-10-22'
status: done
route: dispatch
story_key: 5-8-draft-the-agent-retrieval-policy-skeleton
baseline_commit: '4f5b1904b4a2607052f1ca7e9125eeb83531b7f1'
review_loop_iteration: 0
context:
  - C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp/_bmad-output/implementation-artifacts/epic-5-context.md
---

<frozen-after-approval reason="User requested usable BMad MVP and identified missing agent guidance">

## Intent

Make the running Javadoc MCP useful without repeatedly telling the agent to consult it. Ship a short automatically discoverable Luciad skill and MCP initialization instructions that tell agents to retrieve source evidence before making Luciad SDK claims or generating Luciad API calls. This completes the three-tool guidance slice of existing5.8/7.1; broader six-tool/evaluation acceptance stays open.

## Boundaries & Constraints

The workflow is retrieve relevant knowledge, resolve exact symbols/overloads when needed, fetch saved documentation, generate a grounded answer/code, then compile/test externally where available and repair from evidence. Only advertise search_knowledge, resolve_symbol and get_record; no placeholder checks/relationship tools. All requests include contractVersion read-tools/2. Citation paths/anchors, actual source/build, ambiguity, partial coverage and unavailable evidence remain truthful. A target/source version mismatch never blocks retrieval or causes an automatic warning. Missing tools/service must be reported instead of silently inventing Luciad-specific API details from memory. Ordinary non-L Luciad programming knowledge remains available. Do not claim instructions mechanically guarantee model obedience.

Use a concise SKILL.md description to match Luciad questions, implementation and debugging, with normal automatic invocation. Declare the existing local MCP dependency in agents/openai.yaml. Add self-contained server instructions to BOTH HTTP and optional stdio SDK builders so connected clients receive retrieval-first guidance even without installing a skill. First512characters must state the important workflow. Give each actual tool a useful distinct description. Preserve all schemas, limits, runtime configuration and storage. No new framework, tool, plugin installer or agent runtime.

## I/O & Edge-Case Matrix

| Scenario | Expected behavior |
|---|---|
| Luciad coding/API question | Skill selects retrieval-first workflow; actual tool names and request contract usable |
| MCP initialize | Nonempty useful instructions arrive with actual three tools for HTTP and stdio configuration |
| Missing evidence, ambiguity, unavailable tools | Honest unresolved/unavailable outcome; no invented API or hidden memory fallback |
| Target differs from source | Use actual-source evidence normally; preserve citations without automatic mismatch warning |
| General programming question | Skill description does not claim all coding work |

</frozen-after-approval>

## Code Map

- app/mcp/.../McpEndpoint.java: SDK builders at HTTP/stdio construction; current generic tool descriptions. Add one small shared instructions text/resource, no transport refactor. Existing caller graph includes RuntimeCommands, MvpConnectionTest, McpEndpointAdmissionTest.
- app/cli/.../pipeline/MvpConnectionTest.java: real SDK initialize/list/call; add meaningful initialization/tool-discovery coverage. app/mcp tests cover transport admission; preserve them.
- .agents/skills/luciad/SKILL.md and agents/openai.yaml: new distributable source skill, no generic auxiliary scaffold files. Skill-creator rules: concise discriminating trigger, normal implicit invocation, quote YAML strings; MCP dependency value luciad, streamable_http, URL http://127.0.0.1:8766/mcp.
- docs/MVP-JAVADOC-MCP.md, sprint-status.yaml: explain installation and explicit invocation; mark5.8 and7.1 in-progress for this Javadoc slice, broader acceptance pending.

## Tasks & Acceptance

- [x] Create skill and dependency metadata with concrete three-tool workflow and a small grounded request example; no invented SDK example names.
- [x] Wire shared server initialization guidance and distinct tool descriptions; SDK integration confirms guidance arrives and tools remain callable.
- [x] Validate skill with C:/Users/UserAdmin/.codex/skills/.system/skill-creator/scripts/quick_validate.py; run affected CLI connection/MCP tests and installDist; update guide/tracker and verify scope against matrix.

Given a client connecting to this server, when it initializes, then it receives retrieval-first instructions and descriptions for only the three implemented tools. Given a Luciad task, when the skill is used, then it directs source-backed retrieval and preserves unresolved evidence. Instructions support rather than guarantee model behavior.

## Implementation Notes

Work only in C:/Users/UserAdmin/.codex/worktrees/story-2-10-structured-documentation/luciad-mcp, current feature branch. C: writes/tests/Git require exec_command require_escalated. Preserve original D: dirty work. Commit cohesive changes/tests separately with story scope; no squash/push. Parent owns user-level skill installation and non-destructive local Codex MCP configuration after verification. Current live Java45796 must be stopped by parent before installDist. Do not edit any global configuration yourself.

## Verification

Affected SDK connection/admission tests, skill validation, parent review and real-shape initialization/query smoke. Existing Windows env: GRADLE_USER_HOME=C:/Users/UserAdmin/.gradle; GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT; JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/workspace/luciad-mcp/.nonexistent-unix-socket-dir. Refresh graft after code edits. No proprietary fixtures committed.

## Spec Change Log

## Review Triage Log

Parent verification: skill quick_validate PASS; installed user-level skill and dependency metadata after confirming the destination was absent; installed hashes match reviewed source. Existing Codex luciad configuration already points at the expected local URL and was left intact. Live rebuilt server33212 initialization returned retrieval-first guidance, and real-shape search/resolve/fetch plus canonical Python schema/semantic checks passed. Independent focused review returned no high-confidence findings; nothing deferred by review. Natural-language model obedience and controlled6-tool/evaluation acceptance are not claimed. This specification completes the Javadoc slice; full5.8/7.1 tracking remains in-progress pending their broader acceptance.

CLI discovery correction (2026-10-22): moved the unchanged user skill from .codex/skills/luciad to .agents/skills/luciad, the documented CLI user discovery root. Fresh installed codex-cli0.163.0 app-server skills/list calls, without extra skill roots, find luciad enabled from both C:/Users/UserAdmin and D:/workspace/luciad-mcp with zero discovery errors. No MCP/runtime configuration changed.
