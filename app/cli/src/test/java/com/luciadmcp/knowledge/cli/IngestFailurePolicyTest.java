package com.luciadmcp.knowledge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.pipeline.FailurePolicy;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC 2 + AC 3: the optional {@code --failure-policy} flag is parsed and the chosen policy reaches
 * the pipeline; an out-of-range value is rejected rather than silently ignored.
 */
class IngestFailurePolicyTest {

    /** Builds the tokens after the {@code ingest} subcommand for a valid offline invocation. */
    private static String[] ingestArgs(Path tmp, String... extra) throws Exception {
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

    /** No flag: the runner receives the default policy, FAIL_FAST. */
    @Test
    void defaultsToFailFastWhenFlagAbsent(@TempDir Path tmp) throws Exception {
        AtomicReference<FailurePolicy> seen = new AtomicReference<>();
        IngestCommand command = IngestCommand.withPipelineFactory(policy -> {
            seen.set(policy);
            return args -> { };
        });
        command.run(ingestArgs(tmp));
        assertEquals(FailurePolicy.FAIL_FAST, seen.get());
    }

    /** {@code --failure-policy collect-and-continue} reaches the runner factory. */
    @Test
    void collectAndContinueFlagReachesPipeline(@TempDir Path tmp) throws Exception {
        AtomicReference<FailurePolicy> seen = new AtomicReference<>();
        IngestCommand command = IngestCommand.withPipelineFactory(policy -> {
            seen.set(policy);
            return args -> { };
        });
        command.run(ingestArgs(tmp, "--failure-policy", "collect-and-continue"));
        assertEquals(FailurePolicy.COLLECT_AND_CONTINUE, seen.get());
    }

    /** {@code --failure-policy fail-fast} reaches the runner factory. */
    @Test
    void failFastFlagReachesPipeline(@TempDir Path tmp) throws Exception {
        AtomicReference<FailurePolicy> seen = new AtomicReference<>();
        IngestCommand command = IngestCommand.withPipelineFactory(policy -> {
            seen.set(policy);
            return args -> { };
        });
        command.run(ingestArgs(tmp, "--failure-policy", "fail-fast"));
        assertEquals(FailurePolicy.FAIL_FAST, seen.get());
    }

    /** An out-of-range policy value is rejected with a named usage error, not silently ignored. */
    @Test
    void invalidPolicyValueIsRejectedAndNamed(@TempDir Path tmp) throws Exception {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        String[] full = new String[ingestArgs(tmp, "--failure-policy", "bogus").length + 1];
        full[0] = "ingest";
        System.arraycopy(ingestArgs(tmp, "--failure-policy", "bogus"), 0, full, 1, full.length - 1);

        int exit;
        try (PrintStream o = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
                PrintStream e = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            exit = CliMain.run(full, o, e);
        }

        assertEquals(CliMain.EXIT_USAGE_ERROR, exit);
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains(CliErrorCode.INVALID_OPTION_VALUE.name()),
                () -> errBuf.toString(StandardCharsets.UTF_8));
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("bogus"),
                () -> errBuf.toString(StandardCharsets.UTF_8));
    }
}
