package hu.gds.ldap4j.ldap.extension;

import org.jetbrains.annotations.NotNull;

/**
 * RFC 3673
 */
public class AllOperationAttributes {
    public static final @NotNull String ALL_OPERATIONAL_ATTRIBUTES="+";
    /**
     * RFC 5020
     */
    public static final @NotNull String ENTRY_DN="entryDN";
    /**
     * RFC 4530
     */
    public static final @NotNull String ENTRY_UUID="entryUUID";
    public static final @NotNull String FEATURE_OID="1.3.6.1.4.1.4203.1.5.1";

    private AllOperationAttributes() {
    }
}
