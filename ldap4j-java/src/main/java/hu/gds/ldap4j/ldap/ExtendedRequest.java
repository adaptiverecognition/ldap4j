package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ExtendedRequest(
        @NotNull String requestName,
        byte[] requestValue,
        @NotNull MessageReader<ExtendedResponse> responseReader)
        implements Request<ExtendedRequest, ExtendedResponse> {
    public static final ExtendedRequest FAST_BIND=new ExtendedRequest(
            Ldap.FAST_BIND_OID, null, ExtendedResponse.READER_SUCCESS);
    public static final ExtendedRequest START_TLS=new ExtendedRequest(
            Ldap.EXTENDED_REQUEST_START_TLS_OID, null, ExtendedResponse.READER_SUCCESS);

    public ExtendedRequest(
            @NotNull String requestName,
            byte[] requestValue,
            @NotNull MessageReader<ExtendedResponse> responseReader) {
        this.requestName=Objects.requireNonNull(requestName, "requestName");
        this.requestValue=requestValue;
        this.responseReader=Objects.requireNonNull(responseReader, "responseReader");
    }
    
    public static @NotNull ExtendedRequest cancel(int messageId) throws Throwable {
        return new ExtendedRequest(
                Ldap.EXTENDED_REQUEST_CANCEL_OP_OID,
                DER.writeSequence(
                                DER.writeIntegerTag(messageId))
                        .arrayCopy(),
                ExtendedResponse.READER_CANCEL);
    }

    @Override
    public @NotNull ExtendedRequest self() {
        return this;
    }

    @Override
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
