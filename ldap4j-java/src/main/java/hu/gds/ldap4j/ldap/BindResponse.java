package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record BindResponse(
        @NotNull LdapResult ldapResult,
        byte[] serverSaslCredentials) {
    public BindResponse(@NotNull LdapResult ldapResult, byte[] serverSaslCredentials) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
        this.serverSaslCredentials=serverSaslCredentials;
    }

    public static @NotNull BindResponse read(ByteBuffer.Reader reader) throws Throwable {
        return DER.readTag(
                (reader2)->{
                    LdapResult ldapResult=LdapResult.read(reader2);
                    byte[] serverSaslCredentials=null;
                    if (reader2.hasRemainingBytes()) {
                        serverSaslCredentials=DER.readTag(
                                (reader3)->reader3.readReaminingByteBuffer()
                                        .arrayCopy(),
                                reader2,
                                Ldap.BIND_RESPONSE_CREDENTIALS);
                    }
                    reader2.assertNoRemainingBytes();
                    return new BindResponse(ldapResult, serverSaslCredentials);
                },
                reader,
                Ldap.PROTOCOL_OP_BIND_RESPONSE);
    }
}
