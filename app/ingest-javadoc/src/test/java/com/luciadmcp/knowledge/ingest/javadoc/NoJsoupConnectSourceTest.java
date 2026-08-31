package com.luciadmcp.knowledge.ingest.javadoc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AD-9 (zero egress) source-level guard for jsoup, AC 8. jsoup is network-capable via
 * {@code Jsoup.connect(...)}; this module parses only local bytes and must never open a connection.
 *
 * <p>Because {@code ingest-javadoc} legitimately needs jsoup on the classpath, {@code cli}'s
 * {@code NoNetworkClasspathTest} can no longer prove "no network" for jsoup by the library's
 * absence. This test restores an automated guard by scanning this module's own source for a
 * {@code Jsoup.connect} call, so a stray fetch fails the build instead of relying on review.
 */
class NoJsoupConnectSourceTest {

    // The invocation form Jsoup.connect( (any spacing), not bare prose mentions of the method name
    // in Javadoc/comments — the AD-9 note in JavadocDiscovery references it without parentheses.
    private static final Pattern CONNECT_CALL = Pattern.compile("Jsoup\\s*\\.\\s*connect\\s*\\(");

    @Test
    void moduleSourceNeverCallsJsoupConnect() throws Exception {
        Path mainSrc = locateMainSource();
        // Fail loudly rather than pass vacuously if the source tree is not where we expect it.
        assertTrue(Files.isDirectory(mainSrc),
                () -> "cannot locate module source at " + mainSrc);

        List<String> scanned = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainSrc)) {
            List<Path> javaFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                scanned.add(file.toString());
                String source = Files.readString(file);
                if (CONNECT_CALL.matcher(source).find()) {
                    offenders.add(file.toString());
                }
            }
        }

        assertTrue(!scanned.isEmpty(), "scanned no source files — the guard would pass vacuously");
        assertTrue(offenders.isEmpty(),
                () -> "Jsoup.connect(...) found in ingest-javadoc source (AD-9 / AC 8 violation): "
                        + offenders);
    }

    /**
     * Resolves this module's {@code src/main/java} independently of the test working directory, by
     * anchoring on a known test resource and walking up to the module root (the ancestor that has a
     * {@code src/main/java} child).
     */
    private static Path locateMainSource() throws Exception {
        URL anchor = NoJsoupConnectSourceTest.class.getClassLoader().getResource("javadoc-fixtures/modern");
        assertNotNull(anchor, "missing anchor resource javadoc-fixtures/modern");
        for (Path dir = Path.of(anchor.toURI()); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not locate src/main/java above " + anchor);
    }
}
