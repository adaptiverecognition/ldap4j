package hu.gds.ldap4j.net;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.TestParameters;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.ContextHolder;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.UnboundidDirectoryServer;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkTestParameters extends TestParameters {
    public enum Tls {
        NO_TLS, USE_TLS, WRAP_TLS
    }

    public final int serverPort;
    public final @NotNull Tls tls;

    public NetworkTestParameters(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> blockingIoContextHolderFactory,
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory,
            @NotNull Supplier<@NotNull NetworkConnectionFactory> networkConnectionFactoryFactory,
            long timeoutNanos,
            int serverPort,
            @NotNull Tls tls) {
        super(
                blockingIoContextHolderFactory, contextHolderFactory,
                networkConnectionFactoryFactory, timeoutNanos);
        this.serverPort=serverPort;
        this.tls=Objects.requireNonNull(tls, "tls");
    }

    public NetworkTestParameters(
            @NotNull TestParameters parameters,
            int serverPort,
            @NotNull Tls tls) {
        this(
                parameters.blockingIoContextHolderFactory, parameters.contextHolderFactory,
                parameters.networkConnectionFactoryFactory, parameters.timeoutNanos,
                serverPort, tls);
    }

    public @NotNull Lava<@NotNull DuplexConnection> connectionFactory(
            @NotNull TestContext<NetworkTestParameters> context,
            @Nullable Executor handshakeExecutor,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions)
            throws Throwable {
        return connectionFactory(
                false,
                context,
                TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                handshakeExecutor,
                remoteAddress,
                socketOptions,
                true,
                Lava::complete);
    }

    public @NotNull Lava<@NotNull DuplexConnection> connectionFactory(
            @NotNull TestContext<NetworkTestParameters> context,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions)
            throws Throwable {
        return connectionFactory(
                false,
                context,
                TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                null,
                remoteAddress,
                socketOptions,
                true,
                Lava::complete);
    }

    public @NotNull Lava<@NotNull DuplexConnection> connectionFactory(
            @NotNull TestContext<NetworkTestParameters> context,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions,
            @NotNull Function<@NotNull DuplexConnection, @NotNull Lava<@NotNull DuplexConnection>> wrapNetwork)
            throws Throwable {
        return connectionFactory(
                false,
                context,
                TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                null,
                remoteAddress,
                socketOptions,
                true,
                wrapNetwork);
    }

    public @NotNull Lava<@NotNull DuplexConnection> connectionFactory(
            @NotNull TestContext<NetworkTestParameters> context,
            boolean explicitTlsRenegotiation,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions)
            throws Throwable {
        return connectionFactory(
                false,
                context,
                explicitTlsRenegotiation,
                null,
                remoteAddress,
                socketOptions,
                true,
                Lava::complete);
    }

    public @NotNull Lava<@NotNull DuplexConnection> connectionFactory(
            boolean badCertificate,
            @NotNull TestContext<NetworkTestParameters> context,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions,
            boolean verifyHostname,
            @NotNull Function<@NotNull DuplexConnection, @NotNull Lava<@NotNull DuplexConnection>> wrapNetwork)
            throws Throwable {
        return connectionFactory(
                badCertificate,
                context,
                TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                null,
                remoteAddress,
                socketOptions,
                verifyHostname,
                wrapNetwork);
    }

    public @NotNull Lava<@NotNull DuplexConnection> connectionFactory(
            boolean badCertificate,
            @NotNull TestContext<NetworkTestParameters> context,
            boolean explicitTlsRenegotiation,
            @Nullable Executor handshakeExecutor,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions,
            boolean verifyHostname,
            @NotNull Function<@NotNull DuplexConnection, @NotNull Lava<@NotNull DuplexConnection>> wrapNetwork)
            throws Throwable {
        @NotNull Lava<@NotNull DuplexConnection> connectionFactory0
                =context.networkConnectionFactory().factory(
                        context.blockingIoContextHolder().context(),
                        context.log(),
                        socketOptions)
                .apply(remoteAddress);
        @NotNull Lava<@NotNull DuplexConnection> connectionFactory1
                =Closeable.wrapOrClose(()->connectionFactory0, wrapNetwork);
        @NotNull Lava<@NotNull DuplexConnection> connectionFactory2;
        if (NetworkTestParameters.Tls.NO_TLS.equals(context.parameters().tls)) {
            connectionFactory2=connectionFactory1;
        }
        else {
            Lava<TlsConnection> tlsConnectionFactory
                    =TlsConnection.factory(explicitTlsRenegotiation, (ignore)->connectionFactory1)
                    .apply(remoteAddress);
            if (NetworkTestParameters.Tls.USE_TLS.equals(context.parameters().tls)) {
                connectionFactory2=Closeable.wrapOrClose(
                        ()->tlsConnectionFactory,
                        (connection)->connection.startTlsHandshake(
                                        handshakeExecutor,
                                        UnboundidDirectoryServer.clientTls(badCertificate, false, verifyHostname))
                                .composeIgnoreResult(()->Lava.complete(connection)));
            }
            else {
                connectionFactory2=tlsConnectionFactory
                        .compose(Lava::complete);
            }
        }
        return connectionFactory2;
    }

    public long networkTimeoutNanos() {
        return (Tls.USE_TLS.equals(tls)?3L:2L)*AbstractTest.TIMEOUT_NANOS_SMALL;
    }

    public static @NotNull Stream<@NotNull NetworkTestParameters> streamNetwork() {
        return TestParameters.stream().flatMap(
                (parameters)->Stream.of(Tls.values())
                        .flatMap((tls)->Stream.of(new NetworkTestParameters(
                                parameters, AbstractTest.SERVER_PORT_CLEAR_TEXT, tls))));
    }

    public static @NotNull Stream<@NotNull NetworkTestParameters> streamNetworkUseTls() {
        return streamNetwork().filter((parameters)->Tls.USE_TLS.equals(parameters.tls));
    }

    @Override
    protected void toString(@NotNull Map<@NotNull String, Object> map) {
        super.toString(map);
        map.put("serverPort", serverPort);
        map.put("tls", tls);
    }
}
