package hu.gds.ldap4j.samples;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.future.FutureLdapConnection;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FutureSample {
    public static void main(String[] args) throws Throwable {
        System.out.println("ldap4j future sample");
        // new thread pool
        ScheduledExecutorService executor=Executors.newScheduledThreadPool(8);
        try {
            // connect
            CompletableFuture<Void> future=FutureLdapConnection.factoryJavaAsync(
                            null, // use the global asynchronous channel group
                            executor,
                            Log.systemErr(),
                            new InetSocketAddress("ldap.forumsys.com", 389),
                            10_000_000_000L, // timeout
                            TlsSettings.noTls()) // plain-text connection
                    .get()
                    .thenCompose((connection)->{
                        System.out.println("connected");
                        // authenticate
                        CompletableFuture<Void> rest=connection.bindSimple(
                                        "cn=read-only-admin,dc=example,dc=com", "password".toCharArray())
                                .thenCompose((ignore)->{
                                    System.out.println("bound");
                                    try {
                                        // look up mathematicians
                                        return connection.search(
                                                false,
                                                new SearchRequest(
                                                        List.of("uniqueMember"), // attributes
                                                        "ou=mathematicians,dc=example,dc=com", // base object
                                                        DerefAliases.DEREF_ALWAYS,
                                                        Filter.parse("(objectClass=*)"),
                                                        Scope.WHOLE_SUBTREE,
                                                        100, // size limit
                                                        10, // time limit
                                                        false)); // types only
                                    }
                                    catch (Throwable throwable) {
                                        return CompletableFuture.failedFuture(throwable);
                                    }
                                })
                                .thenCompose((searchResults)->{
                                    System.out.println("mathematicians:");
                                    searchResults.stream()
                                            .filter(SearchResult::isEntry)
                                            .map(SearchResult::asEntry)
                                            .flatMap((entry)->entry.attributes().stream())
                                            .filter((attribute)->"uniqueMember".equals(attribute.type()))
                                            .flatMap((attribute)->attribute.values().stream())
                                            .forEach(System.out::println);
                                    return CompletableFuture.completedFuture(null);
                                });
                        // release resources, timeout only affects the LDAP and TLS shutdown sequences
                        return rest
                                .thenCompose((ignore)->connection.close())
                                .exceptionallyCompose((ignore)->connection.close());
                    });
            //wait for the result in this thread
            future.get(10_000_000_000L, TimeUnit.NANOSECONDS);
        }
        finally {
            executor.shutdown();
        }
    }
}
