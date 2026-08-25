package com.luciadmcp.knowledge.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * The deterministic content-hash recipe for the whole system (Story 1.4).
 *
 * <p>Five hashes, all SHA-256 rendered as lowercase hex. Nothing here reads the clock or a random
 * source; the same inputs always produce the same hex. This is the single authority for how a hash
 * is computed from a set of fields, so no other module invents its own recipe.
 *
 * <ul>
 *   <li>{@link #sourceHash(byte[])} — over the raw bytes of an input file. The only genuinely new
 *       hash; the others reconcile onto identity/fingerprint invariants that already exist.</li>
 *   <li>{@link #recordHash(String, List)} — over a canonical record's declared identity fields
 *       (AD-22 identity fields).</li>
 *   <li>{@link #relationshipHash(String, String, RelationshipType, String, String)} — over an
 *       edge's identity fields: SDK version, source id, type, target id, evidence (AD-22).</li>
 *   <li>{@link #semanticHash(String)} — over the exact normalized text handed to the embedder
 *       (AD-8/AD-21 embedding fingerprint). Hashes the text verbatim; it does not normalize.</li>
 *   <li>{@link #toolchainFingerprint(String, String, String, String, String)} — over the toolchain
 *       component versions (AD-6 compatibility fingerprint), used here in its restart-safety role.</li>
 * </ul>
 *
 * <h2>Field framing</h2>
 *
 * <p>Multi-field hashes frame each field length-prefixed as {@code <utf8ByteLength>:<bytes>} so two
 * distinct field lists can never serialize to the same byte stream. For example {@code ("a","bc")}
 * frames to {@code 1:a2:bc} and {@code ("ab","c")} to {@code 2:ab1:c} — different streams, different
 * hashes. A {@code null} field is framed as {@code -1:} (no bytes), which is distinct from an empty
 * field {@code 0:}. Each hash is also prefixed with a domain tag so records, relationships, and
 * fingerprints occupy separate hash spaces even when their fields coincide.
 */
public final class ContentHashes {

    private ContentHashes() {
    }

    /**
     * Hash of the raw bytes of an input file.
     *
     * @param fileBytes the exact file contents; changing one byte changes only this file's hash
     * @return lowercase-hex SHA-256 of the bytes
     */
    public static String sourceHash(byte[] fileBytes) {
        if (fileBytes == null) {
            throw new IllegalArgumentException("fileBytes must not be null");
        }
        MessageDigest md = newDigest();
        md.update(fileBytes);
        return toHex(md.digest());
    }

    /**
     * Hash of a canonical record from its declared identity fields (AD-22).
     *
     * @param entityType     the record kind (e.g. {@code "ApiElement"}); part of identity so two
     *                       kinds with coincidentally equal fields do not collide
     * @param identityFields the ordered identity fields for this entity, exactly as declared
     * @return lowercase-hex SHA-256 over the framed {@code (entityType, fields...)} stream
     */
    public static String recordHash(String entityType, List<String> identityFields) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        if (identityFields == null) {
            throw new IllegalArgumentException("identityFields must not be null");
        }
        MessageDigest md = newDigest();
        frame(md, "record");
        frame(md, entityType);
        for (String field : identityFields) {
            frame(md, field);
        }
        return toHex(md.digest());
    }

    /**
     * Hash of a relationship edge from its identity fields (AD-22): SDK version, source id,
     * relationship type, target id, evidence, in that fixed order.
     *
     * @param sdkVersion the SDK version the edge belongs to (AD-12)
     * @param sourceId   canonical source identity
     * @param type       the relationship type
     * @param targetId   canonical target identity
     * @param evidence   the resolution evidence backing the edge
     * @return lowercase-hex SHA-256 over the framed identity stream
     */
    public static String relationshipHash(
            String sdkVersion, String sourceId, RelationshipType type, String targetId, String evidence) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        MessageDigest md = newDigest();
        frame(md, "relationship");
        frame(md, sdkVersion);
        frame(md, sourceId);
        frame(md, type.name());
        frame(md, targetId);
        frame(md, evidence);
        return toHex(md.digest());
    }

    /**
     * Hash of the exact normalized text handed to the embedder (AD-8/AD-21).
     *
     * <p>This hashes the string verbatim; it does not itself normalize. Normalization lives inside
     * the embedder — this records what that produced so index-time and query-time inputs can be
     * proven identical.
     *
     * @param normalizedEmbedderInput the already-normalized text
     * @return lowercase-hex SHA-256 of the text's UTF-8 bytes
     */
    public static String semanticHash(String normalizedEmbedderInput) {
        if (normalizedEmbedderInput == null) {
            throw new IllegalArgumentException("normalizedEmbedderInput must not be null");
        }
        MessageDigest md = newDigest();
        md.update(normalizedEmbedderInput.getBytes(StandardCharsets.UTF_8));
        return toHex(md.digest());
    }

    /**
     * Hash over the toolchain component versions (AD-6). A change to any one component changes the
     * fingerprint, which lets a later load refuse a build whose fingerprint does not match the
     * runtime.
     *
     * @param parserVersion            parser code/format version
     * @param schemaVersion            catalog schema version
     * @param normalizerVersion        text-normalization version
     * @param relationshipRulesVersion relationship-resolution rules version
     * @param embeddingConfigId        embedding configuration identity (model + dim + tokenizer + prefixes)
     * @return lowercase-hex SHA-256 over the framed component stream
     */
    public static String toolchainFingerprint(
            String parserVersion,
            String schemaVersion,
            String normalizerVersion,
            String relationshipRulesVersion,
            String embeddingConfigId) {
        MessageDigest md = newDigest();
        frame(md, "toolchain");
        frame(md, parserVersion);
        frame(md, schemaVersion);
        frame(md, normalizerVersion);
        frame(md, relationshipRulesVersion);
        frame(md, embeddingConfigId);
        return toHex(md.digest());
    }

    /** Feeds one length-prefixed field into the digest; {@code null} frames as {@code -1:}. */
    private static void frame(MessageDigest md, String field) {
        if (field == null) {
            md.update("-1:".getBytes(StandardCharsets.US_ASCII));
            return;
        }
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        md.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        md.update((byte) ':');
        md.update(bytes);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JDK algorithm; its absence is not a recoverable condition.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
