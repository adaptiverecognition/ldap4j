package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record SimpleBindRequest(String bindDn, char[] password, int version) {
    public SimpleBindRequest(@NotNull String bindDn, char[] password, int version) {
        this.bindDn=Objects.requireNonNull(bindDn, "baseDn");
        this.password=Objects.requireNonNull(password, "password");
        this.version=version;
    }

    public ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_BIND_REQUEST,
                DER.writeIntegerTag(false, version)
                        .append(DER.writeUtf8Tag(bindDn))
                        .append(DER.writeTag(Ldap.AUTHENTICATION_CHOICE_SIMPLE, DER.writeUtf8NoTag(password))));
    }
}
