package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public record UnbindRequest() implements Message<UnbindRequest> {
    public static class Reader implements MessageReader<UnbindRequest> {
        @Override
        public void check(@NotNull List<@NotNull Control> controls, @NotNull UnbindRequest message, int messageId) {
        }

        @Override
        public @NotNull UnbindRequest read(ByteBuffer.@NotNull Reader reader) throws Throwable {
            return BER.readTag(
                    (reader2)->new UnbindRequest(),
                    reader,
                    REQUEST_TAG);
        }
    }

    public static final @NotNull MessageReader<UnbindRequest> READER=new Reader();
    public static final byte REQUEST_TAG=0x42;

    @Override
    public @NotNull UnbindRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() {
        return BER.writeTag(REQUEST_TAG, ByteBuffer.empty());
    }
}
