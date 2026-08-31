package com.luciadmcp.knowledge.cli;

/**
 * Stable, machine-readable codes for CLI argument-contract failures.
 *
 * <p>These name the way an invocation violated the {@code cli ingest} contract so callers get a
 * clear, non-silent rejection (AC 3). This is deliberately a small, story-scoped set; the full
 * documented exit-code table and the complete {@code serve | validate | stats} error surface are
 * Story 8.3 (Epic 8), not here.
 */
public enum CliErrorCode {
    /** The subcommand is not one this build understands (only {@code ingest} exists so far). */
    UNKNOWN_SUBCOMMAND,
    /** No subcommand was supplied at all. */
    MISSING_SUBCOMMAND,
    /** A flag/option outside the fixed six-option {@code ingest} vocabulary was supplied. */
    UNKNOWN_OPTION,
    /** A recognized option was given without its required directory value. */
    MISSING_OPTION_VALUE,
    /** A required option was omitted. */
    MISSING_REQUIRED_OPTION,
    /** A bare positional argument was supplied where only named options are accepted. */
    UNEXPECTED_POSITIONAL,
    /** An option was supplied more than once. */
    DUPLICATE_OPTION,
    /** A value is not a local filesystem path (URL / {@code scheme://} / UNC / remote form). */
    NON_LOCAL_PATH,
    /** A supplied input directory does not exist or is not a directory. */
    NOT_A_DIRECTORY,
    /** A recognized option was given a value outside its allowed set (e.g. {@code --failure-policy}). */
    INVALID_OPTION_VALUE,
    /** {@code --out} is the same as, or nested within, one of the input directories. */
    OUTPUT_NESTED_IN_INPUT
}
