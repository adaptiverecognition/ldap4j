package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyResponse(
        @NotNull LdapResult ldapResult) {
    public static class Reader implements MessageReader<ModifyResponse> {
        @Override
        public void check(@NotNull ModifyResponse message) throws Throwable {
            message.ldapResult().checkSuccess();
        }

        @Override
        public @NotNull ModifyResponse read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return new ModifyResponse(
                    DER.readTag(
                            LdapResult::read,
                            reader,
                            Ldap.PROTOCOL_OP_MODIFY_RESPONSE));
        }
    }

    public static final @NotNull MessageReader<ModifyResponse> READER=new Reader();

    public ModifyResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }
}
