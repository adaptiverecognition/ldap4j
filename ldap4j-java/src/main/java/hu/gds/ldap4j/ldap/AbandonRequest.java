package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public record AbandonRequest(
        int messageId)
        implements Message<AbandonRequest> {
    public static final byte REQUEST_TAG=0x70;

    @Override
    public @NotNull AbandonRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() {
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeIntegerNoTag(messageId));
    }
}
