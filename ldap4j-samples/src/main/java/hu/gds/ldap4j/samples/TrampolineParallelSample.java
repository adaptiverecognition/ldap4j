package hu.gds.ldap4j.samples;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.ldap.BindRequest;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.LdapMessage;
import hu.gds.ldap4j.ldap.MessageIdGenerator;
import hu.gds.ldap4j.ldap.ParallelMessageReader;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.trampoline.TrampolineLdapConnection;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class TrampolineParallelSample {
    public static void main(String[] args) throws Throwable {
        System.out.println("ldap4j trampoline parallel sample");

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
            connection.writeRequestReadResponseChecked(
                    endNanos,
                    BindRequest.simple(
                                    "cn=read-only-admin,dc=example,dc=com",
                                    "password".toCharArray())
                            .controlsEmpty());
            System.out.println("bound");

            ControlsMessage<SearchRequest> searchRequest=new SearchRequest(
                    List.of("uniqueMember"), // attributes
                    "dc=example,dc=com", // base object
                    DerefAliases.DEREF_ALWAYS,
                    Filter.parse("(objectClass=*)"),
                    Scope.WHOLE_SUBTREE,
                    100, // size limit
                    10, // time limit
                    false) // types only
                    .controlsEmpty();

            // count all the entries + done
            int resultSize=connection.search(endNanos, searchRequest).size();
            System.out.printf("result size: %,d%n", resultSize);

            // start requests in parallel
            int parallelRequests=10;
            System.out.printf("parallel requests: %,d%n", parallelRequests);
            for (int ii=0; parallelRequests>ii; ++ii) {
                connection.writeMessage(
                        endNanos,
                        searchRequest,
                        MessageIdGenerator.constant(ii+1));
            }

            // read all result
            int[] counts=new int[parallelRequests];
            int inversions=0;
            while (true) {
                @NotNull Map<@NotNull Integer, ParallelMessageReader<?, @NotNull LdapMessage<SearchResult>>> readers
                        =new HashMap<>(parallelRequests);
                for (int ii=parallelRequests-1; 0<=ii; --ii) {
                    if (resultSize!=counts[ii]) {
                        readers.put(ii+1, SearchResult.READER.parallel(Function::identity));
                    }
                }
                if (readers.isEmpty()) {
                    break;
                }
                @NotNull LdapMessage<SearchResult> searchResult
                        =connection.readMessageCheckedParallel(endNanos, readers::get);
                int index=searchResult.messageId()-1;
                ++counts[index];
                for (int ii=index-1; 0<=ii; --ii) {
                    inversions+=resultSize-counts[ii];
                }
            }
            System.out.printf("inversions: %,d%n", inversions);
        }
        finally {
            // release resources, timeout only affects the LDAP and TLS shutdown sequences
            connection.close(endNanos);
        }
    }
}
