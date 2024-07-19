package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ModifyDNRequest(
        boolean deleteOldRDN,
        @NotNull String entry,
        @NotNull String newRDN,
        @Nullable String newSuperior) {
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

    public @NotNull ByteBuffer write() throws Throwable {
        ByteBuffer requestBuffer=DER.writeUtf8Tag(entry)
                .append(DER.writeUtf8Tag(newRDN))
                .append(DER.writeBooleanTag(deleteOldRDN));
        if (null!=newSuperior) {
            requestBuffer=requestBuffer.append(
                    DER.writeTag(
                            Ldap.MODIFY_DN_REQUEST_NEW_SUPERIOR,
                            DER.writeUtf8NoTag(newSuperior)));
        }
        return DER.writeTag(
                Ldap.PROTOCOL_OP_MODIFY_DN_REQUEST,
                requestBuffer);
    }
}
