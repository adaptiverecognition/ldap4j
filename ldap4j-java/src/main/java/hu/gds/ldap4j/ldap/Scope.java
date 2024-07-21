package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;

public enum Scope {
    BASE_OBJECT(0),
    SINGLE_LEVEL(1),
    WHOLE_SUBTREE(2);

    private final int value;

    Scope(int value) {
        this.value=value;
    }

    public ByteBuffer write() {
        return DER.writeEnumeratedTag(value);
    }
}
