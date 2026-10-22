package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.cli.IngestArguments;
import com.luciadmcp.knowledge.domain.ContentHashes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The opaque handle the coordinator passes to stages: the single build identity plus the staging
 * directory the run owns.
 *
 * <p>AD-17: {@code cli ingest} is the sole build coordinator. It computes the {@code buildId} once
 * per invocation over the complete ordered input set and hands stages this context. Stages never
 * mint a {@code buildId}. AD-25: the {@code stagingDir} is coordinator-owned, holds the in-progress
 * build's checkpoint, and is invisible to serve.
 *
 * <p>Story 2.4 threads the {@code sdkVersion} (AD-12) so the catalog can version-scope every row, and
 * exposes {@link #catalogDbPath()} — the build- and version-scoped catalog file, kept under the
 * coordinator-owned staging area so an in-progress catalog is never observable until the build is
 * validated and activated (AD-5, AD-25).
 *
 * @param buildId          stable identity of this build, derived from the sealed input digest
 * @param sealedInputDigest deterministic digest over the ordered input set; a resumed run must match
 *                          this before reusing prior work
 * @param stagingDir       coordinator-owned, build-scoped staging directory (checkpoint lives here)
 * @param sdkVersion       the SDK version this build is scoped to (AD-12); every catalog row carries it
 */
public record BuildContext(String buildId, String sealedInputDigest, Path stagingDir, String sdkVersion) {

    /** Fixed name of the build-scoped catalog file under {@link #catalogDbPath()}. */
    public static final String CATALOG_DB_FILE = "catalog.sqlite";

    // A safe single path segment: it becomes a directory name under the staging dir, so it must not be
    // empty, not "." / "..", and contain only characters legal in a filename on every target OS. This
    // keeps a hostile --sdk-version from escaping the coordinator-owned staging area (AD-25) or breaking
    // file creation. Version strings in practice look like "2024.0", "2024.1.2-beta", "1.0.0+build".
    private static final Pattern SAFE_SDK_VERSION_SEGMENT = Pattern.compile("[A-Za-z0-9._+-]+");

    public BuildContext {
        if (buildId == null || buildId.isBlank()) {
            throw new IllegalArgumentException("buildId must not be blank");
        }
        if (sealedInputDigest == null || sealedInputDigest.isBlank()) {
            throw new IllegalArgumentException("sealedInputDigest must not be blank");
        }
        if (stagingDir == null) {
            throw new IllegalArgumentException("stagingDir must not be null");
        }
        // Defensive invariant regardless of entry path: sdkVersion is used as a directory segment in
        // catalogDbPath(), so it must be a safe single path segment (AD-25 — never escape staging).
        if (!isSafeSdkVersionSegment(sdkVersion)) {
            throw new IllegalArgumentException(
                    "sdkVersion must be a safe single path segment (no separators, not '.'/'..', "
                            + "filename-legal characters only): " + sdkVersion);
        }
    }

    /**
     * Whether {@code sdkVersion} is a safe single path segment usable as a directory name: non-blank,
     * not {@code "."}/{@code ".."}, and only filename-legal characters ({@code A-Za-z0-9._+-}). Shared
     * with the argument parser so the same rule is enforced at parse time and as a construction
     * invariant.
     */
    public static boolean isSafeSdkVersionSegment(String sdkVersion) {
        if (sdkVersion == null || sdkVersion.isBlank()) {
            return false;
        }
        if (sdkVersion.equals(".") || sdkVersion.equals("..")) {
            return false;
        }
        return SAFE_SDK_VERSION_SEGMENT.matcher(sdkVersion).matches();
    }

    /**
     * The build- and version-scoped catalog file: {@code {stagingDir}/{sdkVersion}/catalog.sqlite}.
     * It lives under the coordinator-owned staging directory (which already embeds the {@code buildId}),
     * so the in-progress catalog stays invisible to serve until the build is validated and activated
     * (AD-25); the {@code sdkVersion} segment scopes it by version (AD-12). The final immutable
     * {@code {sdkVersion}/{buildId}} activation layout is Epic 6.
     */
    public Path catalogDbPath() {
        return stagingDir.resolve(sdkVersion).resolve(CATALOG_DB_FILE);
    }

    /** Coordinator-owned derived lexical index, alongside the source SQLite catalog. */
    public Path lexicalIndexPath() { return stagingDir.resolve(sdkVersion).resolve("lexical-index"); }

    /**
     * Computes the build identity deterministically from the ordered input set of one invocation.
     *
     * <p>Versioned framed hashing includes profile, governed selection, runtime, SDK version and
     * sorted source-relative file paths/hashes. Absolute input/output roots are operational only.
     * Relocation and enumeration order preserve identity; byte or recipe changes require a new build.
     *
     * @param args the validated six-path inputs
     * @return the coordinator-owned build context for this invocation
     */
    public static BuildContext forInputs(IngestArguments args) {
        List<String> ordered = new ArrayList<>();
        ordered.add("profile=" + com.luciadmcp.knowledge.domain.ContractVersions.PROFILE);
        ordered.add("recipe=" + ContentHashes.recipeFingerprint(com.luciadmcp.knowledge.domain.ContractVersions.PROFILE, List.of()));
        ordered.add("runtime=" + ContentHashes.runtimeFingerprint());
        ordered.add("trainings=" + args.trainings().map(root -> "present").orElse("absent"));
        // The SDK version scopes the whole build (AD-12), so it is part of the sealed input set: a
        // different version is a different build, not a resume. The digest folds a distinct "<absent>"
        // marker when no version was supplied, so an explicit "--sdk-version 0.0.0-stub" and an absent
        // version (which resolves the *field* to the stub sentinel) never collide on the same buildId.
        java.util.Optional<String> explicitVersion = args.sdkVersion().filter(v -> !v.isBlank());
        ordered.add("sdkVersion=" + explicitVersion.orElse("<absent>"));
        String sdkVersion = explicitVersion.orElse(IngestPipelineRunner.STUB_SDK_VERSION);

        // Fold in the bytes of every input file under each input root (never the out destination).
        hashRootFiles(ordered, "javadoc", args.javadoc());
        hashRootFiles(ordered, "samples", args.samples());
        hashRootFiles(ordered, "guides", args.guides());
        // Lexical profile has no embedding model dependency.
        args.trainings().ifPresent(root -> hashRootFiles(ordered, "trainings", root));

        String digest = ContentHashes.digest("build/1", ordered);
        String buildId = "b-" + digest;
        Path stagingDir = args.out().resolve(".staging").resolve(buildId);
        return new BuildContext(buildId, digest, stagingDir, sdkVersion);
    }

    /**
     * Appends one {@code file:<root>/<relpath>=<sourceHash>} entry per regular file under {@code root},
     * in sorted relative-path order. A non-existent root contributes nothing. Reading input files is
     * the only I/O here and never touches the network (AD-9).
     */
    private static void hashRootFiles(List<String> ordered, String label, Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        Path base = root.toAbsolutePath().normalize();
        for (Path file : com.luciadmcp.knowledge.domain.SourceAdmission.enumerate(base)) {
            ordered.add("file:" + label + "/" + relativeKey(base, file) + "=" + ContentHashes.sourceFileHash(file));
        }
    }

    /** Relative path with '/' separators, so the key is stable across operating systems. */
    private static String relativeKey(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }

}
