package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public record UnbindRequest() implements Message<UnbindRequest> {
    @Override
    public @NotNull UnbindRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() {
        return DER.writeTag(Ldap.PROTOCOL_OP_UNBIND_REQUEST, DER.writeNullNoTag());
    }
}
