package com.luciadmcp.knowledge.ingest.javadoc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final Logger LOG = System.getLogger(JavadocDiscovery.class.getName());

    /** Emit an INFO progress line after this many classified pages (large trees have ~17k). */
    private static final int PROGRESS_EVERY = 500;

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
        LOG.log(Level.INFO, "discovery: scanning Javadoc tree {0}", base);

        List<ClassifiedPage> pages = walkAndClassify(base, warnings);
        boolean searchIndexPresent = crossCheckSearchIndex(base, pages, warnings);

        LOG.log(Level.INFO, "discovery: done — {0} pages [{1}], searchIndex={2}, warnings={3}",
                pages.size(), summarize(pages), searchIndexPresent, warnings.size());
        return new DiscoveryResult(pages, searchIndexPresent, warnings);
    }

    /** Recursive {@code *.html} walk + structural classification, in ascending relative-path order. */
    private List<ClassifiedPage> walkAndClassify(Path base, List<String> warnings) {
        List<Path> htmlFiles = walkHtml(base);
        int total = htmlFiles.size();
        LOG.log(Level.INFO, "discovery: found {0} HTML pages; classifying", total);
        List<ClassifiedPage> pages = new ArrayList<>(total);
        int done = 0;
        for (Path file : htmlFiles) {
            String relativePath = relativeKey(base, file);
            PageType type;
            try {
                // charset=null lets JSoup honor a BOM/meta charset (legacy trees may be ISO-8859-1),
                // falling back to UTF-8 when none is declared. Still a local-bytes parse (AD-9).
                Document doc = Jsoup.parse(new java.io.ByteArrayInputStream(com.luciadmcp.knowledge.domain.SourceAdmission.read(file)), null, "");
                if (LOG.isLoggable(Level.DEBUG)) {
                    // Per-page breakdown is DEBUG so proprietary class names are not logged by default
                    // (brief §logging). This is the full extent of the current "understanding": the
                    // kind, the signal that decided it, the heading, and whether members are present.
                    JavadocPageClassifier.Analysis a = classifier.analyze(doc);
                    type = a.type();
                    LOG.log(Level.DEBUG, "discovery: {0} -> {1}  [signal={2}, heading=\"{3}\", members={4}]",
                            relativePath, type, a.signal(),
                            a.heading() == null ? "" : a.heading(), a.hasMembers() ? "yes" : "no");
                } else {
                    type = classifier.classify(doc);
                }
            } catch (IOException e) {
                // A page we cannot read/parse is UNKNOWN, not a crash (AC 7); recorded for later policy.
                warnings.add("failed to read/parse " + relativePath + ": " + e.getMessage());
                LOG.log(Level.WARNING, "discovery: failed to read/parse {0}: {1}", relativePath, e.getMessage());
                type = PageType.UNKNOWN;
            }
            pages.add(new ClassifiedPage(relativePath, type));
            if (++done % PROGRESS_EVERY == 0 && done < total) {
                LOG.log(Level.INFO, "discovery: classified {0}/{1} pages", done, total);
            }
        }
        return pages;
    }

    /** Compact "TYPE=210, EXCLUDED=218, ..." breakdown for the summary log line (counts only). */
    private static String summarize(List<ClassifiedPage> pages) {
        Map<PageType, Integer> counts = new EnumMap<>(PageType.class);
        for (ClassifiedPage page : pages) {
            counts.merge(page.type(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<PageType, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static List<Path> walkHtml(Path base) {
        return com.luciadmcp.knowledge.domain.SourceAdmission.enumerate(base).stream()
                .filter(JavadocDiscovery::isHtml).toList();
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
                String content = new String(com.luciadmcp.knowledge.domain.SourceAdmission.read(indexFile), StandardCharsets.UTF_8);
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
