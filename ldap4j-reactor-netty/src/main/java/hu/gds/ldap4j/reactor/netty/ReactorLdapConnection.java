package hu.gds.ldap4j.reactor.netty;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.AddRequest;
import hu.gds.ldap4j.ldap.AddResponse;
import hu.gds.ldap4j.ldap.BindResponse;
import hu.gds.ldap4j.ldap.CompareRequest;
import hu.gds.ldap4j.ldap.CompareResponse;
import hu.gds.ldap4j.ldap.DeleteRequest;
import hu.gds.ldap4j.ldap.DeleteResponse;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.ModifyDNRequest;
import hu.gds.ldap4j.ldap.ModifyDNResponse;
import hu.gds.ldap4j.ldap.ModifyRequest;
import hu.gds.ldap4j.ldap.ModifyResponse;
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

    public @NotNull Mono<@NotNull AddResponse> add(@NotNull AddRequest addRequest, boolean manageDsaIt) {
        return lavaToMono(connection.add(addRequest, manageDsaIt));
    }

    public @NotNull Mono<BindResponse> bindSimple(@NotNull String bindDn, char[] password) {
        return lavaToMono(connection.bindSimple(bindDn, password));
    }

    public @NotNull Mono<Void> close() {
        return lavaToMono(connection.close());
    }

    public @NotNull Mono<@NotNull CompareResponse> compare(
            @NotNull CompareRequest compareRequest, boolean manageDsaIt) {
        return lavaToMono(connection.compare(compareRequest, manageDsaIt));
    }

    public LdapConnection connection() {
        return connection;
    }

    public @NotNull Mono<@NotNull DeleteResponse> delete(@NotNull DeleteRequest deleteRequest, boolean manageDsaIt) {
        return lavaToMono(connection.delete(deleteRequest, manageDsaIt));
    }

    public @NotNull Mono<Void> fastBind() {
        return lavaToMono(connection.fastBind());
    }

    private <T> @NotNull Mono<T> lavaToMono(@NotNull Lava<T> lava) {
        return LavaMono.create(ReactorContext.createTimeoutNanos(timeoutNanos), lava);
    }

    public @NotNull Mono<@NotNull ModifyResponse> modify(boolean manageDsaIt, @NotNull ModifyRequest modifyRequest) {
        return lavaToMono(connection.modify(manageDsaIt, modifyRequest));
    }

    public @NotNull Mono<@NotNull ModifyDNResponse> modifyDN(
            boolean manageDsaIt, @NotNull ModifyDNRequest modifyDNRequest) {
        return lavaToMono(connection.modifyDN(manageDsaIt, modifyDNRequest));
    }

    public @NotNull Mono<@NotNull List<@NotNull SearchResult>> search(
            boolean manageDsaIt, @NotNull SearchRequest searchRequest) {
        return lavaToMono(connection.search(manageDsaIt, searchRequest));
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
}
