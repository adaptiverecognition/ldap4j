package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record CompareRequest(
        @NotNull Filter.AttributeValueAssertion attributeValueAssertion,
        @NotNull ByteBuffer entry)
        implements Request<CompareRequest, CompareResponse> {
    public static final byte REQUEST_TAG=0x6e;

    public CompareRequest(@NotNull Filter.AttributeValueAssertion attributeValueAssertion, @NotNull ByteBuffer entry) {
        this.attributeValueAssertion=Objects.requireNonNull(attributeValueAssertion, "attributeValueAssertion");
        this.entry=Objects.requireNonNull(entry, "entry");
    }

    @Override
    public @NotNull MessageReader<CompareResponse> responseReader() {
        return CompareResponse.READER;
    }

    @Override
    public @NotNull CompareRequest self() {
        return this;
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.compareRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() throws Throwable {
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeOctetStringTag(entry)
                        .append(attributeValueAssertion.write()));
    }
}
