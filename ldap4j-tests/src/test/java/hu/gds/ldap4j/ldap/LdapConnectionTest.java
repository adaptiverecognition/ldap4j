package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.stream.Streams;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LdapConnectionTest {
    private static @NotNull Stream<@NotNull Integer> badMessageIds() {
        return Streams.of(65535, (1<<24)-1);
    }

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
    public void testAddDelete(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String name="group8";
            String object="cn=%s,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu".formatted(name);
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user1="uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                                                                            "objectclass",
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testBindIncorrectPassword(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: LdapServer.allBinds()) {
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testBindIncorrectUser(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: LdapServer.allBinds()) {
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testBindSASL(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            var bind=LdapServer.allBinds().iterator().next();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testBindSuccess(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (Pair<String, String> bind: LdapServer.allBinds()) {
                context.<Void>get(
                        Closeable.withCloseable(
                                ()->context.parameters().connectionFactory(context, ldapServer, bind),
                                (connection)->Lava.VOID));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testCompare(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String object="cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user2="uid=user2,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testDERLength(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context, ldapServer, LdapServer.adminBind()),
                            new Function<LdapConnection, Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(
                                        @NotNull LdapConnection connection) throws Throwable {
                                    return loop(
                                            connection,
                                            interestingIntegers()
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
                                                            true,
                                                            1,
                                                            true,
                                                            true)
                                                            .controlsEmpty())
                                            .compose((results)->{
                                                assertEquals(2, results.size());
                                                assertTrue(results.get(0).isEntry());
                                                assertEquals(
                                                        "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                        results.get(0).asEntry().objectName());
                                                assertTrue(results.get(1).isDone());
                                                return loop(connection, iterator);
                                            });
                                }
                            }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testInvalidRequest(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            class InvalidMessage implements Message<InvalidMessage> {
                @Override
                public @NotNull InvalidMessage self() {
                    return this;
                }

                @Override
                public @NotNull ByteBuffer write() throws Throwable {
                    return ByteBuffer.create((byte)0, (byte)1, (byte)0);
                }
            }
            context.get(
                    Lava.catchErrors(
                            (extendedLdapException)->{
                                if (Ldap.NOTICE_OF_DISCONNECTION_OID.equals(
                                        extendedLdapException.response.message().responseName())) {
                                    return Lava.VOID;
                                }
                                else {
                                    throw extendedLdapException;
                                }
                            },
                            ()->Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context, ldapServer, LdapServer.adminBind()),
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testMessageId(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context, ldapServer, LdapServer.adminBind()),
                                    new Function<LdapConnection, Lava<Void>>() {
                                        @Override
                                        public @NotNull Lava<Void> apply(
                                                @NotNull LdapConnection connection) throws Throwable {
                                            return loop(connection, badMessageIds().iterator());
                                        }

                                        private @NotNull Lava<Void> loop(
                                                @NotNull LdapConnection connection,
                                                @NotNull Iterator<@NotNull Integer> iterator) throws Throwable {
                                            if (!iterator.hasNext()) {
                                                return Lava.VOID;
                                            }
                                            int messageId=iterator.next();
                                            return connection.search(
                                                            MessageIdGenerator.constant(true, messageId),
                                                            new SearchRequest(
                                                                    List.of(),
                                                                    "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                                    DerefAliases.NEVER_DEREF_ALIASES,
                                                                    Filter.parse("(objectClass=*)"),
                                                                    Scope.BASE_OBJECT,
                                                                    10,
                                                                    true,
                                                                    1,
                                                                    true,
                                                                    true)
                                                                    .controlsEmpty())
                                                    .compose((results)->{
                                                        assertEquals(2, results.size());
                                                        assertTrue(results.get(0).isEntry());
                                                        assertEquals(
                                                                "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                                results.get(0).asEntry().objectName());
                                                        assertTrue(results.get(1).isDone());
                                                        return loop(connection, iterator);
                                                    });
                                        }
                                    })
                            .composeIgnoreResult(new Supplier<@NotNull Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> get() {
                                    return loop(badMessageIds().iterator());
                                }

                                private @NotNull Lava<Void> loop(@NotNull Iterator<@NotNull Integer> iterator) {
                                    if (!iterator.hasNext()) {
                                        return Lava.VOID;
                                    }
                                    int messageId=iterator.next();
                                    return Lava.catchErrors(
                                            (throwable)->{
                                                Exceptions.findCauseOrThrow(
                                                        UnexpectedMessageIdException.class, throwable);
                                                return loop(iterator);
                                            },
                                            ()->Closeable.withCloseable(
                                                    ()->context.parameters().connectionFactory(
                                                            context, ldapServer, LdapServer.adminBind()),
                                                    (connection)->connection.search(
                                                                    MessageIdGenerator.constant(
                                                                            false, messageId),
                                                                    new SearchRequest(
                                                                            List.of(),
                                                                            "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                                            Filter.parse("(objectClass=*)"),
                                                                            Scope.BASE_OBJECT,
                                                                            10,
                                                                            true,
                                                                            1,
                                                                            true,
                                                                            true)
                                                                            .controlsEmpty())
                                                            .composeIgnoreResult(()->Lava.fail(
                                                                    new RuntimeException("should have failed")))),
                                            Throwable.class);
                                }
                            }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testModify(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            String attribute="member";
            String object="cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user0="uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user1="uid=user1,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user2="uid=user2,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            String user3="uid=user3,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu";
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                                                            ModifyRequest.Operation.REPLACE)))
                                            .composeIgnoreResult(()->assertMembers(connection, user2, user3))
                                            .composeIgnoreResult(()->modify(
                                                    connection,
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of()),
                                                            ModifyRequest.Operation.DELETE)))
                                            .composeIgnoreResult(()->assertMembers(connection))
                                            .composeIgnoreResult(()->modify(
                                                    connection,
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of(user0)),
                                                            ModifyRequest.Operation.ADD),
                                                    new ModifyRequest.Change(
                                                            new PartialAttribute(attribute, List.of(user1)),
                                                            ModifyRequest.Operation.ADD)))
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testModifyDN(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
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
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchAttributes(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                                            List.of(Ldap.NO_ATTRIBUTES),
                                            List.of()))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.ALL_ATTRIBUTES),
                                            List.of("objectClass", "cn", "sn", "uid", "userPassword")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.ALL_ATTRIBUTES, Ldap.NO_ATTRIBUTES),
                                            List.of("objectClass", "cn", "sn", "uid", "userPassword")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.NO_ATTRIBUTES, "cn"),
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
                    assertTrue(searchResult.get(0).isEntry());
                    assertTrue(searchResult.get(1).isDone());
                    SearchResult.Entry entry=searchResult.get(0).asEntry();
                    assertEquals(
                            responseAttributes,
                            entry.attributes()
                                    .stream()
                                    .map(PartialAttribute::type)
                                    .toList());
                    return Lava.VOID;
                });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchFail(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
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
                                            context, ldapServer, LdapServer.adminBind()),
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchFilter(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                                List.of(Ldap.NO_ATTRIBUTES),
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
                    for (SearchResult result: searchResult) {
                        assertFalse(result.isReferral());
                        if (result.isEntry()) {
                            actualDns.add(result.asEntry().objectName());
                        }
                    }
                    assertEquals(expectedDns, actualDns);
                    return Lava.VOID;
                });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchManageDsaIt(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (boolean manageDsaIt: new boolean[]{false, true}) {
                List<SearchResult> result;
                @NotNull List<@NotNull String> referrals;
                try {
                    result=context.get(
                            Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                                                    .controlsManageDsaIt(manageDsaIt))));
                    if (!manageDsaIt) {
                        fail("should have failed");
                    }
                    assertEquals(2, result.size());
                    assertTrue(result.get(0).isEntry());
                    SearchResult.Entry entry=result.get(0).asEntry();
                    assertEquals("cn=referral0,ou=test,dc=ldap4j,dc=gds,dc=hu", entry.objectName());
                    assertTrue(result.get(1).isDone());
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

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchSearchSizeLimit(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            record Params(boolean kludge, int limit, boolean sizeTime) {
            }
            List<@NotNull Params> params=Streams.of(false, true)
                    .<Params>flatMap((kludge)->interestingIntegers()
                            .flatMap((limit)->Streams.of(false, true)
                                    .map((sizeTime)->new Params(kludge, limit, sizeTime))))
                    .toList();
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                                                            params.kludge,
                                                            params.sizeTime?0:params.limit,
                                                            params.kludge,
                                                            true)
                                                            .controlsEmpty())
                                            .compose((results)->{
                                                assertEquals(2, results.size());
                                                assertTrue(results.get(0).isEntry());
                                                assertEquals(
                                                        "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                        results.get(0).asEntry().objectName());
                                                assertTrue(results.get(1).isDone());
                                                return loop(connection, iterator);
                                            });
                                }
                            }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.LdapTestParameters#streamLdap")
    public void testSearchSuccess(LdapTestParameters testParameters) throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(testParameters);
             LdapServer ldapServer=new LdapServer(
                     false, testParameters.serverPortClearText, testParameters.serverPortTls)) {
            ldapServer.start();
            for (String user: LdapServer.USERS.keySet()) {
                List<SearchResult> results=context.get(
                        Closeable.withCloseable(
                                ()->context.parameters().connectionFactory(context, ldapServer, LdapServer.adminBind()),
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
                assertTrue(results.get(results.size()-1).isDone());
                results=new ArrayList<>(results);
                results.remove(results.size()-1);
                results.sort(Comparator.comparing((result)->result.asEntry().objectName()));
                List<String> groups=new ArrayList<>();
                LdapServer.GROUPS.forEach((group, users)->{
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
                            new SearchResult.Entry(
                                    List.of(
                                            new PartialAttribute("objectClass", List.of("top", "groupOfNames")),
                                            new PartialAttribute("cn", List.of(cn))),
                                    group),
                            results.get(ii));
                }
            }
        }
    }
}
