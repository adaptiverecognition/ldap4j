package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.ClosedException;
import hu.gds.ldap4j.net.Connection;
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.TlsConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.io.EOFException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LdapConnection implements Connection {
    private @Nullable TlsConnection connection;
    private boolean failed;
    private final boolean ldaps;
    private final Object lock=new Object();
    private final @NotNull MessageIdGenerator messageIdGenerator;
    private ByteBuffer readBuffer=ByteBuffer.EMPTY;
    private boolean usingTls;

    private LdapConnection(
            @NotNull TlsConnection connection, boolean ldaps, @NotNull MessageIdGenerator messageIdGenerator) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.ldaps=ldaps;
        this.messageIdGenerator=Objects.requireNonNull(messageIdGenerator, "messageIdGenerator");
        usingTls=ldaps;
    }

    public @NotNull Lava<Void> abandon(@NotNull AbandonRequest abandonRequest) {
        Objects.requireNonNull(abandonRequest, "abandonRequest");
        return Lava.supplier(()->writeMessage(
                connection(),
                List.of(),
                AbandonRequest::write,
                abandonRequest)
                .composeIgnoreResult(()->Lava.VOID));
    }

    public @NotNull Lava<@NotNull AddResponse> add(@NotNull AddRequest addRequest, boolean manageDsaIt) {
        Objects.requireNonNull(addRequest, "addRequest");
        return Lava.supplier(()->writeMessage(
                connection(),
                manageDsaIt
                        ?List.of(Control.nonCritical(Ldap.MANAGE_DSA_IT_OID))
                        :List.of(),
                AddRequest::write,
                addRequest)
                .compose((messageId)->readLdapMessage(AddResponse::read, messageId))
                .compose((addResponse)->{
                    addResponse.message().ldapResult().check();
                    return Lava.complete(addResponse.message());
                }));
    }

    /**
     * @return a bind response with success result code
     */
    public @NotNull Lava<@NotNull BindResponse> bindSimple(@NotNull String bindDn, char[] password) {
        Objects.requireNonNull(bindDn, "bindDn");
        Objects.requireNonNull(password, "password");
        return Lava.supplier(()->writeMessage(
                connection(),
                List.of(),
                SimpleBindRequest::write,
                new SimpleBindRequest(bindDn, password, Ldap.VERSION))
                .compose((messageId)->readLdapMessage(BindResponse::read, messageId))
                .compose((bindResponse)->{
                    bindResponse.message().ldapResult().check();
                    return Lava.complete(bindResponse.message());
                }));
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.supplier(()->{
            TlsConnection connection2;
            boolean failed2;
            synchronized (lock) {
                connection2=connection;
                connection=null;
                failed2=failed;
                if (null==connection2) {
                    return Lava.VOID;
                }
            }
            return connection2.finallyClose(
                    ()->{
                        if (failed2) {
                            return Lava.VOID;
                        }
                        return connection2.isOpenAndNotFailed()
                                .compose((openAndNotFailed)->{
                                    if (!openAndNotFailed) {
                                        return Lava.VOID;
                                    }
                                    return Lava.catchErrors(
                                            (throwable)->{
                                                if (Exceptions.isConnectionClosedException(throwable)) {
                                                    return Lava.VOID;
                                                }
                                                return Lava.fail(throwable);
                                            },
                                            ()->writeMessage(
                                                    connection2,
                                                    List.of(),
                                                    UnbindRequest::write,
                                                    new UnbindRequest())
                                                    .composeIgnoreResult(connection2::shutDownOutputSafe),
                                            Throwable.class);
                                });
                    });
        });
    }

    public @NotNull Lava<@NotNull CompareResponse> compare(
            @NotNull CompareRequest compareRequest, boolean manageDsaIt) {
        Objects.requireNonNull(compareRequest, "compareRequest");
        return Lava.supplier(()->writeMessage(
                connection(),
                manageDsaIt
                        ?List.of(Control.nonCritical(Ldap.MANAGE_DSA_IT_OID))
                        :List.of(),
                CompareRequest::write,
                compareRequest)
                .compose((messageId)->readLdapMessage(CompareResponse::read, messageId))
                .compose((CompareResponse)->{
                    CompareResponse.message().ldapResult().checkCompare();
                    return Lava.complete(CompareResponse.message());
                }));
    }

    private @NotNull TlsConnection connection() {
        @Nullable TlsConnection connection2;
        synchronized (lock) {
            connection2=connection;
            if (null==connection2) {
                throw new ClosedException();
            }
            if (failed) {
                throw new RuntimeException("ldap connection failed");
            }
        }
        return connection2;
    }

    public @NotNull Lava<@NotNull DeleteResponse> delete(@NotNull DeleteRequest deleteRequest, boolean manageDsaIt) {
        Objects.requireNonNull(deleteRequest, "deleteRequest");
        return Lava.supplier(()->writeMessage(
                connection(),
                manageDsaIt
                        ?List.of(Control.nonCritical(Ldap.MANAGE_DSA_IT_OID))
                        :List.of(),
                DeleteRequest::write,
                deleteRequest)
                .compose((messageId)->readLdapMessage(DeleteResponse::read, messageId))
                .compose((deleteResponse)->{
                    deleteResponse.message().ldapResult().check();
                    return Lava.complete(deleteResponse.message());
                }));
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                factory,
                MessageIdGenerator.smallValues(true),
                remoteAddress,
                tlsSettings);
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(messageIdGenerator, "messageIdGenerator");
        Objects.requireNonNull(tlsSettings, "tlsSettings");
        return Closeable.wrapOrClose(
                ()->TlsConnection.factory(factory).apply(remoteAddress),
                (connection)->{
                    if (tlsSettings.isTls()) {
                        if (tlsSettings.isStarttls()) {
                            return Closeable.wrapOrClose(
                                    ()->Lava.complete(new LdapConnection(connection, false, messageIdGenerator)),
                                    (connection2)->connection2.startTls(tlsSettings.asTls())
                                            .composeIgnoreResult(()->Lava.complete(connection2)));
                        }
                        else {
                            return connection.startTls(tlsSettings.asTls())
                                    .composeIgnoreResult(()->Lava.complete(
                                            new LdapConnection(connection, true, messageIdGenerator)));
                        }
                    }
                    else {
                        return Lava.complete(new LdapConnection(connection, false, messageIdGenerator));
                    }
                });
    }

    public @NotNull Lava<Void> fastBind() {
        return Lava.supplier(()->writeMessage(
                connection(),
                List.of(),
                ExtendedRequest::write,
                new ExtendedRequest(Ldap.FAST_BIND_OID, null))
                .compose((messageId)->readLdapMessage(ExtendedResponse::read, messageId))
                .compose((extendedResponse)->{
                    extendedResponse.message().ldapResult().check();
                    return Lava.VOID;
                }));
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->{
            @Nullable TlsConnection connection2;
            synchronized (lock) {
                connection2=connection;
                if (null==connection2) {
                    return Lava.complete(false);
                }
                if (failed) {
                    return Lava.complete(false);
                }
            }
            return connection2.isOpenAndNotFailed();
        });
    }

    public @NotNull Lava<Void> invalidRequestResponse() {
        return Lava.supplier(()->this.writeMessage(
                        connection(),
                        List.of(),
                        (object)->ByteBuffer.create((byte)0, (byte)1, (byte)0),
                        new Object())
                .compose((messageId)->readLdapMessage(ExtendedResponse::read, messageId))
                .compose((response)->Lava.fail(new IllegalStateException(
                        "should have failed, response %s".formatted(response)))));
    }

    public @NotNull MessageIdGenerator messageIdGenerator() {
        return messageIdGenerator;
    }

    public @NotNull Lava<@NotNull ModifyResponse> modify(boolean manageDsaIt, @NotNull ModifyRequest modifyRequest) {
        Objects.requireNonNull(modifyRequest, "modifyRequest");
        return Lava.supplier(()->writeMessage(
                connection(),
                manageDsaIt
                        ?List.of(Control.nonCritical(Ldap.MANAGE_DSA_IT_OID))
                        :List.of(),
                ModifyRequest::write,
                modifyRequest)
                .compose((messageId)->readLdapMessage(ModifyResponse::read, messageId))
                .compose((modifyResponse)->{
                    modifyResponse.message().ldapResult().check();
                    return Lava.complete(modifyResponse.message());
                }));
    }

    public @NotNull Lava<@NotNull ModifyDNResponse> modifyDN(
            boolean manageDsaIt, @NotNull ModifyDNRequest modifyDNRequest) {
        Objects.requireNonNull(modifyDNRequest, "modifyDNRequest");
        return Lava.supplier(()->writeMessage(
                connection(),
                manageDsaIt
                        ?List.of(Control.nonCritical(Ldap.MANAGE_DSA_IT_OID))
                        :List.of(),
                ModifyDNRequest::write,
                modifyDNRequest)
                .compose((messageId)->readLdapMessage(ModifyDNResponse::read, messageId))
                .compose((modifyDNResponse)->{
                    modifyDNResponse.message().ldapResult().check();
                    return Lava.complete(modifyDNResponse.message());
                }));
    }

    private <T> @NotNull Lava<@NotNull LdapMessage<T>> readLdapMessage(
            @NotNull Function<ByteBuffer.Reader, T> function, int messageId) {
        return Lava.catchErrors(
                (throwable)->{
                    if ((!(throwable instanceof EOFException))
                            && (!(throwable instanceof LdapException))
                            && (!(throwable instanceof TimeoutException))) {
                        synchronized (lock) {
                            failed=true;
                        }
                    }
                    return Lava.fail(throwable);
                },
                ()->readMessage(LdapMessage.read(function, messageId)),
                Throwable.class);
    }

    private <T> @NotNull Lava<T> readMessage(@NotNull Function<ByteBuffer.Reader, T> function) {
        return Lava.checkEndNanos(LdapConnection.class+"readMessage() timeout")
                .composeIgnoreResult(()->{
                    ByteBuffer.Reader reader=readBuffer.reader();
                    T value;
                    try {
                        value=function.apply(reader);
                    }
                    catch (EOFException ex) {
                        return connection().read()
                                .compose((readResult)->{
                                    if (null==readResult) {
                                        throw new EOFException();
                                    }
                                    else {
                                        readBuffer=readBuffer.append(readResult);
                                        return readMessage(function);
                                    }
                                });
                    }
                    catch (ExtendedLdapException ex) {
                        throw ex;
                    }
                    catch (Throwable throwable) {
                        try (StringWriter writer=new StringWriter();
                             PrintWriter printWriter=new PrintWriter(writer)) {
                            printWriter.printf(
                                    "error reading message, size: %,d bytes, %s%n", readBuffer.size(), throwable);
                            DERDump.hexDump(
                                    readBuffer,
                                    "        ",
                                    32,
                                    80,
                                    8,
                                    true,
                                    true,
                                    printWriter);
                            throw new RuntimeException(writer.toString(), throwable);
                        }
                    }
                    readBuffer=reader.readReaminingByteBuffer();
                    return Lava.complete(value);
                });
    }

    public @NotNull Lava<@NotNull List<@NotNull SearchResult>> search(
            boolean manageDsaIt, @NotNull SearchRequest searchRequest) {
        return search(manageDsaIt, messageIdGenerator, searchRequest);
    }

    public @NotNull Lava<@NotNull List<@NotNull SearchResult>> search(
            boolean manageDsaIt,
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull SearchRequest searchRequest) {
        return searchRequest(manageDsaIt, messageIdGenerator, searchRequest)
                .compose((messageId)->search(messageId, new ArrayList<>()));
    }

    private @NotNull Lava<@NotNull List<@NotNull SearchResult>> search(
            int messageId, @NotNull List<@NotNull SearchResult> result) {
        return searchResult(messageId)
                .compose((searchResult)->{
                    result.add(searchResult);
                    if (searchResult.isDone()) {
                        return Lava.complete(result);
                    }
                    else {
                        return search(messageId, result);
                    }
                });
    }

    /**
     * @return messageId
     */
    public @NotNull Lava<Integer> searchRequest(boolean manageDsaIt, @NotNull SearchRequest searchRequest) {
        return searchRequest(manageDsaIt, messageIdGenerator, searchRequest);
    }

    /**
     * @return messageId
     */
    public @NotNull Lava<Integer> searchRequest(
            boolean manageDsaIt,
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull SearchRequest searchRequest) {
        return Lava.supplier(()->writeMessage(
                connection(),
                manageDsaIt
                        ?List.of(Control.nonCritical(Ldap.MANAGE_DSA_IT_OID))
                        :List.of(),
                SearchRequest::write,
                searchRequest,
                messageIdGenerator));
    }

    public @NotNull Lava<@NotNull SearchResult> searchResult(int messageId) {
        return readLdapMessage(SearchResult::read, messageId)
                .compose((searchResult)->{
                    searchResult.message().check();
                    return Lava.complete(searchResult.message());
                });
    }

    public @NotNull Lava<Void> startTls(@NotNull TlsSettings.Tls tls) {
        return Lava.supplier(()->{
            Objects.requireNonNull(tls, "tls");
            if (ldaps) {
                throw new RuntimeException("cannot start tls on ldaps");
            }
            if (usingTls) {
                throw new RuntimeException("already using tls");
            }
            usingTls=true;
            return writeMessage(
                    connection(),
                    List.of(),
                    ExtendedRequest::write,
                    new ExtendedRequest(Ldap.START_TLS_OID, null))
                    .compose((messageId)->readLdapMessage(ExtendedResponse::read, messageId))
                    .compose((response)->{
                        response.message().ldapResult().check();
                        return connection().startTls(tls);
                    });
        });
    }

    /**
     * @return messageId
     */
    private <T> @NotNull Lava<@NotNull Integer> writeMessage(
            @NotNull TlsConnection connection, @NotNull List<@NotNull Control> controls,
            @NotNull Function<T, @NotNull ByteBuffer> function, T message) throws Throwable {
        return writeMessage(connection, controls, function, message, messageIdGenerator);
    }

    /**
     * @return messageId
     */
    private <T> @NotNull Lava<@NotNull Integer> writeMessage(
            @NotNull TlsConnection connection, @NotNull List<@NotNull Control> controls,
            @NotNull Function<T, @NotNull ByteBuffer> function, T message,
            @NotNull MessageIdGenerator messageIdGenerator) throws Throwable {
        int messageId=messageIdGenerator.next();
        ByteBuffer byteBuffer=new LdapMessage<>(controls, message, messageId, messageIdGenerator.signKludge())
                .write(function);
        return Lava.catchErrors(
                        (throwable)->{
                            synchronized (lock) {
                                failed=true;
                            }
                            return Lava.fail(throwable);
                        },
                        ()->connection.write(byteBuffer),
                        Throwable.class)
                .composeIgnoreResult(()->Lava.complete(messageId));
    }
}
