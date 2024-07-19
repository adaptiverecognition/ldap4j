package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ExtendedRequest(
        @NotNull String requestName,
        byte[] requestValue) {
    public ExtendedRequest(@NotNull String requestName, byte[] requestValue) {
        this.requestName=Objects.requireNonNull(requestName, "requestName");
        this.requestValue=requestValue;
    }

    public @NotNull ByteBuffer write() throws Throwable {
        ByteBuffer byteBuffer=DER.writeTag(
                Ldap.PROTOCOL_OP_EXTENDED_REQUEST_NAME,
                DER.writeUtf8NoTag(requestName));
        if (null!=requestValue) {
            byteBuffer=byteBuffer.append(
                    DER.writeTag(
                            Ldap.PROTOCOL_OP_EXTENDED_REQUEST_VALUE,
                            ByteBuffer.create(requestValue)));
        }
        return DER.writeTag(Ldap.PROTOCOL_OP_EXTENDED_REQUEST, byteBuffer);
    }
}
