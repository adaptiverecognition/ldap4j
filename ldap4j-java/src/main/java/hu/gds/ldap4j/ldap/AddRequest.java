package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record AddRequest(
        @NotNull List<@NotNull PartialAttribute> attributes,
        @NotNull String entry)
        implements Request<AddRequest, AddResponse> {
    public static final byte REQUEST_TAG=0x68;

    public AddRequest(@NotNull List<@NotNull PartialAttribute> attributes, @NotNull String entry) {
        this.attributes=Objects.requireNonNull(attributes, "attributes");
        this.entry=Objects.requireNonNull(entry, "entry");
    }

    @Override
    public @NotNull MessageReader<AddResponse> responseReader() {
        return AddResponse.READER;
    }

    @Override
    public @NotNull AddRequest self() {
        return this;
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.addRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        ByteBuffer attributesBuffer=ByteBuffer.empty();
        for (PartialAttribute attribute: attributes) {
            attributesBuffer=attributesBuffer.append(attribute.write());
        }
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeUtf8Tag(entry)
                        .append(BER.writeSequence(
                                attributesBuffer)));
    }
}
