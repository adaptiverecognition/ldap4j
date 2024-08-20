package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record LdapResult(
        @NotNull String diagnosticMessages,
        @NotNull String matchedDn,
        @NotNull List<@NotNull String> referrals,
        int resultCode,
        @Nullable LdapResultCode resultCode2) {
    public static final byte REFERRALS_TAG=(byte)0xa3;

    public LdapResult(
            @NotNull String diagnosticMessages, @NotNull String matchedDn,
            @NotNull List<@NotNull String> referrals, int resultCode, @Nullable LdapResultCode resultCode2) {
        this.diagnosticMessages=Objects.requireNonNull(diagnosticMessages, "resultMessage");
        this.matchedDn=Objects.requireNonNull(matchedDn, "matchedDn");
        this.referrals=Objects.requireNonNull(referrals, "referrals");
        this.resultCode=resultCode;
        this.resultCode2=resultCode2;
    }

    public void checkCompare(@NotNull List<@NotNull Control> controls, int messageId) throws LdapException {
        if ((LdapResultCode.COMPARE_FALSE.code!=resultCode)
                && (LdapResultCode.COMPARE_TRUE.code!=resultCode)) {
            throw new LdapException(controls, diagnosticMessages, messageId, referrals, resultCode, resultCode2);
        }
    }

    public void checkSASL(@NotNull List<@NotNull Control> controls, int messageId) throws LdapException {
        if ((LdapResultCode.SASL_BIND_IN_PROGRESS.code!=resultCode)
                && (LdapResultCode.SUCCESS.code!=resultCode)) {
            throw new LdapException(controls, diagnosticMessages, messageId, referrals, resultCode, resultCode2);
        }
    }

    public void checkSuccess(@NotNull List<@NotNull Control> controls, int messageId) throws LdapException {
        if (LdapResultCode.SUCCESS.code!=resultCode) {
            throw new LdapException(controls, diagnosticMessages, messageId, referrals, resultCode, resultCode2);
        }
    }

    public static @NotNull LdapResult read(@NotNull ByteBuffer.Reader reader) throws Throwable {
        int resultCode=BER.readEnumeratedTag(reader);
        String matchedDn=BER.readUtf8Tag(reader);
        String diagnosticMessage=BER.readUtf8Tag(reader);
        List<@NotNull String> referrals=new ArrayList<>();
        BER.readOptionalTag(
                (reader2)->{
                    while (reader2.hasRemainingBytes()) {
                        referrals.add(BER.readUtf8Tag(reader2));
                    }
                    return null;
                },
                reader,
                ()->null,
                REFERRALS_TAG);
        return new LdapResult(
                diagnosticMessage,
                matchedDn,
                referrals,
                resultCode,
                LdapResultCode.ldapResultCode(resultCode));
    }

    public @NotNull ByteBuffer write() {
        @NotNull ByteBuffer resultBuffer=BER.writeEnumeratedTag(resultCode)
                .append(BER.writeUtf8Tag(matchedDn))
                .append(BER.writeUtf8Tag(diagnosticMessages));
        if (!referrals.isEmpty()) {
            @NotNull ByteBuffer referralsBuffer=ByteBuffer.empty();
            for (@NotNull String referral: referrals) {
                referralsBuffer=referralsBuffer.append(BER.writeUtf8Tag(referral));
            }
            resultBuffer=resultBuffer.append(BER.writeTag(
                    REFERRALS_TAG,
                    referralsBuffer));
        }
        return resultBuffer;
    }
}
