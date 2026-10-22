package com.luciadmcp.knowledge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC 3: anything outside the fixed six-option {@code ingest} vocabulary is rejected with a clear,
 * non-zero-exit error that names the offending argument — never silently ignored.
 */
class IngestArgumentContractTest {

    private record Result(int exit, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int exit;
        try (PrintStream o = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
                PrintStream e = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            exit = CliMain.run(args, o, e);
        }
        return new Result(exit, outBuf.toString(StandardCharsets.UTF_8),
                errBuf.toString(StandardCharsets.UTF_8));
    }

    private static String[] validOfflineArgs(Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        Path samples = Files.createDirectories(tmp.resolve("samples"));
        Path guides = Files.createDirectories(tmp.resolve("guides"));
        Path model = Files.createDirectories(tmp.resolve("model"));
        Path out = tmp.resolve("out");
        return new String[] {
                "ingest",
                "--javadoc", javadoc.toString(),
                "--samples", samples.toString(),
                "--guides", guides.toString(),
                "--model", model.toString(),
                "--sdk-version", "2024.0",
                "--out", out.toString()
        };
    }

    @Test void omittedModelAllowsOutputUnderWorkingDirectory(@TempDir Path tmp) throws Exception {
        String[] base=validOfflineArgs(tmp);
        java.util.List<String> arguments=new java.util.ArrayList<>();
        for(int i=0;i<base.length;i++) { if(base[i].equals("--model")) { i++;continue; } arguments.add(base[i]); }
        Path out=Path.of("build","mf-"+java.util.UUID.randomUUID().toString().substring(0,8)).toAbsolutePath();
        int index=arguments.indexOf("--out"); arguments.set(index+1,out.toString());
        Result result=run(arguments.toArray(String[]::new));
        assertEquals(CliMain.EXIT_OK,result.exit(),result.err());
    }

    @Test
    void validOfflineInvocationSucceeds(@TempDir Path tmp) throws Exception {
        Result r = run(validOfflineArgs(tmp));
        assertEquals(CliMain.EXIT_OK, r.exit(), () -> "stderr=" + r.err());
    }

    @Test
    void unknownOptionIsRejectedAndNamed(@TempDir Path tmp) throws Exception {
        String[] base = validOfflineArgs(tmp);
        String[] withBogus = new String[base.length + 2];
        System.arraycopy(base, 0, withBogus, 0, base.length);
        withBogus[base.length] = "--bogus";
        withBogus[base.length + 1] = "x";

        Result r = run(withBogus);
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.UNKNOWN_OPTION.name()), () -> r.err());
        assertTrue(r.err().contains("--bogus"), () -> r.err());
    }

    @Test
    void unknownSubcommandIsRejected() {
        Result r = run("frobnicate");
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.UNKNOWN_SUBCOMMAND.name()), () -> r.err());
        assertTrue(r.err().contains("frobnicate"), () -> r.err());
    }

    @Test
    void noSubcommandIsRejected() {
        Result r = run();
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.MISSING_SUBCOMMAND.name()), () -> r.err());
    }

    @Test
    void extraPositionalIsRejected(@TempDir Path tmp) throws Exception {
        String[] base = validOfflineArgs(tmp);
        String[] withPositional = new String[base.length + 1];
        System.arraycopy(base, 0, withPositional, 0, base.length);
        withPositional[base.length] = "stray";

        Result r = run(withPositional);
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.UNEXPECTED_POSITIONAL.name()), () -> r.err());
        assertTrue(r.err().contains("stray"), () -> r.err());
    }

    @Test
    void missingRequiredOptionIsRejected(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        // Omit --samples, --guides, --model, --out.
        Result r = run("ingest", "--javadoc", javadoc.toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.MISSING_REQUIRED_OPTION.name()), () -> r.err());
    }

    @Test
    void missingOptionValueIsRejected(@TempDir Path tmp) throws Exception {
        // --javadoc with no following value (next token is another option).
        Result r = run("ingest", "--javadoc", "--samples", tmp.toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.MISSING_OPTION_VALUE.name()), () -> r.err());
    }

    @Test
    void urlValueIsRejectedAsNonLocal(@TempDir Path tmp) throws Exception {
        Result r = run(
                "ingest",
                "--javadoc", "http://internal.example/docs",
                "--samples", tmp.toString(),
                "--guides", tmp.toString(),
                "--model", tmp.toString(),
                "--out", tmp.resolve("out").toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.NON_LOCAL_PATH.name()), () -> r.err());
        assertTrue(r.err().contains("http://internal.example/docs"), () -> r.err());
    }

    @Test
    void uncNetworkPathIsRejectedAsNonLocal(@TempDir Path tmp) throws Exception {
        Result r = run(
                "ingest",
                "--javadoc", "\\\\fileserver\\share\\docs",
                "--samples", tmp.toString(),
                "--guides", tmp.toString(),
                "--model", tmp.toString(),
                "--out", tmp.resolve("out").toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.NON_LOCAL_PATH.name()), () -> r.err());
    }

    @Test
    void extendedLengthUncPathIsRejectedAsNonLocal(@TempDir Path tmp) throws Exception {
        // \\?\UNC\server\share is the Windows extended-length form of a genuine UNC network path —
        // it starts with \\?\ but must not be waved through as a local device path.
        Result r = run(
                "ingest",
                "--javadoc", "\\\\?\\UNC\\fileserver\\share\\docs",
                "--samples", tmp.toString(),
                "--guides", tmp.toString(),
                "--model", tmp.toString(),
                "--out", tmp.resolve("out").toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.NON_LOCAL_PATH.name()), () -> r.err());
    }

    @Test
    void outputNestedInsideInputDirectoryIsRejected(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        Result r = run(
                "ingest",
                "--javadoc", javadoc.toString(),
                "--samples", Files.createDirectories(tmp.resolve("samples")).toString(),
                "--guides", Files.createDirectories(tmp.resolve("guides")).toString(),
                "--model", Files.createDirectories(tmp.resolve("model")).toString(),
                "--out", javadoc.resolve("nested-out").toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.OUTPUT_NESTED_IN_INPUT.name()), () -> r.err());
    }

    @Test
    void outputSymlinkedIntoInputDirectoryIsRejected(@TempDir Path tmp) throws Exception {
        Path javadoc = Files.createDirectories(tmp.resolve("javadoc"));
        Path realOut = javadoc.resolve("real-out");
        Path outLink = tmp.resolve("out-link");
        try {
            Files.createSymbolicLink(outLink, realOut);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // Creating symlinks requires a privilege this environment/user may not hold (e.g.
            // Windows without Developer Mode or admin) — skip rather than fail the build on that.
            Assumptions.abort("symlink creation not permitted in this environment: " + e.getMessage());
            return;
        }

        Result r = run(
                "ingest",
                "--javadoc", javadoc.toString(),
                "--samples", Files.createDirectories(tmp.resolve("samples")).toString(),
                "--guides", Files.createDirectories(tmp.resolve("guides")).toString(),
                "--model", Files.createDirectories(tmp.resolve("model")).toString(),
                "--out", outLink.toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.OUTPUT_NESTED_IN_INPUT.name()), () -> r.err());
    }

    @Test
    void nonExistentInputDirectoryIsRejected(@TempDir Path tmp) throws Exception {
        Path missing = tmp.resolve("does-not-exist");
        Result r = run(
                "ingest",
                "--javadoc", missing.toString(),
                "--samples", tmp.toString(),
                "--guides", tmp.toString(),
                "--model", tmp.toString(),
                "--out", tmp.resolve("out").toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.NOT_A_DIRECTORY.name()), () -> r.err());
    }

    @Test
    void duplicateOptionIsRejected(@TempDir Path tmp) throws Exception {
        String[] base = validOfflineArgs(tmp);
        String[] withDuplicate = new String[base.length + 2];
        System.arraycopy(base, 0, withDuplicate, 0, base.length);
        withDuplicate[base.length] = "--javadoc";
        withDuplicate[base.length + 1] = tmp.toString();

        Result r = run(withDuplicate);
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.DUPLICATE_OPTION.name()), () -> r.err());
        assertTrue(r.err().contains("--javadoc"), () -> r.err());
    }

    @Test
    void missingOptionValueWhenOptionIsLastTokenIsRejected(@TempDir Path tmp) throws Exception {
        // --out with no following value at all (option is the last token, not just followed by
        // another option).
        Result r = run(
                "ingest",
                "--javadoc", tmp.toString(),
                "--samples", tmp.toString(),
                "--guides", tmp.toString(),
                "--model", tmp.toString(),
                "--out");
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.MISSING_OPTION_VALUE.name()), () -> r.err());
        assertTrue(r.err().contains("--out"), () -> r.err());
    }

    @Test
    void outputPathExistingAsFileIsRejected(@TempDir Path tmp) throws Exception {
        Path out = Files.createFile(tmp.resolve("out"));
        Result r = run(
                "ingest",
                "--javadoc", Files.createDirectories(tmp.resolve("javadoc")).toString(),
                "--samples", Files.createDirectories(tmp.resolve("samples")).toString(),
                "--guides", Files.createDirectories(tmp.resolve("guides")).toString(),
                "--model", Files.createDirectories(tmp.resolve("model")).toString(),
                "--sdk-version", "2024.0",
                "--out", out.toString());
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains(CliErrorCode.NOT_A_DIRECTORY.name()), () -> r.err());
        assertTrue(r.err().contains("--out"), () -> r.err());
    }

    @Test
    void trainingsOptionReachesUnimplementedSeamAndFailsCleanly(@TempDir Path tmp) throws Exception {
        String[] base = validOfflineArgs(tmp);
        Path trainings = Files.createDirectories(tmp.resolve("trainings"));
        String[] withTrainings = new String[base.length + 2];
        System.arraycopy(base, 0, withTrainings, 0, base.length);
        withTrainings[base.length] = "--trainings";
        withTrainings[base.length + 1] = trainings.toString();

        Result r = run(withTrainings);
        assertEquals(CliMain.EXIT_USAGE_ERROR, r.exit());
        assertTrue(r.err().contains("AD-28"), () -> r.err());
    }
}
