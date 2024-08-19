package hu.gds.ldap4j.ldap.extension;

import org.jetbrains.annotations.NotNull;

/**
 * RFC 4529
 */
public class AttributesByObjectClass {
    public static final @NotNull String FEATURE_OID="1.3.6.1.4.1.4203.1.5.2";
    public static final @NotNull String OBJECT_CLASS_IDENTIFIER="@";

    private AttributesByObjectClass() {
    }

    public static @NotNull String attributesByObjectClass(@NotNull String objectClass) {
        return OBJECT_CLASS_IDENTIFIER+objectClass;
    }
}
