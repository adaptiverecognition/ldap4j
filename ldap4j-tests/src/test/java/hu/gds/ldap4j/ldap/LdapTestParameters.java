package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.TestParameters;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.ContextHolder;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.NetworkConnectionFactory;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LdapTestParameters extends TestParameters {
    public enum Tls {
        CLEAR_TEXT, START_TLS, TLS
    }

    public final int serverPortClearText;
    public final int serverPortTls;
    public final @NotNull Tls tls;

    public LdapTestParameters(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> blockingIoContextHolderFactory,
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory,
            @NotNull Supplier<@NotNull NetworkConnectionFactory> networkConnectionFactoryFactory,
            long timeoutNanos,
            int serverPortClearText,
            int serverPortTls,
            @NotNull Tls tls) {
        super(
                blockingIoContextHolderFactory, contextHolderFactory,
                networkConnectionFactoryFactory, timeoutNanos);
        this.serverPortClearText=serverPortClearText;
        this.serverPortTls=serverPortTls;
        this.tls=Objects.requireNonNull(tls, "tls");
    }

    public LdapTestParameters(
            @NotNull TestParameters parameters,
            int serverPortClearText,
            int serverPortTls,
            @NotNull Tls tls) {
        this(
                parameters.blockingIoContextHolderFactory, parameters.contextHolderFactory,
                parameters.networkConnectionFactoryFactory, parameters.timeoutNanos,
                serverPortClearText, serverPortTls, tls);
    }

    public @NotNull Lava<@NotNull LdapConnection> connectionFactory(
            @NotNull TestContext<LdapTestParameters> context, @NotNull LdapServer ldapServer,
            @Nullable Pair<@NotNull String, @NotNull String> simpleBind) throws Throwable {
        @NotNull InetSocketAddress remoteAddress;
        @NotNull TlsSettings tlsSettings;
        switch (tls) {
            case CLEAR_TEXT -> {
                remoteAddress=ldapServer.localAddressClearText();
                tlsSettings=TlsSettings.noTls();
            }
            case START_TLS -> {
                remoteAddress=ldapServer.localAddressClearText();
                tlsSettings=LdapServer.clientTls(false, true, true);
            }
            case TLS -> {
                remoteAddress=ldapServer.localAddressTls();
                tlsSettings=LdapServer.clientTls(false, false, true);
            }
            default -> throw new IllegalStateException("unknown tls value %s".formatted(tls));
        }
        @NotNull Lava<@NotNull LdapConnection> connectionFactory0=LdapConnection.factory(
                context.networkConnectionFactory().factory(
                        context.blockingIoContextHolder().context(),
                        Map.of()),
                remoteAddress,
                tlsSettings);
        @NotNull Lava<@NotNull LdapConnection> connectionFactory1;
        if (null==simpleBind) {
            connectionFactory1=connectionFactory0;
        }
        else {
            connectionFactory1=Closeable.wrapOrClose(
                    ()->connectionFactory0,
                    (connection)->connection.writeRequestReadResponseChecked(
                                    BindRequest.simple(
                                                    simpleBind.first(),
                                                    simpleBind.second().toCharArray())
                                            .controlsEmpty())
                            .composeIgnoreResult(()->Lava.complete(connection)));
        }
        return connectionFactory1;
    }

    public static @NotNull Stream<@NotNull LdapTestParameters> streamLdap() {
        return TestParameters.stream().flatMap(
                (parameters)->Stream.of(Tls.values())
                        .flatMap((tls)->Stream.of(new LdapTestParameters(
                                parameters, AbstractTest.SERVER_PORT_CLEAR_TEXT, AbstractTest.SERVER_PORT_TLS, tls))));
    }
}
