package hu.gds.ldap4j.ldap;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LdapException extends Exception {
    @Serial
    private static final long serialVersionUID=0L;

    public final @NotNull List<@NotNull Control> controls;
    public final int messageId;
    public final @NotNull List<@NotNull String> referrals;
    public final int resultCode;
    public final @Nullable LdapResultCode resultCode2;

    public LdapException(
            @NotNull List<@NotNull Control> controls,
            String message,
            int messageId,
            @NotNull List<@NotNull String> referrals,
            int resultCode,
            @Nullable LdapResultCode resultCode2) {
        super(
                "result code %d, %s, message: %s, controls: %s, messageId: %,d, referrals: %s".formatted(
                        resultCode, resultCode2, message, controls, messageId, referrals));
        this.controls=Objects.requireNonNull(controls, "controls");
        this.messageId=messageId;
        this.referrals=Objects.requireNonNull(referrals, "referrals");
        this.resultCode=resultCode;
        this.resultCode2=resultCode2;
    }
}
