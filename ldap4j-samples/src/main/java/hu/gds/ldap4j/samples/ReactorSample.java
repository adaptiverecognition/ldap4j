package hu.gds.ldap4j.samples;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.ldap.BindRequest;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.reactor.netty.ReactorLdapConnection;
import hu.gds.ldap4j.reactor.netty.ReactorLdapPool;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class ReactorSample {
    @RestController
    public static class Controller {
        @Autowired
        public EventLoopGroup eventLoopGroup;
        @Autowired
        public ReactorLdapPool pool;

        @GetMapping(value = "/", produces = "text/html")
        public String index() {
            return "<html><body>"
                    +"<a href=\"no-pool\">no pool</a><br>"
                    +"<a href=\"pool\">pool</a><br>"
                    +"</body></html>";
        }

        @GetMapping(value = "/no-pool", produces = "text/html")
        public Mono<String> noPool() {
            StringBuilder output=new StringBuilder();
            output.append("<html><body>");
            output.append("ldap4j reactor no-pool sample<br>");
            // create a connection, and guard the computation
            return ReactorLdapConnection.withConnection(
                            (evenLoopGroup)->Mono.empty(), // event loop group close
                            ()->Mono.just(eventLoopGroup), // event loop group factory
                            (connection)->run(connection, output),
                            new InetSocketAddress("ldap.forumsys.com", 389),
                            10_000_000_000L, // timeout
                            TlsSettings.noTls()) // plaint-text connection
                    .flatMap((ignore)->{
                        output.append("</body></html>");
                        return Mono.just(output.toString());
                    });
        }

        @GetMapping(value = "/pool", produces = "text/html")
        public Mono<String> pool() {
            StringBuilder output=new StringBuilder();
            output.append("<html><body>");
            output.append("ldap4j reactor pool sample<br>");
            // lease a connection, and guard the computation
            return pool.lease((connection)->run(connection, output))
                    .flatMap((ignore)->{
                        output.append("</body></html>");
                        return Mono.just(output.toString());
                    });
        }

        private Mono<Object> run(ReactorLdapConnection connection, StringBuilder output) {
            output.append("connected<br>");
            // authenticate
            return connection.writeRequestReadResponseChecked(
                            BindRequest.simple(
                                            "cn=read-only-admin,dc=example,dc=com",
                                            "password".toCharArray())
                                    .controlsEmpty())
                    .flatMap((ignore)->{
                        output.append("bound<br>");
                        try {
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
                        }
                        catch (Throwable throwable) {
                            return Mono.error(throwable);
                        }
                    })
                    .flatMap((searchResults)->{
                        output.append("mathematicians:<br>");
                        searchResults.stream()
                                .map(ControlsMessage::message)
                                .filter(SearchResult::isEntry)
                                .map(SearchResult::asEntry)
                                .flatMap((entry)->entry.attributes().stream())
                                .filter((attribute)->"uniqueMember".equals(attribute.type().utf8()))
                                .flatMap((attribute)->attribute.values().stream())
                                .forEach((value)->{
                                    output.append(value);
                                    output.append("<br>");
                                });
                        return Mono.just(new Object());
                    });
        }
    }

    // Netty event loop group for the connection pool, and un-pooled connections
    @Bean
    public EventLoopGroup evenLoopGroup() {
        return new NioEventLoopGroup(4);
    }

    public static void main(String[] args) {
        SpringApplication.run(ReactorSample.class, args);
    }

    // connection pool
    @Bean
    public ReactorLdapPool pool(@Autowired EventLoopGroup eventLoopGroup) {
        return ReactorLdapPool.create(
                eventLoopGroup,
                (eventLoopGroup2)->Mono.empty(), // event loop group close
                Log.slf4j(), // log to SLF4J
                4, // pool size
                new InetSocketAddress("ldap.forumsys.com", 389),
                10_000_000_000L, // timeout
                TlsSettings.noTls()); // plaint-text connection
    }
}
