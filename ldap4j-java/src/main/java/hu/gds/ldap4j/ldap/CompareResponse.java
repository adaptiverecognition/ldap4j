package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record CompareResponse(
        @NotNull LdapResult ldapResult) {
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
                    DER.readTag(
                            LdapResult::read,
                            reader,
                            Ldap.PROTOCOL_OP_COMPARE_RESPONSE));
        }
    }

    public static final @NotNull MessageReader<CompareResponse> READER=new Reader();

    public CompareResponse(@NotNull LdapResult ldapResult) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
    }
}
