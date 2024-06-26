package hu.gds.ldap4j.samples;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.trampoline.TrampolineLdapConnection;
import java.net.InetSocketAddress;
import java.util.List;

public class TrampolineSample {
    public static void main(String[] args) throws Throwable {
        System.out.println("ldap4j trampoline sample");
        // single timeout for all operations
        long endNanos=System.nanoTime()+10_000_000_000L;
        TrampolineLdapConnection connection=TrampolineLdapConnection.createJavaPoll(
                endNanos,
                Log.systemErr(), // log everything to the standard error
                new InetSocketAddress("ldap.forumsys.com", 389),
                TlsSettings.noTls()); // plain-text connection
        try {
            System.out.println("connected");
            // authenticate
            connection.bindSimple("cn=read-only-admin,dc=example,dc=com", endNanos, "password".toCharArray());
            System.out.println("bound");
            // look up mathematicians
            List<SearchResult> searchResults=connection.search(
                    endNanos,
                    false, // manage DSA IT
                    new SearchRequest(
                            List.of("uniqueMember"), // attributes
                            "ou=mathematicians,dc=example,dc=com", // base object
                            DerefAliases.DEREF_ALWAYS,
                            Filter.parse("(objectClass=*)"),
                            Scope.WHOLE_SUBTREE,
                            100, // size limit
                            10, // time limit
                            false)); // types only
            System.out.println("mathematicians:");
            searchResults.get(0)
                    .asEntry()
                    .attributes()
                    .get("uniqueMember")
                    .values()
                    .forEach(System.out::println);
        }
        finally {
            // release resources, timeout only affects the LDAP and TLS shutdown sequences
            connection.close(endNanos);
        }
    }
}
