package com.luciadmcp.knowledge.cli.testsupport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only control point for the DNS-layer egress guard.
 *
 * <p>The guard is a {@link java.net.spi.InetAddressResolverProvider} registered via
 * {@code META-INF/services} (see {@link GuardingInetAddressResolverProvider}). When {@linkplain
 * #arm() armed}, any hostname resolution attempted anywhere in the test JVM is recorded here and
 * blocked with an {@link UnknownHostException}; when disarmed the platform resolver is used, so
 * normal test behavior is unaffected.
 *
 * <h2>Why this mechanism, and why not SecurityManager</h2>
 * The obvious way to trap a socket connect is a {@code SecurityManager.checkConnect}. That is a
 * trap: {@code SecurityManager} is deprecated for removal (JEP 411) and permanently disabled on
 * JDK 24+ (JEP 486), where {@code System.setSecurityManager} throws — so a SecurityManager-based
 * guard would silently stop protecting if the JDK pin (blocker B5) moves to 25. The
 * {@code InetAddressResolver} SPI (JEP 418) is supported and non-removed on JDK 21/24/25, so this
 * guard survives any B5 outcome. Hostname resolution is the first step of essentially any real
 * egress; raw-IP socket egress is covered structurally by {@code NoNetworkClasspathTest} (no
 * HTTP/crawler/downloader library is even present to open such a connection).
 *
 * <h2>Fail loud, never silently no-op</h2>
 * If the provider were not picked up by the ServiceLoader, an armed lookup would not be recorded.
 * {@link #assertInstalledAndTrips()} detects exactly that and fails loudly, so a broken guard can
 * never masquerade as "no egress detected".
 */
public final class EgressGuard {

    private static volatile boolean armed = false;
    private static final List<String> VIOLATIONS = new CopyOnWriteArrayList<>();

    private EgressGuard() {
    }

    /** Begin recording+blocking hostname resolutions. Clears any prior violations. */
    public static void arm() {
        VIOLATIONS.clear();
        armed = true;
    }

    /** Stop guarding; the platform resolver handles subsequent lookups. */
    public static void disarm() {
        armed = false;
    }

    static boolean isArmed() {
        return armed;
    }

    static void recordViolation(String detail) {
        VIOLATIONS.add(detail);
    }

    /** Egress attempts recorded since the last {@link #arm()}. */
    public static List<String> violations() {
        return List.copyOf(VIOLATIONS);
    }

    /**
     * Proves the guard is actually installed and trips on a real resolution attempt. Arms the
     * guard, performs a deliberate lookup of a unique bogus host, and asserts a violation was
     * recorded. If the provider is not wired, no violation is recorded and this throws — the guard
     * is never allowed to silently no-op.
     *
     * @throws IllegalStateException if the guard did not trip (i.e. it is not installed/active)
     */
    public static void assertInstalledAndTrips() {
        arm();
        try {
            String host = "egress-guard-selftest-" + System.nanoTime() + ".invalid";
            try {
                InetAddress.getByName(host);
            } catch (UnknownHostException expected) {
                // Expected: the guard blocks by throwing. The proof is the recorded violation below.
            }
            if (violations().isEmpty()) {
                throw new IllegalStateException(
                        "egress guard is NOT active: a deliberate DNS lookup was not intercepted. "
                                + "The InetAddressResolverProvider was not loaded — refusing to let "
                                + "the egress harness pass without protection.");
            }
        } finally {
            disarm();
        }
    }
}
