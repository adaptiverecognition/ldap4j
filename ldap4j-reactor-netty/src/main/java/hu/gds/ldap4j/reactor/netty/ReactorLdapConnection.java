package hu.gds.ldap4j.reactor.netty;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.LdapMessage;
import hu.gds.ldap4j.ldap.Message;
import hu.gds.ldap4j.ldap.MessageIdGenerator;
import hu.gds.ldap4j.ldap.MessageReader;
import hu.gds.ldap4j.ldap.ParallelMessageReader;
import hu.gds.ldap4j.ldap.Request;
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
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class ReactorLdapConnection {
    private final @NotNull LdapConnection connection;
    private final long timeoutNanos;

    public ReactorLdapConnection(@NotNull LdapConnection connection, long timeoutNanos) {
        this.connection=Objects.requireNonNull(connection, "connection");
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
        return LavaMono.create(ReactorContext.createTimeoutNanos(timeoutNanos), lava);
    }

    public <T> @NotNull Mono<@NotNull LdapMessage<T>> readMessageChecked(
            int messageId, @NotNull MessageReader<T> messageReader) {
        return lavaToMono(connection.readMessageChecked(messageId, messageReader));
    }

    public <T> @NotNull Mono<T> readMessageCheckedParallel(
            @NotNull Map<@NotNull Integer, @NotNull ParallelMessageReader<?, T>> messageReadersByMessageId) {
        return lavaToMono(connection.readMessageCheckedParallel(messageReadersByMessageId));
    }

    public @NotNull Mono<@NotNull List<@NotNull SearchResult>> search(
            @NotNull ControlsMessage<SearchRequest> request) {
        return lavaToMono(connection.search(request));
    }

    public @NotNull Mono<Void> startTls(@NotNull TlsSettings.Tls tls) {
        return lavaToMono(connection.startTls(tls));
    }

    public static <T> @NotNull Mono<T> withConnection(
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Supplier<@NotNull Mono<@NotNull EventLoopGroup>> eventLoopGroupFactory,
            @NotNull Function<ReactorLdapConnection, @NotNull Mono<T>> function,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        Objects.requireNonNull(eventLoopGroupClose, "eventLoopGroupClose");
        Objects.requireNonNull(eventLoopGroupFactory, "eventLoopGroupFactory");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        Objects.requireNonNull(tlsSettings, "tlsSettings");
        return LavaMono.create(
                ReactorContext.createTimeoutNanos(timeoutNanos),
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
                                                new ReactorLdapConnection(connection, timeoutNanos))),
                                (connection)->MonoLava.create(function.apply(connection)))));
    }

    public <M extends Message<M>> @NotNull Mono<@NotNull Integer> writeMessage(@NotNull ControlsMessage<M> message) {
        return lavaToMono(connection.writeMessage(message));
    }

    public <M extends Message<M>> @NotNull Mono<@NotNull Integer> writeMessage(
            @NotNull ControlsMessage<M> message, @NotNull MessageIdGenerator messageIdGenerator) {
        return lavaToMono(connection.writeMessage(message, messageIdGenerator));
    }

    public <M extends Request<M, R>, R> @NotNull Mono<@NotNull ControlsMessage<R>> writeRequestReadResponseChecked(
            @NotNull ControlsMessage<M> request) {
        return lavaToMono(connection.writeRequestReadResponseChecked(request));
    }
}
