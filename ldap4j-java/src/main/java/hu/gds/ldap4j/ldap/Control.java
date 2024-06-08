package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record Control(@NotNull String controlType, @Nullable String controlValue, boolean criticality) {
    public Control(@NotNull String controlType, @Nullable String controlValue, boolean criticality) {
        this.controlType=Objects.requireNonNull(controlType, "controlType");
        this.controlValue=controlValue;
        this.criticality=criticality;
    }

    public static Control nonCritical(@NotNull String controlType) {
        return new Control(controlType, null, false);
    }

    public static @NotNull Control read(ByteBuffer.Reader reader) throws Throwable {
        return DER.readSequence(
                (reader2)->{
                    String controlType=DER.readUtf8Tag(reader2);
                    boolean criticality=false;
                    String controlValue=null;
                    if (reader2.hasRemainingBytes()) {
                        Either<Boolean, String> either=DER.readTags(Map.of(
                                        DER.BOOLEAN, (reader3)->Either.left(DER.readBooleanNoTag(reader3)),
                                        DER.OCTET_STRING, (reader3)->Either.right(DER.readUtf8NoTag(reader3))),
                                reader2);
                        if (either.isLeft()) {
                            criticality=either.left();
                        }
                        else {
                            controlValue=either.right();
                        }
                    }
                    if ((null==controlValue) && reader2.hasRemainingBytes()) {
                        controlValue=DER.readUtf8Tag(reader2);
                    }
                    return new Control(controlType, controlValue, criticality);
                },
                reader);
    }

    public @NotNull ByteBuffer write() throws Throwable {
        if (null!=controlValue) {
            return DER.writeSequence(
                    DER.writeUtf8Tag(controlType)
                            .append(DER.writeBooleanTag(criticality))
                            .append(DER.writeUtf8Tag(controlValue)));
        }
        if (criticality) {
            return DER.writeSequence(
                    DER.writeUtf8Tag(controlType)
                            .append(DER.writeBooleanTag(true)));
        }
        return DER.writeSequence(DER.writeUtf8Tag(controlType));
    }
}
