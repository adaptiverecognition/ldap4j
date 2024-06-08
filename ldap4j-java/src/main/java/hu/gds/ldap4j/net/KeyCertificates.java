package hu.gds.ldap4j.net;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record KeyCertificates(
        @NotNull List<@NotNull X509Certificate> certificates,
        @NotNull PrivateKey privateKey) {
    public KeyCertificates(@NotNull List<@NotNull X509Certificate> certificates, @NotNull PrivateKey privateKey) {
        this.certificates=Objects.requireNonNull(certificates, "certificates");
        this.privateKey=Objects.requireNonNull(privateKey, "privateKey");
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("empty certificates");
        }
    }

    @Override
    public String toString() {
        return "KeyCertificate(certificates: "+certificates+", privateKey: "+privateKey+")";
    }
}
