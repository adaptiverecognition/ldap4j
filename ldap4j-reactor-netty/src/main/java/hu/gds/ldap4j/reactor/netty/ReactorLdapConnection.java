package hu.gds.ldap4j.reactor.netty;

import hu.gds.ldap4j.Consumer;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.LdapMessage;
import hu.gds.ldap4j.ldap.Message;
import hu.gds.ldap4j.ldap.MessageIdGenerator;
import hu.gds.ldap4j.ldap.MessageReader;
import hu.gds.ldap4j.ldap.ParallelMessageReader;
import hu.gds.ldap4j.ldap.Request;
import hu.gds.ldap4j.ldap.Response;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.net.netty.NettyConnection;
import hu.gds.ldap4j.reactor.LavaMono;
import hu.gds.ldap4j.reactor.MonoLava;
import hu.gds.ldap4j.reactor.ReactorContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

public class ReactorLdapConnection {
    private final @NotNull LdapConnection connection;
    private final int parallelism;
    private final long timeoutNanos;

    public ReactorLdapConnection(@NotNull LdapConnection connection, int parallelism, long timeoutNanos) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.parallelism=parallelism;
        this.timeoutNanos=timeoutNanos;
    }

    public @NotNull Mono<Void> close() {
        return lavaToMono(connection.close());
    }

    public LdapConnection connection() {
        return connection;
    }

    public @NotNull Mono<@NotNull Boolean> isOpenAndNotFailed() {
        return lavaToMono(connection.isOpenAndNotFailed());
    }

    private <T> @NotNull Mono<T> lavaToMono(@NotNull Lava<T> lava) {
        return LavaMono.create(ReactorContext.createTimeoutNanos(parallelism, timeoutNanos), lava);
    }

    public @NotNull Mono<@NotNull InetSocketAddress> localAddress() {
        return lavaToMono(connection.localAddress());
    }

    public <T> @NotNull Mono<@NotNull LdapMessage<T>> readMessageChecked(
            int messageId, @NotNull MessageReader<T> messageReader) {
        return lavaToMono(connection.readMessageChecked(messageId, messageReader));
    }

    public <T> @NotNull Mono<T> readMessageCheckedParallel(
            @NotNull Function<@NotNull Integer, @Nullable ParallelMessageReader<?, T>> messageReadersByMessageId) {
        return lavaToMono(connection.readMessageCheckedParallel(messageReadersByMessageId));
    }

    public @NotNull Mono<@NotNull InetSocketAddress> remoteAddress() {
        return lavaToMono(connection.remoteAddress());
    }

    public @NotNull Mono<Void> restartTlsHandshake() {
        return lavaToMono(connection.restartTlsHandshake());
    }

    public @NotNull Mono<Void> restartTlsHandshake(@NotNull Consumer<@NotNull SSLEngine> consumer) {
        return lavaToMono(connection.restartTlsHandshake(consumer));
    }

    public @NotNull Mono<@NotNull List<@NotNull ControlsMessage<SearchResult>>> search(
            @NotNull ControlsMessage<SearchRequest> request) {
        return lavaToMono(connection.search(request));
    }

    public @NotNull Mono<Void> startTls(@NotNull TlsSettings.Tls tls) {
        return lavaToMono(connection.startTls(tls));
    }

    public @NotNull Mono<@Nullable SSLSession> tlsSession() {
        return lavaToMono(connection.tlsSession());
    }

    public static <T> @NotNull Mono<T> withConnection(
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Supplier<@NotNull Mono<@NotNull EventLoopGroup>> eventLoopGroupFactory,
            @NotNull Function<ReactorLdapConnection, @NotNull Mono<T>> function,
            int parallelism,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        Objects.requireNonNull(eventLoopGroupClose, "eventLoopGroupClose");
        Objects.requireNonNull(eventLoopGroupFactory, "eventLoopGroupFactory");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        Objects.requireNonNull(tlsSettings, "tlsSettings");
        return LavaMono.create(
                ReactorContext.createTimeoutNanos(parallelism, timeoutNanos),
                Closeable.withClose(
                        (eventLoopGroup)->MonoLava.create(eventLoopGroupClose.apply(eventLoopGroup)),
                        ()->MonoLava.create(eventLoopGroupFactory.get()),
                        (eventLoopGroup)->Closeable.withClose(
                                (connection)->MonoLava.create(connection.close()),
                                ()->Closeable.wrapOrClose(
                                        ()->LdapConnection.factory(
                                                NettyConnection.factory(
                                                        NioSocketChannel.class,
                                                        eventLoopGroup,
                                                        Map.of()),
                                                remoteAddress,
                                                tlsSettings),
                                        (connection)->Lava.complete(
                                                new ReactorLdapConnection(connection, parallelism, timeoutNanos))),
                                (connection)->MonoLava.create(function.apply(connection)))));
    }

    public static <T> @NotNull Mono<T> withConnection(
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Supplier<@NotNull Mono<@NotNull EventLoopGroup>> eventLoopGroupFactory,
            @NotNull Function<ReactorLdapConnection, @NotNull Mono<T>> function,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return withConnection(
                eventLoopGroupClose,
                eventLoopGroupFactory,
                function,
                Context.defaultParallelism(),
                remoteAddress,
                timeoutNanos,
                tlsSettings);
    }

    public <M extends Message<M>> @NotNull Mono<@NotNull Integer> writeMessage(@NotNull ControlsMessage<M> message) {
        return lavaToMono(connection.writeMessage(message));
    }

    public <M extends Message<M>> @NotNull Mono<@NotNull Integer> writeMessage(
            @NotNull ControlsMessage<M> message, @NotNull MessageIdGenerator messageIdGenerator) {
        return lavaToMono(connection.writeMessage(message, messageIdGenerator));
    }

    public <M extends Request<M, R>, R extends Response>
    @NotNull Mono<@NotNull ControlsMessage<R>> writeRequestReadResponseChecked(
            @NotNull ControlsMessage<M> request) {
        return lavaToMono(connection.writeRequestReadResponseChecked(request));
    }
}
