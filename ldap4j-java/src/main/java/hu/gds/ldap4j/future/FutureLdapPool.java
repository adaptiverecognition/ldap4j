package hu.gds.ldap4j.future;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Pool;
import hu.gds.ldap4j.lava.ThreadLocalScheduledExecutorContext;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.net.Connection;
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.JavaAsyncChannelConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FutureLdapPool {
    private final @NotNull Supplier<@NotNull CompletableFuture<Void>> closeLoopGroup;
    private final @NotNull ScheduledExecutorService executor;
    private final int localSize;
    private final @NotNull Log log;
    private final int parallelism;
    private final @NotNull Pool<@NotNull LdapConnection, @NotNull FutureLdapConnection> pool;
    private final @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal;
    private final long timeoutNanos;

    public FutureLdapPool(
            @NotNull Supplier<@NotNull CompletableFuture<Void>> closeLoopGroup,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull Pool<@NotNull LdapConnection, @NotNull FutureLdapConnection> pool,
            @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal,
            long timeoutNanos) {
        this.closeLoopGroup=Objects.requireNonNull(closeLoopGroup, "closeLoopGroup");
        this.executor=Objects.requireNonNull(executor, "executor");
        this.localSize=localSize;
        this.log=Objects.requireNonNull(log, "log");
        this.parallelism=parallelism;
        this.pool=Objects.requireNonNull(pool, "pool");
        this.threadLocal=Objects.requireNonNull(threadLocal, "threadLocal");
        this.timeoutNanos=timeoutNanos;
    }

    public @NotNull CompletableFuture<Void> close() {
        return Futures.start(
                ThreadLocalScheduledExecutorContext.createDelayNanos(
                        timeoutNanos,
                        executor,
                        localSize,
                        log,
                        parallelism,
                        threadLocal),
                Lava.finallyGet(
                        ()->Futures.handle(closeLoopGroup),
                        pool::close));
    }

    public static @NotNull CompletableFuture<@NotNull FutureLdapPool> create(
            @NotNull Supplier<@NotNull CompletableFuture<Void>> closeLoopGroup,
            @NotNull ScheduledExecutorService executor,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            int localSize,
            @NotNull Log log,
            int parallelism,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return Futures.start(
                ThreadLocalScheduledExecutorContext.createDelayNanos(
                        timeoutNanos,
                        executor,
                        localSize,
                        log,
                        parallelism,
                        threadLocal),
                Lava.supplier(()->Lava.complete(
                        new FutureLdapPool(
                                closeLoopGroup,
                                executor,
                                localSize,
                                log,
                                parallelism,
                                new Pool<>(
                                        LdapConnection::close,
                                        ()->LdapConnection.factory(
                                                factory,
                                                remoteAddress,
                                                tlsSettings),
                                        Connection::checkOpenAndNotFailed,
                                        timeoutNanos,
                                        timeoutNanos,
                                        log,
                                        poolSize,
                                        (connection)->connection.connection().isOpenAndNotFailed(),
                                        (connection)->Lava.complete(
                                                new FutureLdapConnection(
                                                        connection,
                                                        executor,
                                                        localSize,
                                                        log,
                                                        parallelism,
                                                        threadLocal,
                                                        timeoutNanos))),
                                threadLocal,
                                timeoutNanos))));
    }

    public static @NotNull CompletableFuture<@NotNull FutureLdapPool> createJavaAsync(
            @NotNull Function<@Nullable AsynchronousChannelGroup, @NotNull CompletableFuture<Void>> closeLoopGroup,
            @NotNull Supplier<@NotNull CompletableFuture<@Nullable AsynchronousChannelGroup>> createLoopGroup,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull ThreadLocal<ThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return Futures.wrapOrClose(
                closeLoopGroup,
                createLoopGroup,
                (loopGroup)->create(
                        ()->closeLoopGroup.apply(loopGroup),
                        executor,
                        JavaAsyncChannelConnection.factory(
                                loopGroup,
                                Map.of()),
                        localSize,
                        log,
                        parallelism,
                        poolSize,
                        remoteAddress,
                        threadLocal,
                        timeoutNanos,
                        tlsSettings));
    }

    public <T> @NotNull CompletableFuture<T> lease(
            @NotNull Function<@NotNull FutureLdapConnection, @NotNull CompletableFuture<T>> function) {
        return Futures.start(
                ThreadLocalScheduledExecutorContext.createDelayNanos(
                        timeoutNanos,
                        executor,
                        localSize,
                        log,
                        parallelism,
                        threadLocal),
                pool.lease((connection)->Futures.handle(()->function.apply(connection))));
    }
}
