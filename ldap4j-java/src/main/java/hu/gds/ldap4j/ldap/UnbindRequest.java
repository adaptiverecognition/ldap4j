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
            return DER.readTag(
                    (reader2)->{
                        reader2.assertNoRemainingBytes();
                        return new UnbindRequest();
                    },
                    reader,
                    Ldap.PROTOCOL_OP_UNBIND_REQUEST);
        }
    }

    public static final @NotNull MessageReader<UnbindRequest> READER=new Reader();

    @Override
    public @NotNull UnbindRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() {
        return DER.writeTag(Ldap.PROTOCOL_OP_UNBIND_REQUEST, ByteBuffer.EMPTY);
    }
}
