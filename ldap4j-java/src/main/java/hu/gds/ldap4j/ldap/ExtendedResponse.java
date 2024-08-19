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
        public static class Cancel extends ExtendedResponse.Reader {
            @Override
            public void check(
                    @NotNull List<@NotNull Control> controls, @NotNull ExtendedResponse message, int messageId)
                    throws Throwable {
                message.ldapResult.checkCancel(controls, messageId);
            }
        }

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
                                Ldap.PROTOCOL_OP_EXTENDED_RESPONSE_NAME);
                        byte@Nullable[] responseValue=BER.readOptionalTag(
                                BER::readOctetStringNoTag,
                                reader2,
                                ()->null,
                                Ldap.PROTOCOL_OP_EXTENDED_RESPONSE_VALUE);
                        return new ExtendedResponse(ldapResult, responseName, responseValue);
                    },
                    reader,
                    Ldap.PROTOCOL_OP_EXTENDED_RESPONSE);
        }
    }

    public static final @NotNull MessageReader<ExtendedResponse> READER_CANCEL=new Reader.Cancel();
    public static final @NotNull MessageReader<ExtendedResponse> READER_SUCCESS=new Reader.Success();

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
                    Ldap.PROTOCOL_OP_EXTENDED_RESPONSE_NAME,
                    BER.writeUtf8NoTag(responseName)));
        }
        if (null!=responseValue) {
            contentBuffer=contentBuffer.append(BER.writeTag(
                    Ldap.PROTOCOL_OP_EXTENDED_RESPONSE_VALUE,
                    ByteBuffer.create(responseValue)));
        }
        return BER.writeTag(
                Ldap.PROTOCOL_OP_EXTENDED_RESPONSE,
                contentBuffer);
    }
}
