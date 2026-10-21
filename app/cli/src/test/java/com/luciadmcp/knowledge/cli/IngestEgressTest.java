package com.luciadmcp.knowledge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luciadmcp.knowledge.cli.testsupport.EgressGuard;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The core deliverable (AC 1): an offline {@code cli ingest} run performs zero DNS/host egress, and
 * the guard that proves it actually trips on a real attempt (so the harness cannot pass vacuously).
 */
class IngestEgressTest {

    @AfterEach
    void disarmGuard() {
        EgressGuard.disarm();
    }

    /**
     * Negative self-test: the guard must be installed and must trip on a deliberate lookup. This is
     * also the fail-loud check that the ServiceLoader wired the provider — without it the "no
     * egress" assertion below would be meaningless.
     */
    @Test
    void guardIsInstalledAndTripsOnDeliberateEgress() {
        // Fails loudly if the provider was not loaded (guard silently absent).
        EgressGuard.assertInstalledAndTrips();

        // And directly: an armed lookup is blocked and recorded.
        EgressGuard.arm();
        String host = "deliberate-egress-" + System.nanoTime() + ".invalid";
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName(host));
        assertFalse(EgressGuard.violations().isEmpty(),
                "guard must record the deliberate egress attempt");
        assertTrue(EgressGuard.violations().stream().anyMatch(v -> v.contains(host)),
                "recorded violation must name the attempted host");
    }

    /** An offline ingest over the five synthetic local paths attempts no host resolution. */
    @Test
    void noEgressDuringOfflineIngest(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectory(tmp.resolve("javadoc"));
        Path samples = Files.createDirectory(tmp.resolve("samples"));
        Path guides = Files.createDirectory(tmp.resolve("guides"));
        Path model = Files.createDirectory(tmp.resolve("model"));
        Path out = tmp.resolve("out"); // created by the command
        // A trivial file so the fixtures are not merely empty.
        Files.writeString(javadoc.resolve("placeholder.txt"), "synthetic");

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

        EgressGuard.arm();
        int exit;
        try (PrintStream o = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
                PrintStream e = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            exit = CliMain.run(new String[] {
                    "ingest",
                    "--javadoc", javadoc.toString(),
                    "--samples", samples.toString(),
                    "--guides", guides.toString(),
                    "--model", model.toString(),
                    "--sdk-version", "2024.0",
                "--out", out.toString()
            }, o, e);
        } finally {
            EgressGuard.disarm();
        }

        assertEquals(CliMain.EXIT_OK, exit,
                () -> "offline ingest should succeed; stderr=" + errBuf);
        assertTrue(EgressGuard.violations().isEmpty(),
                () -> "offline ingest must not attempt egress; violations=" + EgressGuard.violations());
        assertTrue(Files.isDirectory(out), "output directory should have been created");
    }
}
