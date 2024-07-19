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
    public LdapResult(
            @NotNull String diagnosticMessages, @NotNull String matchedDn,
            @NotNull List<@NotNull String> referrals, int resultCode, @Nullable LdapResultCode resultCode2) {
        this.diagnosticMessages=Objects.requireNonNull(diagnosticMessages, "resultMessage");
        this.matchedDn=Objects.requireNonNull(matchedDn, "matchedDn");
        this.referrals=Objects.requireNonNull(referrals, "referrals");
        this.resultCode=resultCode;
        this.resultCode2=resultCode2;
    }

    public void check() throws LdapException {
        if (!LdapResultCode.SUCCESS.equals(resultCode2)) {
            throw new LdapException(diagnosticMessages, referrals, resultCode, resultCode2);
        }
    }

    public void checkCancel() throws LdapException {
        if ((!LdapResultCode.CANCELED.equals(resultCode2))
                && (!LdapResultCode.CANNOT_CANCEL.equals(resultCode2))
                && (!LdapResultCode.NO_SUCH_OPERATION.equals(resultCode2))
                && (!LdapResultCode.TOO_LATE.equals(resultCode2))) {
            throw new LdapException(diagnosticMessages, referrals, resultCode, resultCode2);
        }
    }

    public void checkCompare() throws LdapException {
        if ((!LdapResultCode.COMPARE_FALSE.equals(resultCode2))
                && (!LdapResultCode.COMPARE_TRUE.equals(resultCode2))) {
            throw new LdapException(diagnosticMessages, referrals, resultCode, resultCode2);
        }
    }

    public void checkSASL() throws LdapException {
        if ((!LdapResultCode.SASL_BIND_IN_PROGRESS.equals(resultCode2))
                && (!LdapResultCode.SUCCESS.equals(resultCode2))) {
            throw new LdapException(diagnosticMessages, referrals, resultCode, resultCode2);
        }
    }

    public static @NotNull LdapResult read(@NotNull ByteBuffer.Reader reader) throws Throwable {
        int resultCode=DER.readEnumeratedTag(reader);
        String matchedDn=DER.readUtf8Tag(reader);
        String diagnosticMessage=DER.readUtf8Tag(reader);
        List<@NotNull String> referrals=new ArrayList<>();
        if (reader.hasRemainingBytes() && (Ldap.LDAP_RESULT_REFERRALS==reader.peekByte())) {
            DER.readTag(
                    (reader2)->{
                        while (reader2.hasRemainingBytes()) {
                            referrals.add(DER.readUtf8Tag(reader2));
                        }
                        return null;
                    },
                    reader,
                    Ldap.LDAP_RESULT_REFERRALS);
        }
        return new LdapResult(
                diagnosticMessage,
                matchedDn,
                referrals,
                resultCode,
                LdapResultCode.ldapResultCode(resultCode));
    }
}
