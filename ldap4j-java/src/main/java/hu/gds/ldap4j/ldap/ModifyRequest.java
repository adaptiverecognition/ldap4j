package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ModifyRequest(
        @NotNull List<@NotNull Change> changes,
        @NotNull String object)
        implements Request<ModifyRequest, ModifyResponse> {
    public record Change(@NotNull PartialAttribute modification, @NotNull Operation operation) {
        public Change(@NotNull PartialAttribute modification, @NotNull Operation operation) {
            this.modification=Objects.requireNonNull(modification, "modification");
            this.operation=Objects.requireNonNull(operation, "operation");
        }

        public @NotNull ByteBuffer write() {
            return DER.writeSequence(
                    operation.write()
                            .append(modification.write()));
        }
    }

    public enum Operation {
        ADD(0),
        DELETE(1),
        REPLACE(2);

        public final int value;

        Operation(int value) {
            this.value=value;
        }

        public @NotNull ByteBuffer write() {
            return DER.writeEnumeratedTag(value);
        }
    }

    public ModifyRequest(@NotNull List<@NotNull Change> changes, @NotNull String object) {
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
        ByteBuffer changesBuffer=ByteBuffer.EMPTY;
        for (Change change: changes) {
            changesBuffer=changesBuffer.append(change.write());
        }
        return DER.writeTag(
                Ldap.PROTOCOL_OP_MODIFY_REQUEST,
                DER.writeUtf8Tag(object)
                        .append(DER.writeSequence(changesBuffer)));
    }
}
