package com.luciadmcp.knowledge.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * AC 2: no crawler, HTTP-client, browser-automation, or model-downloader library is reachable from
 * the {@code cli} runtime classpath (the offline serving-image ingest graph, at this pre-packaging
 * stage). This is a structural, JDK-version-proof guard that complements {@code
 * :checkDependencyDirection}: that task bans the engine/parser groups as direct module edges; this
 * catches network-capable libraries specifically, wherever on the classpath they appear.
 *
 * <p>The training-materials network tooling (docling) is the AD-28 exception and ships only in the
 * separate ingest-time image (Epic 8/9) — never on this classpath.
 *
 * <p>Note: the test classpath includes JUnit and this module's test-only egress-guard support, none
 * of which are network libraries, so scanning {@code java.class.path} is a sound proxy here.
 */
class NoNetworkClasspathTest {

    /** Lowercased artifact-name fragments that indicate a network-capable dependency. */
    private static final List<String> BANNED_FRAGMENTS = List.of(
            "httpclient", "httpcore", "httpasyncclient", "commons-httpclient",
            "okhttp", "okio",
            "async-http-client", "asynchttpclient",
            "retrofit", "feign",
            "netty", "undertow", "grizzly", "jetty",
            "vertx", "vert.x",
            "ktor-client",
            "selenium", "htmlunit", "webdriver", "playwright",
            // NOTE: jsoup is intentionally NOT banned here. It is the sanctioned offline HTML parser
            // for the Javadoc ingest graph (Story 2.1), and `cli` — the composition root that runs
            // `ingest` — necessarily pulls `ingest-javadoc` (and thus jsoup) onto its runtime
            // classpath. jsoup is network-capable via Jsoup.connect, but AD-9 for jsoup is enforced
            // at the source level instead: `ingest-javadoc` parses from local bytes only and never
            // calls Jsoup.connect (Story 2.1 AC 8). A classpath-presence ban is incompatible with
            // needing the parser at all, so the guard for jsoup lives with its usage, not here.
            "crawler", "webmagic", "jaunt");

    @Test
    void noNetworkCapableLibraryOnClasspath() {
        String classpath = System.getProperty("java.class.path", "");
        List<String> offenders = new ArrayList<>();

        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            String name = new File(entry).getName().toLowerCase(Locale.ROOT);
            for (String banned : BANNED_FRAGMENTS) {
                if (name.contains(banned)) {
                    offenders.add(entry + " (matched '" + banned + "')");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                () -> "network-capable library found on the cli classpath (AD-9 violation): "
                        + offenders);
    }
}
