package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.LdapMessage;
import hu.gds.ldap4j.ldap.Message;
import hu.gds.ldap4j.ldap.MessageReader;
import hu.gds.ldap4j.ldap.Request;
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

    public void close(long endNanos) throws Throwable {
        trampoline.contextEndNanos(endNanos)
                .get(false, true, connection.close());
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

    public boolean isOpenAndNotFailed(long endNanos) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.isOpenAndNotFailed());
    }

    public <T> @NotNull LdapMessage<T> readMessageChecked(
            long endNanos, int messageId, @NotNull MessageReader<T> messageReader) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(
                        true,
                        true,
                        connection.readMessageChecked(messageId, messageReader));
    }

    public @NotNull List<@NotNull SearchResult> search(
            long endNanos, @NotNull ControlsMessage<SearchRequest> request) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.search(request));
    }

    public void startTls(long endNanos, @NotNull TlsSettings.Tls tls) throws Throwable {
        trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.startTls(tls));
    }

    public <M extends Message<M>> int writeMessage(
            long endNanos, @NotNull ControlsMessage<M> message) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.writeMessage(message));
    }

    public <M extends Request<M, R>, R> @NotNull ControlsMessage<R> writeRequestReadResponseChecked(
            long endNanos, @NotNull ControlsMessage<M> request) throws Throwable {
        return trampoline.contextEndNanos(endNanos)
                .get(true, true, connection.writeRequestReadResponseChecked(request));
    }
}
