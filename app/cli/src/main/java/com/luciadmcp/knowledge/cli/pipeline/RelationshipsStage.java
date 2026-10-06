package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.domain.RelationshipEdge;
import com.luciadmcp.knowledge.domain.RelationshipStore;
import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import com.luciadmcp.knowledge.ingest.javadoc.ExtractedMember;
import com.luciadmcp.knowledge.ingest.javadoc.ExtractedType;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolKind;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolRecord;
import com.luciadmcp.knowledge.ingest.javadoc.ThrownException;
import com.luciadmcp.knowledge.ingest.javadoc.TypeRef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The real {@link PipelineStage#RELATIONSHIPS} stage (Story 2.5): the end-to-end pass-two
 * graph-construction path. It reads the catalog persisted by pass one, re-parses the Javadoc tree for
 * the relationship-source references (which the catalog does not persist), resolves each documented
 * reference to a catalog symbol, emits the 13 Javadoc <b>structural</b> typed edges, materializes the
 * two inverse pairs, and submits every edge through the {@link RelationshipStore} port — which alone
 * computes edge IDs and persists version-scoped rows (AD-22).
 *
 * <p><b>Two-pass, never fabricate (AD-4).</b> Pass one is the persisted catalog (Story 2.4); pass two
 * resolves references against the <em>completed</em> catalog only. An edge is emitted only when both
 * endpoints are catalog symbols in the same SDK version (AD-12). A reference that does not resolve is
 * surfaced as a {@link FailureSeverity#RECOVERABLE} failure — never fabricated, never silently dropped;
 * the first-class stored unresolved-reference model and traversal-scope reporting are Story 2.6.
 *
 * <p><b>Why re-parse.</b> The catalog persists only identity + {@code (relativePath, anchor)}, not
 * supertype/param/see-also facts, so pass two re-parses the tree (deterministic) for the <em>source</em>
 * references and resolves <em>targets</em> against the completed catalog. This is the AD-4 two-pass
 * shape, not a parse of convenience.
 *
 * <p><b>Resolution ladder (minimal, Story 2.5).</b> (1) a reference carrying an href into the ingested
 * tree resolves via the catalog's persisted {@code (relativePath, anchor)} — reliable and
 * member-precise (this is how {@code OVERRIDES}/{@code SPECIFIED_BY} reach a specific supertype method);
 * (2) otherwise a plain type name resolves by fully-qualified name, else by a <em>unique</em>
 * simple-name match; (3) otherwise it is unresolved. Language primitives and {@code void} name no
 * documented type and are neither edges nor failures.
 *
 * <p><b>Inverse derivation (golden).</b> After emitting a resolved {@code EXTENDS_CLASS(a→b)} the stage
 * also emits {@code DIRECT_SUBCLASS(b→a)}, and after {@code IMPLEMENTS(a→b)} it emits
 * {@code IMPLEMENTED_BY(b→a)} — derived, never parsed from a second source, so the two directions can
 * never disagree. The other 9 structural types have no named inverse and are single-direction.
 *
 * <p><b>Dependency direction (AD-1).</b> This stage lives in {@code cli}. It drives Javadoc parsing only
 * through {@link JavadocCatalogSource} (no JSoup type crosses the boundary) and reaches both stores only
 * through {@code domain} ports; the concrete SQLite stores are supplied by the wiring via factories, so
 * this class names no engine type.
 *
 * <h2>Failure mapping</h2>
 * <ul>
 *   <li><b>Unresolved reference</b> &rarr; {@link FailureSeverity#RECOVERABLE} (per the failure policy).</li>
 *   <li><b>Store open/close, re-parse, or persistence integrity failure, and an edge identity clash</b>
 *       &rarr; {@link FailureSeverity#BUILD_INVALIDATING}: relationship-graph integrity is compromised.</li>
 * </ul>
 */
public final class RelationshipsStage implements Stage {

    private final Path javadocRoot;
    private final JavadocCatalogSource source;
    private final Function<BuildContext, CatalogStore> catalogStoreFactory;
    private final Function<BuildContext, RelationshipStore> relationshipStoreFactory;

    /**
     * @param javadocRoot              the Javadoc tree root to re-parse (validated by {@code IngestCommand})
     * @param source                   the Story-2.1&ndash;2.3 discover/extract/mint facade (now also
     *                                 exposing the relationship-source references per type)
     * @param catalogStoreFactory      opens the build-scoped {@link CatalogStore} to read the catalog
     *                                 for the resolution index
     * @param relationshipStoreFactory opens the build-scoped {@link RelationshipStore} to persist edges
     */
    public RelationshipsStage(
            Path javadocRoot,
            JavadocCatalogSource source,
            Function<BuildContext, CatalogStore> catalogStoreFactory,
            Function<BuildContext, RelationshipStore> relationshipStoreFactory) {
        this.javadocRoot = javadocRoot;
        this.source = source;
        this.catalogStoreFactory = catalogStoreFactory;
        this.relationshipStoreFactory = relationshipStoreFactory;
    }

    @Override
    public void execute(BuildContext context, Consumer<StageFailure> failures) {
        String version = context.sdkVersion();

        // 1. Build the resolution index from the persisted catalog (pass one's completed output).
        ResolutionIndex index;
        try (CatalogStore catalog = catalogStoreFactory.apply(context)) {
            index = ResolutionIndex.build(catalog.allRecords(), version);
        } catch (RuntimeException e) {
            failures.accept(StageFailure.of(
                    PipelineStage.RELATIONSHIPS,
                    FailureSeverity.BUILD_INVALIDATING,
                    "could not read catalog for relationship resolution: " + e.getMessage()));
            return;
        }

        // 2. Re-parse the tree for the source references (deterministic; AD-4 two-pass).
        JavadocCatalogSource.Result parsed;
        try {
            parsed = source.ingest(javadocRoot);
        } catch (RuntimeException e) {
            failures.accept(StageFailure.of(
                    PipelineStage.RELATIONSHIPS,
                    FailureSeverity.BUILD_INVALIDATING,
                    "Javadoc re-parse for relationships failed under " + javadocRoot + ": " + e.getMessage()));
            return;
        }

        // 3. Persist edges through the store (the sole edge-ID authority, AD-22).
        RelationshipStore store;
        try {
            store = relationshipStoreFactory.apply(context);
        } catch (RuntimeException e) {
            failures.accept(StageFailure.of(
                    PipelineStage.RELATIONSHIPS,
                    FailureSeverity.BUILD_INVALIDATING,
                    "could not open relationship store: " + e.getMessage()));
            return;
        }
        try {
            for (JavadocCatalogSource.PageResult page : parsed.pages()) {
                emitPage(version, page, index, store, failures);
            }
        } catch (RuntimeException e) {
            // A genuine storage/integrity error (e.g. an endpoint key with no catalog record) compromises
            // graph integrity — route it through the policy as build-invalidating, not raw.
            failures.accept(StageFailure.of(
                    PipelineStage.RELATIONSHIPS,
                    FailureSeverity.BUILD_INVALIDATING,
                    "relationship persistence failed: " + e.getMessage()));
        } finally {
            try {
                store.close();
            } catch (RuntimeException e) {
                failures.accept(StageFailure.of(
                        PipelineStage.RELATIONSHIPS,
                        FailureSeverity.BUILD_INVALIDATING,
                        "could not close relationship store: " + e.getMessage()));
            }
        }
    }

    /** Emits every structural edge for one type page (type-level then per-member), plus inverses. */
    private void emitPage(
            String version,
            JavadocCatalogSource.PageResult page,
            ResolutionIndex index,
            RelationshipStore store,
            Consumer<StageFailure> failures) {
        Path file = javadocRoot.resolve(page.relativePath());
        ExtractedType type = page.type();

        // The type-level record (memberIndex == -1) is the source for the type-header + type see-also
        // edges; member records supply their own source keys and line up with type.members() by index.
        SymbolRecord typeRecord = null;
        for (SymbolRecord record : page.records()) {
            if (record.kind() == SymbolKind.TYPE) {
                typeRecord = record;
                break;
            }
        }

        if (typeRecord != null && type != null) {
            String typeKey = typeRecord.canonicalKey();
            // EXTENDS_CLASS + derived DIRECT_SUBCLASS (inverse).
            resolveType(type.superclass(), index).ifPresentOrElse(
                    target -> {
                        submit(store, version, typeKey, target, RelationshipType.EXTENDS_CLASS, file, failures);
                        submit(store, version, target, typeKey, RelationshipType.DIRECT_SUBCLASS, file, failures);
                    },
                    () -> unresolved(type.superclass(), "EXTENDS_CLASS", typeKey, file, failures));
            // EXTENDS_INTERFACE (single direction).
            for (TypeRef ref : type.superInterfaces()) {
                resolveType(ref, index).ifPresentOrElse(
                        target -> submit(store, version, typeKey, target,
                                RelationshipType.EXTENDS_INTERFACE, file, failures),
                        () -> unresolved(ref, "EXTENDS_INTERFACE", typeKey, file, failures));
            }
            // IMPLEMENTS + derived IMPLEMENTED_BY (inverse).
            for (TypeRef ref : type.implementedInterfaces()) {
                resolveType(ref, index).ifPresentOrElse(
                        target -> {
                            submit(store, version, typeKey, target, RelationshipType.IMPLEMENTS, file, failures);
                            submit(store, version, target, typeKey, RelationshipType.IMPLEMENTED_BY, file, failures);
                        },
                        () -> unresolved(ref, "IMPLEMENTS", typeKey, file, failures));
            }
            // Type-level SEE_ALSO (single direction; may target a type or a member).
            for (TypeRef ref : type.seeAlso()) {
                resolveAny(ref, index).ifPresentOrElse(
                        target -> submit(store, version, typeKey, target,
                                RelationshipType.SEE_ALSO, file, failures),
                        () -> unresolved(ref, "SEE_ALSO", typeKey, file, failures));
            }
        }

        List<ExtractedMember> members = type == null ? List.of() : type.members();
        for (SymbolRecord record : page.records()) {
            int idx = record.memberIndex();
            if (idx < 0 || idx >= members.size()) {
                continue; // the type record, or a member whose index fell outside the extracted list
            }
            emitMember(version, record, members.get(idx), index, store, file, failures);
        }
    }

    private void emitMember(
            String version,
            SymbolRecord record,
            ExtractedMember member,
            ResolutionIndex index,
            RelationshipStore store,
            Path file,
            Consumer<StageFailure> failures) {
        String sourceKey = record.canonicalKey();

        // RETURN_TYPE (methods) / FIELD_TYPE (fields, enum constants).
        if (record.kind() == SymbolKind.METHOD) {
            emitTypeRef(version, sourceKey, member.returnType(), RelationshipType.RETURN_TYPE,
                    index, store, file, failures);
        } else if (record.kind() == SymbolKind.FIELD || record.kind() == SymbolKind.ENUM_CONSTANT) {
            emitTypeRef(version, sourceKey, member.fieldType(), RelationshipType.FIELD_TYPE,
                    index, store, file, failures);
        }

        // PARAMETER_TYPE / CONSTRUCTOR_PARAMETER_TYPE — from the minted erased parameter types (plain
        // names, resolved by FQN / unique simple name).
        RelationshipType paramType = record.kind() == SymbolKind.CONSTRUCTOR
                ? RelationshipType.CONSTRUCTOR_PARAMETER_TYPE
                : RelationshipType.PARAMETER_TYPE;
        for (String erased : record.erasedParameterTypes()) {
            emitNameRef(version, sourceKey, erased, paramType, index, store, file, failures);
        }

        // THROWS_TYPE — from the documented @throws type names.
        for (ThrownException thrown : member.throwsTypes()) {
            emitNameRef(version, sourceKey, thrown.type(), RelationshipType.THROWS_TYPE,
                    index, store, file, failures);
        }

        // OVERRIDES / SPECIFIED_BY — member-precise; resolved by href only, never guessed by name.
        emitMemberPrecise(version, sourceKey, member.overrides(), RelationshipType.OVERRIDES,
                index, store, file, failures);
        emitMemberPrecise(version, sourceKey, member.specifiedBy(), RelationshipType.SPECIFIED_BY,
                index, store, file, failures);

        // Member-level SEE_ALSO (may target a type or a member).
        for (TypeRef ref : member.seeAlso()) {
            resolveAny(ref, index).ifPresentOrElse(
                    target -> submit(store, version, sourceKey, target,
                            RelationshipType.SEE_ALSO, file, failures),
                    () -> unresolved(ref, "SEE_ALSO", sourceKey, file, failures));
        }
    }

    /** Emits a type-target edge from a {@link TypeRef} (href then name), or records it unresolved. */
    private void emitTypeRef(
            String version, String sourceKey, TypeRef ref, RelationshipType type,
            ResolutionIndex index, RelationshipStore store, Path file, Consumer<StageFailure> failures) {
        if (ref == null || isPrimitiveOrVoid(ref.name())) {
            return; // no reference (constructor/void) or a primitive — never an edge, never a failure
        }
        resolveType(ref, index).ifPresentOrElse(
                target -> submit(store, version, sourceKey, target, type, file, failures),
                () -> unresolved(ref, type.name(), sourceKey, file, failures));
    }

    /** Emits a type-target edge from a plain erased/documented name, or records it unresolved. */
    private void emitNameRef(
            String version, String sourceKey, String name, RelationshipType type,
            ResolutionIndex index, RelationshipStore store, Path file, Consumer<StageFailure> failures) {
        if (name == null || name.isBlank() || isPrimitiveOrVoid(name)) {
            return;
        }
        String target = index.resolveTypeByName(name);
        if (target != null) {
            submit(store, version, sourceKey, target, type, file, failures);
        } else {
            unresolvedName(name, type.name(), sourceKey, file, failures);
        }
    }

    /** Emits a member-precise edge (href only); an unlinked or unmatched target stays unresolved. */
    private void emitMemberPrecise(
            String version, String sourceKey, TypeRef ref, RelationshipType type,
            ResolutionIndex index, RelationshipStore store, Path file, Consumer<StageFailure> failures) {
        if (ref == null) {
            return;
        }
        String target = index.resolveByHref(ref);
        if (target != null) {
            submit(store, version, sourceKey, target, type, file, failures);
        } else {
            unresolved(ref, type.name(), sourceKey, file, failures);
        }
    }

    /** Resolves a type target: href into the tree, else FQN, else a unique simple name. */
    private static java.util.Optional<String> resolveType(TypeRef ref, ResolutionIndex index) {
        if (ref == null) {
            return java.util.Optional.empty();
        }
        String byHref = index.resolveByHref(ref);
        if (byHref != null) {
            return java.util.Optional.of(byHref);
        }
        String byName = index.resolveTypeByName(ref.name());
        return java.util.Optional.ofNullable(byName);
    }

    /** Resolves a see-also target: href (type or member) into the tree, else a type by name. */
    private static java.util.Optional<String> resolveAny(TypeRef ref, ResolutionIndex index) {
        if (ref == null) {
            return java.util.Optional.empty();
        }
        String byHref = index.resolveByHref(ref);
        if (byHref != null) {
            return java.util.Optional.of(byHref);
        }
        return java.util.Optional.ofNullable(index.resolveTypeByName(ref.name()));
    }

    private void submit(
            RelationshipStore store, String version, String sourceKey, String targetKey,
            RelationshipType type, Path file, Consumer<StageFailure> failures) {
        RelationshipStore.PutResult result =
                store.put(new RelationshipEdge(sourceKey, targetKey, type, version));
        if (result.status() == RelationshipStore.PutStatus.IDENTITY_CLASH) {
            // A real identity clash compromises graph integrity — never overwrite or drop (AD-4).
            failures.accept(StageFailure.ofFile(
                    PipelineStage.RELATIONSHIPS,
                    FailureSeverity.BUILD_INVALIDATING,
                    file,
                    "identity clash persisting " + type + " edge " + sourceKey + " -> " + targetKey
                            + " (SDK version " + version + "); computed edge id " + result.edgeId()));
        }
    }

    private void unresolved(
            TypeRef ref, String type, String sourceKey, Path file, Consumer<StageFailure> failures) {
        if (ref == null) {
            return;
        }
        String detail = ref.hasHref()
                ? "'" + ref.name() + "' (link " + ref.relativePath()
                        + (ref.anchor().isBlank() ? "" : "#" + ref.anchor()) + ")"
                : "'" + ref.name() + "'";
        failures.accept(StageFailure.ofFile(
                PipelineStage.RELATIONSHIPS,
                FailureSeverity.RECOVERABLE,
                file,
                "unresolved " + type + " reference " + detail + " from " + sourceKey
                        + "; no edge emitted (explicit unresolved model deferred to Story 2.6)"));
    }

    private void unresolvedName(
            String name, String type, String sourceKey, Path file, Consumer<StageFailure> failures) {
        failures.accept(StageFailure.ofFile(
                PipelineStage.RELATIONSHIPS,
                FailureSeverity.RECOVERABLE,
                file,
                "unresolved " + type + " reference '" + name + "' from " + sourceKey
                        + "; no edge emitted (explicit unresolved model deferred to Story 2.6)"));
    }

    /** Language primitives and {@code void} — never documented types, so never references to resolve. */
    private static boolean isPrimitiveOrVoid(String name) {
        String base = name;
        // Strip array/varargs markers to test the element type.
        while (base.endsWith("[]")) {
            base = base.substring(0, base.length() - 2);
        }
        if (base.endsWith("...")) {
            base = base.substring(0, base.length() - 3);
        }
        return switch (base) {
            case "void", "boolean", "byte", "char", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /**
     * The pass-two resolution index over the completed catalog for one SDK version: {@code (path,
     * anchor)} &rarr; canonical key for link-precise resolution, and type-record FQN / simple-name maps
     * for plain-name resolution. Built once per run from {@code allRecords()} (deterministic).
     */
    static final class ResolutionIndex {

        private final Map<String, String> byPathAnchor;
        private final Map<String, String> byFqnType;
        private final Map<String, List<String>> bySimpleType;

        private ResolutionIndex(
                Map<String, String> byPathAnchor,
                Map<String, String> byFqnType,
                Map<String, List<String>> bySimpleType) {
            this.byPathAnchor = byPathAnchor;
            this.byFqnType = byFqnType;
            this.bySimpleType = bySimpleType;
        }

        static ResolutionIndex build(List<StoredCatalogRecord> records, String version) {
            Map<String, String> byPathAnchor = new HashMap<>();
            Map<String, String> byFqnType = new HashMap<>();
            Map<String, List<String>> bySimpleType = new HashMap<>();
            for (StoredCatalogRecord stored : records) {
                var record = stored.record();
                if (!record.sdkVersion().equals(version)) {
                    continue; // edges connect only same-version symbols (AD-12)
                }
                byPathAnchor.put(
                        pathAnchorKey(record.relativePath(), record.anchor()), record.canonicalKey());
                if ("TYPE".equals(record.kind())) {
                    byFqnType.put(record.typeFqn(), record.canonicalKey());
                    if (!record.simpleName().isBlank()) {
                        bySimpleType.computeIfAbsent(record.simpleName(), k -> new ArrayList<>())
                                .add(record.canonicalKey());
                    }
                }
            }
            return new ResolutionIndex(byPathAnchor, byFqnType, bySimpleType);
        }

        /** The canonical key at a link's {@code (relativePath, anchor)}, or {@code null} if none. */
        String resolveByHref(TypeRef ref) {
            if (ref == null || !ref.hasHref()) {
                return null;
            }
            return byPathAnchor.get(pathAnchorKey(ref.relativePath(), ref.anchor()));
        }

        /** The canonical key for a type by FQN, else by a unique simple name; {@code null} otherwise. */
        String resolveTypeByName(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            if (name.indexOf('.') >= 0) {
                // A qualified name names one specific type; if it is not in the catalog it is outside
                // the ingested tree. Never fall back to a same-simple-name type in another package.
                return byFqnType.get(name);
            }
            List<String> candidates = bySimpleType.get(name);
            return (candidates != null && candidates.size() == 1) ? candidates.get(0) : null;
        }

        private static String pathAnchorKey(String relativePath, String anchor) {
            return relativePath + "#" + normalizeAnchor(anchor);
        }
    }

    /**
     * Normalizes a member anchor so a catalog anchor (built from erased <em>simple</em> parameter type
     * names) and a Javadoc link anchor (which may carry fully-qualified parameter types) compare equal:
     * generics are erased and each parameter type is reduced to its last dotted segment. A type/field
     * anchor (no parameter list) passes through unchanged; the transform is idempotent on an
     * already-simple anchor.
     */
    static String normalizeAnchor(String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return "";
        }
        int open = anchor.indexOf('(');
        if (open < 0) {
            return anchor; // a field / type anchor
        }
        int close = anchor.lastIndexOf(')');
        String name = anchor.substring(0, open);
        String inner = close > open ? anchor.substring(open + 1, close) : anchor.substring(open + 1);
        inner = eraseAngles(inner).trim();
        List<String> params = new ArrayList<>();
        if (!inner.isEmpty()) {
            for (String part : inner.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                int dims = 0;
                while (p.endsWith("[]")) {
                    dims++;
                    p = p.substring(0, p.length() - 2);
                }
                if (p.endsWith("...")) {
                    dims++;
                    p = p.substring(0, p.length() - 3);
                }
                p = lastDotSegment(p) + "[]".repeat(dims);
                params.add(p);
            }
        }
        return name + "(" + String.join(",", params) + ")";
    }

    private static String lastDotSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static String eraseAngles(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (depth > 0) {
                    depth--;
                }
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
