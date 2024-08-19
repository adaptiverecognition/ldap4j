package hu.gds.ldap4j.ldap.extension;

import org.jetbrains.annotations.NotNull;

/**
 * RFC 4525
 */
public class ModifyIncrement {
    public static final @NotNull String FEATURE_OID="1.3.6.1.1.14";
    public static final int OPERATION_INCREMENT=3;

    private ModifyIncrement() {
    }
}
