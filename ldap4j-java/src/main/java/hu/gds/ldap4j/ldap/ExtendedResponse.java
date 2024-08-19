package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ExtendedResponse(
        @NotNull LdapResult ldapResult,
        @Nullable String responseName,
        byte@Nullable[] responseValue)
        implements Message<ExtendedResponse>, Response {
    public static abstract class Reader implements MessageReader<ExtendedResponse> {
        public static class Success extends ExtendedResponse.Reader {
            @Override
            public void check(
                    @NotNull List<@NotNull Control> controls, @NotNull ExtendedResponse message, int messageId)
                    throws Throwable {
                message.ldapResult.checkSuccess(controls, messageId);
            }
        }

        @Override
        public @NotNull ExtendedResponse read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readTag(
                    (reader2)->{
                        @NotNull LdapResult ldapResult=LdapResult.read(reader2);
                        @Nullable String responseName=BER.readOptionalTag(
                                BER::readUtf8NoTag,
                                reader2,
                                ()->null,
                                RESPONSE_NAME_TAG);
                        byte@Nullable[] responseValue=BER.readOptionalTag(
                                BER::readOctetStringNoTag,
                                reader2,
                                ()->null,
                                RESPONSE_VALUE_TAG);
                        return new ExtendedResponse(ldapResult, responseName, responseValue);
                    },
                    reader,
                    RESPONSE_TAG);
        }
    }

    public static final @NotNull String NOTICE_OF_DISCONNECTION_OID="1.3.6.1.4.1.1466.20036";
    public static final @NotNull MessageReader<ExtendedResponse> READER_SUCCESS=new Reader.Success();
    public static final byte RESPONSE_NAME_TAG=(byte)0x8a;
    public static final byte RESPONSE_TAG=0x78;
    public static final byte RESPONSE_VALUE_TAG=(byte)0x8b;

    public ExtendedResponse(
            @NotNull LdapResult ldapResult,
            @Nullable String responseName,
            byte@Nullable[] responseValue) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
        this.responseName=responseName;
        this.responseValue=responseValue;
    }

    @Override
    public @NotNull ExtendedResponse self() {
        return this;
    }

    @Override
    public <T> T visit(Response.@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.extendedResponse(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        ByteBuffer contentBuffer=ldapResult.write();
        if (null!=responseName) {
            contentBuffer=contentBuffer.append(BER.writeTag(
                    RESPONSE_NAME_TAG,
                    BER.writeUtf8NoTag(responseName)));
        }
        if (null!=responseValue) {
            contentBuffer=contentBuffer.append(BER.writeTag(
                    RESPONSE_VALUE_TAG,
                    ByteBuffer.create(responseValue)));
        }
        return BER.writeTag(
                RESPONSE_TAG,
                contentBuffer);
    }
}
