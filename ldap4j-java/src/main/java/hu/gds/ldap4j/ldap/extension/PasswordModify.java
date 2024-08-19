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
    
    public record Response(char @Nullable [] genPasswd) {
        public static @NotNull Response read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readSequence(
                    (reader2)->{
                        char @Nullable [] genPasswd=BER.readOptionalTag(
                                BER::readUtf8NoTagChars,
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
            char @Nullable [] newPasswd, char @Nullable [] oldPasswd, @Nullable String userIdentity) {
        ByteBuffer buffer=ByteBuffer.EMPTY;
        if (null!=userIdentity) {
            buffer=buffer.append(BER.writeTag(
                    REQUEST_USER_IDENTITY_TAG,
                    BER.writeUtf8NoTag(userIdentity)));
        }
        if (null!=oldPasswd) {
            buffer=buffer.append(BER.writeTag(
                    REQUEST_OLD_PASSWD_TAG,
                    BER.writeUtf8NoTag(oldPasswd)));
        }
        if (null!=newPasswd) {
            buffer=buffer.append(BER.writeTag(
                    REQUEST_NEW_PASSWD_TAG,
                    BER.writeUtf8NoTag(newPasswd)));
        }
        return new ExtendedRequest(
                REQUEST_OPERATION_IOD,
                BER.writeSequence(buffer)
                        .arrayCopy(),
                ExtendedResponse.READER_SUCCESS)
                .controlsEmpty();
    }

    public static @Nullable Response response(@NotNull ControlsMessage<ExtendedResponse> response) throws Throwable {
        byte @Nullable [] responseValue=response.message().responseValue();
        if (null==responseValue) {
            return null;
        }
        return ByteBuffer.create(responseValue)
                .read(Response::read);
    }
}
