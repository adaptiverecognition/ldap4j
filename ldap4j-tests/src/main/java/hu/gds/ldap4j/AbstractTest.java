package hu.gds.ldap4j;

import java.net.InetSocketAddress;

public abstract class AbstractTest {
    public static final InetSocketAddress BLACK_HOLE=new InetSocketAddress("198.51.100.1"/*"100::1"*/, 65432);
    public static final int PARALLELISM=8;
    public static final int SERVER_PORT_CLEAR_TEXT=0;//10389;
    public static final int SERVER_PORT_TLS=0;//10636;
    public static final long TIMEOUT_NANOS=10_000_000_000L;
    public static final long TIMEOUT_NANOS_SMALL=100_000_000L;
    public static final InetSocketAddress UNKNOWN_HOST
            =InetSocketAddress.createUnresolved("unknown.host.invalid", 65432);

    private AbstractTest() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "no os")
                .trim()
                .toLowerCase()
                .contains("windows");
    }
}
