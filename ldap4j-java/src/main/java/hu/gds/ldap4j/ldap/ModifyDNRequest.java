package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ModifyDNRequest(
        boolean deleteOldRDN,
        @NotNull String entry,
        @NotNull String newRDN,
        @Nullable String newSuperior)
        implements Request<ModifyDNRequest, ModifyDNResponse> {
    public ModifyDNRequest(
            boolean deleteOldRDN,
            @NotNull String entry,
            @NotNull String newRDN,
            @Nullable String newSuperior) {
        this.deleteOldRDN=deleteOldRDN;
        this.entry=Objects.requireNonNull(entry, "entry");
        this.newRDN=Objects.requireNonNull(newRDN, "newRDN");
        this.newSuperior=newSuperior;
    }

    @Override
    public @NotNull MessageReader<ModifyDNResponse> responseReader() {
        return ModifyDNResponse.READER;
    }

    @Override
    public @NotNull ModifyDNRequest self() {
        return this;
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.modifyDNRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        ByteBuffer requestBuffer=BER.writeUtf8Tag(entry)
                .append(BER.writeUtf8Tag(newRDN))
                .append(BER.writeBooleanTag(deleteOldRDN));
        if (null!=newSuperior) {
            requestBuffer=requestBuffer.append(
                    BER.writeTag(
                            Ldap.MODIFY_DN_REQUEST_NEW_SUPERIOR,
                            BER.writeUtf8NoTag(newSuperior)));
        }
        return BER.writeTag(
                Ldap.PROTOCOL_OP_MODIFY_DN_REQUEST,
                requestBuffer);
    }
}
