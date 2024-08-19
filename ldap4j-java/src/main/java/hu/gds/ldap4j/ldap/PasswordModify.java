package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 3062
 */
public class PasswordModify {
    public record Response(char @Nullable [] genPasswd) {
    }

    private PasswordModify() {
    }

    public static @NotNull ControlsMessage<ExtendedRequest> request(
            char @Nullable [] newPasswd, char @Nullable [] oldPasswd, @Nullable String userIdentity) {
        ByteBuffer buffer=ByteBuffer.EMPTY;
        if (null!=userIdentity) {
            buffer=buffer.append(BER.writeTag(
                    Ldap.PASSWORD_MODIFY_REQUEST_USER_IDENTITY,
                    BER.writeUtf8NoTag(userIdentity)));
        }
        if (null!=oldPasswd) {
            buffer=buffer.append(BER.writeTag(
                    Ldap.PASSWORD_MODIFY_REQUEST_OLD_PASSWD,
                    BER.writeUtf8NoTag(oldPasswd)));
        }
        if (null!=newPasswd) {
            buffer=buffer.append(BER.writeTag(
                    Ldap.PASSWORD_MODIFY_REQUEST_NEW_PASSWD,
                    BER.writeUtf8NoTag(newPasswd)));
        }
        return new ExtendedRequest(
                Ldap.EXTENDED_REQUEST_PASSWORD_MODIFY,
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
        char @Nullable [] genPasswd=ByteBuffer.create(responseValue)
                .read(
                        (reader)->BER.readSequence(
                                (reader2)->BER.readOptionalTag(
                                        BER::readUtf8NoTagChars,
                                        reader2,
                                        ()->null,
                                        Ldap.PASSWORD_MODIFY_RESPONSE_GEN_PASSWD),
                                reader));
        return new Response(genPasswd);
    }
}
