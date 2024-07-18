package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record CompareRequest(@NotNull Filter.AttributeValueAssertion attributeValueAssertion, @NotNull String entry) {
    public CompareRequest(@NotNull Filter.AttributeValueAssertion attributeValueAssertion, @NotNull String entry) {
        this.attributeValueAssertion=Objects.requireNonNull(attributeValueAssertion, "attributeValueAssertion");
        this.entry=Objects.requireNonNull(entry, "entry");
    }

    public @NotNull ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_COMPARE_REQUEST,
                DER.writeUtf8Tag(entry)
                        .append(attributeValueAssertion.write()));
    }
}
