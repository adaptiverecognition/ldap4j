package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PasswordModify {
    public record Response(char @Nullable [] genPasswd) {
    }

    private PasswordModify() {
    }

    public static @NotNull ControlsMessage<ExtendedRequest> request(
            char @Nullable [] newPasswd, char @Nullable [] oldPasswd, @Nullable String userIdentity) {
        ByteBuffer buffer=ByteBuffer.EMPTY;
        if (null!=userIdentity) {
            buffer=buffer.append(DER.writeTag(
                    Ldap.PASSWORD_MODIFY_REQUEST_USER_IDENTITY,
                    DER.writeUtf8NoTag(userIdentity)));
        }
        if (null!=oldPasswd) {
            buffer=buffer.append(DER.writeTag(
                    Ldap.PASSWORD_MODIFY_REQUEST_OLD_PASSWD,
                    DER.writeUtf8NoTag(oldPasswd)));
        }
        if (null!=newPasswd) {
            buffer=buffer.append(DER.writeTag(
                    Ldap.PASSWORD_MODIFY_REQUEST_NEW_PASSWD,
                    DER.writeUtf8NoTag(newPasswd)));
        }
        return new ExtendedRequest(
                Ldap.EXTENDED_REQUEST_PASSWORD_MODIFY,
                DER.writeSequence(buffer)
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
                        (reader)->DER.readSequence(
                                (reader2)->DER.readOptionalTag(
                                        DER::readUtf8NoTagChars,
                                        reader2,
                                        ()->null,
                                        Ldap.PASSWORD_MODIFY_RESPONSE_GEN_PASSWD),
                                reader));
        return new Response(genPasswd);
    }
}
