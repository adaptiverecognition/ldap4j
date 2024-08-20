package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public class StartTls {
    public static final @NotNull ExtendedRequest REQUEST;
    public static final @NotNull String REQUEST_OID="1.3.6.1.4.1.1466.20037";

    static {
        REQUEST=new ExtendedRequest(
                ByteBuffer.create(REQUEST_OID),
                null,
                ExtendedResponse.READER_SUCCESS);
    }

    private StartTls() {
    }
}
