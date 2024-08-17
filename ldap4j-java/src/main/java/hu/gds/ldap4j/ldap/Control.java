package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Control(
        @NotNull String controlType,
        byte@Nullable[] controlValue,
        boolean criticality) {
    public Control(
            @NotNull String controlType,
            byte@Nullable[] controlValue,
            boolean criticality) {
        this.controlType=Objects.requireNonNull(controlType, "controlType");
        this.controlValue=controlValue;
        this.criticality=criticality;
    }

    public static Control nonCritical(@NotNull String controlType) {
        return new Control(controlType, null, false);
    }

    public static @NotNull Control read(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return DER.readSequence(
                (reader2)->{
                    String controlType=DER.readUtf8Tag(reader2);
                    boolean criticality=DER.readOptionalTag(
                            DER::readBooleanNoTag,
                            reader2,
                            ()->false,
                            DER.BOOLEAN);
                    byte@Nullable[] controlValue=DER.readOptionalTag(
                            DER::readOctetStringNoTag,
                            reader2,
                            ()->null,
                            DER.OCTET_STRING);
                    return new Control(controlType, controlValue, criticality);
                },
                reader);
    }

    public @NotNull ByteBuffer write() {
        ByteBuffer buffer=DER.writeUtf8Tag(controlType);
        if (criticality || (null!=controlValue)) {
            buffer=buffer.append(DER.writeBooleanTag(criticality));
        }
        if (null!=controlValue) {
            buffer=buffer.append(DER.writeTag(DER.OCTET_STRING, ByteBuffer.create(controlValue)));
        }
        return DER.writeSequence(buffer);
    }
}
