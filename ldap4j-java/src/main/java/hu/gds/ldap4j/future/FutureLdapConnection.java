package hu.gds.ldap4j.future;

import hu.gds.ldap4j.Consumer;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.ThreadLocalScheduledExecutorContext;
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
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.JavaAsyncChannelConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FutureLdapConnection {
    private final @NotNull LdapConnection connection;
    private final @NotNull ScheduledExecutorService executor;
    private final int localSize;
    private final @NotNull Log log;
    private final int parallelism;
    private final @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal;
    private final long timeoutNanos;

    public FutureLdapConnection(
            @NotNull LdapConnection connection,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal,
            long timeoutNanos) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.executor=Objects.requireNonNull(executor, "executor");
        this.localSize=localSize;
        this.log=Objects.requireNonNull(log, "log");
        this.parallelism=parallelism;
        this.threadLocal=Objects.requireNonNull(threadLocal, "threadLocal");
        this.timeoutNanos=timeoutNanos;
    }

    public @NotNull CompletableFuture<Void> close() {
        return startLava(connection.close());
    }

    public @NotNull LdapConnection connection() {
        return connection;
    }

    public static @NotNull Supplier<@NotNull CompletableFuture<@NotNull FutureLdapConnection>> factory(
            @NotNull ScheduledExecutorService executor,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return ()->Futures.start(
                ThreadLocalScheduledExecutorContext.createDelayNanos(
                        timeoutNanos,
                        executor,
                        localSize,
                        log,
                        parallelism,
                        threadLocal),
                Closeable.wrapOrClose(
                        LdapConnection::close,
                        ()->LdapConnection.factory(
                                factory,
                                remoteAddress,
                                tlsSettings),
                        (connection)->Lava.complete(
                                new FutureLdapConnection(
                                        connection,
                                        executor,
                                        localSize,
                                        log,
                                        parallelism,
                                        threadLocal,
                                        timeoutNanos))));
    }

    public static @NotNull Supplier<@NotNull CompletableFuture<@NotNull FutureLdapConnection>> factoryJavaAsync(
            @Nullable AsynchronousChannelGroup asynchronousChannelGroup,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                executor,
                JavaAsyncChannelConnection.factory(
                        asynchronousChannelGroup,
                        Map.of()),
                localSize,
                log,
                parallelism,
                remoteAddress,
                threadLocal,
                timeoutNanos,
                tlsSettings);
    }

    public static @NotNull Supplier<@NotNull CompletableFuture<@NotNull FutureLdapConnection>> factoryJavaAsync(
            @Nullable AsynchronousChannelGroup asynchronousChannelGroup,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            int parallelism,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return factoryJavaAsync(
                asynchronousChannelGroup,
                executor,
                ThreadLocalScheduledExecutorContext.DEFAULT_LOCAL_SIZE,
                log,
                parallelism,
                remoteAddress,
                new ThreadLocal<>(),
                timeoutNanos,
                tlsSettings);
    }

    public @NotNull CompletableFuture<@NotNull Boolean> isOpenAndNotFailed() {
        return startLava(connection.isOpenAndNotFailed());
    }

    public @NotNull CompletableFuture<@NotNull InetSocketAddress> localAddress() {
        return startLava(connection.localAddress());
    }

    public <T> @NotNull CompletableFuture<@NotNull LdapMessage<T>> readMessageChecked(
            int messageId, @NotNull MessageReader<T> messageReader) {
        return startLava(connection.readMessageChecked(messageId, messageReader));
    }

    public <T> @NotNull CompletableFuture<T> readMessageCheckedParallel(
            @NotNull Function<@NotNull Integer, @Nullable ParallelMessageReader<?, T>> messageReadersByMessageId) {
        return startLava(connection.readMessageCheckedParallel(messageReadersByMessageId));
    }

    public @NotNull CompletableFuture<@NotNull InetSocketAddress> remoteAddress() {
        return startLava(connection.remoteAddress());
    }

    public @NotNull CompletableFuture<Void> restartTlsHandshake() {
        return startLava(connection.restartTlsHandshake());
    }

    public @NotNull CompletableFuture<Void> restartTlsHandshake(@NotNull Consumer<@NotNull SSLEngine> consumer) {
        return startLava(connection.restartTlsHandshake(consumer));
    }

    public @NotNull CompletableFuture<@NotNull List<@NotNull ControlsMessage<SearchResult>>> search(
            @NotNull ControlsMessage<SearchRequest> request) {
        return startLava(connection.search(request));
    }

    private <T> @NotNull CompletableFuture<T> startLava(@NotNull Lava<T> lava) {
        return Futures.start(
                ThreadLocalScheduledExecutorContext.createDelayNanos(
                        timeoutNanos,
                        executor,
                        localSize,
                        log,
                        parallelism,
                        threadLocal),
                lava);
    }

    public @NotNull CompletableFuture<Void> startTls(TlsSettings.@NotNull Tls tls) {
        return startLava(connection.startTls(tls));
    }

    public @NotNull CompletableFuture<@Nullable SSLSession> tlsSession() {
        return startLava(connection.tlsSession());
    }

    public <M extends Message<M>> @NotNull CompletableFuture<@NotNull Integer> writeMessage(
            @NotNull ControlsMessage<M> message) {
        return startLava(connection.writeMessage(message));
    }

    public <M extends Message<M>> @NotNull CompletableFuture<@NotNull Integer> writeMessage(
            @NotNull ControlsMessage<M> message, @NotNull MessageIdGenerator messageIdGenerator) {
        return startLava(connection.writeMessage(message, messageIdGenerator));
    }

    public <M extends Request<M, R>, R extends Response>
    @NotNull CompletableFuture<@NotNull ControlsMessage<R>> writeRequestReadResponseChecked(
            @NotNull ControlsMessage<M> request) {
        return startLava(connection.writeRequestReadResponseChecked(request));
    }
}
