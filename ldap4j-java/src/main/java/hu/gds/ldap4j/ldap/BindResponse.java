package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BindResponse(
        @NotNull LdapResult ldapResult,
        byte@Nullable[] serverSaslCredentials)
        implements Response {
    public static abstract class Reader implements MessageReader<BindResponse> {
        public static class SASL extends BindResponse.Reader {
            @Override
            public void check(
                    @NotNull List<@NotNull Control> controls, @NotNull BindResponse message, int messageId)
                    throws Throwable {
                message.ldapResult.checkSASL(controls, messageId);
            }
        }

        public static class Success extends BindResponse.Reader {
            @Override
            public void check(
                    @NotNull List<@NotNull Control> controls, @NotNull BindResponse message, int messageId)
                    throws Throwable {
                message.ldapResult.checkSuccess(controls, messageId);
            }
        }

        @Override
        public @NotNull BindResponse read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return BER.readTag(
                    (reader2)->{
                        @NotNull LdapResult ldapResult=LdapResult.read(reader2);
                        byte@Nullable[] serverSaslCredentials=BER.readOptionalTag(
                                BER::readOctetStringNoTag,
                                reader2,
                                ()->null,
                                CREDENTIALS_TAG);
                        return new BindResponse(ldapResult, serverSaslCredentials);
                    },
                    reader,
                    RESPONSE_TAG);
        }
    }

    public static final byte CREDENTIALS_TAG=(byte)0x87;
    public static final @NotNull MessageReader<BindResponse> READER_SASL=new Reader.SASL();
    public static final @NotNull MessageReader<BindResponse> READER_SUCCESS=new Reader.Success();
    public static final byte RESPONSE_TAG=0x61;

    public BindResponse(@NotNull LdapResult ldapResult, byte@Nullable [] serverSaslCredentials) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
        this.serverSaslCredentials=serverSaslCredentials;
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.bindResponse(this);
    }
}
