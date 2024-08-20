package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.ExtendedResponse;
import hu.gds.ldap4j.ldap.LdapException;
import hu.gds.ldap4j.ldap.LdapResult;
import hu.gds.ldap4j.ldap.LdapResultCode;
import hu.gds.ldap4j.ldap.MessageReader;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * RFC 3909
 */
public class Cancel {
    public static class ResponseReader extends ExtendedResponse.Reader {
        @Override
        public void check(
                @NotNull List<@NotNull Control> controls, @NotNull ExtendedResponse message, int messageId)
                throws Throwable {
            checkLdapResultCode(controls, messageId, message.ldapResult());
        }
    }

    public static final @NotNull MessageReader<ExtendedResponse> READER=new ResponseReader();
    public static final @NotNull String REQUEST_OPERATION_OID="1.3.6.1.1.8";
    
    private Cancel() {
    }

    public static void checkLdapResultCode(
            @NotNull List<@NotNull Control> controls,
            int messageId,
            @NotNull LdapResult result)
            throws LdapException {
        if ((LdapResultCode.CANCELED.code!=result.resultCode())
                && (LdapResultCode.CANNOT_CANCEL.code!=result.resultCode())
                && (LdapResultCode.NO_SUCH_OPERATION.code!=result.resultCode())
                && (LdapResultCode.TOO_LATE.code!=result.resultCode())) {
            throw new LdapException(
                    controls,
                    result.diagnosticMessages().utf8(),
                    messageId,
                    result.referrals(),
                    result.resultCode(),
                    result.resultCode2());
        }
    }

    public static @NotNull ExtendedRequest request(int messageId) {
        return new ExtendedRequest(
                ByteBuffer.create(REQUEST_OPERATION_OID),
                BER.writeSequence(
                                BER.writeIntegerTag(messageId)),
                READER);
    }
}
