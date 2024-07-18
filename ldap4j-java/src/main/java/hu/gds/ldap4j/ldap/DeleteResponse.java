package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record DeleteResponse(
        @NotNull LdapResult ldapResult) {
    public DeleteResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    public static @NotNull DeleteResponse read(ByteBuffer.Reader reader) throws Throwable {
        return new DeleteResponse(
                DER.readTag(
                        LdapResult::read,
                        reader,
                        Ldap.PROTOCOL_OP_DELETE_RESPONSE));
    }
}
