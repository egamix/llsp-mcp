package com.luciadmcp.knowledge.cli;

import com.luciadmcp.knowledge.cli.pipeline.BuildContext;
import com.luciadmcp.knowledge.cli.pipeline.FailurePolicy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses the argument vector of a {@code cli ingest} invocation into {@link IngestArguments}.
 *
 * <p>The accepted vocabulary is the six FR-1 path options plus the optional orchestration flag
 * {@code --failure-policy} ({@code fail-fast} | {@code collect-and-continue}, default
 * {@code collect-and-continue}) and nothing else. Anything outside it — an unknown flag, a bare positional, a
 * repeated option, a missing value, a path value that is not a local filesystem path, or an
 * out-of-range {@code --failure-policy} value — is rejected with a structured {@link CliError}
 * (AC 3). The failure policy is per-run orchestration configuration, not a serve-time setting, so it
 * is returned alongside {@link IngestArguments} in {@link IngestInvocation} rather than added to the
 * six-path record (AD-16). Parsing is hand-rolled on purpose: no CLI-parsing library is on the
 * pinned Stack, and the formal {@code --help} / exit-code surface is Story 8.3.
 *
 * <p>This class does argument-shape and local-path-form validation only. Filesystem checks
 * (existence, directory-ness, creating the output dir) live in {@link IngestCommand} so that the
 * argument contract can be tested without touching disk.
 */
public final class IngestArgumentParser {

    static final String OPT_JAVADOC = "--javadoc";
    static final String OPT_SAMPLES = "--samples";
    static final String OPT_GUIDES = "--guides";
    static final String OPT_MODEL = "--model";
    static final String OPT_OUT = "--out";
    static final String OPT_TRAININGS = "--trainings";
    static final String OPT_FAILURE_POLICY = "--failure-policy";
    static final String OPT_SDK_VERSION = "--sdk-version";

    static final String POLICY_FAIL_FAST = "fail-fast";
    static final String POLICY_COLLECT_AND_CONTINUE = "collect-and-continue";

    /** URL/scheme form, e.g. {@code http://}, {@code file://}, {@code s3://}. */
    private static final Pattern URL_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*://");

    private IngestArgumentParser() {
    }

    /**
     * @param ingestArgs the tokens after the {@code ingest} subcommand
     * @return the validated six-path arguments plus the chosen failure policy
     * @throws CliException if the tokens violate the ingest option contract
     */
    public static IngestInvocation parse(String[] ingestArgs) {
        Map<String, String> values = new LinkedHashMap<>();

        int i = 0;
        while (i < ingestArgs.length) {
            String token = ingestArgs[i];
            if (!isKnownOption(token)) {
                if (token.startsWith("-")) {
                    throw new CliException(CliError.of(
                            CliErrorCode.UNKNOWN_OPTION,
                            "unknown option; ingest accepts only "
                                    + "--javadoc, --samples, --guides, --model, --out, --trainings, "
                                    + "--failure-policy, --sdk-version",
                            token));
                }
                throw new CliException(CliError.of(
                        CliErrorCode.UNEXPECTED_POSITIONAL,
                        "unexpected positional argument; ingest takes only named options",
                        token));
            }
            if (values.containsKey(token)) {
                throw new CliException(CliError.of(
                        CliErrorCode.DUPLICATE_OPTION, "option supplied more than once", token));
            }
            if (i + 1 >= ingestArgs.length) {
                throw new CliException(CliError.of(
                        CliErrorCode.MISSING_OPTION_VALUE, "option requires a directory value", token));
            }
            String value = ingestArgs[i + 1];
            // A value that itself looks like an option means the previous option's value is missing.
            if (isKnownOption(value) || value.startsWith("--")) {
                throw new CliException(CliError.of(
                        CliErrorCode.MISSING_OPTION_VALUE, "option requires a directory value", token));
            }
            // --failure-policy and --sdk-version carry scalar values, not filesystem paths; skip
            // the path-form checks for them.
            if (!token.equals(OPT_FAILURE_POLICY) && !token.equals(OPT_SDK_VERSION)) {
                requireLocalPath(token, value);
            }
            values.put(token, value);
            i += 2;
        }

        Path javadoc = required(values, OPT_JAVADOC);
        Path samples = required(values, OPT_SAMPLES);
        Path guides = required(values, OPT_GUIDES);
        Path model = Optional.ofNullable(values.get(OPT_MODEL)).map(Path::of).orElse(Path.of("."));
        Path out = required(values, OPT_OUT);
        Optional<Path> trainings = Optional.ofNullable(values.get(OPT_TRAININGS)).map(Path::of);
        Optional<String> sdkVersion = parseSdkVersion(values.get(OPT_SDK_VERSION));
        FailurePolicy policy = parsePolicy(values.get(OPT_FAILURE_POLICY));

        return new IngestInvocation(
                new IngestArguments(javadoc, samples, guides, model, out, trainings, sdkVersion), policy);
    }

    private static boolean isKnownOption(String token) {
        return switch (token) {
            case OPT_JAVADOC, OPT_SAMPLES, OPT_GUIDES, OPT_MODEL, OPT_OUT, OPT_TRAININGS,
                    OPT_FAILURE_POLICY, OPT_SDK_VERSION -> true;
            default -> false;
        };
    }

    /**
     * Maps the optional {@code --sdk-version} value to an {@link Optional}. Absent means empty (the
     * build then scopes records under a default version label; it does not abort). The value becomes a
     * directory segment under the build's staging area, so a blank
     * value or one that is not a safe single path segment (separators, {@code .}/{@code ..},
     * filename-illegal characters) is rejected rather than treated as a version — this is the
     * argument-contract half of the AD-25 staging-escape guard enforced in {@link BuildContext}.
     */
    private static Optional<String> parseSdkVersion(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (!BuildContext.isSafeSdkVersionSegment(value)) {
            throw new CliException(CliError.of(
                    CliErrorCode.INVALID_OPTION_VALUE,
                    "value for " + OPT_SDK_VERSION + " must be a safe version identifier "
                            + "(no path separators, not '.'/'..', filename-legal characters only)",
                    value));
        }
        return Optional.of(value);
    }

    /**
     * Maps the optional {@code --failure-policy} value to a {@link FailurePolicy}. Absent means the
     * default; any value outside the two accepted spellings is rejected rather than silently ignored.
     */
    private static FailurePolicy parsePolicy(String value) {
        if (value == null) {
            return FailurePolicy.DEFAULT;
        }
        return switch (value) {
            case POLICY_FAIL_FAST -> FailurePolicy.FAIL_FAST;
            case POLICY_COLLECT_AND_CONTINUE -> FailurePolicy.COLLECT_AND_CONTINUE;
            default -> throw new CliException(CliError.of(
                    CliErrorCode.INVALID_OPTION_VALUE,
                    "value for " + OPT_FAILURE_POLICY + " must be '" + POLICY_FAIL_FAST + "' or '"
                            + POLICY_COLLECT_AND_CONTINUE + "'",
                    value));
        };
    }

    private static Path required(Map<String, String> values, String option) {
        String value = values.get(option);
        if (value == null) {
            throw new CliException(CliError.of(
                    CliErrorCode.MISSING_REQUIRED_OPTION, "required option is missing", option));
        }
        return Path.of(value);
    }

    /**
     * Rejects anything that is not a local filesystem path: URL/scheme forms and UNC/network paths.
     * This is the argument-contract half of AD-9 (zero egress) — a remote-looking input is refused
     * outright rather than resolved.
     */
    private static void requireLocalPath(String option, String value) {
        if (value.isBlank()) {
            throw new CliException(CliError.of(
                    CliErrorCode.MISSING_OPTION_VALUE, "option requires a directory value", option));
        }
        if (URL_SCHEME.matcher(value).find()) {
            throw new CliException(CliError.of(
                    CliErrorCode.NON_LOCAL_PATH,
                    "value for " + option + " must be a local path, not a URL", value));
        }
        // Windows extended-length device-path prefixes (\\?\, \\.\) are local paths, not UNC shares —
        // except \\?\UNC\server\share, which is the extended-length form of a genuine UNC network path.
        if (value.startsWith("\\\\?\\UNC\\")) {
            throw new CliException(CliError.of(
                    CliErrorCode.NON_LOCAL_PATH,
                    "value for " + option + " must be a local path, not a UNC/network path", value));
        }
        if (value.startsWith("\\\\?\\") || value.startsWith("\\\\.\\")) {
            return;
        }
        if (value.startsWith("\\\\") || value.startsWith("//")) {
            throw new CliException(CliError.of(
                    CliErrorCode.NON_LOCAL_PATH,
                    "value for " + option + " must be a local path, not a UNC/network path", value));
        }
    }
}
