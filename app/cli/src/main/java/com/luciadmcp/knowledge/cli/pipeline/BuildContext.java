package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.cli.IngestArguments;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

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
     * <p>The six {@link IngestArguments} paths are hashed in fixed order (javadoc, samples, guides,
     * model, out, then trainings or an explicit "absent" marker) as their normalized absolute path
     * strings. Same inputs &rarr; same digest &rarr; same {@code buildId} and {@code stagingDir}, so
     * an interrupted run resumes into the same staging; a changed input set yields a different
     * {@code buildId} and therefore a fresh run rather than a resume (AD-25).
     *
     * <p>PROVISIONAL — Story 1.4 replaces this with the formal five-hash content-hash recipe
     * (sourceHash/recordHash/relationshipHash/semanticHash/toolchainFingerprint). This story hashes
     * only the input path set, which is enough for staging-dir naming and resume matching. It does
     * NOT read file contents or the toolchain, and it does NOT implement the five hashes.
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

        String digest = sha256Hex(String.join("\n", ordered));
        String buildId = "b-" + digest.substring(0, 16);
        Path stagingDir = args.out().resolve(".staging").resolve(buildId);
        return new BuildContext(buildId, digest, stagingDir);
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
