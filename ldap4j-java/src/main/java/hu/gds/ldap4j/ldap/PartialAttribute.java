package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record PartialAttribute(
        @NotNull ByteBuffer type,
        @NotNull List<@NotNull ByteBuffer> values) {
    public PartialAttribute(
            @NotNull ByteBuffer type,
            @NotNull List<@NotNull ByteBuffer> values) {
        this.type=Objects.requireNonNull(type, "type");
        this.values=Objects.requireNonNull(values, "values");
    }

    public PartialAttribute(
            @NotNull String type,
            @NotNull List<@NotNull String> values) {
        this(
                ByteBuffer.create(type),
                values.stream()
                        .map(ByteBuffer::create)
                        .toList());
    }

    public static @NotNull PartialAttribute readAttribute(ByteBuffer.Reader reader) throws Throwable {
        return BER.readSequence(
                (reader2)->{
                    @NotNull ByteBuffer type=BER.readOctetStringTag(reader2);
                    return BER.readTag(
                            (reader3)->{
                                @NotNull List<@NotNull ByteBuffer> values=new ArrayList<>();
                                while (reader3.hasRemainingBytes()) {
                                    values.add(BER.readOctetStringTag(reader3));
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

    public @NotNull List<@NotNull String> valuesUtf8() {
        return values()
                .stream()
                .map(ByteBuffer::utf8)
                .toList();
    }

    public @NotNull ByteBuffer write() {
        @NotNull ByteBuffer valuesBuffer=ByteBuffer.empty();
        for (ByteBuffer value: values) {
            valuesBuffer=valuesBuffer.append(BER.writeOctetStringTag(value));
        }
        return BER.writeSequence(
                BER.writeOctetStringTag(type)
                        .append(BER.writeTag(
                                BER.SET,
                                valuesBuffer)));
    }
}
