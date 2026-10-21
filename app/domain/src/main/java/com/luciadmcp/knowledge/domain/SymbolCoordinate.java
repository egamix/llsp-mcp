package com.luciadmcp.knowledge.domain;

import java.util.List;

/** Versioned logical coordinate; aliases and filesystem roots are deliberately excluded. */
public record SymbolCoordinate(String product, String sdkVersion, String language, String namespace,
        String owner, String member, List<String> parameterTypes, String elementKind) {
    public SymbolCoordinate {
        if (product == null || product.isBlank() || sdkVersion == null || sdkVersion.isBlank()
                || language == null || language.isBlank() || namespace == null || owner == null
                || member == null || parameterTypes == null || elementKind == null || elementKind.isBlank())
            throw new IllegalArgumentException("complete coordinate required");
        parameterTypes = List.copyOf(parameterTypes);
    }
    /** Nested framed digests prevent delimiter ambiguity without losing qualified parameter names. */
    public String serialize() {
        return "symbol:1:" + ContentHashes.digest("symbol-coordinate/1", List.of(product, sdkVersion,
                language, namespace, owner, member, ContentHashes.digest("parameters/1", parameterTypes), elementKind));
    }
    public static SymbolCoordinate from(CatalogRecord record) {
        String key = record.canonicalKey();
        int hash = key.indexOf('#');
        String owner = hash < 0 ? key : key.substring(0, hash);
        String member = hash < 0 ? "" : key.substring(hash + 1);
        int open = member.indexOf('(');
        List<String> parameters = open >= 0 && member.endsWith(")")
                ? (member.substring(open + 1, member.length() - 1).isEmpty() ? List.of()
                    : List.of(member.substring(open + 1, member.length() - 1).split(",", -1))) : List.of();
        if (open >= 0) member = member.substring(0, open);
        int dot = owner.lastIndexOf('.');
        return new SymbolCoordinate("LuciadLightspeed", record.sdkVersion(), "java",
                dot < 0 ? "" : owner.substring(0, dot), owner, member, parameters, record.kind());
    }
}
