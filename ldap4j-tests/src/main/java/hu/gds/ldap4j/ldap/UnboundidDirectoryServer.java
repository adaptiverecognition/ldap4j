package hu.gds.ldap4j.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldif.LDIFReader;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.net.CryptoUtil;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class UnboundidDirectoryServer implements AutoCloseable {
    public static final Map<@NotNull String, @NotNull String> ADDITIONAL_BIND_CREDENTIALS=Map.of(
            adminBind().first(), adminBind().second(),
            "cn=bind0", "vu4pCu4drEGrBoBG",
            "cn=bind1", "urRrqUvkKaeVkS7a");
    public static final String ADMIN_PASSWORD="secret";
    public static final String ADMIN_USER="cn=admin";
    public static final String BASE_DN="ou=test,dc=ldap4j,dc=gds,dc=hu";
    public static final String CERTIFICATE_FILE_BAD="server-bad.cer.pem";
    public static final String CERTIFICATE_FILE_GOOD="server-good.cer.pem";
    public static final Map<@NotNull String, @NotNull Set<@NotNull String>> GROUPS=Map.of(
            "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
            Set.of(
                    "uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                    "uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu"),
            "cn=group1,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
            Set.of("uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu"));
    public static final String KEY_FILE_BAD="server-bad.p12";
    public static final String KEY_FILE_GOOD="server-good.p12";
    public static final String KEY_FILE_PASS="serverpass";
    public static final String LISTENER_CLEAR_TEXT="listener-clear-text";
    public static final String LISTENER_TLS="listener-tls";
    public static final Map<@NotNull String, @NotNull String> USERS=Map.of(
            "uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu", "ZZcDr3YLKAuPl5lc",
            "uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu", "7o5FInTc4PJeM1nr");

    private final InMemoryDirectoryServer directoryServer;

    public UnboundidDirectoryServer(
            boolean badCertificate,
            int serverPortClearText,
            int serverPortTls)
            throws Throwable {
        InMemoryDirectoryServerConfig config=new InMemoryDirectoryServerConfig(BASE_DN);
        for (Map.Entry<String, String> entry: ADDITIONAL_BIND_CREDENTIALS.entrySet()) {
            config.addAdditionalBindCredentials(entry.getKey(), entry.getValue());
        }
        TlsSettings tlsSettings=serverTls(badCertificate);
        config.getListenerConfigs().clear();
        config.getListenerConfigs().add(InMemoryListenerConfig.createLDAPConfig(
                LISTENER_CLEAR_TEXT,
                Inet4Address.getLoopbackAddress(),
                serverPortClearText,
                tlsSettings.createSSLSocketFactory(null)));
        config.getListenerConfigs().add(InMemoryListenerConfig.createLDAPSConfig(
                LISTENER_TLS,
                Inet4Address.getLoopbackAddress(),
                serverPortTls,
                tlsSettings.createSSLServerSocketFactory(null),
                tlsSettings.createSSLSocketFactory(null)));
        directoryServer=new InMemoryDirectoryServer(config);
    }

    public static @NotNull Pair<@NotNull String, @NotNull String> adminBind() {
        return Pair.of(ADMIN_USER, ADMIN_PASSWORD);
    }

    public static @NotNull List<@NotNull Pair<@NotNull String, @NotNull String>> allBinds() {
        List<@NotNull Pair<@NotNull String, @NotNull String>> list=new ArrayList<>();
        List.of(ADDITIONAL_BIND_CREDENTIALS, USERS)
                .forEach((map)->map.forEach((key, value)->list.add(Pair.of(key, value))));
        return list;
    }

    @Override
    public void close() {
        directoryServer.shutDown(true);
    }

    public static @NotNull TlsSettings.Tls clientTls(
            boolean badCertificate, boolean startTls, boolean verifyHostname) throws Throwable {
        return TlsSettings.tls()
                .client(true)
                .startTls(startTls)
                .trustCertificates(CryptoUtil.resource(
                        badCertificate?CERTIFICATE_FILE_BAD:CERTIFICATE_FILE_GOOD,
                        CryptoUtil::loadPEM,
                        UnboundidDirectoryServer.class))
                .verifyHostname(verifyHostname)
                .build();
    }

    public static @NotNull TlsSettings.Tls serverTls(boolean badCertificate) throws Throwable {
        return TlsSettings.tls()
                .client(false)
                .privateKey(CryptoUtil.resource(
                        badCertificate?KEY_FILE_BAD:KEY_FILE_GOOD,
                        (stream)->CryptoUtil.loadPKCS12(
                                stream, KEY_FILE_PASS.toCharArray(), KEY_FILE_PASS.toCharArray()),
                        UnboundidDirectoryServer.class))
                .build();
    }

    private InetSocketAddress localAddress(String listener) {
        return new InetSocketAddress(
                directoryServer.getListenAddress(listener),
                directoryServer.getListenPort(listener));
    }

    public InetSocketAddress localAddressClearText() {
        return localAddress(LISTENER_CLEAR_TEXT);
    }

    public InetSocketAddress localAddressTls() {
        return localAddress(LISTENER_TLS);
    }

    public void start() throws Throwable {
        CryptoUtil.resource(
                "test.ldif",
                (stream)->directoryServer.importFromLDIF(true, new LDIFReader(stream)),
                getClass());
        directoryServer.startListening();
    }
}
