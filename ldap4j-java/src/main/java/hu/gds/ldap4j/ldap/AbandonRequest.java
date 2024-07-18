package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public record AbandonRequest(int messageId, boolean signKludge) {
    public AbandonRequest(int messageId) {
        this(messageId, true);
    }

    public @NotNull ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_ABANDON_REQUEST,
                DER.writeIntegerNoTag(signKludge, messageId));
    }
}
