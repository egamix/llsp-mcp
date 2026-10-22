package com.luciadmcp.knowledge.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * AC 2: no crawler, HTTP-client, browser-automation, or model-downloader library is reachable from
 * the {@code cli} runtime classpath (the offline serving-image ingest graph, at this pre-packaging
 * stage). This is a structural, JDK-version-proof guard that complements {@code
 * :checkDependencyDirection}: that task bans the engine/parser groups as direct module edges; this
 * catches outbound-client and unsanctioned network libraries wherever they appear.
 * The exact Jetty server/servlet artifact families required by Story 5.5 are permitted;
 * Jetty client and other Jetty modules remain forbidden.
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

    /** Only the inbound SDK servlet container and its required server dependencies. */
    private static final List<String> INBOUND_JETTY_ARTIFACTS = List.of(
            "jetty-ee11-servlet", "jetty-server", "jetty-security", "jetty-session",
            "jetty-http", "jetty-io", "jetty-util");

    private static boolean inboundJettyArtifact(String name) {
        return INBOUND_JETTY_ARTIFACTS.stream().anyMatch(artifact ->
                name.matches(Pattern.quote(artifact) + "-[0-9]+\\.[0-9]+\\.[0-9]+\\.jar"));
    }

    private static List<String> offenders(String classpath) {
        List<String> offenders = new ArrayList<>();

        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            String name = new File(entry).getName().toLowerCase(Locale.ROOT);
            for (String banned : BANNED_FRAGMENTS) {
                if (name.contains(banned) && !(banned.equals("jetty") && inboundJettyArtifact(name))) {
                    offenders.add(entry + " (matched '" + banned + "')");
                }
            }
        }

        return offenders;
    }

    @Test
    void noNetworkCapableLibraryOnClasspath() {
        List<String> offenders = offenders(System.getProperty("java.class.path", ""));
        assertTrue(offenders.isEmpty(),
                () -> "outbound-client or unsanctioned network library on cli classpath (AD-9 violation): "
                        + offenders);
    }

    @Test
    void inboundServletExceptionStillRejectsOutboundClientFamilies() {
        String inbound = String.join(File.pathSeparator, INBOUND_JETTY_ARTIFACTS.stream()
                .map(artifact -> artifact + "-12.1.14.jar").toList());
        assertTrue(offenders(inbound).isEmpty());
        for (String forbidden : List.of("jetty-client-12.1.14.jar", "jetty-http2-client-12.1.14.jar",
                "jetty-websocket-jetty-client-12.1.14.jar", "jetty-http-12.1.14-httpclient.jar",
                "httpclient5-5.4.jar", "okhttp-4.12.jar", "netty-handler-4.1.jar",
                "async-http-client-2.12.jar", "selenium-java-4.0.jar", "crawler-1.0.jar")) {
            assertTrue(!offenders(forbidden).isEmpty(), forbidden);
        }
    }
}
