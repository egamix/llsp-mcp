package com.luciadmcp.knowledge.catalog.sqlite;

import com.luciadmcp.knowledge.domain.TraversalRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Provisional v1, authenticated stateless replay; runtime keys are never persisted in the build. */
final class TraversalCursorCodec {
    private final byte[] key;
    TraversalCursorCodec(byte[] key) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("signing key needs 32 bytes");
        this.key = key.clone();
    }
    String binding(String build, String sdk, TraversalRequest query) {
        String filters = query.types().stream().map(Enum::name).sorted().reduce("", (a,b) -> a + "," + b);
        String canonical = field(build) + field(sdk) + field(query.seedKey()) + field(query.mode().name())
                + field(query.direction().name()) + field(filters) + field(query.limits().toString());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private String field(String text) { return text.length() + ":" + text; }
    String encode(String binding, int offset) {
        String payload = "1:" + binding + ":" + offset;
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }
    int decode(String token, String binding) {
        try {
            if (token.length() > 4096) return -1;
            int dot = token.lastIndexOf('.');
            if (dot < 0) return -1;
            String payload = token.substring(0, dot);
            if (!MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(token.substring(dot + 1)))) return -1;
            String prefix = "1:" + binding + ":";
            if (!payload.startsWith(prefix)) return -1;
            int offset = Integer.parseInt(payload.substring(prefix.length()));
            return offset > 0 && offset <= 10_000 ? offset : -1;
        } catch (IllegalArgumentException e) { return -1; }
    }
    private byte[] sign(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
