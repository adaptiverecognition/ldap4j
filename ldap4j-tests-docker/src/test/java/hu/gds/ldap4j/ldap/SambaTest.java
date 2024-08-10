package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.trampoline.TrampolineLdapConnection;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
https://github.com/diegogslomp/samba-ad-dc
https://hub.docker.com/r/diegogslomp/samba-ad-dc
*/
public class SambaTest {
    @Test
    public void test() throws Throwable {
        try (GenericContainer<?> container=new GenericContainer<>("diegogslomp/samba-ad-dc:latest")) {
            container.withCreateContainerCmdModifier((cmd)->cmd.withHostName("DC1"))
                    .withEnv("REALM", "SAMDOM.EXAMPLE.COM")
                    .withEnv("DOMAIN", "SAMDOM")
                    .withEnv("ADMIN_PASS", "Passw0rd")
                    .withEnv("DNS_FORWARDER", "8.8.8.8")
                    //.withLogConsumer((frame)->System.out.printf(
                    //        "container log: %s%n",
                    //        frame.getUtf8StringWithoutLineEnding()))
                    .withNetworkMode("host")
                    .withPrivilegedMode(true)
                    //.withFileSystemBind("dc1_etc", "/usr/local/samba/etc", BindMode.READ_WRITE)
                    //.withFileSystemBind("dc1_private", "/usr/local/samba/private", BindMode.READ_WRITE)
                    //.withFileSystemBind("dc1_var", "/usr/local/samba/var", BindMode.READ_WRITE)
                    .withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS));
            container.setPortBindings(List.of("389:389"));
            container.start();

            long endNanos=Clock.SYSTEM_NANO_TIME.delayNanosToEndNanos(10_000_000_000L);
            TrampolineLdapConnection connection=TrampolineLdapConnection.createJavaAsync(
                    null,
                    endNanos,
                    Log.systemErr(),
                    new InetSocketAddress(container.getHost(), 389),
                    TlsSettings.tls()
                            .client(true)
                            .trustEverything()
                            .verifyHostname(false)
                            .noPrivateKey()
                            .startTls(true)
                            .build());
            try {
                @NotNull ControlsMessage<BindResponse> bindResponse=connection.writeRequestReadResponseChecked(
                        endNanos,
                        BindRequest.simple("Administrator@SAMDOM", "Passw0rd".toCharArray())
                                .controlsEmpty());
                assertEquals(LdapResultCode.SUCCESS, bindResponse.message().ldapResult().resultCode2());

                @NotNull List<@NotNull ControlsMessage<SearchResult>> searchResponse=connection.search(
                        endNanos,
                        new SearchRequest(
                                List.of("name"),
                                "CN=Administrator,CN=Users,DC=samdom,DC=example,DC=com",
                                DerefAliases.NEVER_DEREF_ALIASES,
                                Filter.parse("(objectClass=*)"),
                                Scope.BASE_OBJECT,
                                10,
                                10,
                                false)
                                .controlsEmpty());
                assertEquals(2, searchResponse.size());
                assertTrue(searchResponse.get(0).message().isEntry());
                assertEquals(
                        List.of(new PartialAttribute("name", List.of("Administrator"))),
                        searchResponse.get(0).message().asEntry().attributes());
                assertTrue(searchResponse.get(1).message().isDone());
            }
            finally {
                connection.close(endNanos);
            }
        }
    }
}
