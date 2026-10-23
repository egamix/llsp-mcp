package com.luciadmcp.knowledge.ingest.examples;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.luciadmcp.knowledge.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Story 3.1 (AD-30): turns one sample {@code .java} file into searchable example records — one per
 * declared type and one per method/constructor body of at least {@link #MIN_METHOD_LINES} lines.
 * Each record cites its source path and line range and carries the code verbatim.
 * Syntax only: nothing is compiled, symbol-solved or executed.
 */
public final class JavaExampleExtractor {
    public static final int MIN_METHOD_LINES = 3;
    public record Example(KnowledgeRecord record, DocumentationPayload payload) {}
    public record Result(List<Example> examples, List<String> issues) {}

    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21).setAttributeComments(true));

    public Result extract(String sdkVersion, String relativePath, byte[] bytes) {
        String source = new String(bytes, StandardCharsets.UTF_8);
        var parsed = parser.parse(source);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            String problem = parsed.getProblems().isEmpty() ? "unparseable" : parsed.getProblems().getFirst().getMessage();
            return new Result(List.of(), List.of(relativePath + ": not parsed as Java: " + firstLine(problem)));
        }
        CompilationUnit unit = parsed.getResult().get();
        String[] lines = source.split("\r?\n", -1);
        String sourceHash = ContentHashes.sourceHash(bytes);
        String pkg = unit.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");
        List<String> luciadImports = unit.getImports().stream().map(i -> i.getNameAsString() + (i.isAsterisk() ? ".*" : ""))
                .filter(n -> n.startsWith("com.luciad.")).toList();
        var examples = new ArrayList<Example>();
        var issues = new ArrayList<String>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            if (type.getRange().isEmpty()) continue;
            String typeName = pkg + nestedName(type);
            boolean topLevel = type.isTopLevelType();
            String typeId = KnowledgeRecord.exampleId(sdkVersion, relativePath, typeName, "");
            var blocks = new ArrayList<DocumentationPayload.Block>();
            javadoc(type).ifPresent(text -> blocks.add(paragraph("summary", text)));
            if (topLevel && !luciadImports.isEmpty()) blocks.add(paragraph("luciad imports", String.join(", ", luciadImports)));
            blocks.add(code(topLevel ? source : text(lines, type)));
            add(examples, issues, sdkVersion, relativePath, typeId, "Example " + typeName, range(type, topLevel, lines.length), sourceHash, blocks);

            var seen = new HashMap<String, Integer>();
            for (CallableDeclaration<?> callable : type.findAll(CallableDeclaration.class)) {
                if (owner(callable) != type || callable.getRange().isEmpty()) continue;
                var r = callable.getRange().get();
                if (r.end.line - r.begin.line + 1 < MIN_METHOD_LINES) continue;
                String signature = callable.getNameAsString() + "(" + String.join(",",
                        callable.getParameters().stream().map(p -> p.getType().asString() + (p.isVarArgs() ? "..." : "")).toList()) + ")";
                int ordinal = seen.merge(signature, 1, Integer::sum);
                String member = ordinal == 1 ? signature : signature + "#" + ordinal;
                var methodBlocks = new ArrayList<DocumentationPayload.Block>();
                javadoc(callable).ifPresent(text -> methodBlocks.add(paragraph("summary", text)));
                methodBlocks.add(paragraph("context", "Member of example " + typeName + " (record " + typeId + ")"));
                methodBlocks.add(code(text(lines, callable)));
                add(examples, issues, sdkVersion, relativePath, KnowledgeRecord.exampleId(sdkVersion, relativePath, typeName, member),
                        "Example " + typeName + "#" + signature, new SourceLocator.LineRange(r.begin.line, r.end.line), sourceHash, methodBlocks);
            }
        }
        return new Result(List.copyOf(examples), List.copyOf(issues));
    }

    private static void add(List<Example> out, List<String> issues, String sdk, String path, String id, String title,
            SourceLocator.LineRange lines, String sourceHash, List<DocumentationPayload.Block> blocks) {
        try {
            var locator = new SourceLocator(SourceLocator.Kind.SOURCE, path, Optional.empty(), "", Optional.of(lines), Optional.empty());
            var record = new KnowledgeRecord(id, sdk, KnowledgeRecord.RecordType.EXAMPLE, KnowledgeRecord.KnowledgeKind.AUTHORITATIVE_FACT,
                    Optional.empty(), locator,
                    new KnowledgeRecord.Governance(KnowledgeRecord.Approval.APPROVED, "samples", "local SDK sample source", "shipped SDK sample code"),
                    new KnowledgeRecord.Applicability("LuciadLightspeed", List.of(sdk), List.of()));
            var sections = List.of(new DocumentationPayload.Section("example", title, locator, blocks));
            out.add(new Example(record, new DocumentationPayload(id, sourceHash, sections,
                    ContentHashes.contentHash(DocumentationPayload.canonicalFields(sections)))));
        } catch (IllegalArgumentException e) {
            issues.add(path + ": " + title + " skipped: " + e.getMessage());
        }
    }

    private static TypeDeclaration<?> owner(Node node) {
        // Methods of anonymous classes stay part of their enclosing method's code, not separate records.
        for (Node at = node.getParentNode().orElse(null); at != null; at = at.getParentNode().orElse(null)) {
            if (at instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) return null;
            if (at instanceof TypeDeclaration<?> type) return type;
        }
        return null;
    }

    private static String nestedName(TypeDeclaration<?> type) {
        var names = new ArrayDeque<String>();
        for (Node at = type; at != null; at = at.getParentNode().orElse(null))
            if (at instanceof TypeDeclaration<?> t) names.addFirst(t.getNameAsString());
        return String.join(".", names);
    }

    private static SourceLocator.LineRange range(Node node, boolean wholeFile, int lineCount) {
        if (wholeFile) return new SourceLocator.LineRange(1, Math.max(1, lineCount));
        var r = node.getRange().orElseThrow();
        return new SourceLocator.LineRange(r.begin.line, r.end.line);
    }

    /** Whole source lines of the node, with the common leading indentation removed. */
    private static String text(String[] lines, Node node) {
        var r = node.getRange().orElseThrow();
        int first = r.begin.line - 1, last = Math.min(lines.length - 1, r.end.line - 1);
        var lineList = Arrays.asList(lines).subList(first, last + 1);
        int indent = lineList.stream().filter(l -> !l.isBlank()).mapToInt(l -> l.length() - l.stripLeading().length()).min().orElse(0);
        var out = new StringBuilder();
        for (String l : lineList) out.append(l.length() >= indent ? l.substring(indent) : l.strip()).append('\n');
        return out.toString().stripTrailing();
    }

    private static Optional<String> javadoc(Node node) {
        if (!(node instanceof NodeWithJavadoc<?> documented)) return Optional.empty();
        return documented.getJavadoc().map(j -> j.getDescription().toText().strip()).filter(s -> !s.isEmpty());
    }

    private static DocumentationPayload.Block paragraph(String label, String text) {
        return new DocumentationPayload.Block(DocumentationPayload.BlockKind.PARAGRAPH, label, text, List.of());
    }

    private static DocumentationPayload.Block code(String text) {
        return new DocumentationPayload.Block(DocumentationPayload.BlockKind.CODE, "source", text, List.of());
    }

    private static String firstLine(String text) {
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }
}
