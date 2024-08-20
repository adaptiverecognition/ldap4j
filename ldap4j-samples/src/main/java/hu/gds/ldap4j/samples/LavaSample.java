package hu.gds.ldap4j.samples;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.ThreadLocalScheduledExecutorContext;
import hu.gds.ldap4j.ldap.BindRequest;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.JavaAsyncChannelConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;

public class LavaSample {
    private static @NotNull Lava<Void> main() {
        return Lava.supplier(()->{
            System.out.println("ldap4j lava sample");
            // create a connection, and guard the computation
            return Closeable.withCloseable(
                    ()->LdapConnection.factory(
                            // use the global asynchronous channel group
                            JavaAsyncChannelConnection.factory(null, Map.of()),
                            new InetSocketAddress("ldap.forumsys.com", 389),
                            TlsSettings.noTls()), // plain-text connection
                    (connection)->{
                        System.out.println("connected");
                        // authenticate
                        return connection.writeRequestReadResponseChecked(
                                        BindRequest.simple(
                                                        "cn=read-only-admin,dc=example,dc=com",
                                                        "password".toCharArray())
                                                .controlsEmpty())
                                .composeIgnoreResult(()->{
                                    System.out.println("bound");
                                    // look up mathematicians
                                    return connection.search(
                                            new SearchRequest(
                                                    List.of("uniqueMember"), // attributes
                                                    "ou=mathematicians,dc=example,dc=com", // base object
                                                    DerefAliases.DEREF_ALWAYS,
                                                    Filter.parse("(objectClass=*)"),
                                                    Scope.WHOLE_SUBTREE,
                                                    100, // size limit
                                                    10, // time limit
                                                    false) // types only
                                                    .controlsEmpty());
                                })
                                .compose((searchResults)->{
                                    System.out.println("mathematicians:");
                                    searchResults.stream()
                                            .map(ControlsMessage::message)
                                            .filter(SearchResult::isEntry)
                                            .map(SearchResult::asEntry)
                                            .flatMap((entry)->entry.attributes().stream())
                                            .filter((attribute)->"uniqueMember".equals(attribute.type().utf8()))
                                            .flatMap((attribute)->attribute.values().stream())
                                            .forEach(System.out::println);
                                    return Lava.VOID;
                                });
                    });
        });
    }

    public static void main(String[] args) throws Throwable {
        // new thread pool
        ScheduledExecutorService executor=Executors.newScheduledThreadPool(Context.defaultParallelism());
        try {
            Context context=ThreadLocalScheduledExecutorContext.createDelayNanos(
                    10_000_000_000L, // timeout
                    executor,
                    Log.systemErr(),
                    Context.defaultParallelism());
            // going to wait for the result in this thread
            JoinCallback<Void> join=Callback.join(context);
            // compute the result
            context.get(join, main());
            // wait for the result
            join.joinEndNanos(context.endNanos());
        }
        finally {
            executor.shutdown();
        }
    }
}
