/**
 * CLI composition root: {@code ingest | serve | validate} entrypoints. The only module
 * allowed to wire {@code config}, all {@code ingest.*} modules, and {@code mcp} together
 * (AD-1).
 *
 * <p>Story 1.2 ships the {@code ingest} subcommand shell and its six-local-path argument
 * contract (FR-1) only. The {@code serve}/{@code validate} subcommands, {@code --help}, and the
 * full documented exit-code table are Story 8.3 (Epic 8).
 */
package com.luciadmcp.knowledge.cli;
