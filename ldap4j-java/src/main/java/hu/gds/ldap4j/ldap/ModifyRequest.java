package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyRequest(
        @NotNull List<@NotNull Change> changes,
        @NotNull ByteBuffer object)
        implements Request<ModifyRequest, ModifyResponse> {
    public record Change(
            @NotNull PartialAttribute modification,
            int operation) {
        public Change(
                @NotNull PartialAttribute modification,
                int operation) {
            this.modification=Objects.requireNonNull(modification, "modification");
            this.operation=operation;
        }

        public @NotNull ByteBuffer write() {
            return BER.writeSequence(
                    BER.writeEnumeratedTag(operation)
                            .append(modification.write()));
        }
    }

    public static final int OPERATION_ADD=0;
    public static final int OPERATION_DELETE=1;
    public static final int OPERATION_REPLACE=2;
    public static final byte REQUEST_TAG=0x66;

    public ModifyRequest(
            @NotNull List<@NotNull Change> changes,
            @NotNull ByteBuffer object) {
        this.changes=Objects.requireNonNull(changes, "changes");
        this.object=Objects.requireNonNull(object, "object");
    }

    @Override
    public @NotNull MessageReader<ModifyResponse> responseReader() {
        return ModifyResponse.READER;
    }

    @Override
    public @NotNull ModifyRequest self() {
        return this;
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.modifyRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        ByteBuffer changesBuffer=ByteBuffer.empty();
        for (Change change: changes) {
            changesBuffer=changesBuffer.append(change.write());
        }
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeOctetStringTag(object)
                        .append(BER.writeSequence(changesBuffer)));
    }
}
