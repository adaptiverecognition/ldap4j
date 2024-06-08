package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ExtendedRequest(
        @Nullable String requestName,
        @Nullable String requestValue) {
    public @NotNull ByteBuffer write() throws Throwable {
        ByteBuffer byteBuffer=ByteBuffer.EMPTY;
        if (null!=requestName) {
            byteBuffer=byteBuffer.append(
                    DER.writeTag(Ldap.PROTOCOL_OP_EXTENDED_REQUEST_NAME, DER.writeUtf8NoTag(requestName)));
        }
        if (null!=requestValue) {
            byteBuffer=byteBuffer.append(
                    DER.writeTag(Ldap.PROTOCOL_OP_EXTENDED_REQUEST_VALUE, DER.writeUtf8NoTag(requestValue)));
        }
        return DER.writeTag(Ldap.PROTOCOL_OP_EXTENDED_REQUEST, byteBuffer);
    }
}
