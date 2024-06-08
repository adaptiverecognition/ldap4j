package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Function;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public class CryptoUtil {
    public static final String PKCS12="PKCS12";

    private CryptoUtil() {
    }

    public static @NotNull KeyCertificates loadKeyCertificates(
            InputStream inputStream, char[] keyStorePassword, char[] privateKeyPassword, String type)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException,
            NoSuchElementException, UnrecoverableKeyException {
        KeyStore keyStore=loadKeyStore(inputStream, keyStorePassword, type);
        Enumeration<String> enumeration=keyStore.aliases();
        if (!enumeration.hasMoreElements()) {
            throw new NoSuchElementException("no aliases");
        }
        String alias=enumeration.nextElement();
        if (enumeration.hasMoreElements()) {
            throw new NoSuchElementException("multiple aliases");
        }
        if (!keyStore.isKeyEntry(alias)) {
            throw new NoSuchElementException(String.format("alias %1$s is not a key entry", alias));
        }
        Key key=keyStore.getKey(alias, privateKeyPassword);
        if (!(key instanceof PrivateKey)) {
            throw new NoSuchElementException(String.format("alias %1$s doesn't contain a private key", alias));
        }
        List<X509Certificate> certificates=new ArrayList<>();
        Certificate[] chain=keyStore.getCertificateChain(alias);
        if (null==chain) {
            throw new NoSuchElementException(String.format("alias %1$s doesn't contain a certificate chain", alias));
        }
        for (Certificate certificate: chain) {
            certificates.add((X509Certificate)certificate);
        }
        if (certificates.isEmpty()) {
            throw new NoSuchElementException(String.format("alias %1$s contains an empty certificate chain", alias));
        }
        return new KeyCertificates(certificates, (PrivateKey)key);
    }

    public static KeyStore loadKeyStore(
            InputStream inputStream, char[] keyStorePassword, String type)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        KeyStore keyStore=KeyStore.getInstance(type);
        keyStore.load(inputStream, keyStorePassword);
        return keyStore;
    }

    public static List<X509Certificate> loadPEM(
            InputStream inputStream) throws CertificateException, NoSuchElementException {
        CertificateFactory certificateFactory=CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates=new ArrayList<>();
        certificateFactory.generateCertificates(inputStream)
                .forEach((certificate)->certificates.add((X509Certificate)certificate));
        return certificates;
    }

    public static KeyCertificates loadPKCS12(
            InputStream inputStream, char[] keyStorePassword, char[] privateKeyPassword)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException,
            NoSuchElementException, UnrecoverableKeyException {
        return loadKeyCertificates(inputStream, keyStorePassword, privateKeyPassword, PKCS12);
    }

    public static @NotNull InputStream resource(String file, Class<?> type) {
        return Objects.requireNonNull(
                type.getResourceAsStream(
                        "/"+type.getPackageName().replaceAll(Pattern.quote("."), "/")+"/"+file),
                type+", "+file);
    }
    
    public static <T> T resource(
            String file, Function<@NotNull InputStream, T> function, Class<?> type) throws Throwable {
        try (InputStream stream=resource(file, type)) {
            return function.apply(stream);
        }
    }
}
