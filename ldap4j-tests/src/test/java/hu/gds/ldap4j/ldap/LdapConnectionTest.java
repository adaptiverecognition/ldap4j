package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.JavaAsyncChannelConnection;
import hu.gds.ldap4j.net.TlsConnection;
import hu.gds.ldap4j.net.TlsHandshakeRestartNeededException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LdapConnectionTest {
    public static @NotNull Stream<@NotNull Integer> interestingIntegers() {
        List<Integer> limits=new ArrayList<>();
        limits.add(0);
        limits.add(1);
        limits.add(10);
        for (int bits: new int[]{7, 8, 15, 16, 23, 24}) {
            limits.add((1<<bits)-1);
            limits.add((1<<bits));
        }
        limits.add((1<<31)-1);
        return limits.stream();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testBindSuccess(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: UnboundidDirectoryServer.allBinds()) {
                context.<Void>get(
                        Closeable.withCloseable(
                                ()->context.parameters().connectionFactory(context, ldapServer, bind),
                                (connection)->Lava.VOID));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testMessageId(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context, ldapServer, UnboundidDirectoryServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(
                                        @NotNull LdapConnection connection) throws Throwable {
                                    return loop(
                                            connection,
                                            interestingIntegers()
                                                    .filter((value)->0<value)
                                                    .iterator());
                                }

                                private @NotNull Lava<Void> loop(
                                        @NotNull LdapConnection connection,
                                        @NotNull Iterator<@NotNull Integer> iterator) throws Throwable {
                                    if (!iterator.hasNext()) {
                                        return Lava.VOID;
                                    }
                                    int messageId=iterator.next();
                                    return connection.search(
                                                    MessageIdGenerator.constant(messageId),
                                                    new SearchRequest(
                                                            List.of(),
                                                            "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            10,
                                                            1,
                                                            true)
                                                            .controlsEmpty())
                                            .compose((results)->{
                                                assertEquals(2, results.size());
                                                assertTrue(results.get(0).message().isEntry());
                                                assertEquals(
                                                        "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                        results.get(0).message().asEntry().objectName());
                                                assertTrue(results.get(1).message().isDone());
                                                return loop(connection, iterator);
                                            });
                                }
                            }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testParallelSearch(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            int size=10;
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context, ldapServer, UnboundidDirectoryServer.adminBind()),
                            new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                                private final int[] counts=new int[size];

                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    return startSearches(connection, 0)
                                            .composeIgnoreResult(()->readResults(connection))
                                            .composeIgnoreResult(()->{
                                                for (int ii=size-1; 0<=ii; --ii) {
                                                    assertEquals(3, counts[ii]);
                                                }
                                                return Lava.VOID;
                                            });
                                }

                                private @NotNull Lava<Void> readResults(@NotNull LdapConnection connection) {
                                    return Lava.checkEndNanos("readResults")
                                            .composeIgnoreResult(()->{
                                                @NotNull Map<
                                                        @NotNull Integer,
                                                        @NotNull ParallelMessageReader<
                                                                ?,
                                                                @NotNull LdapMessage<SearchResult>>> readers
                                                        =new HashMap<>();
                                                for (int ii=size-1; 0<=ii; --ii) {
                                                    if (3>counts[ii]) {
                                                        readers.put(
                                                                ii+1,
                                                                SearchResult.READER.parallel(Function::identity));
                                                    }
                                                }
                                                if (readers.isEmpty()) {
                                                    return Lava.VOID;
                                                }
                                                return connection.readMessageCheckedParallel(readers::get)
                                                        .compose((searchResult)->{
                                                            int index=searchResult.messageId()-1;
                                                            ++counts[index];
                                                            assertTrue(3>=counts[index]);
                                                            if (3>counts[index]) {
                                                                assertTrue(searchResult.message().isEntry());
                                                            }
                                                            else {
                                                                assertTrue(searchResult.message().isDone());
                                                            }
                                                            return readResults(connection);
                                                        });
                                            });
                                }

                                private @NotNull Lava<Void> startSearches(
                                        @NotNull LdapConnection connection, int index) {
                                    return Lava.checkEndNanos("startSearches")
                                            .composeIgnoreResult(()->{
                                                if (size<=index) {
                                                    return Lava.VOID;
                                                }
                                                return connection.writeMessage(
                                                                new SearchRequest(
                                                                        List.of(),
                                                                        "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                                        DerefAliases.NEVER_DEREF_ALIASES,
                                                                        Filter.parse("(objectClass=person)"),
                                                                        Scope.WHOLE_SUBTREE,
                                                                        100,
                                                                        10,
                                                                        true)
                                                                        .controlsEmpty(),
                                                                MessageIdGenerator.constant(index+1))
                                                        .composeIgnoreResult(
                                                                ()->startSearches(connection, index+1));
                                            });
                                }
                            }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchSuccess(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (String user: UnboundidDirectoryServer.USERS.keySet()) {
                List<ControlsMessage<SearchResult>> results=context.get(
                        Closeable.withCloseable(
                                ()->context.parameters().connectionFactory(
                                        context,
                                        ldapServer,
                                        UnboundidDirectoryServer.adminBind()),
                                (connection)->connection.search(
                                        new SearchRequest(
                                                List.of("cn", "objectClass"),
                                                "ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                DerefAliases.DEREF_ALWAYS,
                                                Filter.parse("(&(objectClass=*)(member=%s))".formatted(user)),
                                                Scope.WHOLE_SUBTREE,
                                                100,
                                                10,
                                                false)
                                                .controlsEmpty())));
                assertTrue(results.get(results.size()-1).message().isDone());
                assertSame(results.get(results.size()-1).message(), results.get(results.size()-1).message().asDone());
                results=new ArrayList<>(results);
                results.remove(results.size()-1);
                results.sort(Comparator.comparing((result)->result.message().asEntry().objectName()));
                List<String> groups=new ArrayList<>();
                UnboundidDirectoryServer.GROUPS.forEach((group, users)->{
                    if (users.contains(user)) {
                        groups.add(group);
                    }
                });
                groups.sort(null);
                assertEquals(groups.size(), results.size(), results.toString());
                for (int ii=0; groups.size()>ii; ++ii) {
                    String group=groups.get(ii);
                    String cn=group.substring(group.indexOf('=')+1, group.indexOf(','));
                    assertEquals(
                            new ControlsMessage<>(
                                    List.of(),
                                    new SearchResult.Entry(
                                            List.of(
                                                    new PartialAttribute(
                                                            "objectClass",
                                                            List.of("top", "groupOfNames")),
                                                    new PartialAttribute("cn", List.of(cn))),
                                            group)),
                            results.get(ii));
                }
            }
        }
    }

    private void testTlsRenegotiation(
            boolean explicitTlsRenegotiation, LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters)) {
            class Server {
                private final @NotNull LdapConnection ldapConnection;
                private final @NotNull TlsConnection tlsConnection;

                public Server(@NotNull DuplexConnection connection) {
                    tlsConnection=new TlsConnection(connection);
                    ldapConnection=new LdapConnection(
                            tlsConnection,
                            LdapTestParameters.Tls.TLS.equals(context.parameters().tls),
                            MessageIdGenerator.smallValues());
                }

                private @NotNull Lava<Void> readStartTls() {
                    return Lava.supplier(()->switch (context.parameters().tls) {
                        case CLEAR_TEXT -> throw new IllegalStateException();
                        case START_TLS -> ldapConnection.readMessageCheckedParallel(
                                        (messageId)->{
                                            if (0>=messageId) {
                                                return null;
                                            }
                                            return new ExtendedRequest.Reader(
                                                    MessageReader.fail(()->{
                                                        throw new UnsupportedOperationException();
                                                    }))
                                                    .parallel(Function::identity);
                                        })
                                .compose((request)->{
                                    assertTrue(request.controls().isEmpty());
                                    assertEquals(
                                            Ldap.EXTENDED_REQUEST_START_TLS_OID,
                                            request.message().requestName());
                                    assertNull(request.message().requestValue());
                                    return ldapConnection.writeMessage(
                                            new ExtendedResponse(
                                                    new LdapResult(
                                                            "",
                                                            "",
                                                            List.of(),
                                                            LdapResultCode.SUCCESS.code,
                                                            LdapResultCode.SUCCESS),
                                                    null,
                                                    null)
                                                    .controlsEmpty(),
                                            MessageIdGenerator.constant(request.messageId()));
                                })
                                .composeIgnoreResult(()->Lava.VOID);
                        case TLS -> Lava.VOID;
                    });
                }

                public @NotNull Lava<Void> run() {
                    return readStartTls()
                            .composeIgnoreResult(()->tlsConnection.startTlsHandshake(
                                    null,
                                    UnboundidDirectoryServer.serverTls(false)))
                            .composeIgnoreResult(()->ldapConnection.readMessageCheckedParallel((messageId)->{
                                if (0>=messageId) {
                                    return null;
                                }
                                return new ExtendedRequest.Reader(
                                        MessageReader.fail(()->{
                                            throw new UnsupportedOperationException();
                                        }))
                                        .parallel(Function::identity);
                            }))
                            .compose((request)->{
                                assertEquals(Ldap.EXTENDED_REQUEST_CANCEL_OP_OID, request.message().requestName());
                                return ldapConnection.restartTlsHandshake()
                                        .composeIgnoreResult(()->ldapConnection.writeMessage(
                                                new ExtendedResponse(
                                                        new LdapResult(
                                                                "",
                                                                "",
                                                                List.of(),
                                                                LdapResultCode.SUCCESS.code,
                                                                LdapResultCode.SUCCESS),
                                                        Ldap.EXTENDED_REQUEST_FAST_BIND_OID,
                                                        null)
                                                        .controlsEmpty(),
                                                MessageIdGenerator.constant(request.messageId())));
                            })
                            .composeIgnoreResult(()->ldapConnection.readMessageCheckedParallel(
                                    (messageId)->{
                                        if (0>=messageId) {
                                            return null;
                                        }
                                        return UnbindRequest.READER.parallel(Function::identity);
                                    }))
                            .composeIgnoreResult(tlsConnection::close);
                }
            }
            class TlsRenegotiationTest {
                private final @NotNull JavaAsyncChannelConnection.Server server;

                public TlsRenegotiationTest(@NotNull JavaAsyncChannelConnection.Server server) {
                    this.server=Objects.requireNonNull(server, "server");
                }

                private @NotNull Lava<Void> accept() {
                    return Closeable.withCloseable(
                            server::acceptNotNull,
                            (connection)->new Server(connection)
                                    .run());
                }

                private @NotNull Lava<Void> client(@NotNull LdapConnection connection) {
                    return connection.writeMessage(ExtendedRequest.cancel(13).controlsEmpty())
                            .compose((messageId)->{
                                @NotNull Lava<Void> tryRead;
                                if (explicitTlsRenegotiation) {
                                    tryRead=Lava.catchErrors(
                                            (throwable)->connection.restartTlsHandshake(),
                                            ()->connection.readMessageChecked(
                                                            messageId,
                                                            ExtendedResponse.READER_SUCCESS)
                                                    .composeIgnoreResult(()->Lava.fail(new RuntimeException(
                                                            "should have failed"))),
                                            TlsHandshakeRestartNeededException.class);
                                }
                                else {
                                    tryRead=Lava.VOID;
                                }
                                return tryRead.composeIgnoreResult(()->connection.readMessageChecked(
                                                messageId,
                                                ExtendedResponse.READER_SUCCESS)
                                        .compose((response)->{
                                            assertEquals(
                                                    Ldap.EXTENDED_REQUEST_FAST_BIND_OID,
                                                    response.message().responseName());
                                            return Lava.VOID;
                                        }));
                            });
                }

                private @NotNull Lava<Void> connect() {
                    return server.localAddress()
                            .compose((localAddress)->Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context,
                                            explicitTlsRenegotiation,
                                            ()->localAddress,
                                            ()->localAddress,
                                            null),
                                    this::client));
                }

                public @NotNull Lava<Void> test() {
                    return Lava.forkJoin(this::accept, this::connect)
                            .composeIgnoreResult(()->Lava.VOID);
                }
            }
            context.get(Closeable.withCloseable(
                    ()->JavaAsyncChannelConnection.Server.factory(
                            null,
                            1,
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                            Map.of()),
                    (server)->new TlsRenegotiationTest(server)
                            .test()));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdapTls")
    public void testTlsRenegotiationExplicit(LdapTestParameters testParameters) throws Throwable {
        testTlsRenegotiation(true, testParameters);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdapTls")
    public void testTlsRenegotiationImplicit(LdapTestParameters testParameters) throws Throwable {
        testTlsRenegotiation(false, testParameters);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testTlsSession(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            context.<Void>get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            (connection)->connection.tlsSession()
                                    .compose((tlsSession)->{
                                        assertEquals(
                                                LdapTestParameters.Tls.CLEAR_TEXT.equals(testParameters.tls),
                                                null==tlsSession);
                                        return Lava.VOID;
                                    })));
        }
    }
}
