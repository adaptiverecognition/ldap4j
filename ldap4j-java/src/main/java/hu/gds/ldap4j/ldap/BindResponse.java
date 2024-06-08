package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record BindResponse(
        @NotNull LdapResult ldapResult) {
    public BindResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    public static @NotNull BindResponse read(ByteBuffer.Reader reader) throws Throwable {
        return new BindResponse(
                DER.readTag(
                        LdapResult::read,
                        reader,
                        Ldap.PROTOCOL_OP_BIND_RESPONSE));
    }
}
