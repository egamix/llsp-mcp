package com.luciadmcp.knowledge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The optional {@code --sdk-version} input (AD-12): a valid value parses onto
 * {@link IngestArguments#sdkVersion()}, and a blank or path-unsafe value is rejected with a named
 * usage error rather than silently accepted (the argument-contract half of the AD-25 staging-escape
 * guard).
 */
class SdkVersionArgumentTest {

    /** Tokens after the {@code ingest} subcommand for a valid offline invocation. */
    private static String[] ingestTokens(Path tmp, String... extra) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        Path samples = Files.createDirectories(tmp.resolve("samples"));
        Path guides = Files.createDirectories(tmp.resolve("guides"));
        Path model = Files.createDirectories(tmp.resolve("model"));
        Path out = tmp.resolve("out");
        String[] base = {
                "--javadoc", javadoc.toString(),
                "--samples", samples.toString(),
                "--guides", guides.toString(),
                "--model", model.toString(),
                "--out", out.toString()
        };
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    /** A valid value reaches IngestArguments.sdkVersion. */
    @Test
    void validSdkVersionParsesOntoArguments(@TempDir Path tmp) throws Exception {
        AtomicReference<IngestArguments> seen = new AtomicReference<>();
        IngestCommand command = IngestCommand.withPipelineFactory(policy -> seen::set);
        command.run(ingestTokens(tmp, "--sdk-version", "2024.0"));
        assertEquals(Optional.of("2024.0"), seen.get().sdkVersion());
    }

    /** Absent means empty (the CATALOG stage then aborts the build downstream). */
    @Test
    void absentSdkVersionIsEmpty(@TempDir Path tmp) throws Exception {
        AtomicReference<IngestArguments> seen = new AtomicReference<>();
        IngestCommand command = IngestCommand.withPipelineFactory(policy -> seen::set);
        command.run(ingestTokens(tmp));
        assertEquals(Optional.empty(), seen.get().sdkVersion());
    }

    /** A blank value is rejected with INVALID_OPTION_VALUE. */
    @Test
    void blankSdkVersionIsRejected(@TempDir Path tmp) throws Exception {
        assertRejected(tmp, " ");
    }

    /** A path-traversal / separator-bearing value is rejected (AD-25 staging-escape guard). */
    @Test
    void pathUnsafeSdkVersionIsRejected(@TempDir Path tmp) throws Exception {
        assertRejected(tmp, "../evil");
    }

    private static void assertRejected(Path tmp, String badVersion) throws Exception {
        String[] tokens = ingestTokens(tmp, "--sdk-version", badVersion);
        String[] full = new String[tokens.length + 1];
        full[0] = "ingest";
        System.arraycopy(tokens, 0, full, 1, tokens.length);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int exit;
        try (PrintStream o = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
                PrintStream e = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            exit = CliMain.run(full, o, e);
        }
        assertEquals(CliMain.EXIT_USAGE_ERROR, exit);
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains(CliErrorCode.INVALID_OPTION_VALUE.name()),
                () -> errBuf.toString(StandardCharsets.UTF_8));
    }
}
