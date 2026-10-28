<div align="center">

# 🛰️ Luciad Knowledge

### Your coding agent. Your documentation. Fewer invented APIs.

Turn SDK documentation, examples, and engineering guidance into knowledge your coding agent can actually use.

**Find the approach. Get the right APIs. Build with evidence.**

[Why it matters](#-why-luciad-knowledge) · [How it works](#-how-it-works) · [Get started](#-get-started) · [Example prompts](#-try-these-prompts)

</div>

---

Your agent knows Java. That does not mean it knows your SDK.

A method can look perfectly reasonable and still not exist. An example can be relevant but omit the initialization that makes it work. A correct API call can still violate a threading or resource-lifecycle rule buried in the documentation.

**Luciad Knowledge gives your agent the evidence before it makes those decisions.**

It combines an agent skill with a local knowledge service: the skill guides the development workflow; the service supplies documentation, connected examples, and API checks through MCP.

## ⚡ Ask for the outcome

> Use project knowledge to add asynchronous dataset loading with cancellation. Find a relevant example, follow its threading and cleanup requirements, implement the change, then build and test it.

The skill guides your agent to:

1. **Find** the relevant explanation and example.
2. **Read** the prerequisites, signatures, warnings, and constraints.
3. **Resolve** the APIs instead of guessing names or overloads.
4. **Implement** the change in your project.
5. **Check, compile, and test**—then repair using the actual failures.

You get a code change, evidence behind the important choices, and the build/test results. You stay in your coding assistant.

## ✨ Why Luciad Knowledge?

| | What it means for you |
|---|---|
| 🎯 **Precise API knowledge** | Distinct overloads, documented signatures, and explicit unknown or ambiguous references. |
| 📚 **Documentation that keeps its meaning** | Code formatting, instructions, tables, warnings, and API notes preserved through ingestion. |
| 🧩 **Connected context** | Move from an explanation to the relevant API, related types, and usage examples. |
| 🔎 **Search by intention** | Ask how to accomplish a task without already knowing the class or method name. |
| 🧾 **Evidence you can follow** | Source citations accompany retrieved content; uncertainty and limited coverage stay visible. |
| 🔒 **Knowledge under your control** | Local inputs and a local or trusted internal service, including offline environments. |

Project configuration selects the appropriate documentation once. You should not have to repeat library-release settings in every conversation.

## 🧠 How it works

```text
Your documentation + examples + selected guidance
                       │
                Ingest and validate
                       │
              Local knowledge service
                       │ MCP
                  Agent skill
                       │
           Your usual coding assistant
                       │
          Retrieve → Implement → Check
                       │
             Project compiler + tests
```

The knowledge service supplies evidence and checks documented API references. The skill tells the agent how to use it. Your agent writes the code and runs your project's tools.

**An API existing is not proof that the whole program is correct.** Documentation, compilation, tests, and human review each have a role.

## 🚀 Get started

You need two things: a running **luciad server** with your SDK's documentation loaded, and the
**luciad plugin** in your coding agent. The plugin contains the skill and the connection to the
server, so you install one thing per agent.

### 1. Run the server

Build it, load your SDK's Javadoc and samples, and start it. Follow the
[server guide](docs/server-guide.md). It serves at `http://127.0.0.1:8766/mcp` by default.
The server accepts connections from the same machine only, so run it where the agent runs.

### 2. Install the plugin

This repository is a plugin marketplace for both Claude Code and Codex. Use the repository URL
or a local clone path as `<marketplace>`.

**Claude Code**

```bash
claude plugin marketplace add <marketplace>
claude plugin install luciad@luciad-mcp
```

The server URL defaults to `http://127.0.0.1:8766/mcp`. For another URL, add
`--config server_url=http://127.0.0.1:<port>/mcp` to the install command, or later run
`/plugin configure luciad@luciad-mcp` in a session. Inside a session you can also install with
`/plugin marketplace add <marketplace>` and `/plugin install luciad@luciad-mcp`, which asks for the URL.

**Codex**

```bash
codex plugin marketplace add <marketplace>
codex plugin add luciad@luciad-mcp
```

Codex uses `http://127.0.0.1:8766/mcp`. For another URL, add this to `~/.codex/config.toml`;
it replaces the plugin's URL:

```toml
[mcp_servers.luciad]
url = "http://127.0.0.1:<port>/mcp"
```

### 3. Check it works

Restart the agent. In Claude Code, `/mcp` should list `plugin:luciad:luciad` as connected; in
Codex, `codex mcp list` should list `luciad`. Then ask:

> Which Luciad version are you answering from? Show the signature of TLspShapeLayerBuilder#newBuilder() with its source.

The plugin's skill, `luciad-sdk`, loads by itself for Luciad tasks. To call it explicitly in
Claude Code, run `/luciad:luciad-sdk`; in Codex, type `$` and pick `luciad-sdk`.

<details>
<summary><strong>Update, uninstall, and moving from a manual setup</strong></summary>

| | Claude Code | Codex |
|---|---|---|
| Update | `claude plugin marketplace update luciad-mcp` | `codex plugin marketplace upgrade luciad-mcp` (Git marketplaces; a local path is read in place) |
| Uninstall | `claude plugin uninstall luciad@luciad-mcp` | `codex plugin remove luciad@luciad-mcp` |

If you set up luciad by hand before the plugin existed, remove the manual copies so the agent
doesn't get two of them:

- Claude Code: `claude mcp remove luciad`, and delete `~/.claude/skills/luciad` if present.
- Codex: delete `~/.agents/skills/luciad`. A `[mcp_servers.luciad]` entry in
  `~/.codex/config.toml` can stay only if you need it to change the URL; otherwise remove it.

</details>

### Build from source

Requires **JDK 21** and Git. Run the checked-in Gradle wrapper from `app/`.

**Windows PowerShell**

```powershell
java -version
cd app
.\gradlew.bat check
```

**Linux / macOS**

```bash
java -version
cd app
./gradlew check
```

This builds and runs the tests. To produce the server command, run `.\gradlew.bat :cli:installDist` (Windows) or `./gradlew :cli:installDist`; the [server guide](docs/server-guide.md) continues from there.

## 💬 Try these prompts

### Build something

> Use project knowledge to implement [feature]. Find the documented approach and a usable example first. Follow the prerequisites, error handling, and cleanup rules. Make the change, check the APIs, and run the relevant tests.

### Find the right approach

> How should I implement [task] with the libraries in this project? Compare the documented options, explain the tradeoffs, and cite the relevant sources. Say what the evidence does not establish.

### Get an example you can actually use

> Find an example of [task]. Retrieve the complete relevant method or call sequence, including initialization and cleanup. Include necessary helper context instead of an isolated snippet.

### Fix a failure

> This change fails with [error]. Check the referenced APIs and retrieve relevant parameter rules, exceptions, and examples. Make the smallest justified fix and run the applicable project tests.

### Check existing code

> Compare [files or code] with our documentation. Check API existence, overload choices, and documented constraints. Separate confirmed findings from suggestions and anything requiring runtime testing.

### Make uncertainty explicit

> If our documentation does not establish an API or behavior, say so. Do not treat a similarly named result as an exact match. Retrieve more evidence or ask for the missing context.

## 📦 Bring your team's knowledge

**One person maintains the knowledge collection. Everyone uses it through their assistant.**

| Bring | Preserve |
|---|---|
| Complete local Javadocs | API identities, descriptions, signatures, constraints, and source locations. |
| Selected Java examples | Usable source, call sequences, supporting context, and setup notes. |
| Selected guides | Explanations, prerequisites, ordered instructions, and limitations. |
| Approved practices and workflows | Reviewed recommendations, applicability, and dependencies. |

<details>
<summary><strong>Maintainer workflow</strong></summary>

1. **Collect authorized local sources.** Preserve the documentation tree and supporting files. Keep material for different library releases separate.
2. **Configure the import.** Register the relevant source locations and a dedicated output location outside every input directory.
3. **Import and inspect.** Review included, excluded, partial, unsupported, and failed content. Check unresolved references and missing sections.
4. **Validate and activate.** Serve a validated build rather than a partially built collection. Incompatible or corrupt data must not be treated as usable.
5. **Share the connection.** Give developers the MCP connection details and any deployment-required credentials.
6. **Update deliberately.** Build and validate replacement knowledge before activating it when dependencies or source material change.

Retaining a link does not mean its destination was imported. The service does not crawl arbitrary websites or invent missing content.

Stored knowledge must remain retrievable without re-reading the original input directory. Ingestion coverage must describe what was actually preserved, not merely report that a command finished.

The [server guide](docs/server-guide.md) has the commands for each step.

</details>

## ❓ A few useful distinctions

**Is this a skill or an MCP server?**

Both. The skill is the agent-facing workflow. MCP connects it to the service that stores and retrieves knowledge.

**Do I need to retrain my model?**

No. Your agent retrieves project evidence during the task.

**Do I need an embedding model?**

Not for exact and lexical retrieval.

**Does “no results” mean an API does not exist?**

No. Search relevance and catalog-backed API resolution are different operations. The agent must distinguish missing results, incomplete coverage, ambiguity, and a confirmed exact match.

**Does it guarantee correct code?**

No. It supplies evidence and API checks; your compiler, tests, and review validate the resulting application.

---

[Development guide](docs/development-guide.md) · [Documentation map](docs/index.md)

**Less documentation hunting. Fewer plausible guesses. Better evidence behind the code.**
