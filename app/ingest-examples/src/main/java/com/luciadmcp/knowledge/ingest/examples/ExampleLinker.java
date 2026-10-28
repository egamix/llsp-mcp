package com.luciadmcp.knowledge.ingest.examples;

import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.ingest.examples.JavaExampleExtractor.Usage;
import java.util.*;

/**
 * Story 3.2 (AD-30): resolves names used in example code to catalog symbols without compiling or
 * symbol-solving. Only a provably single target becomes a link:
 * fully qualified name, explicit import, same package, unique wildcard-import match. A call links only
 * when exactly one overload of that name exists on the receiver type or, failing that, on the nearest
 * supertype that declares it. Everything else stays unlinked.
 */
public final class ExampleLinker {
    public record Link(RelationshipType type, String targetKey, int line, String token) {
        public String evidence() { return "L" + line + " " + token; }
    }

    private final Set<String> keys;
    private final Map<String, List<String>> members = new HashMap<>();
    private final Map<String, List<String>> supertypes;

    /** @param supertypes direct supertypes (extends/implements) of each catalog type key */
    public ExampleLinker(Collection<String> canonicalKeys, Map<String, List<String>> supertypes) {
        keys = Set.copyOf(canonicalKeys);
        this.supertypes = supertypes;
        for (String key : canonicalKeys) {
            int hash = key.indexOf('#');
            if (hash < 0) continue;
            int open = key.indexOf('(', hash);
            members.computeIfAbsent(open < 0 ? key : key.substring(0, open), k -> new ArrayList<>()).add(key);
        }
    }

    /** Links in first-seen order, one per (type, target). */
    public List<Link> link(String packageName, List<String> imports, List<Usage> usages) {
        var out = new LinkedHashMap<String, Link>();
        for (Usage usage : usages) {
            var owner = resolveType(usage.typeName(), packageName, imports);
            if (owner.isEmpty()) continue;
            String type = owner.get();
            switch (usage.kind()) {
                case TYPE -> put(out, new Link(RelationshipType.USES_TYPE, type, usage.line(), usage.typeName()));
                case CONSTRUCT -> {
                    put(out, new Link(RelationshipType.CONSTRUCTS, type, usage.line(), "new " + usage.typeName()));
                    put(out, new Link(RelationshipType.USES_TYPE, type, usage.line(), usage.typeName()));
                }
                case CALL -> {
                    put(out, new Link(RelationshipType.USES_TYPE, type, usage.line(), usage.typeName()));
                    method(type, usage.member()).ifPresent(m ->
                            put(out, new Link(RelationshipType.CALLS, m, usage.line(), usage.typeName() + "." + usage.member())));
                }
            }
        }
        return List.copyOf(out.values());
    }

    private static void put(Map<String, Link> out, Link link) { out.putIfAbsent(link.type() + " " + link.targetKey(), link); }

    Optional<String> resolveType(String name, String packageName, List<String> imports) {
        if (name == null || name.isEmpty()) return Optional.empty();
        if (name.indexOf('.') > 0 && keys.contains(name)) return Optional.of(name);
        int dot = name.indexOf('.');
        String first = dot < 0 ? name : name.substring(0, dot), rest = dot < 0 ? "" : name.substring(dot);
        for (String imported : imports) {
            // An explicit import decides the name, whether or not it is an SDK type.
            if (!imported.endsWith(".*") && imported.endsWith("." + first))
                return keys.contains(imported + rest) ? Optional.of(imported + rest) : Optional.empty();
        }
        if (!packageName.isEmpty() && keys.contains(packageName + "." + name)) return Optional.of(packageName + "." + name);
        String found = null;
        for (String imported : imports) {
            if (!imported.endsWith(".*")) continue;
            String candidate = imported.substring(0, imported.length() - 1) + name;
            if (keys.contains(candidate)) {
                if (found != null && !found.equals(candidate)) return Optional.empty();
                found = candidate;
            }
        }
        return Optional.ofNullable(found);
    }

    private Optional<String> method(String type, String name) {
        var queue = new ArrayDeque<String>(List.of(type));
        var seen = new HashSet<String>();
        while (!queue.isEmpty() && seen.size() < 64) {
            // Breadth-first by inheritance level; the first level that declares the name decides.
            var level = new ArrayList<String>();
            for (int n = queue.size(); n > 0; n--) {
                String t = queue.remove();
                if (!seen.add(t)) continue;
                level.addAll(members.getOrDefault(t + "#" + name, List.of()));
                queue.addAll(supertypes.getOrDefault(t, List.of()));
            }
            if (!level.isEmpty()) return level.stream().distinct().count() == 1 ? Optional.of(level.getFirst()) : Optional.empty();
        }
        return Optional.empty();
    }
}
