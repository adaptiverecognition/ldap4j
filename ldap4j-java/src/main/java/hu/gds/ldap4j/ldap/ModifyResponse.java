package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyResponse(
        @NotNull LdapResult ldapResult) {
    public static class Reader implements MessageReader<ModifyResponse> {
        @Override
        public void check(
                @NotNull List<@NotNull Control> controls, @NotNull ModifyResponse message, int messageId)
                throws Throwable {
            message.ldapResult().checkSuccess(controls, messageId);
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
