package hu.gds.ldap4j.ldap;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

public class LdapException extends Exception {
    @Serial
    private static final long serialVersionUID=0L;

    public final @Nullable List<@NotNull String> referrals;
    public final int resultCode;
    public final @Nullable LdapResultCode resultCode2;

    public LdapException(
            String message, @Nullable List<@NotNull String> referrals,
            int resultCode, @Nullable LdapResultCode resultCode2) {
        super("result code %d, %s, message: %s, referrals: %s".formatted(resultCode, resultCode2, message, referrals));
        this.referrals=referrals;
        this.resultCode=resultCode;
        this.resultCode2=resultCode2;
    }
}
