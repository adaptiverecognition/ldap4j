package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record AddResponse(
        @NotNull LdapResult ldapResult) {
    public AddResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    public static @NotNull AddResponse read(ByteBuffer.Reader reader) throws Throwable {
        return new AddResponse(
                DER.readTag(
                        LdapResult::read,
                        reader,
                        Ldap.PROTOCOL_OP_ADD_RESPONSE));
    }
}
