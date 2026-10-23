package com.luciadmcp.knowledge.cli;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * CLI composition-root entrypoint. Story 1.2 wires only the {@code ingest} subcommand and its
 * six-path argument contract; {@code serve} and {@code validate}, {@code --help}, and the full
 * documented exit-code table are Story 8.3 (Epic 8).
 *
 * <p>{@link #run(String[], PrintStream, PrintStream)} returns an exit code instead of calling
 * {@code System.exit}, so the contract is unit-testable without spawning a process. Only the thin
 * {@link #main(String[])} wrapper exits the JVM.
 */
public final class CliMain {

    /** Successful invocation. */
    public static final int EXIT_OK = 0;
    /** Argument-contract violation (AC 3). A single usage-error code suffices for this story; the
     *  full exit-code table is Story 8.3. */
    public static final int EXIT_USAGE_ERROR = 2;

    private static boolean SetRuntimeCommands(String command) { return java.util.Set.of("serve","validate","activate","publish-staged","query").contains(command); }
    private static String safeRuntimeReason(RuntimeException e) {
        String message=e.getMessage();
        if(message!=null && (message.startsWith("BUILD_INVALID:") || message.startsWith("INVALID_CONFIGURATION")))return message;
        // Operator output (stderr), not agent output: name the cause so the operator can act on it.
        Throwable deepest=e;while(deepest.getCause()!=null && deepest.getCause()!=deepest)deepest=deepest.getCause();
        return "INVALID_REQUEST_OR_RUNTIME_FAILURE: "+e.getClass().getSimpleName()+(message==null?"":": "+message)
            +(deepest!=e?" (cause: "+deepest.getClass().getSimpleName()+": "+deepest.getMessage()+")":"");
    }
    private CliMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * @param args the full argument vector, including the subcommand token
     * @param out  standard output stream
     * @param err  standard error stream
     * @return {@link #EXIT_OK} on success, {@link #EXIT_USAGE_ERROR} on an argument-contract failure
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println(CliError.of(
                    CliErrorCode.MISSING_SUBCOMMAND, "no subcommand; expected: ingest").render());
            return EXIT_USAGE_ERROR;
        }

        String subcommand = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        try {
            switch (subcommand) {
                case "ingest" -> new IngestCommand().run(rest);
                case "serve","validate","activate","publish-staged","query" -> RuntimeCommands.run(subcommand,rest,out);
                default -> throw new CliException(CliError.of(
                        CliErrorCode.UNKNOWN_SUBCOMMAND, "unknown subcommand; expected: ingest",
                        subcommand));
            }
            return EXIT_OK;
        } catch (CliException e) {
            err.println(e.error().render());
            return EXIT_USAGE_ERROR;
        } catch (RuntimeException e) {
            err.println("error: " + (SetRuntimeCommands(subcommand) ? "Runtime command failed: " + safeRuntimeReason(e) : e.getMessage()));
            return EXIT_USAGE_ERROR;
        }
    }
}
