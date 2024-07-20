package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record DeleteResponse(
        @NotNull LdapResult ldapResult) {
    public static class Reader implements MessageReader<DeleteResponse> {
        @Override
        public void check(@NotNull DeleteResponse message) throws Throwable {
            message.ldapResult().checkSuccess();
        }

        @Override
        public @NotNull DeleteResponse read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return new DeleteResponse(
                    DER.readTag(
                            LdapResult::read,
                            reader,
                            Ldap.PROTOCOL_OP_DELETE_RESPONSE));
        }
    }

    public static final @NotNull MessageReader<DeleteResponse> READER=new Reader();

    public DeleteResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }
}
