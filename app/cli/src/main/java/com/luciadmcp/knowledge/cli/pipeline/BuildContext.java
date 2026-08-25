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
 * @param buildId          stable identity of this build, derived from the sealed input digest
 * @param sealedInputDigest deterministic digest over the ordered input set; a resumed run must match
 *                          this before reusing prior work
 * @param stagingDir       coordinator-owned, build-scoped staging directory (checkpoint lives here)
 */
public record BuildContext(String buildId, String sealedInputDigest, Path stagingDir) {

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
    }

    /**
     * Computes the build identity deterministically from the ordered input set of one invocation.
     *
     * <p>The digest folds two things, in fixed order: the input path structure (javadoc, samples,
     * guides, model, out, then trainings or an explicit "absent" marker as normalized path strings),
     * then the content of every input file under each input root — each file contributes its
     * relative path and its {@link ContentHashes#sourceHash(byte[]) sourceHash}. So the {@code buildId}
     * is a content hash over the complete build-input set (AD-17), not just the paths: changing one
     * byte of one input file changes the {@code buildId} and therefore forces a fresh run rather than
     * a resume (AD-25). Same inputs &rarr; same digest &rarr; same {@code buildId} and {@code stagingDir}.
     *
     * <p>Files are enumerated in sorted relative-path order so the digest is independent of filesystem
     * walk order. The {@code out} directory is the build destination, not an input, so its contents are
     * never folded in — only its path participates, as before.
     *
     * @param args the validated six-path inputs
     * @return the coordinator-owned build context for this invocation
     */
    public static BuildContext forInputs(IngestArguments args) {
        List<String> ordered = new ArrayList<>();
        ordered.add("javadoc=" + normalize(args.javadoc()));
        ordered.add("samples=" + normalize(args.samples()));
        ordered.add("guides=" + normalize(args.guides()));
        ordered.add("model=" + normalize(args.model()));
        ordered.add("out=" + normalize(args.out()));
        ordered.add("trainings=" + args.trainings().map(BuildContext::normalize).orElse("<absent>"));

        // Fold in the bytes of every input file under each input root (never the out destination).
        hashRootFiles(ordered, "javadoc", args.javadoc());
        hashRootFiles(ordered, "samples", args.samples());
        hashRootFiles(ordered, "guides", args.guides());
        hashRootFiles(ordered, "model", args.model());
        args.trainings().ifPresent(root -> hashRootFiles(ordered, "trainings", root));

        String digest = sha256Hex(String.join("\n", ordered));
        String buildId = "b-" + digest.substring(0, 16);
        Path stagingDir = args.out().resolve(".staging").resolve(buildId);
        return new BuildContext(buildId, digest, stagingDir);
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
        try (Stream<Path> walk = Files.walk(base)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> relativeKey(base, p)))
                    .toList();
            for (Path file : files) {
                ordered.add("file:" + label + "/" + relativeKey(base, file) + "="
                        + ContentHashes.sourceHash(readAll(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to enumerate input files under " + base, e);
        }
    }

    /** Relative path with '/' separators, so the key is stable across operating systems. */
    private static String relativeKey(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }

    private static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read input file " + file, e);
        }
    }

    private static String normalize(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String sha256Hex(String input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JDK algorithm; its absence is not a recoverable condition.
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
