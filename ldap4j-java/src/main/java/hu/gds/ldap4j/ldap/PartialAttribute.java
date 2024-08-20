package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record PartialAttribute(@NotNull String type, @NotNull List<@NotNull String> values) {
    public PartialAttribute(@NotNull String type, @NotNull List<@NotNull String> values) {
        this.type=Objects.requireNonNull(type, "type");
        this.values=Objects.requireNonNull(values, "values");
    }

    public static @NotNull PartialAttribute readAttribute(ByteBuffer.Reader reader) throws Throwable {
        return BER.readSequence(
                (reader2)->{
                    String type=BER.readUtf8Tag(reader2);
                    return BER.readTag(
                            (reader3)->{
                                List<@NotNull String> values=new ArrayList<>();
                                while (reader3.hasRemainingBytes()) {
                                    values.add(BER.readUtf8Tag(reader3));
                                }
                                return new PartialAttribute(type, values);
                            },
                            reader2,
                            BER.SET);
                },
                reader);
    }

    public static @NotNull List<@NotNull PartialAttribute> readAttributes(
            ByteBuffer.Reader reader) throws Throwable {
        return BER.readSequence(
                (reader2)->{
                    List<@NotNull PartialAttribute> attributes=new ArrayList<>();
                    while (reader2.hasRemainingBytes()) {
                        PartialAttribute attribute=readAttribute(reader2);
                        attributes.add(attribute);
                    }
                    return attributes;
                },
                reader);
    }

    public @NotNull ByteBuffer write() {
        ByteBuffer valuesBuffer=ByteBuffer.empty();
        for (String value: values) {
            valuesBuffer=valuesBuffer.append(BER.writeUtf8Tag(value));
        }
        return BER.writeSequence(
                BER.writeUtf8Tag(type)
                        .append(BER.writeTag(
                                BER.SET,
                                valuesBuffer)));
    }
}
