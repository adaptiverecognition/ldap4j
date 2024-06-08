package hu.gds.ldap4j.net;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.X509ExtendedTrustManager;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.jetbrains.annotations.NotNull;

public class VerifyHostnameClientTrustManager extends X509ExtendedTrustManager {
    private final @NotNull String hostname;
    private final DefaultHostnameVerifier hostnameVerifier=new DefaultHostnameVerifier();
    private final @NotNull X509ExtendedTrustManager trustManager;

    public VerifyHostnameClientTrustManager(@NotNull String hostname, @NotNull X509ExtendedTrustManager trustManager) {
        this.hostname=Objects.requireNonNull(hostname, "hostname");
        this.trustManager=Objects.requireNonNull(trustManager, "trustManager");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        trustManager.checkServerTrusted(chain, authType);
        try {
            hostnameVerifier.verify(hostname, chain[0]);
        }
        catch (SSLException ex) {
            throw new CertificateException(ex);
        }
    }

    @Override
    public void checkServerTrusted(
            X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        trustManager.checkServerTrusted(chain, authType, socket);
        try {
            hostnameVerifier.verify(hostname, chain[0]);
        }
        catch (SSLException ex) {
            throw new CertificateException(ex);
        }
    }

    @Override
    public void checkServerTrusted(
            X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        trustManager.checkServerTrusted(chain, authType, engine);
        try {
            hostnameVerifier.verify(hostname, chain[0]);
        }
        catch (SSLException ex) {
            throw new CertificateException(ex);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager.getAcceptedIssuers();
    }
}
