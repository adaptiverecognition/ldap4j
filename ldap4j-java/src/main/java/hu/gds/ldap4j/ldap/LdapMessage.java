package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record LdapMessage<T>(
        @NotNull List<@NotNull Control> controls,
        @NotNull T message,
        int messageId) {
    public LdapMessage(
            @NotNull List<@NotNull Control> controls, @NotNull T message, int messageId) {
        this.controls=Objects.requireNonNull(controls, "controls");
        this.message=Objects.requireNonNull(message, "message");
        this.messageId=messageId;
    }

    public static @NotNull List<@NotNull Control> controls(@NotNull ByteBuffer.Reader reader) throws Throwable {
        List<@NotNull Control> controls=new ArrayList<>();
        if (reader.hasRemainingBytes()) {
            DER.readTag(
                    (reader3)->{
                        while (reader3.hasRemainingBytes()) {
                            controls.add(Control.read(reader3));
                        }
                        return null;
                    },
                    reader,
                    Ldap.MESSAGE_CONTROLS);
        }
        return controls;
    }

    public static <T> @NotNull Function<ByteBuffer.Reader, @NotNull Lava<T>> readCheckedParallel(
            @NotNull Function<@NotNull Integer, @Nullable ParallelMessageReader<?, T>> messageReadersByMessageId) {
        Objects.requireNonNull(messageReadersByMessageId, "messageReadersByMessageId");
        return (reader)->DER.readSequence(
                (reader2)->{
                    int messageId=DER.readIntegerTag(true, reader2);
                    ParallelMessageReader<?, T> messageReader=messageReadersByMessageId.apply(messageId);
                    if (null!=messageReader) {
                        return messageReader.readMessageChecked(messageId, reader2);
                    }
                    else if (0==messageId) {
                        ExtendedResponse response=ExtendedResponse.READER_SUCCESS.read(reader2);
                        @NotNull List<@NotNull Control> controls=controls(reader2);
                        throw new ExtendedLdapException(new LdapMessage<>(controls, response, messageId));
                    }
                    else {
                        throw new UnexpectedMessageIdException("expected message id %,d".formatted(messageId));
                    }
                },
                reader);
    }

    public @NotNull ByteBuffer write(@NotNull Function<@NotNull T, ByteBuffer> function) throws Throwable {
        ByteBuffer controls2;
        if (controls.isEmpty()) {
            controls2=ByteBuffer.EMPTY;
        }
        else {
            ByteBuffer controls3=ByteBuffer.EMPTY;
            for (Control control: controls) {
                controls3=controls3.append(control.write());
            }
            controls2=DER.writeTag(Ldap.MESSAGE_CONTROLS, controls3);
        }
        return DER.writeSequence(
                DER.writeIntegerTag(messageId)
                        .append(function.apply(message))
                        .append(controls2));
    }
}
