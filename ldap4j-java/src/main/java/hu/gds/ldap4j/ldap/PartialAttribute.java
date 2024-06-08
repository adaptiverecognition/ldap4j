package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record PartialAttribute(@NotNull String type, @NotNull Set<@NotNull String> values) {
    public PartialAttribute(@NotNull String type, @NotNull Set<@NotNull String> values) {
        this.type=Objects.requireNonNull(type, "type");
        this.values=Objects.requireNonNull(values, "values");
    }

    public static @NotNull PartialAttribute readAttribute(ByteBuffer.Reader reader) throws Throwable {
        return DER.readSequence(
                (reader2)->{
                    String type=DER.readUtf8Tag(reader2);
                    return DER.readTag(
                            (reader3)->{
                                Set<@NotNull String> values=new LinkedHashSet<>();
                                while (reader3.hasRemainingBytes()) {
                                    values.add(DER.readUtf8Tag(reader3));
                                }
                                return new PartialAttribute(type, values);
                            },
                            reader2,
                            DER.SET);
                },
                reader);
    }

    public static @NotNull Map<@NotNull String, @NotNull PartialAttribute> readAttributes(
            ByteBuffer.Reader reader) throws Throwable {
        return DER.readSequence(
                (reader2)->{
                    Map<@NotNull String, @NotNull PartialAttribute> attributes=new LinkedHashMap<>();
                    while (reader2.hasRemainingBytes()) {
                        PartialAttribute attribute=readAttribute(reader2);
                        attributes.put(attribute.type, attribute);
                    }
                    return attributes;
                },
                reader);
    }
}
