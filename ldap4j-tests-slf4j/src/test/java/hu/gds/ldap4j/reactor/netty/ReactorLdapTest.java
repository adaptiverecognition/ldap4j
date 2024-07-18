package hu.gds.ldap4j.reactor.netty;

import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.LdapResultCode;
import hu.gds.ldap4j.ldap.LdapServer;
import hu.gds.ldap4j.ldap.PartialAttribute;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.reactor.Monos;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootApplication
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReactorLdapTest {
    public static final String LDAP_CLEAR_TEXT_ADDRESS="ldap-clear-text-address";
    public static final String LDAP_CLEAR_TEXT_PORT="ldap-clear-text-port";
    public static final long TIMEOUT_NANOS=10_000_000_000L;
    public static final String URL="/ldap-test";

    @Component
    public static class Handler {
        public Mono<ServerResponse> test(ServerRequest request) {
            Optional<String> ldapClearTextAddressValue=request.queryParam(LDAP_CLEAR_TEXT_ADDRESS);
            Optional<String> ldapClearTextPortValue=request.queryParam(LDAP_CLEAR_TEXT_PORT);
            assertTrue(ldapClearTextAddressValue.isPresent());
            assertTrue(ldapClearTextPortValue.isPresent());
            InetSocketAddress ldapClearTextAddress=new InetSocketAddress(
                    ldapClearTextAddressValue.get(),
                    Integer.parseInt(ldapClearTextPortValue.get()));
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(testMono(ldapClearTextAddress), Boolean.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Router {
        @Bean
        public RouterFunction<ServerResponse> route(Handler handler) {
            return RouterFunctions
                    .route(
                            RequestPredicates.GET(URL)
                                    .and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                            handler::test);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void test() throws Throwable {
        try (LdapServer ldapServer=new LdapServer(false, 0, 0)) {
            ldapServer.start();
            webTestClient
                    .get()
                    .uri((uriBuilder)->uriBuilder
                            .path(URL)
                            .queryParam(LDAP_CLEAR_TEXT_ADDRESS, ldapServer.localAddressClearText().getHostString())
                            .queryParam(LDAP_CLEAR_TEXT_PORT, ldapServer.localAddressClearText().getPort())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .value((result)->Assertions.assertThat(result).isEqualTo(true));
        }
    }

    private static @NotNull Mono<Void> testConnection(ReactorLdapConnection connection) {
        Map.Entry<String, String> user=LdapServer.USERS.entrySet().iterator().next();
        return Monos.compose(
                connection.bindSimple(user.getKey(), user.getValue().toCharArray()),
                (bindResponse)->{
                    assertEquals(LdapResultCode.SUCCESS, bindResponse.ldapResult().resultCode2());
                    int index=user.getKey().indexOf(',');
                    assertTrue(0<index);
                    String first=user.getKey().substring(0, index);
                    String base=user.getKey().substring(index+1);
                    index=first.indexOf('=');
                    assertTrue(0<index);
                    String attribute=first.substring(0, index);
                    String value=first.substring(index+1);
                    return Monos.compose(
                            connection.search(
                                    false,
                                    new SearchRequest(
                                            List.of(attribute),
                                            base,
                                            DerefAliases.DEREF_ALWAYS,
                                            Filter.parse("(&(objectClass=*)(%s=%s))".formatted(attribute, value)),
                                            Scope.WHOLE_SUBTREE,
                                            128,
                                            10,
                                            false)),
                            (searchResults)->{
                                assertEquals(2, searchResults.size(), searchResults.toString());
                                assertTrue(searchResults.get(0).isEntry());
                                SearchResult.Entry entry=searchResults.get(0).asEntry();
                                assertEquals(user.getKey(), entry.objectName());
                                assertEquals(1, entry.attributes().size());
                                PartialAttribute attribute2=entry.attributes().get(0);
                                assertNotNull(attribute2);
                                assertEquals(attribute, attribute2.type());
                                assertEquals(List.of(value), attribute2.values());
                                assertTrue(searchResults.get(1).isDone());
                                return Mono.empty();
                            });
                });
    }

    private static @NotNull Mono<Void> testDirect(
            InetSocketAddress ldapClearTextAddress) throws Throwable {
        return ReactorLdapConnection.withConnection(
                (eventLoopGroup)->Mono.empty(),
                ()->Mono.just(HttpResources.get().onClient(false)),
                ReactorLdapTest::testConnection,
                ldapClearTextAddress,
                TIMEOUT_NANOS,
                LdapServer.clientTls(false, true, true));
    }

    private static @NotNull Mono<@NotNull Boolean> testMono(InetSocketAddress ldapClearTextAddress) {
        return Monos.compose(
                Monos.supplier(()->testDirect(ldapClearTextAddress)),
                (ignore0)->Monos.compose(
                        Monos.supplier(()->testPool(ldapClearTextAddress)),
                        (ignore1)->Mono.just(true)));
    }

    private static @NotNull Mono<Void> testPool(InetSocketAddress ldapClearTextAddress) throws Throwable {
        return Monos.compose(
                ReactorLdapPool.create(
                        (eventLoopGroup)->Mono.empty(),
                        ()->Mono.just(HttpResources.get().onClient(false)),
                        4,
                        ldapClearTextAddress,
                        TIMEOUT_NANOS,
                        LdapServer.clientTls(false, true, true)),
                (pool)->Monos.finallyGet(
                        pool::close,
                        ()->pool.lease(ReactorLdapTest::testConnection)));
    }
}