/**
 * CLI composition root: {@code ingest | serve | validate} entrypoints. The only module
 * allowed to wire {@code config}, all {@code ingest.*} modules, and {@code mcp} together
 * (AD-1). Entrypoints and exit codes land in Epic 8.
 */
package com.luciadmcp.knowledge.cli;
