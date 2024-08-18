package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.ThreadLocalScheduledExecutorContext;
import hu.gds.ldap4j.net.JavaAsyncChannelConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.lang3.stream.Streams;
import org.apache.directory.api.ldap.model.constants.SupportedSaslMechanisms;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.handlers.sasl.cramMD5.CramMd5MechanismHandler;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.geronimo.javamail.authentication.CramMD5Authenticator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@CreateDS(
        allowAnonAccess=true,
        name="test-directory-service",
        partitions={
                @CreatePartition(
                        name="test",
                        suffix="dc=test,dc=ldap4j,dc=gds,dc=hu")
        })
@ApplyLdifFiles("hu/gds/ldap4j/ldap/apache-directory-studio-test.ldif")
public class ApacheDSTest {
    private static final int PORT=0;

    @Test
    public void test() throws Throwable {
        LdapServer ldapServer=new LdapServer();
        try {
            ldapServer.setDirectoryService(DSAnnotationProcessor.getDirectoryService());
            DSAnnotationProcessor.applyLdifs(
                    getClass(),
                    "ldap server display name",
                    ldapServer.getDirectoryService());
            ldapServer.addSaslMechanismHandler(
                    SupportedSaslMechanisms.CRAM_MD5,
                    new CramMd5MechanismHandler());
            ldapServer.setSearchBaseDn("ou=system");
            ldapServer.addTransports(new TcpTransport("localhost", PORT));
            ldapServer.start();
            InetSocketAddress serverAddress
                    =(InetSocketAddress)ldapServer.getTransports()[0].getAcceptor().getLocalAddress();
            assertNotNull(serverAddress);

            ScheduledExecutorService executor=Executors.newScheduledThreadPool(AbstractTest.PARALLELISM);
            try {
                Context context=ThreadLocalScheduledExecutorContext.createDelayNanos(
                        AbstractTest.TIMEOUT_NANOS,
                        executor,
                        Log.systemErr(),
                        AbstractTest.PARALLELISM);
                JoinCallback<Void> join=Callback.join(context);
                context.get(
                        join,
                        testApproxMatch(serverAddress)
                                .composeIgnoreResult(()->testBindSASL(serverAddress))
                                .composeIgnoreResult(()->testDERLength(serverAddress))
                                .composeIgnoreResult(()->testFeatureDiscovery(serverAddress))
                                .composeIgnoreResult(()->testMessageId(serverAddress))
                                .composeIgnoreResult(()->testReferrals(serverAddress))
                                .composeIgnoreResult(()->testSearchSizeTimeLimit(serverAddress)));
                join.joinEndNanos(context.endNanos());
            }
            finally {
                executor.shutdown();
            }
        }
        finally {
            ldapServer.stop();
        }
    }

    private @NotNull Lava<Void> testApproxMatch(InetSocketAddress serverAddress) {
        return withConnection(
                (connection)->testSearch(
                        "ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                        connection,
                        "(&(objectClass=*)(cn~=User 0))",
                        false,
                        connection.messageIdGenerator(),
                        10,
                        1)
                        .compose((results)->{
                            assertEquals(1, results.size());
                            assertTrue(results.get(0).isEntry());
                            assertEquals(
                                    "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                    results.get(0).asEntry().objectName());
                            return Lava.VOID;
                        }),
                serverAddress);
    }

    private @NotNull Lava<Void> testBindSASL(InetSocketAddress serverAddress) {
        return withConnection(
                new Function<>() {
                    private @NotNull Lava<Void> bind(@NotNull LdapConnection connection, @NotNull String password) {
                        return connection.writeRequestReadResponseChecked(
                                        BindRequest.sasl(
                                                        null,
                                                        SupportedSaslMechanisms.CRAM_MD5,
                                                        "")
                                                .controlsEmpty())
                                .compose((bindResponse)->{
                                    assertEquals(
                                            LdapResultCode.SASL_BIND_IN_PROGRESS,
                                            bindResponse.message().ldapResult().resultCode2());
                                    assertNotNull(bindResponse.message().serverSaslCredentials());
                                    return connection.writeRequestReadResponseChecked(
                                            BindRequest.sasl(
                                                            new CramMD5Authenticator(
                                                                    "admin",
                                                                    password)
                                                                    .evaluateChallenge(
                                                                            bindResponse.message()
                                                                                    .serverSaslCredentials()),
                                                            SupportedSaslMechanisms.CRAM_MD5,
                                                            "")
                                                    .controlsEmpty());
                                })
                                .compose((bindResponse)->{
                                    assertEquals(
                                            LdapResultCode.SUCCESS,
                                            bindResponse.message().ldapResult().resultCode2());
                                    return Lava.VOID;
                                });
                    }

                    @Override
                    public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                        return bind(connection, "secret")
                                .composeIgnoreResult(()->Lava.catchErrors(
                                        (exception)->{
                                            assertEquals(LdapResultCode.INVALID_CREDENTIALS, exception.resultCode2);
                                            return Lava.VOID;
                                        },
                                        ()->bind(connection, "secretx")
                                                .composeIgnoreResult(()->{
                                                    fail("should have failed");
                                                    return Lava.VOID;
                                                }),
                                        LdapException.class));
                    }
                },
                serverAddress);
    }

    private @NotNull Lava<Void> testDERLength(InetSocketAddress serverAddress) {
        return withConnection(
                new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                    @Override
                    public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                        return loop(
                                connection,
                                LdapConnectionTest.interestingIntegers()
                                        .filter((value)->65536>=value)
                                        .iterator());
                    }

                    private @NotNull Lava<Void> loop(
                            @NotNull LdapConnection connection, @NotNull Iterator<@NotNull Integer> iterator)
                            throws Throwable {
                        if (!iterator.hasNext()) {
                            return Lava.VOID;
                        }
                        int length=iterator.next();
                        char[] chars=new char[length];
                        Arrays.fill(chars, 'a');
                        String string=new String(chars);
                        return testSearch(
                                "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                connection,
                                "(|(objectClass=*)(sn=%s))".formatted(string),
                                false,
                                connection.messageIdGenerator(),
                                10,
                                1)
                                .compose((results)->{
                                    assertEquals(1, results.size());
                                    assertTrue(results.get(0).isEntry());
                                    assertEquals(
                                            "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                            results.get(0).asEntry().objectName());
                                    return loop(connection, iterator);
                                });
                    }
                },
                serverAddress);
    }

    private @NotNull Lava<Void> testFeatureDiscovery(InetSocketAddress serverAddress) {
        return withConnection(
                new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                    @Override
                    public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                        return connection.search(FeatureDiscovery.searchRequest())
                                .compose((results)->{
                                    FeatureDiscovery featureDiscovery=FeatureDiscovery.create(results);
                                    assertTrue(
                                            featureDiscovery.namingContexts
                                                    .contains("dc=test,dc=ldap4j,dc=gds,dc=hu"));
                                    assertEquals(Set.of("3"), featureDiscovery.supportedLdapVersions);
                                    assertTrue(featureDiscovery.supportedSaslMechanisms.contains("CRAM-MD5"));
                                    assertTrue(featureDiscovery.supportedControlsFeature
                                            .contains(Feature.MANAGE_DSA_IT_CONTROL));
                                    assertTrue(featureDiscovery.supportedControlsOid
                                            .contains(Ldap.CONTROL_MANAGE_DSA_IT_OID));
                                    assertTrue(featureDiscovery.supportedExtensionsFeature
                                            .contains(Feature.NOTICE_OF_DISCONNECT_UNSOLICITED_NOTIFICATION));
                                    assertTrue(featureDiscovery.supportedExtensionsOid
                                            .contains(Ldap.NOTICE_OF_DISCONNECTION_OID));
                                    assertTrue(featureDiscovery.supportedFeaturesFeature
                                            .contains(Feature.ALL_OPERATIONAL_ATTRIBUTES));
                                    return Lava.VOID;
                                });
                    }
                },
                serverAddress);
    }

    private @NotNull Lava<Void> testMessageId(InetSocketAddress serverAddress) {
        return withConnection(
                new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                    @Override
                    public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                        return loop(
                                connection,
                                LdapConnectionTest.interestingIntegers()
                                        .filter((value)->0<value)
                                        .iterator());
                    }

                    private @NotNull Lava<Void> loop(
                            @NotNull LdapConnection connection, @NotNull Iterator<@NotNull Integer> iterator)
                            throws Throwable {
                        if (!iterator.hasNext()) {
                            return Lava.VOID;
                        }
                        int messageId=iterator.next();
                        return testSearch(
                                "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                connection,
                                "(objectClass=*)",
                                false,
                                MessageIdGenerator.constant(messageId),
                                10,
                                1)
                                .compose((results)->{
                                    assertEquals(1, results.size());
                                    assertTrue(results.get(0).isEntry());
                                    assertEquals(
                                            "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                            results.get(0).asEntry().objectName());
                                    return loop(connection, iterator);
                                });
                    }
                },
                serverAddress);
    }

    private @NotNull Lava<Void> testReferrals(InetSocketAddress serverAddress) {
        return withConnection(
                new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                    @Override
                    public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) {
                        return testBaseFalseDsaFalse(connection);
                    }

                    private void assertReferral0Entry(@NotNull SearchResult result) {
                        assertTrue(result.isEntry());
                        SearchResult.Entry entry=result.asEntry();
                        assertEquals(
                                "cn=Referral 0,ou=Referrals,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                entry.objectName());
                        PartialAttribute refAttribute=null;
                        for (PartialAttribute partialAttribute: entry.attributes()) {
                            if ("ref".equals(partialAttribute.type())) {
                                refAttribute=partialAttribute;
                                break;
                            }
                        }
                        assertNotNull(refAttribute);
                        assertEquals(2, refAttribute.values().size());
                        assertTrue(
                                refAttribute.values()
                                        .contains("ldap://test.example.org:10389/dc=test,dc=example,dc=org"));
                        assertTrue(
                                refAttribute.values()
                                        .contains("ldap:///dc=foobar"));
                    }

                    private void assertReferralsEntry(@NotNull SearchResult result) {
                        assertTrue(result.isEntry());
                        assertEquals(
                                "ou=Referrals,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                result.asEntry().objectName());
                    }

                    public @NotNull Lava<Void> testBaseFalseDsaFalse(@NotNull LdapConnection connection) {
                        return Lava.catchErrors(
                                (throwable)->{
                                    LdapException ldapException
                                            =Exceptions.findCauseOrThrow(LdapException.class, throwable);
                                    assertEquals(LdapResultCode.REFERRAL.code, ldapException.resultCode);
                                    assertEquals(LdapResultCode.REFERRAL, ldapException.resultCode2);
                                    assertNotNull(ldapException.referrals);
                                    assertEquals(2, ldapException.referrals.size());
                                    assertTrue(ldapException.referrals.contains(
                                            "ldap://test.example.org:10389/dc=test,dc=example,dc=org?ref,*?sub"));
                                    assertTrue(ldapException.referrals.contains("ldap:///dc=foobar?ref,*?sub"));
                                    return testBaseFalseDsaTrue(connection);
                                },
                                ()->testSearch(
                                        "cn=Referral 0,ou=Referrals,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                        connection,
                                        "(objectClass=*)",
                                        false,
                                        connection.messageIdGenerator(),
                                        10,
                                        1)
                                        .composeIgnoreResult(()->Lava.fail(
                                                new RuntimeException("should have failed"))),
                                Throwable.class);
                    }

                    public @NotNull Lava<Void> testBaseFalseDsaTrue(
                            @NotNull LdapConnection connection) throws Throwable {
                        return testSearch(
                                "cn=Referral 0,ou=Referrals,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                connection,
                                "(objectClass=*)",
                                true,
                                connection.messageIdGenerator(),
                                10,
                                1)
                                .compose((results)->{
                                    assertEquals(1, results.size());
                                    assertReferral0Entry(results.get(0));
                                    return testBaseTrueDsaFalse(connection);
                                });
                    }

                    public @NotNull Lava<Void> testBaseTrueDsaFalse(
                            @NotNull LdapConnection connection) throws Throwable {
                        return testSearch(
                                "ou=Referrals,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                connection,
                                "(objectClass=*)",
                                false,
                                connection.messageIdGenerator(),
                                10,
                                1)
                                .compose((results)->{
                                    assertEquals(2, results.size());
                                    assertTrue(results.get(0).isReferral());
                                    assertEquals(2, results.get(0).asReferral().uris().size());
                                    assertTrue(results.get(0).asReferral().uris().contains(
                                            "ldap://test.example.org:10389/dc=test,dc=example,dc=org??sub"));
                                    assertTrue(results.get(0).asReferral().uris().contains("ldap:///dc=foobar??sub"));
                                    assertReferralsEntry(results.get(1));
                                    return testBaseTrueDsaTrue(connection);
                                });
                    }

                    public @NotNull Lava<Void> testBaseTrueDsaTrue(
                            @NotNull LdapConnection connection) throws Throwable {
                        return testSearch(
                                "ou=Referrals,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                connection,
                                "(objectClass=*)",
                                true,
                                connection.messageIdGenerator(),
                                10,
                                1)
                                .compose((results)->{
                                    assertEquals(2, results.size());
                                    assertReferral0Entry(results.get(0));
                                    assertReferralsEntry(results.get(1));
                                    return Lava.VOID;
                                });
                    }
                },
                serverAddress);
    }

    private @NotNull Lava<@NotNull List<@NotNull SearchResult>> testSearch(
            @NotNull String baseObject, @NotNull LdapConnection connection, @NotNull String filter,
            boolean manageDsaIt, @NotNull MessageIdGenerator messageIdGenerator, int sizeLimitEntries,
            int timeLimitSeconds) throws Throwable {
        return connection.search(
                        messageIdGenerator,
                        new SearchRequest(
                                List.of("ref", Ldap.ALL_ATTRIBUTES),
                                baseObject,
                                DerefAliases.DEREF_ALWAYS,
                                Filter.parse(filter),
                                Scope.WHOLE_SUBTREE,
                                sizeLimitEntries,
                                timeLimitSeconds,
                                false)
                                .controlsManageDsaIt(manageDsaIt))
                .compose((results)->{
                    assertFalse(results.isEmpty());
                    results=new ArrayList<>(results);
                    assertTrue(results.remove(results.size()-1).message().isDone());
                    results.sort(
                            Comparator.<ControlsMessage<SearchResult>, Integer>comparing(
                                            (result)->result.message().isReferral()?0:1)
                                    .thenComparing((result)->result.message().isReferral()
                                            ?result.message().asReferral().uris().toString()
                                            :result.message().asEntry().objectName()));
                    return Lava.complete(results.stream()
                            .map(ControlsMessage::message)
                            .toList());
                });
    }

    private @NotNull Lava<Void> testSearchSizeTimeLimit(InetSocketAddress serverAddress) {
        record Params(int limit, boolean sizeTime) {
        }
        List<@NotNull Params> params=LdapConnectionTest.interestingIntegers()
                .flatMap((limit)->Streams.of(false, true)
                        .map((sizeTime)->new Params(limit, sizeTime)))
                .toList();
        return withConnection(
                new Function<@NotNull LdapConnection, @NotNull Lava<Void>>() {
                    @Override
                    public @NotNull Lava<Void> apply(@NotNull LdapConnection connection) throws Throwable {
                        return loop(connection, params.iterator());
                    }

                    public @NotNull Lava<Void> loop(
                            @NotNull LdapConnection connection, @NotNull Iterator<@NotNull Params> iterator)
                            throws Throwable {
                        if (!iterator.hasNext()) {
                            return Lava.VOID;
                        }
                        Params params=iterator.next();
                        return testSearch(
                                "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                connection,
                                "(objectClass=*)",
                                false,
                                connection.messageIdGenerator(),
                                params.sizeTime?params.limit:0,
                                params.sizeTime?0:params.limit)
                                .compose((results)->{
                                    assertEquals(1, results.size());
                                    assertTrue(results.get(0).isEntry());
                                    assertEquals(
                                            "cn=User 0,ou=Users,dc=test,dc=ldap4j,dc=gds,dc=hu",
                                            results.get(0).asEntry().objectName());
                                    return loop(connection, iterator);
                                });
                    }
                },
                serverAddress);
    }

    private static <T> @NotNull Lava<T> withConnection(
            @NotNull Function<@NotNull LdapConnection, @NotNull Lava<T>> function,
            @NotNull InetSocketAddress serverAddress) {
        return Closeable.withCloseable(
                ()->LdapConnection.factory(
                        JavaAsyncChannelConnection.factory(
                                null,
                                Map.of()),
                        serverAddress,
                        TlsSettings.noTls()),
                function);
    }
}
