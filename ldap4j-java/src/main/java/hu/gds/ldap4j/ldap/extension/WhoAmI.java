package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.ExtendedResponse;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * RFC 4532
 */
public class WhoAmI {
    public static final @NotNull ExtendedRequest REQUEST;
    public static final @NotNull String REQUEST_OID="1.3.6.1.4.1.4203.1.11.3";

    static {
        REQUEST=new ExtendedRequest(
                ByteBuffer.create(REQUEST_OID),
                null,
                ExtendedResponse.READER_SUCCESS);
    }

    private WhoAmI() {
    }
}
