package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.ThreadPoolContextHolder;
import hu.gds.ldap4j.ldap.extension.FeatureDiscovery;
import hu.gds.ldap4j.net.NetworkConnectionFactory;
import hu.gds.ldap4j.net.TlsConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
https://github.com/osixia/docker-openldap
*/
@TestMethodOrder(MethodOrderer.MethodName.class)
public class OpenLdapTest {
    private static final @NotNull Pair<@NotNull String, @NotNull String> ADMIN_BIND=
            Pair.of("cn=admin,dc=example,dc=org", "secret");
    private static final @NotNull LdapTestParameters TEST_PARAMETERS=new LdapTestParameters(
            ThreadPoolContextHolder.factory(AbstractTest.PARALLELISM, null, true),
            ThreadPoolContextHolder.factory(AbstractTest.PARALLELISM, null, true),
            NetworkConnectionFactory.javaAsyncChannel(),
            AbstractTest.TIMEOUT_NANOS,
            AbstractTest.SERVER_PORT_CLEAR_TEXT,
            AbstractTest.SERVER_PORT_TLS,
            LdapTestParameters.Tls.CLEAR_TEXT);

    private static GenericContainer<?> container;

    @AfterAll
    public static void afterAll() {
        if (null!=container) {
            try {
                container.close();
            }
            finally {
                container=null;
            }
        }
    }

    @BeforeAll
    public static void beforeAll() {
        container=new GenericContainer<>("osixia/openldap:latest");
        container.withCreateContainerCmdModifier((cmd)->cmd.withHostName("ldap"))
                .withEnv("LDAP_ADMIN_PASSWORD", ADMIN_BIND.second())
                .withEnv("LDAP_TLS", "true")
                /*.withLogConsumer((frame)->System.out.printf(
                        "container log: %s%n",
                        frame.getUtf8StringWithoutLineEnding()))*/
                .withNetworkMode("host")
                .withPrivilegedMode(true)
                .withStartupTimeout(Duration.of(120L, ChronoUnit.SECONDS));
        container.setPortBindings(List.of("389:389"));
        container.start();
    }

    @Test
    public void testExtensibleMatch() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS)) {
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                                    ()->new InetSocketAddress(InetAddress.getLoopbackAddress(), 389),
                                    ()->new InetSocketAddress(InetAddress.getLoopbackAddress(), 636),
                                    ADMIN_BIND),
                            new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                                @Override
                                public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                                    return search(
                                            connection,
                                            true,
                                            MatchingRule.DISTINGUISHED_NAME_MATCH,
                                            "dc=example,dc=org",
                                            "entryDN")
                                            .composeIgnoreResult(()->search(
                                                    connection,
                                                    false,
                                                    MatchingRule.CASE_IGNORE_MATCH,
                                                    "foobar",
                                                    "dc"));
                                }

                                private @NotNull Lava<Void> search(
                                        @NotNull LdapConnection connection,
                                        boolean expectResult,
                                        @Nullable String matchingRule,
                                        @NotNull String matchValue,
                                        @NotNull String type) {
                                    return connection.search(
                                                    new SearchRequest(
                                                            List.of(SearchRequest.ALL_ATTRIBUTES),
                                                            "dc=example,dc=org",
                                                            DerefAliases.DEREF_ALWAYS,
                                                            new Filter.And(List.of(
                                                                    new Filter.Present("objectClass"),
                                                                    new Filter.ExtensibleMatch(
                                                                            true,
                                                                            matchingRule,
                                                                            matchValue,
                                                                            type))),
                                                            Scope.BASE_OBJECT,
                                                            100,
                                                            10,
                                                            false)
                                                            .controlsEmpty())
                                            .compose((results)->{
                                                if (expectResult) {
                                                    assertEquals(2, results.size());
                                                    assertTrue(results.get(0).message().isEntry());
                                                    assertEquals(
                                                            "dc=example,dc=org",
                                                            results.get(0).message().asEntry().objectName());
                                                    assertTrue(results.get(1).message().isDone());
                                                }
                                                else {
                                                    assertEquals(1, results.size());
                                                    assertTrue(results.get(0).message().isDone());
                                                }
                                                return Lava.VOID;
                                            });
                                }
                            }));
        }
    }

    @Test
    public void testFeatureDiscovery() throws Throwable {
        try (TestContext<LdapTestParameters> context=TestContext.create(TEST_PARAMETERS)) {
            context.get(
                    Closeable.withCloseable(
                            ()->context.parameters().connectionFactory(
                                    context,
                                    TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                                    ()->new InetSocketAddress(InetAddress.getLoopbackAddress(), 389),
                                    ()->new InetSocketAddress(InetAddress.getLoopbackAddress(), 636),
                                    ADMIN_BIND),
                            (connection)->connection.search(FeatureDiscovery.searchRequest())
                                    .compose((results)->{
                                        FeatureDiscovery featureDiscovery=FeatureDiscovery.create(results);
                                        assertTrue(
                                                featureDiscovery.namingContexts
                                                        .contains("dc=example,dc=org"));
                                        assertTrue(featureDiscovery.supportedLdapVersions.contains("3"));
                                        assertTrue(featureDiscovery.supportedSaslMechanisms.contains("GSSAPI"));
                                        return Lava.VOID;
                                    })));
        }
    }
}
