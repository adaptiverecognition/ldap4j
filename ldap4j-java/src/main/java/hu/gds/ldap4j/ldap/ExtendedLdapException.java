package hu.gds.ldap4j.ldap;

import java.io.Serial;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class ExtendedLdapException extends LdapException {
    @Serial
    private static final long serialVersionUID=0L;
    
    public final @NotNull LdapMessage<ExtendedResponse> response;

    public ExtendedLdapException(@NotNull LdapMessage<ExtendedResponse> response) {
        super(
                response.toString(),
                response.message().ldapResult().referrals(),
                response.message().ldapResult().resultCode(),
                response.message().ldapResult().resultCode2());
        this.response=Objects.requireNonNull(response, "response");
    }
}
