package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.Control;
import org.jetbrains.annotations.NotNull;

/**
 * RFC 6171
 */
public class DonTUseCopyControl {
    public static final @NotNull String CONTROL_OID="1.3.6.1.1.22";

    private DonTUseCopyControl() {
    }

    public static @NotNull Control request() {
        return request(true);
    }

    public static @NotNull Control request(boolean criticality) {
        return new Control(CONTROL_OID, null, criticality);
    }
}
