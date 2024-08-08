package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Supplier;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface TlsSettings {
    class Builder {
        private boolean client;
        private @NotNull ClientAuthentication clientAuthentication;
        private @Nullable KeyCertificates privateKey;
        private @NotNull Supplier<@Nullable SecureRandom> randomFactory;
        private boolean startTls;
        private @Nullable List<@NotNull X509Certificate> trustedCertificates;
        private boolean verifyHostname;

        public Builder(
                boolean client, @NotNull ClientAuthentication clientAuthentication,
                @Nullable KeyCertificates privateKey, @NotNull Supplier<@Nullable SecureRandom> randomFactory,
                boolean startTls, @Nullable List<@NotNull X509Certificate> trustedCertificates,
                boolean verifyHostname) {
            this.client=client;
            this.clientAuthentication=Objects.requireNonNull(clientAuthentication, "clientAuthentication");
            this.privateKey=privateKey;
            this.randomFactory=Objects.requireNonNull(randomFactory, "randomFactory");
            this.startTls=startTls;
            this.trustedCertificates=trustedCertificates;
            this.verifyHostname=verifyHostname;
        }

        public Tls build() {
            return new Tls(
                    client, clientAuthentication, privateKey, randomFactory,
                    startTls, trustedCertificates, verifyHostname);
        }

        public Builder client(boolean client) {
            this.client=client;
            return this;
        }

        public Builder clientAuthenticationNone() {
            clientAuthentication=ClientAuthentication.NONE;
            return this;
        }

        public Builder clientAuthenticationOptional() {
            clientAuthentication=ClientAuthentication.OPTIONAL;
            return this;
        }

        public Builder clientAuthenticationRequire() {
            clientAuthentication=ClientAuthentication.REQUIRED;
            return this;
        }

        public Builder copy() {
            return new Builder(
                    client, clientAuthentication, privateKey, randomFactory,
                    startTls, trustedCertificates, verifyHostname);
        }

        public Builder noPrivateKey() {
            this.privateKey=null;
            return this;
        }

        public Builder privateKey(@Nullable KeyCertificates keyCertificates) {
            this.privateKey=keyCertificates;
            return this;
        }

        public Builder randomFactory(@NotNull Supplier<@Nullable SecureRandom> randomFactory) {
            this.randomFactory=Objects.requireNonNull(randomFactory, "randomFactory");
            return this;
        }

        public Builder startTls(boolean startTls) {
            this.startTls=startTls;
            return this;
        }

        public Builder trustEverything() {
            this.trustedCertificates=null;
            return this;
        }

        public Builder trustCertificates(@Nullable List<@NotNull X509Certificate> trustedCertificates) {
            this.trustedCertificates=trustedCertificates;
            return this;
        }

        public Builder verifyHostname(boolean verifyHostname) {
            this.verifyHostname=verifyHostname;
            return this;
        }
    }

    enum ClientAuthentication {
        NONE, OPTIONAL, REQUIRED;

        public void set(SSLEngine engine) {
            switch (this) {
                case NONE -> {
                    engine.setNeedClientAuth(false);
                    engine.setWantClientAuth(false);
                }
                case OPTIONAL -> {
                    engine.setNeedClientAuth(true);
                    engine.setWantClientAuth(false);
                }
                case REQUIRED -> {
                    engine.setNeedClientAuth(true);
                    engine.setWantClientAuth(true);
                }
            }
        }
    }

    record NoTls() implements TlsSettings {
        @Override
        public String toString() {
            return "no tls";
        }
    }

    record Tls(
            boolean client,
            @NotNull ClientAuthentication clientAuthentication,
            @Nullable KeyCertificates privateKey,
            @NotNull Supplier<@Nullable SecureRandom> randomFactory,
            boolean startTls,
            @Nullable List<@NotNull X509Certificate> trustedCertificates,
            boolean verifyHostname)
            implements TlsSettings {
        public Tls(
                boolean client, @NotNull ClientAuthentication clientAuthentication,
                @Nullable KeyCertificates privateKey, @NotNull Supplier<@Nullable SecureRandom> randomFactory,
                boolean startTls, @Nullable List<@NotNull X509Certificate> trustedCertificates,
                boolean verifyHostname) {
            this.client=client;
            this.clientAuthentication=Objects.requireNonNull(clientAuthentication, "clientAuth");
            this.privateKey=privateKey;
            this.randomFactory=Objects.requireNonNull(randomFactory, "randomFactory");
            this.startTls=startTls;
            this.trustedCertificates=(null==trustedCertificates)?null:new ArrayList<>(trustedCertificates);
            this.verifyHostname=verifyHostname;
        }

        @Override
        public @NotNull Tls asTls() {
            return this;
        }

        @Override
        public @NotNull SSLContext createSSLContext(@Nullable InetSocketAddress peerAddress) throws Throwable {
            SSLContext sslContext=SSLContext.getInstance("TLS");
            KeyManager[] keyManagers=keyManagers();
            TrustManager[] trustManagers=trustManagers();
            X509ExtendedTrustManager extendedTrustManager=null;
            if (null!=trustManagers) {
                for (TrustManager trustManager: trustManagers) {
                    if (trustManager instanceof X509ExtendedTrustManager extendedTrustManager2) {
                        extendedTrustManager=extendedTrustManager2;
                        break;
                    }
                }
            }
            if (null==extendedTrustManager) {
                throw new IllegalStateException(
                        "no X509ExtendedTrustManager, trust managers %s"
                                .formatted(Arrays.toString(trustManagers)));
            }
            if (client && verifyHostname) {
                Objects.requireNonNull(peerAddress, "peerAddress");
                extendedTrustManager=new VerifyHostnameClientTrustManager(
                        peerAddress.getHostString(),
                        extendedTrustManager);
            }
            sslContext.init(keyManagers, new TrustManager[]{extendedTrustManager}, randomFactory.get());
            return sslContext;
        }

        @Override
        public @NotNull SSLEngine createSSLEngine(@Nullable InetSocketAddress peerAddress) throws Throwable {
            SSLContext context=createSSLContext(peerAddress);
            SSLEngine engine;
            if (null==peerAddress) {
                engine=context.createSSLEngine();
            }
            else {
                engine=context.createSSLEngine(peerAddress.getHostString(), peerAddress.getPort());
            }
            clientAuthentication().set(engine);
            engine.setUseClientMode(client());
            return engine;
        }

        @Override
        public @NotNull SSLServerSocketFactory createSSLServerSocketFactory(
                @Nullable InetSocketAddress peerAddress) throws Throwable {
            return createSSLContext(peerAddress).getServerSocketFactory();
        }

        @Override
        public @NotNull SSLSocketFactory createSSLSocketFactory(
                @Nullable InetSocketAddress peerAddress) throws Throwable {
            return createSSLContext(peerAddress).getSocketFactory();
        }

        @Override
        public boolean isStarttls() {
            return startTls;
        }

        @Override
        public boolean isTls() {
            return true;
        }

        private KeyManager[] keyManagers() throws Throwable {
            if (null==privateKey) {
                return null;
            }
            KeyStore keyStore=KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry(
                    "server", privateKey.privateKey(), new char[0],
                    privateKey.certificates().toArray(new X509Certificate[0]));
            KeyManagerFactory keyManagerFactory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, new char[0]);
            return keyManagerFactory.getKeyManagers();
        }

        @Override
        public String toString() {
            return "tls(client: "+client
                    +", clientAuth: "+clientAuthentication
                    +", privateKey: "+privateKey
                    +", startTls: "+startTls
                    +", trustedCertificates: "+trustedCertificates
                    +", verifyHostname: "+verifyHostname+")";
        }

        private TrustManager[] trustManagers() throws Throwable {
            if (null==trustedCertificates) {
                return new TrustManager[]{new TrustEveryoneManager()};
            }
            if (trustedCertificates.isEmpty()) {
                return new TrustManager[]{new TrustNoneManager()};
            }
            KeyStore trustStore=KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            for (int ii=0; trustedCertificates.size()>ii; ++ii) {
                trustStore.setCertificateEntry("cert"+ii, trustedCertificates.get(ii));
            }
            TrustManagerFactory trustManagerFactory
                    =TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            return trustManagerFactory.getTrustManagers();
        }
    }

    NoTls NO_TLS=new NoTls();

    default @NotNull Tls asTls() {
        throw new ClassCastException("cannot cast %s to %s".formatted(this, Tls.class));
    }

    default @Nullable SSLContext createSSLContext(@Nullable InetSocketAddress peerAddress) throws Throwable {
        return null;
    }

    default @Nullable SSLEngine createSSLEngine(@Nullable InetSocketAddress peerAddress) throws Throwable {
        return null;
    }

    default @Nullable SSLServerSocketFactory createSSLServerSocketFactory(
            @Nullable InetSocketAddress peerAddress) throws Throwable {
        return null;
    }

    default @Nullable SSLSocketFactory createSSLSocketFactory(
            @Nullable InetSocketAddress peerAddress) throws Throwable {
        return null;
    }

    default boolean isStarttls() {
        return false;
    }

    default boolean isTls() {
        return false;
    }

    static TlsSettings noTls() {
        return NO_TLS;
    }

    static Builder tls() {
        return new Builder(true, ClientAuthentication.NONE, null, ()->null, false, null, true);
    }
}
