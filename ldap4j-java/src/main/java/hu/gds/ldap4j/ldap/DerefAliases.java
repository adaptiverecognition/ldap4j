package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public enum DerefAliases {
    DEREF_ALWAYS(3),
    DEREF_FINDING_BASE_OBJ(2),
    DEREF_IN_SEARCHING(1),
    NEVER_DEREF_ALIASES(0);

    private final int value;

    DerefAliases(int value) {
        this.value=value;
    }

    public @NotNull ByteBuffer write() {
        return BER.writeEnumeratedTag(value);
    }
}
