package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.ExtendedResponse;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public class FastBind {
    public static final @NotNull ExtendedRequest REQUEST;
    public static final @NotNull String REQUEST_OPERATION_OID="1.2.840.113556.1.4.1781";

    static {
        REQUEST=new ExtendedRequest(
                ByteBuffer.create(REQUEST_OPERATION_OID),
                null,
                ExtendedResponse.READER_SUCCESS);
    }

    private FastBind() {
    }
}
