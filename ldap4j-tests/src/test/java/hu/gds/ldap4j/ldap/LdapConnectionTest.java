package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
                                                    false,
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
                                                            true))
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
                                    (connection)->connection.invalidRequestResponse()
                                            .composeIgnoreResult(()->Lava.fail(new IllegalStateException()))),
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
                                                            false,
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
                                                                    true))
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
                                                                    false,
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
                                                                            true))
                                                            .composeIgnoreResult(()->Lava.fail(
                                                                    new RuntimeException("should have failed")))),
                                            Throwable.class);
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
                                    Set.of("objectClass", "cn", "sn", "uid", "userPassword"))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of("cn"),
                                            Set.of("cn")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of("cn", "sn"),
                                            Set.of("cn", "sn")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.NO_ATTRIBUTES),
                                            Set.of()))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.ALL_ATTRIBUTES),
                                            Set.of("objectClass", "cn", "sn", "uid", "userPassword")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.ALL_ATTRIBUTES, Ldap.NO_ATTRIBUTES),
                                            Set.of("objectClass", "cn", "sn", "uid", "userPassword")))
                                    .composeIgnoreResult(()->testSearchAttributes(
                                            connection,
                                            List.of(Ldap.NO_ATTRIBUTES, "cn"),
                                            Set.of("cn")))));
        }
    }

    private @NotNull Lava<Void> testSearchAttributes(
            @NotNull LdapConnection connection, @NotNull List<@NotNull String> requestAttributes,
            @NotNull Set<@NotNull String> responseAttributes) throws Throwable {
        return connection.search(
                        false,
                        new SearchRequest(
                                requestAttributes,
                                "uid=user0,ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                DerefAliases.DEREF_ALWAYS,
                                Filter.parse("(objectClass=*)"),
                                Scope.WHOLE_SUBTREE,
                                100,
                                10,
                                false))
                .compose((searchResult)->{
                    assertEquals(2, searchResult.size());
                    assertTrue(searchResult.get(0).isEntry());
                    assertTrue(searchResult.get(1).isDone());
                    SearchResult.Entry entry=searchResult.get(0).asEntry();
                    assertEquals(responseAttributes, entry.attributes().keySet());
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
                                                    false,
                                                    new SearchRequest(
                                                            List.of("cn", "objectClass"),
                                                            "ou=invalid,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.DEREF_ALWAYS,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.WHOLE_SUBTREE,
                                                            100,
                                                            10,
                                                            false))
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
                        false,
                        new SearchRequest(
                                List.of(Ldap.NO_ATTRIBUTES),
                                "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                DerefAliases.DEREF_ALWAYS,
                                Filter.parse(filter),
                                Scope.WHOLE_SUBTREE,
                                100,
                                10,
                                true))
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
                                            manageDsaIt,
                                            new SearchRequest(
                                                    List.of("ref"),
                                                    "cn=referral0,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                    DerefAliases.DEREF_ALWAYS,
                                                    Filter.parse("(objectClass=*)"),
                                                    Scope.WHOLE_SUBTREE,
                                                    100,
                                                    10,
                                                    false))));
                    if (!manageDsaIt) {
                        fail("should have failed");
                    }
                    assertEquals(2, result.size());
                    assertTrue(result.get(0).isEntry());
                    SearchResult.Entry entry=result.get(0).asEntry();
                    assertEquals("cn=referral0,ou=test,dc=ldap4j,dc=gds,dc=hu", entry.objectName());
                    assertTrue(result.get(1).isDone());
                    referrals=new ArrayList<>(entry.attributes().get("ref").values());
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
                                                    false,
                                                    new SearchRequest(
                                                            List.of(),
                                                            "cn=group0,ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                            DerefAliases.NEVER_DEREF_ALIASES,
                                                            Filter.parse("(objectClass=*)"),
                                                            Scope.BASE_OBJECT,
                                                            params.sizeTime?params.limit:0,
                                                            params.kludge,
                                                            params.sizeTime?0:params.limit,
                                                            true))
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
                                        false,
                                        new SearchRequest(
                                                List.of("cn", "objectClass"),
                                                "ou=groups,ou=test,dc=ldap4j,dc=gds,dc=hu",
                                                DerefAliases.DEREF_ALWAYS,
                                                Filter.parse("(&(objectClass=*)(member=%s))".formatted(user)),
                                                Scope.WHOLE_SUBTREE,
                                                100,
                                                10,
                                                false))));
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
                                    Map.of(
                                            "objectClass",
                                            new PartialAttribute("objectClass", Set.of("top", "groupOfNames")),
                                            "cn",
                                            new PartialAttribute("cn", Set.of(cn))
                                    ),
                                    group),
                            results.get(ii));
                }
            }
        }
    }
}
