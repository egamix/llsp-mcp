package com.luciadmcp.knowledge.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Application query only; read-tools/2 serialization belongs to the transport. */
public record ExactSymbolQuery(String symbol, boolean includeAliases, int pageSize,
        int candidateLimit, int workLimit, Optional<String> sourceSdkVersion,
        Optional<String> buildId, Optional<String> targetSdkVersion) {
    public ExactSymbolQuery(String symbol) {
        this(symbol, true, 10, 1000, 5000, Optional.empty(), Optional.empty(), Optional.empty());
    }
    public ExactSymbolQuery {
        text(symbol);
        if (pageSize < 1 || pageSize > 50 || candidateLimit < 1 || candidateLimit > 10000
                || workLimit < 1 || workLimit > 50000) throw new IllegalArgumentException("INVALID_REQUEST: query limits");
        if (sourceSdkVersion == null || buildId == null || targetSdkVersion == null) throw new IllegalArgumentException("INVALID_REQUEST: optional context required");
        sourceSdkVersion.ifPresent(ExactSymbolQuery::text); buildId.ifPresent(ExactSymbolQuery::text);
        targetSdkVersion.ifPresent(ExactSymbolQuery::text);
    }
    public static void text(String value) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > 2048
                || value.codePoints().anyMatch(Character::isISOControl)
                || value.chars().anyMatch(c -> Character.isSurrogate((char)c)) && !validUnicode(value))
            throw new IllegalArgumentException("INVALID_REQUEST: bounded UTF-8 text required");
    }
    private static boolean validUnicode(String value) {
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)) { if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))return false; }
            else if(Character.isLowSurrogate(c))return false;
        }
        return true;
    }
}
