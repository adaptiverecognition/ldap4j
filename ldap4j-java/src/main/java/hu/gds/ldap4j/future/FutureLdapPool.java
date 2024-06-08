package hu.gds.ldap4j.future;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Pool;
import hu.gds.ldap4j.lava.ScheduledExecutorContext;
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
    private final @NotNull Log log;
    private final @NotNull Pool<LdapConnection, FutureLdapConnection> pool;
    private final long timeoutNanos;

    public FutureLdapPool(
            @NotNull Supplier<@NotNull CompletableFuture<Void>> closeLoopGroup,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            @NotNull Pool<LdapConnection, FutureLdapConnection> pool,
            long timeoutNanos) {
        this.closeLoopGroup=Objects.requireNonNull(closeLoopGroup, "closeLoopGroup");
        this.executor=Objects.requireNonNull(executor, "executor");
        this.log=Objects.requireNonNull(log, "log");
        this.pool=Objects.requireNonNull(pool, "pool");
        this.timeoutNanos=timeoutNanos;
    }

    public @NotNull CompletableFuture<Void> close() {
        return Futures.start(
                ScheduledExecutorContext.createDelayNanos(timeoutNanos, executor, log),
                Lava.finallyGet(
                        ()->Futures.handle(closeLoopGroup),
                        pool::close));
    }

    public static @NotNull CompletableFuture<@NotNull FutureLdapPool> create(
            @NotNull Supplier<@NotNull CompletableFuture<Void>> closeLoopGroup,
            @NotNull ScheduledExecutorService executor,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull Log log,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return Futures.start(
                ScheduledExecutorContext.createDelayNanos(timeoutNanos, executor, log),
                Lava.supplier(()->Lava.complete(
                        new FutureLdapPool(
                                closeLoopGroup,
                                executor,
                                log,
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
                                        (connection)->Lava.complete(new FutureLdapConnection(
                                                connection, executor, log, timeoutNanos))),
                                timeoutNanos))));
    }

    public static @NotNull CompletableFuture<@NotNull FutureLdapPool> createJavaAsync(
            @NotNull Function<@Nullable AsynchronousChannelGroup, @NotNull CompletableFuture<Void>> closeLoopGroup,
            @NotNull Supplier<@NotNull CompletableFuture<@Nullable AsynchronousChannelGroup>> createLoopGroup,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
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
                        log,
                        poolSize,
                        remoteAddress,
                        timeoutNanos,
                        tlsSettings));
    }

    public <T> @NotNull CompletableFuture<T> lease(
            @NotNull Function<@NotNull FutureLdapConnection, @NotNull CompletableFuture<T>> function) {
        return Futures.start(
                ScheduledExecutorContext.createDelayNanos(timeoutNanos, executor, log),
                pool.lease((connection)->Futures.handle(()->function.apply(connection))));
    }
}
