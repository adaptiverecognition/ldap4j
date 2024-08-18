package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.trampoline.TrampolineLdapConnection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
https://github.com/osixia/docker-openldap
*/
public class OpenLdapTest {
    @Test
    public void test() throws Throwable {
        try (GenericContainer<?> container=new GenericContainer<>("osixia/openldap:latest")) {
            container.withCreateContainerCmdModifier((cmd)->cmd.withHostName("ldap"))
                    .withEnv("LDAP_ADMIN_PASSWORD", "Passw0rd")
                    .withEnv("LDAP_TLS", "true")
                    /*.withLogConsumer((frame)->System.out.printf(
                            "container log: %s%n",
                            frame.getUtf8StringWithoutLineEnding()))*/
                    .withNetworkMode("host")
                    .withPrivilegedMode(true)
                    .withStartupTimeout(Duration.of(120L, ChronoUnit.SECONDS));
            container.setPortBindings(List.of("389:389"));
            container.start();

            long endNanos=Clock.SYSTEM_NANO_TIME.delayNanosToEndNanos(10_000_000_000L);
            TrampolineLdapConnection connection=TrampolineLdapConnection.createJavaAsync(
                    null,
                    endNanos,
                    Log.systemErr(),
                    new InetSocketAddress(container.getHost(), 389),
                    TlsSettings.noTls());
            try {
                @NotNull ControlsMessage<BindResponse> bindResponse=connection.writeRequestReadResponseChecked(
                        endNanos,
                        BindRequest.simple("cn=admin,dc=example,dc=org", "Passw0rd".toCharArray())
                                .controlsEmpty());
                assertEquals(LdapResultCode.SUCCESS, bindResponse.message().ldapResult().resultCode2());

                FeatureDiscovery featureDiscovery=FeatureDiscovery.create(
                        connection.search(endNanos, FeatureDiscovery.searchRequest()));
                assertTrue(
                        featureDiscovery.namingContexts
                                .contains("dc=example,dc=org"));
                assertTrue(featureDiscovery.supportedLdapVersions.contains("3"));
                assertTrue(featureDiscovery.supportedSaslMechanisms.contains("GSSAPI"));
            }
            finally {
                connection.close(endNanos);
            }
        }
    }
}
