package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ExtendedRequest(
        @NotNull String requestName,
        byte@Nullable[] requestValue,
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
            return BER.readTag(
                    (reader2)->{
                        @NotNull String requestName=BER.readTag(
                                BER::readUtf8NoTag,
                                reader2,
                                REQUEST_NAME_TAG);
                        byte@Nullable[] requestValue=BER.readOptionalTag(
                                BER::readOctetStringNoTag,
                                reader2,
                                ()->null,
                                REQUEST_VALUE_TAG);
                        return new ExtendedRequest(requestName, requestValue, responseReader);
                    },
                    reader,
                    REQUEST_TAG);
        }
    }
    
    public static final byte REQUEST_NAME_TAG=(byte)0x80;
    public static final byte REQUEST_TAG=0x77;
    public static final byte REQUEST_VALUE_TAG=(byte)0x81;

    public ExtendedRequest(
            @NotNull String requestName,
            byte@Nullable[] requestValue,
            @NotNull MessageReader<ExtendedResponse> responseReader) {
        this.requestName=Objects.requireNonNull(requestName, "requestName");
        this.requestValue=requestValue;
        this.responseReader=Objects.requireNonNull(responseReader, "responseReader");
    }

    @Override
    public @NotNull ExtendedRequest self() {
        return this;
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.extendedRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        ByteBuffer byteBuffer=BER.writeTag(
                REQUEST_NAME_TAG,
                BER.writeUtf8NoTag(requestName));
        if (null!=requestValue) {
            byteBuffer=byteBuffer.append(
                    BER.writeTag(
                            REQUEST_VALUE_TAG,
                            ByteBuffer.create(requestValue)));
        }
        return BER.writeTag(REQUEST_TAG, byteBuffer);
    }
}
