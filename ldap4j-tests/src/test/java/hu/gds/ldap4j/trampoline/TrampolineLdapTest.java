package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.TestLog;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrampolineLdapTest {
    @Test
    public void test() throws Throwable {
        try (LdapServer ldapServer=new LdapServer(false, 0, 0);
             Trampoline trampoline=new Trampoline(Log.systemErr())) {
            ldapServer.start();
            long endNanos=trampoline.clock().delayNanosToEndNanos(AbstractTest.TIMEOUT_NANOS);
            TestLog log=new TestLog();
            testDirect(endNanos, ldapServer.localAddressClearText(), log, false);
            testDirect(endNanos, ldapServer.localAddressClearText(), log, true);
            log.assertEmpty();
        }
    }

    private void testConnection(
            @NotNull TrampolineLdapConnection connection, long endNanos) throws Throwable {
        Map.Entry<String, String> user=LdapServer.USERS.entrySet().iterator().next();
        connection.bindSimple(endNanos, user.getKey(), user.getValue().toCharArray());
        int index=user.getKey().indexOf(',');
        assertTrue(0<index);
        String first=user.getKey().substring(0, index);
        String base=user.getKey().substring(index+1);
        index=first.indexOf('=');
        assertTrue(0<index);
        String attribute=first.substring(0, index);
        String value=first.substring(index+1);
        @NotNull List<@NotNull SearchResult> searchResults=connection.search(
                endNanos,
                false,
                new SearchRequest(
                        List.of(attribute),
                        base,
                        DerefAliases.DEREF_ALWAYS,
                        Filter.parse("(&(objectClass=*)(%s=%s))".formatted(attribute, value)),
                        Scope.WHOLE_SUBTREE,
                        128,
                        10,
                        false));
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
    }

    private void testDirect(
            long endNanos, @NotNull InetSocketAddress ldapClearTextAddress,
            @NotNull Log log, boolean poll) throws Throwable {
        @NotNull TrampolineLdapConnection connection;
        if (poll) {
            connection=TrampolineLdapConnection.createJavaPoll(
                    endNanos,
                    log,
                    ldapClearTextAddress,
                    LdapServer.clientTls(false, true, true));
        }
        else {
            connection=TrampolineLdapConnection.createJavaAsync(
                    null,
                    endNanos,
                    log,
                    ldapClearTextAddress,
                    LdapServer.clientTls(false, true, true));
        }
        try {
            testConnection(connection, endNanos);
        }
        finally {
            connection.close(endNanos);
        }
    }
}
