package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.ExtendedResponse;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 3062
 */
public class PasswordModify {
    public static final byte REQUEST_USER_IDENTITY_TAG=(byte)0x80;
    public static final byte REQUEST_OLD_PASSWD_TAG=(byte)0x81;
    public static final @NotNull String REQUEST_OPERATION_IOD="1.3.6.1.4.1.4203.1.11.1";
    public static final byte REQUEST_NEW_PASSWD_TAG=(byte)0x82;
    public static final byte RESPONSE_GEN_PASSWD_TAG=(byte)0x80;
    
    public record Response(@Nullable ByteBuffer genPasswd) {
        public static @NotNull Response read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readSequence(
                    (reader2)->{
                        @Nullable ByteBuffer genPasswd=BER.readOptionalTag(
                                BER::readOctetStringNoTag,
                                reader2,
                                ()->null,
                                RESPONSE_GEN_PASSWD_TAG);
                        return new Response(genPasswd);
                    },
                    reader);
        }
    }

    private PasswordModify() {
    }

    public static @NotNull ControlsMessage<ExtendedRequest> request(
            @Nullable ByteBuffer newPasswd, @Nullable ByteBuffer oldPasswd, @Nullable ByteBuffer userIdentity) {
        ByteBuffer buffer=ByteBuffer.empty();
        if (null!=userIdentity) {
            buffer=buffer.append(BER.writeTag(
                    REQUEST_USER_IDENTITY_TAG,
                    BER.writeOctetStringNoTag(userIdentity)));
        }
        if (null!=oldPasswd) {
            buffer=buffer.append(BER.writeTag(
                    REQUEST_OLD_PASSWD_TAG,
                    BER.writeOctetStringNoTag(oldPasswd)));
        }
        if (null!=newPasswd) {
            buffer=buffer.append(BER.writeTag(
                    REQUEST_NEW_PASSWD_TAG,
                    BER.writeOctetStringNoTag(newPasswd)));
        }
        return new ExtendedRequest(
                ByteBuffer.create(REQUEST_OPERATION_IOD),
                BER.writeSequence(buffer),
                ExtendedResponse.READER_SUCCESS)
                .controlsEmpty();
    }

    public static @NotNull ControlsMessage<ExtendedRequest> request(
            char @Nullable [] newPasswd, char @Nullable [] oldPasswd, @Nullable String userIdentity) {
        return request(
                ByteBuffer.createNull(newPasswd),
                ByteBuffer.createNull(oldPasswd),
                ByteBuffer.createNull(userIdentity));
    }

    public static @Nullable Response response(@NotNull ControlsMessage<ExtendedResponse> response) throws Throwable {
        @Nullable ByteBuffer responseValue=response.message().responseValue();
        if (null==responseValue) {
            return null;
        }
        return responseValue.read(Response::read);
    }
}
