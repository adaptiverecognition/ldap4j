package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record DeleteRequest(
        @NotNull String entry)
        implements Request<DeleteRequest, DeleteResponse> {
    public DeleteRequest(@NotNull String entry) {
        this.entry=Objects.requireNonNull(entry, "entry");
    }

    @Override
    public @NotNull MessageReader<DeleteResponse> responseReader() {
        return DeleteResponse.READER;
    }

    @Override
    public @NotNull DeleteRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_DELETE_REQUEST,
                DER.writeUtf8NoTag(entry));
    }
}
