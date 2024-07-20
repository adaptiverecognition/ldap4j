package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public record AbandonRequest(
        int messageId)
        implements Message<AbandonRequest> {
    @Override
    public @NotNull AbandonRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_ABANDON_REQUEST,
                DER.writeIntegerNoTag(messageId));
    }
}
