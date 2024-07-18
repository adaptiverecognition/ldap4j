package hu.gds.ldap4j.future;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.ScheduledExecutorContext;
import hu.gds.ldap4j.ldap.AddRequest;
import hu.gds.ldap4j.ldap.AddResponse;
import hu.gds.ldap4j.ldap.BindResponse;
import hu.gds.ldap4j.ldap.DeleteRequest;
import hu.gds.ldap4j.ldap.DeleteResponse;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.ModifyDNRequest;
import hu.gds.ldap4j.ldap.ModifyDNResponse;
import hu.gds.ldap4j.ldap.ModifyRequest;
import hu.gds.ldap4j.ldap.ModifyResponse;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FutureLdapConnection {
    private final @NotNull LdapConnection connection;
    private final @NotNull ScheduledExecutorService executor;
    private final @NotNull Log log;
    private final long timeoutNanos;

    public FutureLdapConnection(
            @NotNull LdapConnection connection,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            long timeoutNanos) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.executor=Objects.requireNonNull(executor, "executor");
        this.log=Objects.requireNonNull(log, "log");
        this.timeoutNanos=timeoutNanos;
    }

    public @NotNull CompletableFuture<@NotNull AddResponse> add(@NotNull AddRequest addRequest, boolean manageDsaIt) {
        return startLava(connection.add(addRequest, manageDsaIt));
    }

    public @NotNull CompletableFuture<BindResponse> bindSimple(@NotNull String bindDn, char[] password) {
        return startLava(connection.bindSimple(bindDn, password));
    }

    public @NotNull CompletableFuture<Void> close() {
        return startLava(connection.close());
    }

    public @NotNull LdapConnection connection() {
        return connection;
    }

    public @NotNull CompletableFuture<@NotNull DeleteResponse> delete(
            @NotNull DeleteRequest deleteRequest, boolean manageDsaIt) {
        return startLava(connection.delete(deleteRequest, manageDsaIt));
    }

    public static @NotNull Supplier<@NotNull CompletableFuture<@NotNull FutureLdapConnection>> factory(
            @NotNull ScheduledExecutorService executor,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull Log log,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return ()->Futures.start(
                ScheduledExecutorContext.createDelayNanos(timeoutNanos, executor, log),
                Closeable.wrapOrClose(
                        LdapConnection::close,
                        ()->LdapConnection.factory(
                                factory,
                                remoteAddress,
                                tlsSettings),
                        (connection)->Lava.complete(
                                new FutureLdapConnection(connection, executor, log, timeoutNanos))));
    }

    public static @NotNull Supplier<@NotNull CompletableFuture<@NotNull FutureLdapConnection>> factoryJavaAsync(
            @Nullable AsynchronousChannelGroup asynchronousChannelGroup,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                executor,
                JavaAsyncChannelConnection.factory(
                        asynchronousChannelGroup,
                        Map.of()),
                log,
                remoteAddress,
                timeoutNanos,
                tlsSettings);
    }

    public @NotNull CompletableFuture<Void> fastBind() {
        return startLava(connection.fastBind());
    }

    public @NotNull CompletableFuture<@NotNull ModifyResponse> modify(
            boolean manageDsaIt, @NotNull ModifyRequest modifyRequest) {
        return startLava(connection.modify(manageDsaIt, modifyRequest));
    }

    public @NotNull CompletableFuture<@NotNull ModifyDNResponse> modifyDN(
            boolean manageDsaIt, @NotNull ModifyDNRequest modifyDNRequest) {
        return startLava(connection.modifyDN(manageDsaIt, modifyDNRequest));
    }

    public @NotNull CompletableFuture<@NotNull List<@NotNull SearchResult>> search(
            boolean manageDsaIt, @NotNull SearchRequest searchRequest) {
        return startLava(connection.search(manageDsaIt, searchRequest));
    }

    private <T> @NotNull CompletableFuture<T> startLava(@NotNull Lava<T> lava) {
        return Futures.start(
                ScheduledExecutorContext.createDelayNanos(timeoutNanos, executor, log),
                lava);
    }
}
