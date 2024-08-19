package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.ThreadPoolContextHolder;
import hu.gds.ldap4j.ldap.extension.AllOperationAttributes;
import hu.gds.ldap4j.ldap.extension.FeatureDiscovery;
import hu.gds.ldap4j.ldap.extension.ManageDsaIt;
import hu.gds.ldap4j.ldap.extension.ModifyIncrement;
import hu.gds.ldap4j.ldap.extension.PasswordModify;
import hu.gds.ldap4j.ldap.extension.ServerSideSorting;
import hu.gds.ldap4j.ldap.extension.SimplePagedResults;
import hu.gds.ldap4j.ldap.extension.WhoAmI;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.NetworkConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.stream.Streams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class UnboundidDSTest {
    private static class InvalidMessage implements Message<InvalidMessage> {
        @Override
        public @NotNull InvalidMessage self() {
            return this;
        }

        @Override
        public @NotNull ByteBuffer write() {
            return ByteBuffer.create((byte)0, (byte)1, (byte)0);
        }
    }

    private static final @NotNull LdapTestParameters TEST_PARAMETERS=new LdapTestParameters(
            ThreadPoolContextHolder.factory(AbstractTest.PARALLELISM, null, true),
            ThreadPoolContextHolder.factory(AbstractTest.PARALLELISM, null, true),
            NetworkConnectionFactory.javaAsyncChannel(),
            AbstractTest.TIMEOUT_NANOS,
            AbstractTest.SERVER_PORT_CLEAR_TEXT,
            AbstractTest.SERVER_PORT_TLS,
            LdapTestParameters.Tls.START_TLS);

    @Test
    public void testAbsoluteTrueAndFalseFilters() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.<Void>get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                                    return search(connection, false)
                                            .composeIgnoreResult(()->search(connection, true));
                                }

                                private @NotNull Lava<Void> search(
                                        @NotNull LdapConnection connection, boolean enabled) throws Throwable {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(),
                                                            "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.DEREF_ALWAYS,
                                                            Filter.parse(enabled?"(&)":"(|)"),
                                                            Scope.BASE_OBJECT,
                                                            100,
                                                            10,
                                                            true)
                                                            .controlsEmpty())
                                            .compose((results)->{
                                                assertEquals(
                                                        enabled?2:1,
                                                        results.size());
                                                return Lava.VOID;
                                            });
                                }
                            }));
        }
    }

    @Test
    public void testAddDelete() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String name="group8";
            String object="cn=%s,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu".formatted(name);
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user1="uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                private @NotNull Lava<Void> assertMembers(
                                        @NotNull LdapConnection connection, String... members) throws Throwable {
                                    return members(connection)
                                            .compose((members2)->{
                                                assertEquals(List.of(members), members2);
                                                return Lava.VOID;
                                            });
                                }

                                private @NotNull Lava<Void> assertNoSuchObject(@NotNull LdapConnection connection) {
                                    return Lava.catchErrors(
                                            (exception)->{
                                                assertEquals(LdapResultCode.NO_SUCH_OBJECT, exception.resultCode2);
                                                return Lava.VOID;
                                            },
                                            ()->members(connection)
                                                    .composeIgnoreResult(()->{
                                                        fail("should have failed");
                                                        return Lava.VOID;
                                                    }),
                                            LdapException.class);
                                }

                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    return assertNoSuchObject(connection)
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new AddRequest(
                                                            List.of(
                                                                    new PartialAttribute(
                                                                            "objectClass",
                                                                            List.of("top", "groupOfNames")),
                                                                    new PartialAttribute(
                                                                            "cn",
                                                                            List.of(name)),
                                                                    new PartialAttribute(
                                                                            attribute,
                                                                            List.of(user0, user1))),
                                                            object)
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertMembers(connection, user0, user1))
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new DeleteRequest(object)
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertNoSuchObject(connection));
                                }

                                private @NotNull Lava<@NotNull List<@NotNull String>> members(
                                        @NotNull LdapConnection connection) throws Throwable {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(attribute),
                                                            object,
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            10,
                                                            10,
                                                            false)
                                                            .controlsEmpty())
                                            .compose((searchResults)->{
                                                List<String> members=new ArrayList<>(
                                                        searchResults.stream()
                                                                .map(ControlsMessage::message)
                                                                .filter(SearchResult::isEntry)
                                                                .map(SearchResult::asEntry)
                                                                .flatMap((entry)->entry.attributes().stream())
                                                                .filter((attribute2)->attribute.equals(attribute2.type()))
                                                                .flatMap((attribute2)->attribute2.values().stream())
                                                                .toList());
                                                members.sort(null);
                                                return Lava.complete(members);
                                            });
                                }
                            }));

        }
    }

    @Test
    public void testBindIncorrectPassword() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: UnboundidDirectoryServer.allBinds()) {
                context.<Void>get(
                        Lava.catchErrors(
                                (ldapException)->{
                                    if (!LdapResultCode.INVALID_CREDENTIALS.equals(ldapException.resultCode2)) {
                                        throw ldapException;
                                    }
                                    return Lava.VOID;
                                },
                                ()->Closeable.withCloseable(
                                        ()->context.parameters().connectionFactory(
                                                context,
                                                ldapServer,
                                                Pair.of(bind.first(), bind.second()+"x")),
                                        (connection)->Lava.VOID),
                                LdapException.class));
            }
        }
    }

    @Test
    public void testBindIncorrectUser() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: UnboundidDirectoryServer.allBinds()) {
                context.<Void>get(
                        Lava.catchErrors(
                                (ldapException)->{
                                    if (!LdapResultCode.INVALID_CREDENTIALS.equals(ldapException.resultCode2)) {
                                        throw ldapException;
                                    }
                                    return Lava.VOID;
                                },
                                ()->Closeable.withCloseable(
                                        ()->context.parameters().connectionFactory(
                                                context,
                                                ldapServer,
                                                Pair.of(bind.first()+"x", bind.second())),
                                        (connection)->Lava.VOID),
                                LdapException.class));
            }
        }
    }

    @Test
    public void testBindSASL() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            var bind=UnboundidDirectoryServer.allBinds().iterator().next();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            (connection)->connection.writeRequestReadResponseChecked(
                                            BindRequest.sasl(
                                                            "dn: %s\0dn: %s\0%s".formatted(
                                                                            bind.first(),
                                                                            bind.first(),
                                                                            bind.second())
                                                                    .getBytes(StandardCharsets.UTF_8),
                                                            "PLAIN",
                                                            "")
                                                    .controlsEmpty())
                                    .compose((bindResponse)->{
                                        assertEquals(
                                                LdapResultCode.SUCCESS,
                                                bindResponse.message().ldapResult().resultCode2());
                                        byte[] challengeResponse="dn: %s\0dn: %s\0%s".formatted(
                                                        bind.first(),
                                                        bind.first(),
                                                        bind.second()+"x")
                                                .getBytes(StandardCharsets.UTF_8);
                                        return Lava.catchErrors(
                                                (exception)->{
                                                    assertEquals(
                                                            LdapResultCode.INVALID_CREDENTIALS,
                                                            exception.resultCode2);
                                                    return Lava.VOID;
                                                },
                                                ()->connection.writeRequestReadResponseChecked(
                                                                BindRequest.sasl(
                                                                                challengeResponse,
                                                                                "PLAIN",
                                                                                "")
                                                                        .controlsEmpty())
                                                        .composeIgnoreResult(()->{
                                                            fail("should have failed");
                                                            return Lava.VOID;
                                                        }),
                                                LdapException.class);
                                    })));

        }
    }

    @Test
    public void testCompare() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String object="cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user2="uid=user2,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                private @NotNull Lava<Void> assertCompare(
                                        @NotNull LdapConnection connection, boolean result, String value) {
                                    return compare(connection, value)
                                            .compose((result2)->{
                                                assertEquals(result, result2);
                                                return Lava.VOID;
                                            });
                                }

                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    return assertCompare(connection, true, user0)
                                            .composeIgnoreResult(()->assertCompare(connection, false, user2));
                                }

                                private @NotNull Lava<@NotNull Boolean> compare(
                                        @NotNull LdapConnection connection, String value) {
                                    return connection.writeRequestReadResponseChecked(
                                                    new CompareRequest(
                                                            new Filter.EqualityMatch(value, attribute),
                                                            object)
                                                            .controlsEmpty())
                                            .compose((response)->Lava.complete(
                                                    LdapResultCode.COMPARE_TRUE.equals(
                                                            response.message().ldapResult().resultCode2())));
                                }
                            }));
        }
    }

    @Test
    public void testDERLength() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
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
                                            LdapConnectionTest.interestingIntegers()
                                                    .filter((value)->65536>=value)
                                                    .iterator());
                                }

                                private @NotNull Lava<Void> loop(
                                        @NotNull LdapConnection connection,
                                        @NotNull Iterator<@NotNull Integer> iterator) throws Throwable {
                                    if (!iterator.hasNext()) {
                                        return Lava.VOID;
                                    }
                                    int length=iterator.next();
                                    char[] chars=new char[length];
                                    Arrays.fill(chars, 'a');
                                    String string=new String(chars);
                                    return connection.search(
                                                    connection.messageIdGenerator(),
                                                    new SearchRequest(
                                                            List.of(),
                                                            "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse(
                                                                    "(|(objectClass=*)(sn=%s))".formatted(
                                                                            string)),
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

    @Test
    public void testFeatureDiscovery() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            Pair<String, String> bind=UnboundidDirectoryServer.allBinds().iterator().next();
            context.<Void>get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, bind),
                            (connection)->connection.search(FeatureDiscovery.searchRequest())
                                    .compose((results)->{
                                        FeatureDiscovery featureDiscovery=FeatureDiscovery.create(results);
                                        assertTrue(
                                                featureDiscovery.namingContexts
                                                        .contains("ou=test,dc=ldap4j,dc=gds,dc=hu"));
                                        assertEquals(Set.of("3"), featureDiscovery.supportedLdapVersions);
                                        assertTrue(featureDiscovery.supportedSaslMechanisms.contains("PLAIN"));
                                        assertTrue(featureDiscovery.supportedControls
                                                .contains(ManageDsaIt.REQUEST_CONTROL_OID));
                                        assertTrue(featureDiscovery.supportedExtensions
                                                .contains(WhoAmI.REQUEST_OID));
                                        assertTrue(featureDiscovery.supportedFeatures
                                                .contains(AllOperationAttributes.FEATURE_OID));
                                        return Lava.VOID;
                                    })));
        }
    }

    @Test
    public void testInvalidRequest() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Lava.catchErrors(
                            (extendedLdapException)->{
                                if (ExtendedResponse.NOTICE_OF_DISCONNECTION_OID.equals(
                                        extendedLdapException.response.message().responseName())) {
                                    return Lava.VOID;
                                }
                                else {
                                    throw extendedLdapException;
                                }
                            },
                            ()->Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context, ldapServer, UnboundidDirectoryServer.adminBind()),
                                    (connection)->connection.writeMessage(
                                                    new InvalidMessage()
                                                            .controlsEmpty())
                                            .compose((messageId)->connection.readMessageChecked(
                                                    messageId, ExtendedResponse.READER_SUCCESS))
                                            .compose((response)->Lava.fail(
                                                    new IllegalStateException("should have failed, response: %s"
                                                            .formatted(response))))),
                            ExtendedLdapException.class));
        }
    }

    @Test
    public void testModify() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String object="cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user1="uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user2="uid=user2,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user3="uid=user3,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                private @NotNull Lava<Void> assertMembers(
                                        @NotNull LdapConnection connection, String... members) throws Throwable {
                                    return members(connection)
                                            .compose((members2)->{
                                                assertEquals(List.of(members), members2);
                                                return Lava.VOID;
                                            });
                                }

                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                                    return assertMembers(connection, user0, user1)
                                            .composeIgnoreResult(()->modify(
                                                    connection,
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of(user2, user3)),
                                                            ModifyRequest.OPERATION_REPLACE)))
                                            .composeIgnoreResult(()->assertMembers(connection, user2, user3))
                                            .composeIgnoreResult(()->modify(
                                                    connection,
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of()),
                                                            ModifyRequest.OPERATION_DELETE)))
                                            .composeIgnoreResult(()->assertMembers(connection))
                                            .composeIgnoreResult(()->modify(
                                                    connection,
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of(user0)),
                                                            ModifyRequest.OPERATION_ADD),
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of(user1)),
                                                            ModifyRequest.OPERATION_ADD)))
                                            .composeIgnoreResult(()->assertMembers(connection, user0, user1));
                                }

                                private @NotNull Lava<@NotNull List<@NotNull String>> members(
                                        @NotNull LdapConnection connection) throws Throwable {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(attribute),
                                                            object,
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            10,
                                                            10,
                                                            false)
                                                            .controlsEmpty())
                                            .compose((searchResults)->{
                                                List<String> members=new ArrayList<>(
                                                        searchResults.stream()
                                                                .map(ControlsMessage::message)
                                                                .filter(SearchResult::isEntry)
                                                                .map(SearchResult::asEntry)
                                                                .flatMap((entry)->entry.attributes().stream())
                                                                .filter((attribute2)->attribute.equals(attribute2.type()))
                                                                .flatMap((attribute2)->attribute2.values().stream())
                                                                .toList());
                                                members.sort(null);
                                                return Lava.complete(members);
                                            });
                                }

                                private @NotNull Lava<Void> modify(
                                        @NotNull LdapConnection connection, ModifyRequest.Change... changes) {
                                    return connection.writeRequestReadResponseChecked(
                                                    new ModifyRequest(List.of(changes), object)
                                                            .controlsEmpty())
                                            .composeIgnoreResult(()->Lava.VOID);
                                }
                            }));
        }
    }

    @Test
    public void testModifyDN() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String newName="group8";
            String oldName="group0";
            String newParent="ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String oldParent="ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String newRDN="cn=%s".formatted(newName);
            String oldRDN="cn=%s".formatted(oldName);
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user1="uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                private @NotNull Lava<Void> assertMembers(
                                        @NotNull LdapConnection connection, @NotNull String object, String... members)
                                        throws Throwable {
                                    return members(connection, object)
                                            .compose((members2)->{
                                                assertEquals(List.of(members), members2);
                                                return Lava.VOID;
                                            });
                                }

                                private @NotNull Lava<Void> assertNoSuchObject(
                                        @NotNull LdapConnection connection, @NotNull String object) {
                                    return Lava.catchErrors(
                                            (exception)->{
                                                assertEquals(LdapResultCode.NO_SUCH_OBJECT, exception.resultCode2);
                                                return Lava.VOID;
                                            },
                                            ()->members(connection, object)
                                                    .composeIgnoreResult(()->{
                                                        fail("should have failed");
                                                        return Lava.VOID;
                                                    }),
                                            LdapException.class);
                                }

                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    return assertNoSuchObject(connection, "%s,%s".formatted(newRDN, newParent))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(newRDN, oldParent)))
                                            .composeIgnoreResult(()->assertMembers(
                                                    connection, "%s,%s".formatted(oldRDN, oldParent), user0, user1))
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new ModifyDNRequest(
                                                            false,
                                                            "%s,%s".formatted(oldRDN, oldParent),
                                                            newRDN,
                                                            null)
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(newRDN, newParent)))
                                            .composeIgnoreResult(()->assertMembers(
                                                    connection, "%s,%s".formatted(newRDN, oldParent), user0, user1))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(oldRDN, oldParent)))
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new ModifyDNRequest(
                                                            false,
                                                            "%s,%s".formatted(newRDN, oldParent),
                                                            newRDN,
                                                            newParent)
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertMembers(
                                                    connection, "%s,%s".formatted(newRDN, newParent), user0, user1))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(newRDN, oldParent)))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(oldRDN, oldParent)))
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new ModifyDNRequest(
                                                            true,
                                                            "%s,%s".formatted(newRDN, newParent),
                                                            oldRDN,
                                                            oldParent)
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(newRDN, newParent)))
                                            .composeIgnoreResult(()->assertNoSuchObject(
                                                    connection, "%s,%s".formatted(newRDN, oldParent)))
                                            .composeIgnoreResult(()->assertMembers(
                                                    connection, "%s,%s".formatted(oldRDN, oldParent), user0, user1));
                                }

                                private @NotNull Lava<@NotNull List<@NotNull String>> members(
                                        @NotNull LdapConnection connection, @NotNull String object) throws Throwable {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(attribute),
                                                            object,
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            10,
                                                            10,
                                                            false)
                                                            .controlsEmpty())
                                            .compose((searchResults)->{
                                                List<String> members=new ArrayList<>(
                                                        searchResults.stream()
                                                                .map(ControlsMessage::message)
                                                                .filter(SearchResult::isEntry)
                                                                .map(SearchResult::asEntry)
                                                                .flatMap((entry)->entry.attributes().stream())
                                                                .filter((attribute2)->attribute.equals(attribute2.type()))
                                                                .flatMap((attribute2)->attribute2.values().stream())
                                                                .toList());
                                                members.sort(null);
                                                return Lava.complete(members);
                                            });
                                }
                            }));

        }
    }

    @Test
    public void testModifyIncrement() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context, ldapServer, UnboundidDirectoryServer.adminBind()),
                            new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    String type="( test-counter-oid"
                                            +" NAME 'testCounter'"
                                            +" SYNTAX "+Syntax.INTEGER_SYNTAX
                                            +" EQUALITY "+MatchingRule.INTEGER_MATCH
                                            +" ORDERING "+MatchingRule.INTEGER_ORDERING_MATCH
                                            +" SINGLE-VALUE )";
                                    return connection.writeRequestReadResponseChecked(
                                                    new ModifyRequest(
                                                            List.of(new ModifyRequest.Change(
                                                                    new PartialAttribute(
                                                                            "attributeTypes",
                                                                            List.of(type)),
                                                                    ModifyRequest.OPERATION_ADD)),
                                                            "cn=schema")
                                                            .controlsEmpty())
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new ModifyRequest(
                                                            List.of(
                                                                    new ModifyRequest.Change(
                                                                            new PartialAttribute(
                                                                                    "objectClass",
                                                                                    List.of("extensibleObject")),
                                                                            ModifyRequest.OPERATION_ADD),
                                                                    new ModifyRequest.Change(
                                                                            new PartialAttribute(
                                                                                    "testCounter",
                                                                                    List.of("123")),
                                                                            ModifyRequest.OPERATION_ADD)),
                                                            "ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu")
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertCounter(connection, 123))
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    new ModifyRequest(
                                                            List.of(
                                                                    new ModifyRequest.Change(
                                                                            new PartialAttribute(
                                                                                    "testCounter",
                                                                                    List.of("456")),
                                                                            ModifyIncrement.OPERATION_INCREMENT)),
                                                            "ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu")
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->assertCounter(connection, 579))
                                            .composeIgnoreResult(()->Lava.VOID);
                                }

                                private @NotNull Lava<Void> assertCounter(
                                        @NotNull LdapConnection connection, int counter) throws Throwable {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of("testCounter"),
                                                            "ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.DEREF_ALWAYS,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            100,
                                                            10,
                                                            false)
                                                            .controlsEmpty())
                                            .compose((results)->{
                                                assertEquals(2, results.size());
                                                assertEquals(
                                                        List.of(Integer.toString(counter)),
                                                        results.get(0).message().asEntry()
                                                                .attributes().get(0).values());
                                                return Lava.VOID;
                                            });
                                }
                            }));
        }
    }

    @Test
    public void testPasswordModify() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            var bind=UnboundidDirectoryServer.USERS.entrySet().iterator().next();
            @NotNull String bindDn=bind.getKey();
            char @NotNull [] password=bind.getValue().toCharArray();
            char @NotNull [] password2=(bind.getValue()+"x").toCharArray();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context, ldapServer, Pair.of(bindDn, new String(password))),
                            new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    return Lava.catchErrors(
                                                    (throwable)->{
                                                        assertEquals(
                                                                LdapResultCode.INVALID_CREDENTIALS,
                                                                throwable.resultCode2);
                                                        return Lava.VOID;
                                                    },
                                                    ()->modifyPassword(connection, null, password2, null)
                                                            .composeIgnoreResult(()->Lava.fail(
                                                                    new RuntimeException("should have failed")
                                                            )),
                                                    LdapException.class)
                                            .composeIgnoreResult(()->modifyPassword(connection, null, null, null))
                                            .compose((genPasswd)->{
                                                assertNotNull(genPasswd);
                                                return test(password, connection, genPasswd);
                                            })
                                            .composeIgnoreResult(()->modifyPassword(
                                                    connection, password2, null, bindDn))
                                            .composeIgnoreResult(()->test(password, connection, password2));
                                }

                                private @NotNull Lava<char @Nullable []> modifyPassword(
                                        @NotNull LdapConnection connection,
                                        char @Nullable [] newPassword,
                                        char @Nullable [] oldPassword,
                                        @Nullable String userIdentity) {
                                    return connection.writeRequestReadResponseChecked(
                                                    PasswordModify.request(newPassword, oldPassword, userIdentity))
                                            .compose((response)->{
                                                PasswordModify.Response response2=PasswordModify.response(response);
                                                if (null==newPassword) {
                                                    assertNotNull(response2);
                                                    assertNotNull(response2.genPasswd());
                                                    return Lava.complete(response2.genPasswd());
                                                }
                                                else {
                                                    assertTrue((null==response2) || (null==response2.genPasswd()));
                                                    return Lava.complete(null);
                                                }
                                            });
                                }

                                private @NotNull Lava<Void> test(
                                        char @NotNull [] badPassword,
                                        @NotNull LdapConnection connection,
                                        char @NotNull [] goodPassword) {
                                    return Lava.catchErrors(
                                                    (throwable)->{
                                                        assertEquals(
                                                                LdapResultCode.INVALID_CREDENTIALS,
                                                                throwable.resultCode2);
                                                        return Lava.VOID;
                                                    },
                                                    ()->connection.writeRequestReadResponseChecked(
                                                                    BindRequest.simple(bindDn, badPassword)
                                                                            .controlsEmpty())
                                                            .compose((response)->Lava.fail
                                                                    (new RuntimeException("should have failed"))),
                                                    LdapException.class)
                                            .composeIgnoreResult(()->connection.writeRequestReadResponseChecked(
                                                    BindRequest.simple(bindDn, goodPassword)
                                                            .controlsEmpty()))
                                            .composeIgnoreResult(()->Lava.VOID);
                                }
                            }));
        }
    }

    @Test
    public void testReadNoticeOfDisconnect() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context, ldapServer, UnboundidDirectoryServer.adminBind()),
                            (connection)->connection.writeMessage(
                                            new InvalidMessage()
                                                    .controlsEmpty())
                                    .compose((messageId)->connection.readMessageChecked(
                                            0, new ExtendedResponse.Reader() {
                                                @Override
                                                public void check(
                                                        @NotNull List<@NotNull Control> controls,
                                                        @NotNull ExtendedResponse message,
                                                        int messageId) {
                                                }
                                            }))
                                    .compose((response)->{
                                        assertNotEquals(
                                                LdapResultCode.SUCCESS,
                                                response.message().ldapResult().resultCode2());
                                        assertEquals(0, response.messageId());
                                        assertEquals(
                                                ExtendedResponse.NOTICE_OF_DISCONNECTION_OID,
                                                response.message().responseName());
                                        return Lava.VOID;
                                    })));
        }
    }

    @Test
    public void testSearchAttributes() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            (connection)->testSearchAttributes(
                                    connection,
                                    List.of(),
                                    List.of("objectClass", "cn", "sn", "uid", "userPassword"))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of("cn"),
                                            List.of("cn")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of("cn", "sn"),
                                            List.of("cn", "sn")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(SearchRequest.NO_ATTRIBUTES),
                                            List.of()))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(SearchRequest.ALL_ATTRIBUTES),
                                            List.of("objectClass", "cn", "sn", "uid", "userPassword")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(SearchRequest.ALL_ATTRIBUTES, SearchRequest.NO_ATTRIBUTES),
                                            List.of("objectClass", "cn", "sn", "uid", "userPassword")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(SearchRequest.NO_ATTRIBUTES, "cn"),
                                            List.of("cn")))));
        }
    }

    private @NotNull Lava<Void> testSearchAttributes(
            @NotNull LdapConnection connection, @NotNull List<@NotNull String> requestAttributes,
            @NotNull List<@NotNull String> responseAttributes) throws Throwable {
        return connection.search(
                        new SearchRequest(
                                requestAttributes,
                                "uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                DerefAliases.DEREF_ALWAYS,
                                Filter.parse("(objectClass=*)"),
                                Scope.WHOLE_SUBTREE,
                                100,
                                10,
                                false)
                                .controlsEmpty())
                .compose((searchResult)->{
                    assertEquals(2, searchResult.size());
                    assertTrue(searchResult.get(0).message().isEntry());
                    assertTrue(searchResult.get(1).message().isDone());
                    SearchResult.Entry entry=searchResult.get(0).message().asEntry();
                    assertEquals(
                            responseAttributes,
                            entry.attributes()
                                    .stream()
                                    .map(PartialAttribute::type)
                                    .toList());
                    return Lava.VOID;
                });
    }

    @Test
    public void testSearchFail() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Lava.catchErrors(
                            (ldapException)->{
                                if (!LdapResultCode.NO_SUCH_OBJECT.equals(ldapException.resultCode2)) {
                                    throw ldapException;
                                }
                                return Lava.VOID;
                            },
                            ()->Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context, ldapServer, UnboundidDirectoryServer.adminBind()),
                                    (connection)->connection.search(
                                                    new SearchRequest(
                                                            List.of("cn", "objectClass"),
                                                            "ou=invalid,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.DEREF_ALWAYS,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.WHOLE_SUBTREE,
                                                            100,
                                                            10,
                                                            false)
                                                            .controlsEmpty())
                                            .composeIgnoreResult(()->Lava.fail(new IllegalStateException()))),
                            LdapException.class));
        }
    }

    @Test
    public void testSearchFilter() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            (connection)->testSearchFilter(
                                    connection, "(objectClass=*)",
                                    true, true, true)
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn=User 0)",
                                            false, true, false))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn<=User 0)",
                                            false, true, false))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn>=User 1)",
                                            false, false, true))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(&(objectClass=*)(cn=User 0))",
                                            false, true, false))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(|(objectClass=*)(cn=User 0))",
                                            true, true, true))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(!(cn=User 0))",
                                            true, false, true))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn=User*)",
                                            false, true, true))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn=*0)",
                                            false, true, false))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn=*ser*)",
                                            false, true, true))
                                    .composeIgnoreResult(()->testSearchFilter(
                                            connection, "(cn=U*0)",
                                            false, true, false))));
        }
    }

    private @NotNull Lava<Void> testSearchFilter(
            @NotNull LdapConnection connection, @NotNull String filter, boolean base, boolean user0, boolean user1)
            throws Throwable {
        return connection.search(
                        new SearchRequest(
                                List.of(SearchRequest.NO_ATTRIBUTES),
                                "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                DerefAliases.DEREF_ALWAYS,
                                Filter.parse(filter),
                                Scope.WHOLE_SUBTREE,
                                100,
                                10,
                                true)
                                .controlsEmpty())
                .compose((searchResult)->{
                    Set<String> expectedDns=new HashSet<>(3);
                    if (base) {
                        expectedDns.add("ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu");
                    }
                    if (user0) {
                        expectedDns.add("uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu");
                    }
                    if (user1) {
                        expectedDns.add("uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu");
                    }
                    Set<String> actualDns=new HashSet<>(3);
                    for (ControlsMessage<SearchResult> result: searchResult) {
                        assertFalse(result.message().isReferral());
                        if (result.message().isEntry()) {
                            actualDns.add(result.message().asEntry().objectName());
                        }
                    }
                    assertEquals(expectedDns, actualDns);
                    return Lava.VOID;
                });
    }

    @Test
    public void testSearchSearchSizeLimit() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            record Params(int limit, boolean sizeTime) {
            }
            List<@NotNull Params> params=LdapConnectionTest.interestingIntegers()
                    .flatMap((limit)->Streams.of(false, true)
                            .map((sizeTime)->new Params(limit, sizeTime)))
                    .toList();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                                    return loop(connection, params.iterator());
                                }

                                private @NotNull Lava<Void> loop(
                                        @NotNull LdapConnection connection,
                                        @NotNull Iterator<@NotNull Params> iterator) throws Throwable {
                                    if (!iterator.hasNext()) {
                                        return Lava.VOID;
                                    }
                                    Params params=iterator.next();
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(),
                                                            "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            params.sizeTime?params.limit:0,
                                                            params.sizeTime?0:params.limit,
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

    @Test
    public void testSearchManageDsaIt() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            for (boolean manageDsaIt: new boolean[]{false, true}) {
                List<ControlsMessage<SearchResult>> result;
                @NotNull List<@NotNull String> referrals;
                try {
                    result=context.get(
                            Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context,
                                            ldapServer,
                                            UnboundidDirectoryServer.adminBind()),
                                    (connection)->connection.search(
                                            new SearchRequest(
                                                    List.of("ref"),
                                                    "cn=referral0,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                    DerefAliases.DEREF_ALWAYS,
                                                    Filter.parse("(objectClass=*)"),
                                                    Scope.WHOLE_SUBTREE,
                                                    100,
                                                    10,
                                                    false)
                                                    .controls(ManageDsaIt.requestControls(manageDsaIt)))));
                    if (!manageDsaIt) {
                        fail("should have failed");
                    }
                    assertEquals(2, result.size());
                    assertTrue(result.get(0).message().isEntry());
                    SearchResult.Entry entry=result.get(0).message().asEntry();
                    assertEquals("cn=referral0,ou=test,dc=ldap4j,dc=gds,dc=hu", entry.objectName());
                    assertTrue(result.get(1).message().isDone());
                    assertEquals(1, entry.attributes().size());
                    PartialAttribute attribute=entry.attributes().get(0);
                    assertEquals("ref", attribute.type());
                    referrals=attribute.values();
                }
                catch (Throwable throwable) {
                    if (manageDsaIt) {
                        throw new RuntimeException("shouldn't have failed", throwable);
                    }
                    LdapException ldapException=Exceptions.findCauseOrThrow(LdapException.class, throwable);
                    assertEquals(LdapResultCode.REFERRAL, ldapException.resultCode2);
                    assertNotNull(ldapException.referrals);
                    referrals=ldapException.referrals;
                }
                assertEquals(3, referrals.size());
                assertEquals("ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu", referrals.get(0));
                assertEquals("ldap://localhost:389/cn=J.%20Duke,ou=NewHires,o=JNDITutorial", referrals.get(1));
                assertEquals("foo", referrals.get(2));
            }
        }
    }

    @Test
    public void testServerSideSorting() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.<Void>get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                                    return search(connection, null, false)
                                            .composeIgnoreResult(()->search(
                                                    connection,
                                                    MatchingRule.CASE_IGNORE_ORDERING_MATCH,
                                                    true));
                                }

                                private @NotNull Lava<Void> search(
                                        @NotNull LdapConnection connection,
                                        @Nullable String orderingRule,
                                        boolean reverseOrder)
                                        throws Throwable {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(),
                                                            "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.DEREF_ALWAYS,
                                                            Filter.parse("(objectClass=person)"),
                                                            Scope.WHOLE_SUBTREE,
                                                            100,
                                                            10,
                                                            true)
                                                            .controls(List.of(ServerSideSorting.requestControl(
                                                                    true,
                                                                    List.of(new ServerSideSorting.SortKey(
                                                                            "uid",
                                                                            orderingRule,
                                                                            reverseOrder))))))
                                            .compose((results)->{
                                                assertEquals(3, results.size());
                                                assertTrue(results.get(0).message().isEntry());
                                                assertTrue(results.get(1).message().isEntry());
                                                assertTrue(results.get(2).message().isDone());
                                                @NotNull ServerSideSorting.SortResult sortResult
                                                        =ServerSideSorting.responseControlCheckSuccess(
                                                        results.get(2).controls());
                                                assertEquals(
                                                        ServerSideSorting.SortResultCode.SUCCESS,
                                                        sortResult.sortResultCode());
                                                assertEquals(
                                                        "uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                        results.get(reverseOrder?1:0)
                                                                .message()
                                                                .asEntry()
                                                                .objectName());
                                                assertEquals(
                                                        "uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                        results.get(reverseOrder?0:1)
                                                                .message()
                                                                .asEntry()
                                                                .objectName());
                                                return Lava.VOID;
                                            });
                                }
                            }));
        }
    }

    @Test
    public void testSimplePagedResults() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            context.<Void>get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    ldapServer,
                                    UnboundidDirectoryServer.adminBind()),
                            (connection)->{
                                @NotNull Control sortControl=ServerSideSorting.requestControl(
                                        true,
                                        List.of(new ServerSideSorting.SortKey("uid", null, false)));
                                @NotNull SearchRequest searchRequest=new SearchRequest(
                                        List.of(),
                                        "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                        DerefAliases.DEREF_ALWAYS,
                                        Filter.parse("(objectClass=person)"),
                                        Scope.WHOLE_SUBTREE,
                                        100,
                                        10,
                                        true);
                                return connection.search(searchRequest.controls(List.of(
                                                sortControl,
                                                SimplePagedResults.startRequest(1))))
                                        .compose((results)->{
                                            assertEquals(2, results.size());
                                            assertEquals(
                                                    "uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                    results.get(0).message().asEntry().objectName());
                                            assertTrue(results.get(1).message().isDone());
                                            @NotNull SimplePagedResults.SearchControlValue pageControl
                                                    =SimplePagedResults.responseControlCheck(results.get(1).controls());
                                            assertFalse(pageControl.cookie().isEmpty());
                                            assertEquals(2, pageControl.size());
                                            return connection.search(searchRequest.controls(List.of(
                                                    sortControl,
                                                    pageControl.continueRequest(1))));
                                        })
                                        .compose((results)->{
                                            assertEquals(2, results.size());
                                            assertEquals(
                                                    "uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                    results.get(0).message().asEntry().objectName());
                                            assertTrue(results.get(1).message().isDone());
                                            @NotNull SimplePagedResults.SearchControlValue pageControl
                                                    =SimplePagedResults.responseControlCheck(results.get(1).controls());
                                            assertTrue(pageControl.cookie().isEmpty());
                                            assertEquals(2, pageControl.size());
                                            return Lava.VOID;
                                        });
                            }));
        }
    }

    @Test
    public void testWhoAmI() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS);
             UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                     false, TEST_PARAMETERS.serverPortClearText, TEST_PARAMETERS.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: UnboundidDirectoryServer.allBinds()) {
                context.<Void>get(
                        Closeable.withCloseable(
                                ()->context.parameters().connectionFactory(context, ldapServer, bind),
                                (connection)->connection.writeRequestReadResponseChecked(
                                                WhoAmI.REQUEST.controlsEmpty())
                                        .compose((response)->{
                                            assertEquals(
                                                    LdapResultCode.SUCCESS,
                                                    response.message().ldapResult().resultCode2());
                                            assertNotNull(response.message().responseValue());
                                            String response2=new String(
                                                    response.message().responseValue(),
                                                    StandardCharsets.UTF_8);
                                            assertEquals("dn:"+bind.first(), response2);
                                            return Lava.VOID;
                                        })));
            }
        }
    }
}
