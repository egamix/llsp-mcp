package com.luciadmcp.knowledge.cli.testsupport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.stream.Stream;

/**
 * Test-only {@link InetAddressResolverProvider} that routes all hostname resolution through
 * {@link EgressGuard}. Registered for the test JVM via
 * {@code src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider}.
 *
 * <p>When the guard is armed, resolutions are recorded and blocked; otherwise they delegate to the
 * platform built-in resolver, so ordinary test behavior is unchanged. This is the JDK-21/24/25-safe
 * egress guard (JEP 418) chosen over the removed {@code SecurityManager} approach — see
 * {@link EgressGuard}.
 */
public final class GuardingInetAddressResolverProvider extends InetAddressResolverProvider {

    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new GuardingResolver(configuration.builtinResolver());
    }

    @Override
    public String name() {
        return "luciad-egress-guard";
    }

    private static final class GuardingResolver implements InetAddressResolver {

        private final InetAddressResolver builtin;

        private GuardingResolver(InetAddressResolver builtin) {
            this.builtin = builtin;
        }

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
                throws UnknownHostException {
            if (EgressGuard.isArmed()) {
                EgressGuard.recordViolation("DNS lookupByName: " + host);
                throw new UnknownHostException(
                        "egress blocked by luciad egress guard (AD-9): " + host);
            }
            return builtin.lookupByName(host, lookupPolicy);
        }

        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            if (EgressGuard.isArmed()) {
                EgressGuard.recordViolation("reverse DNS lookupByAddress");
                throw new UnknownHostException("egress blocked by luciad egress guard (AD-9)");
            }
            return builtin.lookupByAddress(addr);
        }
    }
}
