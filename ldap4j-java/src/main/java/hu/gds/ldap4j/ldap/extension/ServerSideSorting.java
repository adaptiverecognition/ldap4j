package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.NoSuchControlException;
import hu.gds.ldap4j.net.ByteBuffer;
import java.io.Serial;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 2891
 */
public class ServerSideSorting {
    public static class SortingException extends RuntimeException {
        @Serial
        private static final long serialVersionUID=0L;

        public final @Nullable String attributeType;
        public final int sortResult;
        public final @Nullable SortResultCode sortResultCode;

        public SortingException(
                @Nullable String attributeType,
                @NotNull String message,
                int sortResult,
                @Nullable SortResultCode sortResultCode) {
            super(
                    "%s, sort result: %,d, %s, attribute type: %s"
                            .formatted(message, sortResult, sortResultCode, attributeType));
            this.attributeType=attributeType;
            this.sortResult=sortResult;
            this.sortResultCode=sortResultCode;
        }
    }

    public record SortKey(
            @NotNull String attributeType,
            @Nullable String orderingRule,
            boolean reverseOrder) {
        public SortKey(@NotNull String attributeType, @Nullable String orderingRule, boolean reverseOrder) {
            this.attributeType=Objects.requireNonNull(attributeType, "attributeType");
            this.orderingRule=orderingRule;
            this.reverseOrder=reverseOrder;
        }

        public @NotNull ByteBuffer write() {
            @NotNull ByteBuffer buffer=BER.writeUtf8Tag(attributeType);
            if (null!=orderingRule) {
                buffer=buffer.append(
                        BER.writeTag(
                                SORT_KEY_ORDERING_RULE_TAG,
                                BER.writeUtf8NoTag(orderingRule)));
            }
            if (reverseOrder) {
                buffer=buffer.append(
                        BER.writeTag(
                                SORT_KEY_REVERSE_ORDER_TAG,
                                BER.writeBooleanNoTag(reverseOrder)));
            }
            return BER.writeSequence(buffer);
        }
    }

    public record SortResult(
            @Nullable String attributeType,
            int sortResult,
            @Nullable SortResultCode sortResultCode) {
        public void checkSuccess() {
            if (SortResultCode.SUCCESS.code!=sortResult) {
                throw new SortingException(attributeType, "sort not succeeded", sortResult, sortResultCode);
            }
        }

        public static @NotNull SortResult read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readSequence(
                    (reader2)->{
                        int sortResult=BER.readEnumeratedTag(reader2);
                        @Nullable String attributeType=BER.readOptionalTag(
                                BER::readUtf8NoTag,
                                reader2,
                                ()->null,
                                SORT_RESULT_ATTRIBUTE_TYPE_TAG);
                        return new SortResult(
                                attributeType,
                                sortResult,
                                SortResultCode.sortResultCode(sortResult));
                    },
                    reader);
        }
    }

    public enum SortResultCode {
        SUCCESS(0, "success"),
        OPERATION_ERROR(1, "operationsError"),
        TIME_LIMIT_EXCEEDED(3, "timeLimitExceeded"),
        STRONG_AUTH_REQUIRED(8, "strongAuthRequired"),
        ADMIN_LIMIT_EXCEEDED(11, "adminLimitExceeded"),
        NO_SUCH_ATTRIBUTE(16, "noSuchAttribute"),
        INAPPROPRIATE_MATCHING(18, "inappropriateMatching"),
        INSUFFICIENT_ACCESS_RIGHTS(50, "insufficientAccessRights"),
        BUSY(51, "busy"),
        UNWILLING_TO_PERFORM(53, "unwillingToPerform"),
        OTHER(80, "other");

        public static final Map<@NotNull Integer, @NotNull SortResultCode> VALUES;

        static {
            Map<@NotNull Integer, @NotNull SortResultCode> values=new HashMap<>();
            for (SortResultCode sortResultCode: values()) {
                values.put(sortResultCode.code, sortResultCode);
            }
            VALUES=Collections.unmodifiableMap(new HashMap<>(values));
        }

        public final int code;
        public final @NotNull String message;

        SortResultCode(int code, @NotNull String message) {
            this.code=code;
            this.message=Objects.requireNonNull(message, "message");
        }

        public static @Nullable SortResultCode sortResultCode(int code) {
            return VALUES.get(code);
        }
    }

    public static final @NotNull String REQUEST_CONTROL_OID="1.2.840.113556.1.4.473";
    public static final @NotNull String RESPONSE_CONTROL_OID="1.2.840.113556.1.4.474";
    public static final byte SORT_KEY_ORDERING_RULE_TAG=(byte)0x80;
    public static final byte SORT_KEY_REVERSE_ORDER_TAG=(byte)0x81;
    public static final byte SORT_RESULT_ATTRIBUTE_TYPE_TAG=(byte)0x80;

    private ServerSideSorting() {
    }

    public static @NotNull Control requestControl(boolean criticality, @NotNull List<@NotNull SortKey> sortKeyList) {
        @NotNull ByteBuffer buffer=ByteBuffer.EMPTY;
        for (@NotNull SortKey sortKey: sortKeyList) {
            buffer=buffer.append(sortKey.write());
        }
        return new Control(
                REQUEST_CONTROL_OID,
                BER.writeSequence(buffer)
                        .arrayCopy(),
                criticality);
    }

    public static @Nullable SortResult responseControl(@NotNull List<@NotNull Control> controls) throws Throwable {
        return Control.findOne(
                controls,
                (control)->{
                    if (!RESPONSE_CONTROL_OID.equals(control.controlType())) {
                        return null;
                    }
                    if (null==control.controlValue()) {
                        return null;
                    }
                    return ByteBuffer.create(control.controlValue())
                            .read(SortResult::read);
                }
        );
    }

    public static @NotNull SortResult responseControlCheckSuccess(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        @Nullable SortResult sortResult=responseControl(controls);
        if (null==sortResult) {
            throw new NoSuchControlException();
        }
        sortResult.checkSuccess();
        return sortResult;
    }
}
