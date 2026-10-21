package com.luciadmcp.knowledge.cli.pipeline;

import com.luciadmcp.knowledge.domain.CatalogStore;
import com.luciadmcp.knowledge.domain.RelationshipEdge;
import com.luciadmcp.knowledge.domain.ReferenceResolution;
import com.luciadmcp.knowledge.domain.RelationshipStore;
import com.luciadmcp.knowledge.domain.RelationshipType;
import com.luciadmcp.knowledge.domain.StoredCatalogRecord;
import com.luciadmcp.knowledge.ingest.javadoc.ExtractedMember;
import com.luciadmcp.knowledge.ingest.javadoc.ExtractedType;
import com.luciadmcp.knowledge.ingest.javadoc.JavadocCatalogSource;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolKind;
import com.luciadmcp.knowledge.ingest.javadoc.SymbolRecord;
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
 * persisted with its original token and locator, outcome, reason and sorted scoped candidate IDs.
 * Ordinary unresolved or ambiguous source references are evidence, so neither failure policy aborts.
 * Explicit external, escaping or dead locators never fall back to display-name resolution.
 *
 * <p><b>Why re-parse.</b> The catalog persists only identity + {@code (relativePath, anchor)}, not
 * supertype/param/see-also facts, so pass two re-parses the tree (deterministic) for the <em>source</em>
 * references and resolves <em>targets</em> against the completed catalog. This is the AD-4 two-pass
 * shape, not a parse of convenience.
 *
 * <p><b>Resolution within the documented SDK.</b> (1) a reference carrying an href into the ingested
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
 *   <li><b>Unresolved or ambiguous reference</b> &rarr; stored evidence, with no selected edge endpoint.</li>
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
        for (SymbolRecord record : page.records()) {
            StoredCatalogRecord stored = index.byKey.get(record.canonicalKey());
            if (stored == null || !stored.record().relativePath().equals(record.relativePath())
                    || !stored.record().anchor().equals(CatalogStage.anchorFor(record))) {
                throw new IllegalStateException("reference source provenance differs from completed catalog: " + record.canonicalKey());
            }
        }

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
            emitTypeRef(version, typeKey, type.superclass(), RelationshipType.EXTENDS_CLASS, index, store, file, failures);
            for (TypeRef ref : type.superInterfaces())
                emitTypeRef(version, typeKey, ref, RelationshipType.EXTENDS_INTERFACE, index, store, file, failures);
            for (TypeRef ref : type.implementedInterfaces())
                emitTypeRef(version, typeKey, ref, RelationshipType.IMPLEMENTS, index, store, file, failures);
            for (TypeRef ref : type.seeAlso())
                emitTypeRef(version, typeKey, ref, RelationshipType.SEE_ALSO, index, store, file, failures);
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
        // Qualified extracted references are independent of the unchanged canonical identity recipe.
        for (TypeRef ref : member.parameterRefs()) {
            emitTypeRef(version, sourceKey, ref, paramType, index, store, file, failures);
        }
        for (TypeRef ref : member.throwsRefs()) {
            emitTypeRef(version, sourceKey, ref, RelationshipType.THROWS_TYPE, index, store, file, failures);
        }

        // OVERRIDES / SPECIFIED_BY — member-precise; resolved by href only, never guessed by name.
        emitMemberPrecise(version, sourceKey, member.overrides(), RelationshipType.OVERRIDES,
                index, store, file, failures);
        emitMemberPrecise(version, sourceKey, member.specifiedBy(), RelationshipType.SPECIFIED_BY,
                index, store, file, failures);

        for (TypeRef ref : member.seeAlso()) {
            emitTypeRef(version, sourceKey, ref, RelationshipType.SEE_ALSO, index, store, file, failures);
        }
    }

    private void emitTypeRef(
            String version, String sourceKey, TypeRef ref, RelationshipType type,
            ResolutionIndex index, RelationshipStore store, Path file, Consumer<StageFailure> failures) {
        if (ref == null || (ref.name().isBlank() && !ref.hasHref()) || isPrimitiveOrVoid(ref.name())) return;
        ReferenceResolution evidence = index.resolve(sourceKey, ref, type, version);
        store.putResolution(evidence);
        if (evidence.status() != ReferenceResolution.Status.EXACT) return;
        String target = index.byId.get(evidence.selectedTargetId()).record().canonicalKey();
        submit(store, version, sourceKey, target, type, file, failures);
        if (type == RelationshipType.EXTENDS_CLASS)
            submit(store, version, target, sourceKey, RelationshipType.DIRECT_SUBCLASS, file, failures);
        if (type == RelationshipType.IMPLEMENTS)
            submit(store, version, target, sourceKey, RelationshipType.IMPLEMENTED_BY, file, failures);
    }

    private void emitMemberPrecise(
            String version, String sourceKey, TypeRef ref, RelationshipType type,
            ResolutionIndex index, RelationshipStore store, Path file, Consumer<StageFailure> failures) {
        emitTypeRef(version, sourceKey, ref, type, index, store, file, failures);
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
        private final Map<String, List<StoredCatalogRecord>> byPathAnchor = new HashMap<>();
        private final Map<String, List<StoredCatalogRecord>> byFqnType = new HashMap<>();
        private final Map<String, List<StoredCatalogRecord>> bySimpleType = new HashMap<>();
        private final Map<String, StoredCatalogRecord> byKey = new HashMap<>();
        private final Map<String, StoredCatalogRecord> byId = new HashMap<>();

        static ResolutionIndex build(List<StoredCatalogRecord> records, String version) {
            ResolutionIndex index = new ResolutionIndex();
            for (StoredCatalogRecord stored : records) {
                var record = stored.record();
                if (!record.sdkVersion().equals(version)) continue;
                index.byKey.put(record.canonicalKey(), stored);
                index.byId.put(stored.recordId(), stored);
                index.byPathAnchor.computeIfAbsent(pathAnchorKey(record.relativePath(), record.anchor()), k -> new ArrayList<>()).add(stored);
                if ("TYPE".equals(record.kind())) {
                    index.byFqnType.computeIfAbsent(record.typeFqn(), k -> new ArrayList<>()).add(stored);
                    index.bySimpleType.computeIfAbsent(record.simpleName(), k -> new ArrayList<>()).add(stored);
                }
            }
            return index;
        }

        ReferenceResolution resolve(String sourceKey, TypeRef ref, RelationshipType type, String version) {
            StoredCatalogRecord source = byKey.get(sourceKey);
            if (source == null) throw new IllegalStateException("reference source is absent from scoped catalog");
            boolean member = type == RelationshipType.OVERRIDES || type == RelationshipType.SPECIFIED_BY;
            ReferenceResolution.Kind kind;
            ReferenceResolution.Reason reason;
            List<StoredCatalogRecord> matches = List.of();
            String name = ref.name().replace("[]", "").replace("...", "");
            if (ref.hasHref()) {
                kind = member ? ReferenceResolution.Kind.MEMBER_LOCATOR : ReferenceResolution.Kind.LOCAL_LOCATOR;
                reason = switch (ref.locatorKind()) {
                    case EXTERNAL -> ReferenceResolution.Reason.EXTERNAL_LOCATOR;
                    case ROOT_ESCAPE -> ReferenceResolution.Reason.ROOT_ESCAPE;
                    default -> ReferenceResolution.Reason.MISSING_LOCATOR;
                };
                if (ref.locatorKind() == TypeRef.LocatorKind.EXTERNAL) kind = ReferenceResolution.Kind.EXTERNAL_LOCATOR;
                if (ref.locatorKind() == TypeRef.LocatorKind.ROOT_ESCAPE) kind = ReferenceResolution.Kind.ROOT_ESCAPE;
                if (ref.locatorKind() == TypeRef.LocatorKind.LOCAL) {
                    matches = byPathAnchor.getOrDefault(pathAnchorKey(ref.relativePath(), ref.anchor()), List.of());
                    if (member) matches = matches.stream().filter(r -> !r.record().kind().equals("TYPE")).toList();
                    else if (type != RelationshipType.SEE_ALSO)
                        matches = matches.stream().filter(r -> r.record().kind().equals("TYPE")).toList();
                }
            } else if (member) {
                kind = ReferenceResolution.Kind.MEMBER_LOCATOR;
                reason = ReferenceResolution.Reason.LOCATOR_REQUIRED;
            } else if (name.contains(".")) {
                kind = ReferenceResolution.Kind.QUALIFIED_NAME;
                reason = ReferenceResolution.Reason.MISSING_QUALIFIED_NAME;
                matches = byFqnType.getOrDefault(name, List.of());
            } else {
                kind = ReferenceResolution.Kind.SIMPLE_NAME;
                reason = ReferenceResolution.Reason.MISSING_NAME;
                matches = bySimpleType.getOrDefault(name, List.of());
            }
            List<String> ids = matches.stream().map(StoredCatalogRecord::recordId).distinct().sorted().toList();
            ReferenceResolution.Status status = ids.isEmpty() ? ReferenceResolution.Status.UNRESOLVED
                    : ids.size() == 1 ? ReferenceResolution.Status.EXACT : ReferenceResolution.Status.AMBIGUOUS;
            if (status == ReferenceResolution.Status.EXACT) reason = ReferenceResolution.Reason.MATCHED;
            if (status == ReferenceResolution.Status.AMBIGUOUS) reason = ReferenceResolution.Reason.COMPETING_CANDIDATES;
            return new ReferenceResolution(version, source.recordId(), source.record().relativePath(),
                    source.record().anchor(), type, ref.name(), ref.originalLocator(), status, kind, reason,
                    ids, status == ReferenceResolution.Status.EXACT ? ids.get(0) : "");
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
