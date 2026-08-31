package com.luciadmcp.knowledge.ingest.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tolerant reader for Javadoc {@code *-search-index.js} files (AD-10: read as text, never executed).
 *
 * <p>These files assign a JSON array to a variable, e.g.
 * {@code typeSearchIndex = [{"p":"com.example","l":"Foo"}, ...];updateSearchResults();}. Only the
 * array is of interest; the {@code var} prefix, trailing {@code ;}, and result-callback are ignored.
 * A file that does not contain a JSON array is treated as malformed (the caller falls back to the
 * recursive walk, per FR-4).
 */
final class SearchIndex {

    private SearchIndex() {
    }

    private static final Pattern OBJECT = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern P_FIELD = Pattern.compile("\"p\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern L_FIELD = Pattern.compile("\"l\"\\s*:\\s*\"([^\"]*)\"");

    /**
     * Returns the JSON array substring (including the enclosing brackets) from a search-index file.
     *
     * @throws IllegalArgumentException if no {@code [ ... ]} array is present
     */
    static String extractArray(String content) {
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalArgumentException("no JSON array found in search index");
        }
        return content.substring(start, end + 1);
    }

    /**
     * Relative HTML page paths of the types declared by a {@code type-search-index.js} body. Each
     * entry {@code {"p":"pkg","l":"Label"}} maps to {@code pkg/Label.html} (nested types keep their
     * {@code Outer.Inner} label). Entries missing a package or label are skipped defensively.
     */
    static List<String> typePagePaths(String content) {
        String array = extractArray(content);
        List<String> paths = new ArrayList<>();
        Matcher objects = OBJECT.matcher(array);
        while (objects.find()) {
            String object = objects.group();
            Matcher pkg = P_FIELD.matcher(object);
            Matcher label = L_FIELD.matcher(object);
            if (pkg.find() && label.find()) {
                String p = pkg.group(1);
                String l = label.group(1);
                if (l.isEmpty()) {
                    continue;
                }
                String dir = p.isEmpty() ? "" : p.replace('.', '/') + "/";
                paths.add(dir + l + ".html");
            }
        }
        return paths;
    }
}
