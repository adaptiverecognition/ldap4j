package hu.gds.ldap4j.reactor.netty;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Pool;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.net.Connection;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.net.netty.NettyConnection;
import hu.gds.ldap4j.reactor.LavaMono;
import hu.gds.ldap4j.reactor.MonoLava;
import hu.gds.ldap4j.reactor.ReactorContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactorLdapPool {
    private final @NotNull EventLoopGroup eventLoopGroup;
    private final @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose;
    private final int parallelism;
    private final @NotNull Pool<LdapConnection, ReactorLdapConnection> pool;
    private final long timeoutNanos;

    public ReactorLdapPool(
            @NotNull EventLoopGroup eventLoopGroup,
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            int parallelism,
            @NotNull Pool<LdapConnection, ReactorLdapConnection> pool,
            long timeoutNanos) {
        this.eventLoopGroup=Objects.requireNonNull(eventLoopGroup, "eventLoopGroup");
        this.eventLoopGroupClose=Objects.requireNonNull(eventLoopGroupClose, "eventLoopGroupClose");
        this.parallelism=parallelism;
        this.pool=Objects.requireNonNull(pool, "pool");
        this.timeoutNanos=timeoutNanos;
    }

    public @NotNull Mono<Void> close() {
        return LavaMono.create(
                ReactorContext.createTimeoutNanos(parallelism, timeoutNanos),
                Lava.finallyGet(
                        ()->MonoLava.create(eventLoopGroupClose.apply(eventLoopGroup)),
                        pool::close));
    }

    public static @NotNull ReactorLdapPool create(
            EventLoopGroup eventLoopGroup,
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Log log,
            int parallelism,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return new ReactorLdapPool(
                eventLoopGroup,
                eventLoopGroupClose,
                parallelism,
                new Pool<>(
                        LdapConnection::close,
                        ()->LdapConnection.factory(
                                NettyConnection.factory(
                                        NioSocketChannel.class,
                                        eventLoopGroup,
                                        Map.of()),
                                Schedulers.boundedElastic()::schedule,
                                remoteAddress,
                                tlsSettings),
                        Connection::checkOpenAndNotFailed,
                        timeoutNanos,
                        timeoutNanos,
                        log,
                        poolSize,
                        (connection)->connection.connection().isOpenAndNotFailed(),
                        (connection)->Lava.complete(
                                new ReactorLdapConnection(connection, parallelism, timeoutNanos))),
                timeoutNanos);
    }

    public static @NotNull ReactorLdapPool create(
            EventLoopGroup eventLoopGroup,
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Log log,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return create(
                eventLoopGroup,
                eventLoopGroupClose,
                log,
                Context.defaultParallelism(),
                poolSize,
                remoteAddress,
                timeoutNanos,
                tlsSettings);
    }

    public static @NotNull Mono<@NotNull ReactorLdapPool> create(
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Supplier<@NotNull Mono<@NotNull EventLoopGroup>> eventLoopGroupFactory,
            int parallelism,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return LavaMono.create(
                ReactorContext.createTimeoutNanos(parallelism, timeoutNanos),
                Lava.supplier(()->Closeable.wrapOrClose(
                        (eventLoopGroup)->MonoLava.create(eventLoopGroupClose.apply(eventLoopGroup)),
                        ()->MonoLava.create(eventLoopGroupFactory.get()),
                        (eventLoopGroup)->Lava.context()
                                .compose((context)->Lava.complete(
                                        create(
                                                eventLoopGroup,
                                                eventLoopGroupClose,
                                                context.log(),
                                                parallelism,
                                                poolSize,
                                                remoteAddress,
                                                timeoutNanos,
                                                tlsSettings))))));
    }

    public static @NotNull Mono<@NotNull ReactorLdapPool> create(
            @NotNull Function<@NotNull EventLoopGroup, @NotNull Mono<Void>> eventLoopGroupClose,
            @NotNull Supplier<@NotNull Mono<@NotNull EventLoopGroup>> eventLoopGroupFactory,
            int poolSize,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return create(
                eventLoopGroupClose,
                eventLoopGroupFactory,
                Context.defaultParallelism(),
                poolSize,
                remoteAddress,
                timeoutNanos,
                tlsSettings);
    }

    public <T> @NotNull Mono<T> lease(@NotNull Function<@NotNull ReactorLdapConnection, @NotNull Mono<T>> function) {
        return new LavaMono<>(
                ReactorContext.createTimeoutNanos(parallelism, timeoutNanos),
                pool.lease((connection)->MonoLava.create(function.apply(connection))));
    }
}
