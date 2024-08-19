package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record CompareResponse(
        @NotNull LdapResult ldapResult)
        implements Response {
    public static class Reader implements MessageReader<CompareResponse> {
        @Override
        public void check(
                @NotNull List<@NotNull Control> controls, @NotNull CompareResponse message, int messageId)
                throws Throwable {
            message.ldapResult().checkCompare(controls, messageId);
        }

        @Override
        public @NotNull CompareResponse read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return new CompareResponse(
                    BER.readTag(
                            LdapResult::read,
                            reader,
                            RESPONSE_TAG));
        }
    }

    public static final @NotNull MessageReader<CompareResponse> READER=new Reader();
    public static final byte RESPONSE_TAG=0x6f;

    public CompareResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.compareResponse(this);
    }
}
