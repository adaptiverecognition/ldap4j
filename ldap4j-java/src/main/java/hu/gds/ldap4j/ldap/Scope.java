package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public enum Scope {
    BASE_OBJECT(0),
    SINGLE_LEVEL(1),
    WHOLE_SUBTREE(2);

    private final int value;

    Scope(int value) {
        this.value=value;
    }

    public @NotNull ByteBuffer write() {
        return BER.writeEnumeratedTag(value);
    }
}
