package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.Filter;
import org.jetbrains.annotations.NotNull;

/**
 * RFC 4528
 */
public class AssertionControl {
    public static final @NotNull String CONTROL_OID="1.3.6.1.1.12";

    private AssertionControl() {
    }

    public static @NotNull Control request(boolean criticality, @NotNull Filter filter) throws Throwable {
        return Control.create(
                CONTROL_OID,
                filter.write(),
                criticality);
    }

    public static @NotNull Control request(@NotNull Filter filter) throws Throwable {
        return request(true, filter);
    }
}
