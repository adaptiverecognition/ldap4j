package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ExtendedRequest(
        @NotNull String requestName,
        byte[] requestValue,
        @NotNull MessageReader<ExtendedResponse> responseReader)
        implements Request<ExtendedRequest, ExtendedResponse> {
    public static class Reader implements MessageReader<ExtendedRequest> {
        private final @NotNull MessageReader<ExtendedResponse> responseReader;

        public Reader(@NotNull MessageReader<ExtendedResponse> responseReader) {
            this.responseReader=Objects.requireNonNull(responseReader, "responseReader");
        }

        @Override
        public void check(@NotNull List<@NotNull Control> controls, @NotNull ExtendedRequest message, int messageId) {
        }

        @Override
        public @NotNull ExtendedRequest read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return DER.readTag(
                    (reader2)->{
                        @NotNull String requestName=DER.readTag(
                                DER::readUtf8NoTag,
                                reader2,
                                Ldap.PROTOCOL_OP_EXTENDED_REQUEST_NAME);
                        byte[] requestValue=null;
                        if (reader2.hasRemainingBytes()) {
                            requestValue=DER.readTag(
                                    (reader3)->reader3.readReaminingByteBuffer().arrayCopy(),
                                    reader2,
                                    Ldap.PROTOCOL_OP_EXTENDED_REQUEST_VALUE);
                        }
                        reader2.assertNoRemainingBytes();
                        return new ExtendedRequest(requestName, requestValue, responseReader);
                    },
                    reader,
                    Ldap.PROTOCOL_OP_EXTENDED_REQUEST);
        }
    }
    
    public static final @NotNull ExtendedRequest FAST_BIND=new ExtendedRequest(
            Ldap.FAST_BIND_OID, null, ExtendedResponse.READER_SUCCESS);
    public static final @NotNull ExtendedRequest START_TLS=new ExtendedRequest(
            Ldap.EXTENDED_REQUEST_START_TLS_OID, null, ExtendedResponse.READER_SUCCESS);

    public ExtendedRequest(
            @NotNull String requestName,
            byte[] requestValue,
            @NotNull MessageReader<ExtendedResponse> responseReader) {
        this.requestName=Objects.requireNonNull(requestName, "requestName");
        this.requestValue=requestValue;
        this.responseReader=Objects.requireNonNull(responseReader, "responseReader");
    }
    
    public static @NotNull ExtendedRequest cancel(int messageId) {
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
    public @NotNull ByteBuffer write() {
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
