package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyResponse(
        @NotNull LdapResult ldapResult) {
    public ModifyResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    public static @NotNull ModifyResponse read(ByteBuffer.Reader reader) throws Throwable {
        return new ModifyResponse(
                DER.readTag(
                        LdapResult::read,
                        reader,
                        Ldap.PROTOCOL_OP_MODIFY_RESPONSE));
    }
}
