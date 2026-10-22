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
    /** Canonical wire format: version prefix, then URL-safe unpadded UTF-8 base64 fields.
     * Fixed eight fields followed by zero or more parameter fields; separators never occur in fields. */
    public String serialize() {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>(List.of(product, sdkVersion,
                language, namespace, owner, member, elementKind, Integer.toString(parameterTypes.size())));
        fields.addAll(parameterTypes);
        return "symbol:1:" + fields.stream().map(SymbolCoordinate::encode).collect(java.util.stream.Collectors.joining("."));
    }
    private static String encode(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static SymbolCoordinate parse(String wire) {
        if (wire == null || !wire.startsWith("symbol:1:")) throw new IllegalArgumentException("unsupported coordinate version");
        try {
            String[] encoded = wire.substring(9).split("\\.", -1);
            java.util.ArrayList<String> f = new java.util.ArrayList<>();
            for (String field : encoded) f.add(new String(java.util.Base64.getUrlDecoder().decode(field), java.nio.charset.StandardCharsets.UTF_8));
            if (f.size() < 8 || Integer.parseInt(f.get(7)) != f.size() - 8) throw new IllegalArgumentException("parameter count mismatch");
            var coordinate = new SymbolCoordinate(f.get(0), f.get(1), f.get(2), f.get(3), f.get(4), f.get(5), f.subList(8, f.size()), f.get(6));
            if (!coordinate.serialize().equals(wire)) throw new IllegalArgumentException("noncanonical coordinate");
            return coordinate;
        } catch (RuntimeException e) { throw new IllegalArgumentException("invalid coordinate", e); }
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
