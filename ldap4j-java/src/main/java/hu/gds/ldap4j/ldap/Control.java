package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Control(
        @NotNull ByteBuffer controlType,
        @Nullable ByteBuffer controlValue,
        boolean criticality) {
    public static final byte CONTROLS_TAG=(byte)0xa0;

    public Control(
            @NotNull ByteBuffer controlType,
            @Nullable ByteBuffer controlValue,
            boolean criticality) {
        this.controlType=Objects.requireNonNull(controlType, "controlType");
        this.controlValue=controlValue;
        this.criticality=criticality;
    }

    public static @NotNull Control create(
            @NotNull ByteBuffer controlType,
            @Nullable ByteBuffer controlValue,
            boolean criticality) {
        return new Control(controlType, controlValue, criticality);
    }

    public static @NotNull Control create(
            @NotNull String controlType,
            @Nullable ByteBuffer controlValue,
            boolean criticality) {
        return create(ByteBuffer.create(controlType), controlValue, criticality);
    }

    public static <T> @Nullable T findOne(
            @NotNull List<@NotNull Control> controls,
            @NotNull Function<@NotNull Control, T> function)
            throws Throwable {
        @Nullable T result=null;
        for (@NotNull Control control: controls) {
            @Nullable T value=function.apply(control);
            if (null!=value) {
                if (null!=result) {
                    throw new RuntimeException("more than one matching control");
                }
                result=value;
            }
        }
        return result;
    }

    public static @NotNull Control read(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return BER.readSequence(
                (reader2)->{
                    @NotNull ByteBuffer controlType=BER.readOctetStringTag(reader2);
                    boolean criticality=BER.readOptionalTag(
                            BER::readBooleanNoTag,
                            reader2,
                            ()->false,
                            BER.BOOLEAN);
                    @Nullable ByteBuffer controlValue=BER.readOptionalTag(
                            BER::readOctetStringNoTag,
                            reader2,
                            ()->null,
                            BER.OCTET_STRING);
                    return new Control(controlType, controlValue, criticality);
                },
                reader);
    }

    public static @NotNull List<@NotNull Control> readControls(@NotNull ByteBuffer.Reader reader) throws Throwable {
        List<@NotNull Control> controls=new ArrayList<>();
        if (reader.hasRemainingBytes()) {
            BER.readTag(
                    (reader3)->{
                        while (reader3.hasRemainingBytes()) {
                            controls.add(Control.read(reader3));
                        }
                        return null;
                    },
                    reader,
                    CONTROLS_TAG);
        }
        return controls;
    }

    public @NotNull ByteBuffer write() {
        ByteBuffer buffer=BER.writeOctetStringTag(controlType);
        if (criticality || (null!=controlValue)) {
            buffer=buffer.append(BER.writeBooleanTag(criticality));
        }
        if (null!=controlValue) {
            buffer=buffer.append(BER.writeOctetStringTag(controlValue));
        }
        return BER.writeSequence(buffer);
    }

    public static @NotNull ByteBuffer writeControls(@NotNull List<@NotNull Control> controls) {
        if (controls.isEmpty()) {
            return ByteBuffer.empty();
        }
        ByteBuffer buffer=ByteBuffer.empty();
        for (Control control: controls) {
            buffer=buffer.append(control.write());
        }
        return BER.writeTag(CONTROLS_TAG, buffer);
    }
}
