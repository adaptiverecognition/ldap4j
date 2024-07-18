package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyDNResponse(
        @NotNull LdapResult ldapResult) {
    public ModifyDNResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    public static @NotNull ModifyDNResponse read(ByteBuffer.Reader reader) throws Throwable {
        return new ModifyDNResponse(
                DER.readTag(
                        LdapResult::read,
                        reader,
                        Ldap.PROTOCOL_OP_MODIFY_DN_RESPONSE));
    }
}
