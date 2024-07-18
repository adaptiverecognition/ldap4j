package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record CompareResponse(
        @NotNull LdapResult ldapResult) {
    public CompareResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    public static @NotNull CompareResponse read(ByteBuffer.Reader reader) throws Throwable {
        return new CompareResponse(
                DER.readTag(
                        LdapResult::read,
                        reader,
                        Ldap.PROTOCOL_OP_COMPARE_RESPONSE));
    }
}
