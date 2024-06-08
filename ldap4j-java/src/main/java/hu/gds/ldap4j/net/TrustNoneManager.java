package hu.gds.ldap4j.net;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

public class TrustNoneManager extends X509ExtendedTrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        fail();
    }

    @Override
    public void checkClientTrusted(
            X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        fail();
    }

    @Override
    public void checkClientTrusted(
            X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        fail();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        fail();
    }

    @Override
    public void checkServerTrusted(
            X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        fail();
    }

    @Override
    public void checkServerTrusted(
            X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        fail();
    }

    private void fail() throws CertificateException {
        throw new CertificateException("no one is trusted");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
