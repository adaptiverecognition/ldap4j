package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record DeleteRequest(
        @NotNull String entry)
        implements Request<DeleteRequest, DeleteResponse> {
    public static final byte REQUEST_TAG=0x4a;

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
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.deleteRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeUtf8NoTag(entry));
    }
}
