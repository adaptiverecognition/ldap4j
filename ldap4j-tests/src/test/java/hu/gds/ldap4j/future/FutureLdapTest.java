package hu.gds.ldap4j.future;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.lava.ScheduledExecutorContext;
import hu.gds.ldap4j.ldap.BindRequest;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.LdapServer;
import hu.gds.ldap4j.ldap.PartialAttribute;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureLdapTest {
    @Test
    public void test() throws Throwable {
        ScheduledExecutorService executor=Executors.newScheduledThreadPool(4);
        try (LdapServer ldapServer=new LdapServer(false, 0, 0)) {
            ldapServer.start();
            Log log=Log.systemErr();
            Context context=ScheduledExecutorContext.createDelayNanos(AbstractTest.TIMEOUT_NANOS, executor, log);
            CompletableFuture<Void> stage=Futures.compose(
                    (ignore)->testPool(executor, ldapServer.localAddressClearText(), log),
                    ()->testDirect(executor, ldapServer.localAddressClearText(), log));
            JoinCallback<Void> join=Callback.join(context);
            Futures.handle(join, context, stage);
            join.joinDelayNanos(AbstractTest.TIMEOUT_NANOS);
        }
        finally {
            executor.shutdown();
        }
    }

    private @NotNull CompletableFuture<Void> testConnection(@NotNull FutureLdapConnection connection) {
        Map.Entry<String, String> user=LdapServer.USERS.entrySet().iterator().next();
        return Futures.compose(
                (bindResponse)->{
                    int index=user.getKey().indexOf(',');
                    assertTrue(0<index);
                    String first=user.getKey().substring(0, index);
                    String base=user.getKey().substring(index+1);
                    index=first.indexOf('=');
                    assertTrue(0<index);
                    String attribute=first.substring(0, index);
                    String value=first.substring(index+1);
                    return Futures.compose(
                            (searchResults)->{
                                assertEquals(2, searchResults.size(), searchResults.toString());
                                assertTrue(searchResults.get(0).message().isEntry());
                                SearchResult.Entry entry=searchResults.get(0).message().asEntry();
                                assertEquals(user.getKey(), entry.objectName());
                                assertEquals(1, entry.attributes().size());
                                PartialAttribute attribute2=entry.attributes().get(0);
                                assertNotNull(attribute2);
                                assertEquals(attribute, attribute2.type());
                                assertEquals(List.of(value), attribute2.values());
                                assertTrue(searchResults.get(1).message().isDone());
                                return CompletableFuture.completedFuture(null);
                            },
                            ()->connection.search(
                                    new SearchRequest(
                                            List.of(attribute),
                                            base,
                                            DerefAliases.DEREF_ALWAYS,
                                            Filter.parse("(&(objectClass=*)(%s=%s))".formatted(attribute, value)),
                                            Scope.WHOLE_SUBTREE,
                                            128,
                                            10,
                                            false)
                                            .controlsEmpty()));
                },
                ()->connection.writeRequestReadResponseChecked(
                        BindRequest.simple(
                                        user.getKey(),
                                        user.getValue().toCharArray())
                                .controlsEmpty()));
    }

    private @NotNull CompletableFuture<Void> testDirect(
            @NotNull ScheduledExecutorService executor,
            @NotNull InetSocketAddress ldapClearTextAddress,
            @NotNull Log log) throws Throwable {
        return Futures.withClose(
                FutureLdapConnection::close,
                FutureLdapConnection.factoryJavaAsync(
                        null,
                        executor,
                        log,
                        ldapClearTextAddress,
                        AbstractTest.TIMEOUT_NANOS,
                        LdapServer.clientTls(false, true, true)),
                this::testConnection);
    }

    private @NotNull CompletableFuture<Void> testPool(
            @NotNull ScheduledExecutorService executor,
            @NotNull InetSocketAddress ldapClearTextAddress,
            @NotNull Log log) {
        return Futures.withClose(
                FutureLdapPool::close,
                ()->FutureLdapPool.createJavaAsync(
                        (loopGroup)->CompletableFuture.completedFuture(null),
                        ()->CompletableFuture.completedFuture(null),
                        executor,
                        log,
                        4,
                        ldapClearTextAddress,
                        AbstractTest.TIMEOUT_NANOS,
                        LdapServer.clientTls(false, true, true)),
                (pool)->pool.lease(this::testConnection));
    }
}
