package com.luciadmcp.knowledge.ingest.javadoc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Discovers and classifies the pages of a Javadoc directory (Story 2.1, FR-4).
 *
 * <p>Discovery always enumerates {@code *.html} pages by a recursive walk of the tree, in ascending
 * relative-path order, so the result is deterministic and independent of filesystem walk order. When
 * a {@code *-search-index.js} is present it is read as a corroborating <em>assist</em> — it never
 * replaces the walk, because it lists only types/members/packages, not the nav/tree/help/overview
 * pages the walk must still find. Consequently the classified page set is identical whether or not a
 * search index exists (AC 1); the index only adds cross-check warnings.
 *
 * <p>Classification is delegated to {@link JavadocPageClassifier} and is purely structural. This
 * component is a pure library: it reads local files only (AD-9 — no network; JSoup parses from bytes,
 * {@code Jsoup.connect} is never used), writes nothing, and is not wired into the ingest pipeline
 * (that happens with persistence in Story 2.4).
 */
public final class JavadocDiscovery {

    /** The four search-index files modern Javadoc emits at the tree root. */
    private static final String[] SEARCH_INDEX_FILES = {
            "type-search-index.js",
            "member-search-index.js",
            "package-search-index.js",
            "tag-search-index.js",
    };

    private static final String TYPE_SEARCH_INDEX = "type-search-index.js";

    private final JavadocPageClassifier classifier = new JavadocPageClassifier();

    /**
     * Discovers and classifies every page under {@code root}.
     *
     * @param root the Javadoc tree root directory
     * @return the deterministic, relative-path-ordered classification result
     * @throws IllegalArgumentException if {@code root} is null or not a directory
     */
    public DiscoveryResult classify(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("javadoc root is not a directory: " + root);
        }
        Path base = root.toAbsolutePath().normalize();
        List<String> warnings = new ArrayList<>();

        List<ClassifiedPage> pages = walkAndClassify(base, warnings);
        boolean searchIndexPresent = crossCheckSearchIndex(base, pages, warnings);

        return new DiscoveryResult(pages, searchIndexPresent, warnings);
    }

    /** Recursive {@code *.html} walk + structural classification, in ascending relative-path order. */
    private List<ClassifiedPage> walkAndClassify(Path base, List<String> warnings) {
        List<Path> htmlFiles = walkHtml(base);
        List<ClassifiedPage> pages = new ArrayList<>(htmlFiles.size());
        for (Path file : htmlFiles) {
            String relativePath = relativeKey(base, file);
            PageType type;
            try {
                Document doc = Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name());
                type = classifier.classify(doc);
            } catch (IOException e) {
                // A page we cannot read/parse is UNKNOWN, not a crash (AC 7); recorded for later policy.
                warnings.add("failed to read/parse " + relativePath + ": " + e.getMessage());
                type = PageType.UNKNOWN;
            }
            pages.add(new ClassifiedPage(relativePath, type));
        }
        return pages;
    }

    private static List<Path> walkHtml(Path base) {
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(Files::isRegularFile)
                    .filter(JavadocDiscovery::isHtml)
                    .sorted(Comparator.comparing(p -> relativeKey(base, p)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to enumerate Javadoc pages under " + base, e);
        }
    }

    private static boolean isHtml(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    /**
     * Reads any present {@code *-search-index.js} as a corroborating assist. Returns whether one was
     * found. The type index, if parseable, is cross-checked against the structural classification:
     * a declared type page that was not classified {@link PageType#TYPE} yields a warning, but the
     * classified page set is never changed (that is what keeps AC 1 true).
     */
    private static boolean crossCheckSearchIndex(Path base, List<ClassifiedPage> pages, List<String> warnings) {
        boolean present = false;
        for (String name : SEARCH_INDEX_FILES) {
            Path indexFile = base.resolve(name);
            if (!Files.isRegularFile(indexFile)) {
                continue;
            }
            present = true;
            if (!name.equals(TYPE_SEARCH_INDEX)) {
                continue; // presence is enough for the others in this story
            }
            try {
                String content = Files.readString(indexFile, StandardCharsets.UTF_8);
                Set<String> classifiedTypePaths = typePathsClassifiedAsType(pages);
                for (String declared : SearchIndex.typePagePaths(content)) {
                    if (!classifiedTypePaths.contains(declared)) {
                        warnings.add("search index lists type page not classified as TYPE: " + declared);
                    }
                }
            } catch (IOException e) {
                // Fallback path: a missing/unreadable index is not an error, just no assist (FR-4).
                warnings.add("failed to read search index " + name + ": " + e.getMessage());
            } catch (RuntimeException e) {
                warnings.add("malformed search index " + name + ": " + e.getMessage());
            }
        }
        return present;
    }

    private static Set<String> typePathsClassifiedAsType(List<ClassifiedPage> pages) {
        Set<String> paths = new LinkedHashSet<>();
        for (ClassifiedPage page : pages) {
            if (page.type() == PageType.TYPE) {
                paths.add(page.relativePath());
            }
        }
        return paths;
    }

    /** Relative path with {@code /} separators, so the key is stable across operating systems. */
    private static String relativeKey(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }
}
