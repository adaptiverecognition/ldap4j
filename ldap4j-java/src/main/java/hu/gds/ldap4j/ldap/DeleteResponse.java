package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record DeleteResponse(
        @NotNull LdapResult ldapResult)
        implements Response {
    public static class Reader implements MessageReader<DeleteResponse> {
        @Override
        public void check(
                @NotNull List<@NotNull Control> controls, @NotNull DeleteResponse message, int messageId)
                throws Throwable {
            message.ldapResult().checkSuccess(controls, messageId);
        }

        @Override
        public @NotNull DeleteResponse read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return new DeleteResponse(
                    BER.readTag(
                            LdapResult::read,
                            reader,
                            RESPONSE_TAG));
        }
    }

    public static final @NotNull MessageReader<DeleteResponse> READER=new Reader();
    public static final byte RESPONSE_TAG=0x6b;

    public DeleteResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.deleteResponse(this);
    }
}
