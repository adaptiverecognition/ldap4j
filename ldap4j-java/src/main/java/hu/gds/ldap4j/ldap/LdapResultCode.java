package hu.gds.ldap4j.ldap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum LdapResultCode {
    SUCCESS(0, "success"),
    OPERATIONS_ERROR(1, "operationsError"),
    PROTOCOL_ERROR(2, "protocolError"),
    TIME_LIMIT_EXCEEDED(3, "timeLimitExceeded"),
    SIZE_LIMIT_EXCEEDED(4, "sizeLimitExceeded"),
    COMPARE_FALSE(5, "compareFalse"),
    COMPARE_TRUE(6, "compareTrue"),
    AUTH_METHOD_NOT_SUPPORTED(7, "authMethodNotSupported"),
    STRONGER_AUTH_REQUIRED(8, "strongerAuthRequired"),
    RESERVED_9(9, "reserved 9"),
    REFERRAL(10, "referral"),
    ADMIN_LIMIT_EXCEEDED(11, "adminLimitExceeded"),
    UNAVAILABLE_CRITICAL_EXTENSION(12, "unavailableCriticalExtension"),
    CONFIDENTIALITY_REQUIRED(13, "confidentialityRequired"),
    SASL_BIND_IN_PROGRESS(14, "saslBindInProgress"),
    NO_SUCH_ATTRIBUTE(16, "noSuchAttribute"),
    UNDEFINED_ATTRIBUTE_TYPE(17, "undefinedAttributeType"),
    INAPPROPRIATE_MATCHING(18, "inappropriateMatching"),
    CONSTRAINT_VIOLATION(19, "constraintViolation"),
    ATTRIBUTE_OR_VALUE_EXISTS(20, "attributeOrValueExists"),
    INVALID_ATTRIBUTE_SYNTAX(21, "invalidAttributeSyntax"),
    NO_SUCH_OBJECT(32, "noSuchObject"),
    ALIAS_PROBLEM(33, "aliasProblem"),
    INVALID_DN_SYNTAX(34, "invalidDNSyntax"),
    RESERVED_35(35, "reserved 35"),
    ALIAS_DEREFERENCING_PROBLEM(36, "aliasDereferencingProblem"),
    INAPPROPRIATE_AUTHENTICATION(48, "inappropriateAuthentication"),
    INVALID_CREDENTIALS(49, "invalidCredentials"),
    INSUFFICIENT_ACCESS_RIGHTS(50, "insufficientAccessRights"),
    BUSY(51, "busy"),
    UNAVAILABLE(52, "unavailable"),
    UNWILLING_TO_PERFORM(53, "unwillingToPerform"),
    LOOP_DETECT(54, "loopDetect"),
    NAMING_VIOLATION(64, "namingViolation"),
    OBJECT_CLASS_VIOLATION(65, "objectClassViolation"),
    NOT_ALLOWED_ON_NON_LEAF(66, "notAllowedOnNonLeaf"),
    NOT_ALLOWED_ON_RDN(67, "notAllowedOnRDN"),
    ENTRY_ALREADY_EXISTS(68, "entryAlreadyExists"),
    OBJECT_CLASS_MODS_PROHIBITED(69, "objectClassModsProhibited"),
    RESERVED_FOR_CLDAP(70, "reserved for CLDAP"),
    AFFECTS_MULTIPLE_DSAS(71, "affectsMultipleDSAs"),
    OTHER(80, "other");

    public static final Map<@NotNull Integer, @NotNull LdapResultCode> VALUES;

    static {
        Map<@NotNull Integer, @NotNull LdapResultCode> values=new HashMap<>();
        for (LdapResultCode ldapResultCode: values()) {
            values.put(ldapResultCode.code, ldapResultCode);
        }
        VALUES=Collections.unmodifiableMap(new HashMap<>(values));
    }

    public final int code;
    public final @NotNull String message;

    LdapResultCode(int code, @NotNull String message) {
        this.code=code;
        this.message=Objects.requireNonNull(message, "message");
    }

    public static @Nullable LdapResultCode ldapResultCode(int code) {
        return VALUES.get(code);
    }
}
