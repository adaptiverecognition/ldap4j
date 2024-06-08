package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public record UnbindRequest() {
    public @NotNull ByteBuffer write() throws Throwable {
        return DER.writeTag(Ldap.PROTOCOL_OP_UNBIND_REQUEST, DER.writeNullNoTag());
    }
}
