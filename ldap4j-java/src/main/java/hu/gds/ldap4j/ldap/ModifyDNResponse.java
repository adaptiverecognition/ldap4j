package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyDNResponse(
        @NotNull LdapResult ldapResult) {
    public static class Reader implements MessageReader<ModifyDNResponse> {
        @Override
        public void check(@NotNull ModifyDNResponse message) throws Throwable {
            message.ldapResult().checkSuccess();
        }

        @Override
        public @NotNull ModifyDNResponse read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return new ModifyDNResponse(
                    DER.readTag(
                            LdapResult::read,
                            reader,
                            Ldap.PROTOCOL_OP_MODIFY_DN_RESPONSE));
        }
    }

    public static final @NotNull MessageReader<ModifyDNResponse> READER=new Reader();

    public ModifyDNResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }
}
