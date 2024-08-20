package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.ExtendedResponse;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 5805
 */
public class Transactions {
    public record EndResponse(
            @Nullable Integer messageId,
            @NotNull List<@NotNull UpdateControls> updatesControls) {
        public EndResponse(
                @Nullable Integer messageId,
                @NotNull List<@NotNull UpdateControls> updatesControls) {
            this.messageId=messageId;
            this.updatesControls=Objects.requireNonNull(updatesControls, "updatesControls");
        }

        public static @NotNull EndResponse read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readSequence(
                    (reader2)->{
                        @Nullable Integer messageId=BER.readOptionalTag(
                                (reader3)->BER.readIntegerNoTag(true, reader3),
                                reader2,
                                ()->null,
                                BER.INTEGER);
                        @NotNull List<@NotNull UpdateControls> updatesControls=BER.readOptionalTag(
                                (reader3)->{
                                    @NotNull List<@NotNull UpdateControls> updatesControls2=new ArrayList<>();
                                    while (reader3.hasRemainingBytes()) {
                                        updatesControls2.add(UpdateControls.read(reader3));
                                    }
                                    return updatesControls2;
                                },
                                reader2,
                                List::of,
                                BER.SEQUENCE);
                        return new EndResponse(messageId, updatesControls);
                    },
                    reader);
        }
    }

    public record UpdateControls(
            @NotNull List<@NotNull Control> controls,
            int messageId) {
        public UpdateControls(@NotNull List<@NotNull Control> controls, int messageId) {
            this.controls=Objects.requireNonNull(controls, "controls");
            this.messageId=messageId;
        }

        public static @NotNull UpdateControls read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readSequence(
                    (reader2)->{
                        int messageId=BER.readIntegerTag(true, reader2);
                        @NotNull List<@NotNull Control> controls=Control.readControls(reader2);
                        return new UpdateControls(controls, messageId);
                    },
                    reader);
        }
    }

    public static final @NotNull String END_TRANSACTION_OID="1.3.6.1.1.21.3";
    public static final @NotNull String START_TRANSACTION_OID="1.3.6.1.1.21.1";
    public static final @NotNull String TRANSACTION_SPECIFICATIONS_CONTROL_OID="1.3.6.1.1.21.2";

    private Transactions() {
    }

    public static @NotNull ExtendedRequest endTransactionRequest(
            boolean commit, @NotNull ByteBuffer transactionId) {
        return new ExtendedRequest(
                ByteBuffer.create(END_TRANSACTION_OID),
                BER.writeSequence(
                                BER.writeBooleanTag(commit)
                                        .append(BER.writeTag(BER.OCTET_STRING, transactionId))),
                ExtendedResponse.READER_SUCCESS);
    }

    public static @NotNull EndResponse endTransactionResponseCheck(
            @NotNull ControlsMessage<ExtendedResponse> response) throws Throwable {
        if (null==response.message().responseValue()) {
            throw new RuntimeException("no end transaction response");
        }
        return response.message().responseValue()
                .read(EndResponse::read);
    }

    public static @NotNull ExtendedRequest startTransactionRequest() {
        return new ExtendedRequest(
                ByteBuffer.create(START_TRANSACTION_OID),
                null,
                ExtendedResponse.READER_SUCCESS);
    }

    public static @NotNull ByteBuffer startTransactionResponseTransactionId(
            @NotNull ControlsMessage<ExtendedResponse> response) {
        if (null==response.message().responseValue()) {
            throw new RuntimeException("missing transaction id");
        }
        return response.message().responseValue();
    }

    public static @NotNull Control transactionSpecificationControl(@NotNull ByteBuffer transactionId) {
        return Control.create(TRANSACTION_SPECIFICATIONS_CONTROL_OID, transactionId, true);
    }
}
