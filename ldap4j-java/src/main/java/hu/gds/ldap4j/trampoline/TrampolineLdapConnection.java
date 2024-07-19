package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.AddRequest;
import hu.gds.ldap4j.ldap.AddResponse;
import hu.gds.ldap4j.ldap.BindRequest;
import hu.gds.ldap4j.ldap.BindResponse;
import hu.gds.ldap4j.ldap.CompareRequest;
import hu.gds.ldap4j.ldap.CompareResponse;
import hu.gds.ldap4j.ldap.DeleteRequest;
import hu.gds.ldap4j.ldap.DeleteResponse;
import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.ExtendedResponse;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.ModifyDNRequest;
import hu.gds.ldap4j.ldap.ModifyDNResponse;
import hu.gds.ldap4j.ldap.ModifyRequest;
import hu.gds.ldap4j.ldap.ModifyResponse;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.JavaAsyncChannelConnection;
import hu.gds.ldap4j.net.JavaChannelPollConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record TrampolineLdapConnection(
        @NotNull LdapConnection connection,
        @NotNull Trampoline trampoline) {
    public TrampolineLdapConnection(
            @NotNull LdapConnection connection, @NotNull Trampoline trampoline) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.trampoline=Objects.requireNonNull(trampoline, "trampoline");
    }

    public @NotNull AddResponse delete(
            @NotNull AddRequest addRequest, long endNanos, boolean manageDsaIt) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.add(addRequest, manageDsaIt));
    }

    public @NotNull BindResponse bind(@NotNull BindRequest bindRequest, long endNanos) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.bind(bindRequest));
    }

    public void bindSimple(long endNanos, String name, char[] password) throws Throwable {
        trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.bindSimple(name, password));
    }

    public @NotNull ExtendedResponse cancel(long endNanos, int messageId, boolean signKludge) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(false, true, connection.cancel(messageId, signKludge));
    }

    public void close(long endNanos) throws Throwable {
        trampoline.contextEndNanos(endNanos)
                .get(false, true, connection.close());
    }

    public @NotNull CompareResponse compare(
            @NotNull CompareRequest compareRequest, long endNanos, boolean manageDsaIt) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.compare(compareRequest, manageDsaIt));
    }

    public static @NotNull TrampolineLdapConnection create(
            long endNanos,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull Log log,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) throws Throwable {
        boolean error=true;
        Trampoline trampoline=new Trampoline(log);
        try {
            TrampolineLdapConnection connection=trampoline.contextEndNanos(endNanos)
                    .get(
                            true,
                            true,
                            Closeable.wrapOrClose(
                                    LdapConnection::close,
                                    ()->LdapConnection.factory(
                                            factory,
                                            remoteAddress,
                                            tlsSettings),
                                    (LdapConnection connection2)->Lava.complete(
                                            new TrampolineLdapConnection(
                                                    connection2, trampoline))));
            error=false;
            return connection;
        }
        finally {
            if (error) {
                trampoline.close();
            }
        }
    }

    public static @NotNull TrampolineLdapConnection createJavaAsync(
            @Nullable AsynchronousChannelGroup asynchronousChannelGroup, long endNanos, @NotNull Log log,
            @NotNull InetSocketAddress remoteAddress, @NotNull TlsSettings tlsSettings) throws Throwable {
        return create(
                endNanos,
                JavaAsyncChannelConnection.factory(asynchronousChannelGroup, Map.of()),
                log,
                remoteAddress,
                tlsSettings);
    }

    public static @NotNull TrampolineLdapConnection createJavaPoll(
            long endNanos, @NotNull Log log, @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) throws Throwable {
        return create(
                endNanos,
                JavaChannelPollConnection.factory(Map.of()),
                log,
                remoteAddress,
                tlsSettings);
    }

    public @NotNull DeleteResponse delete(
            @NotNull DeleteRequest deleteRequest, long endNanos, boolean manageDsaIt) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.delete(deleteRequest, manageDsaIt));
    }

    public @NotNull ExtendedResponse extended(
            long endNanos, @NotNull ExtendedRequest extendedRequest) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.extended(extendedRequest));
    }

    public void fastBind(long endNanos) throws Throwable {
        trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.fastBind());
    }

    public @NotNull ModifyResponse modify(
            long endNanos, boolean manageDsaIt, @NotNull ModifyRequest modifyRequest) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.modify(manageDsaIt, modifyRequest));
    }

    public @NotNull ModifyDNResponse modifyDN(
            long endNanos, boolean manageDsaIt, @NotNull ModifyDNRequest modifyDNRequest) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.modifyDN(manageDsaIt, modifyDNRequest));
    }

    public @NotNull List<@NotNull SearchResult> search(
            long endNanos, boolean manageDsaIt, @NotNull SearchRequest searchRequest) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.search(manageDsaIt, searchRequest));
    }
}
