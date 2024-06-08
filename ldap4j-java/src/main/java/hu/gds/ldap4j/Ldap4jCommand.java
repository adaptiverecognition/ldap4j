package hu.gds.ldap4j;

import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.PartialAttribute;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.CryptoUtil;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.trampoline.Trampoline;
import hu.gds.ldap4j.trampoline.TrampolineLdapConnection;
import java.io.InputStream;
import java.io.Serial;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class Ldap4jCommand {
    private static final String BIND_SIMPLE_ARGUMENT="argument";
    private static final String BIND_SIMPLE_CONSOLE="console";
    private static final String BIND_SIMPLE_FILE="file";
    private static final String COMMAND_BIND_SIMPLE="bind-simple";
    private static final String COMMAND_FAST_BIND="fast-bind";
    private static final String COMMAND_SEARCH="search";
    private static final String CONNECTION_OPTION_DON_T_VERIFY_HOSTNAME="-don-t-verify-hostname";
    private static final String CONNECTION_OPTION_PLAINTEXT="-plaintext";
    private static final String CONNECTION_OPTION_PORT="-port";
    private static final String CONNECTION_OPTION_PORT_DEFAULT="-port-default";
    private static final String CONNECTION_OPTION_STARTTLS="-starttls";
    private static final String CONNECTION_OPTION_TIMEOUT_NANOS="-timeout-nanos";
    private static final String CONNECTION_OPTION_TLS="-tls";
    private static final String CONNECTION_OPTION_TRUST="-trust";
    private static final String CONNECTION_OPTION_TRUST_EVERYONE="-trust-everyone";
    private static final String CONNECTION_OPTION_VERIFY_HOSTNAME="-verify-hostname";
    private static final String SEARCH_OPTION_ATTRIBUTE="-attribute";
    private static final String SEARCH_OPTION_DEREF_ALIASES_ALWAYS="-deref-aliases-always";
    private static final String SEARCH_OPTION_DEREF_ALIASES_FINDING_BASE_OBJ="-deref-aliases-finding-base-obj";
    private static final String SEARCH_OPTION_DEREF_ALIASES_IN_SEARCHING="-deref-aliases-in-searching";
    private static final String SEARCH_OPTION_DEREF_ALIASES_NEVER="-deref-aliases-never";
    private static final String SEARCH_OPTION_DON_T_MANAGE_DSA_IT="-don-t-manage-dsa-it";
    private static final String SEARCH_OPTION_MANAGE_DSA_IT="-manage-dsa-it";
    private static final String SEARCH_OPTION_SCOPE_BASE_OBJECT="-scope-base-object";
    private static final String SEARCH_OPTION_SCOPE_SINGLE_LEVEL="-scope-single-level";
    private static final String SEARCH_OPTION_SCOPE_WHOLE_SUBTREE="-scope-whole-subtree";
    private static final String SEARCH_OPTION_SIZE_LIMIT_ENTRIES="-size-limit-entries";
    private static final String SEARCH_OPTION_TIME_LIMIT_SECS="-time-limit-secs";
    private static final String SEARCH_OPTION_TYPES_AND_VALUES="-types-and-values";
    private static final String SEARCH_OPTION_TYPES_ONLY="-types-only";

    private static class Arguments {
        private final Deque<@NotNull String> arguments;

        public Arguments(@NotNull String[] args) {
            this.arguments=new ArrayDeque<>(Arrays.asList(args));
        }

        public boolean isEmpty() {
            return arguments.isEmpty();
        }

        public @NotNull String removeFirst(@NotNull String emptyMessage) {
            if (arguments.isEmpty()) {
                throw new SyntaxException(emptyMessage);
            }
            return arguments.removeFirst();
        }

        public int removeFirstInt(@NotNull String emptyMessage) {
            try {
                return Integer.parseInt(removeFirst(emptyMessage));
            }
            catch (NumberFormatException ex) {
                throw new SyntaxException(ex.toString());
            }
        }

        public long removeFirstLong(@NotNull String emptyMessage) {
            try {
                return Long.parseLong(removeFirst(emptyMessage));
            }
            catch (NumberFormatException ex) {
                throw new SyntaxException(ex.toString());
            }
        }

        public boolean removeOptional(@NotNull String value) {
            Objects.requireNonNull(value, "value");
            if (arguments.isEmpty()) {
                return false;
            }
            if (value.equals(arguments.peekFirst())) {
                arguments.removeFirst();
                return true;
            }
            return false;
        }
    }

    private record Config(
            @NotNull Function<@NotNull Long, @NotNull Function<@NotNull Log, @NotNull TrampolineLdapConnection>>
                    connectionFactory,
            @NotNull InetSocketAddress remoteAddress,
            long timeoutNanos) {
        private Config(
                @NotNull Function<@NotNull Long, @NotNull Function<@NotNull Log, @NotNull TrampolineLdapConnection>>
                        connectionFactory,
                @NotNull InetSocketAddress remoteAddress,
                long timeoutNanos) {
            this.connectionFactory=Objects.requireNonNull(connectionFactory, "connectionFactory");
            this.remoteAddress=Objects.requireNonNull(remoteAddress, "remoteAddress");
            this.timeoutNanos=timeoutNanos;
        }
    }

    private static class SyntaxException extends RuntimeException {
        @Serial
        private static final long serialVersionUID=0L;

        public SyntaxException(String message) {
            super(message);
        }
    }

    private static void command(
            @NotNull Arguments arguments, @NotNull TrampolineLdapConnection connection,
            long endNanos) throws Throwable {
        String command=arguments.removeFirst("should have a command");
        switch (command) {
            case COMMAND_BIND_SIMPLE -> commandBindSimple(arguments, connection, endNanos);
            case COMMAND_FAST_BIND -> commandFastBind(connection, endNanos);
            case COMMAND_SEARCH -> commandSearch(arguments, connection, endNanos);
            default -> throw new SyntaxException("unknown command %s".formatted(command));
        }
    }

    private static void commandBindSimple(
            @NotNull Arguments arguments, @NotNull TrampolineLdapConnection connection,
            long endNanos) throws Throwable {
        String name=arguments.removeFirst("missing bind name");
        String passwordSource=arguments.removeFirst("missing password source");
        String password=switch (passwordSource) {
            case BIND_SIMPLE_ARGUMENT -> arguments.removeFirst("missing bind password");
            case BIND_SIMPLE_CONSOLE -> new String(System.console().readPassword("Password for %s: ", name));
            case BIND_SIMPLE_FILE -> Files.readString(Paths.get(
                    arguments.removeFirst("missing bind password file")));
            default -> throw new SyntaxException("unknown password source %s".formatted(passwordSource));
        };
        System.out.printf("bind simple, %s%n", name);
        connection.bindSimple(name, endNanos, password.toCharArray());
        System.out.printf("bind successful%n");
    }

    private static void commandFastBind(@NotNull TrampolineLdapConnection connection, long endNanos) throws Throwable {
        System.out.printf("fast bind%n");
        connection.fastBind(endNanos);
        System.out.printf("fast bind successful%n");
    }

    private static void commandSearch(
            @NotNull Arguments arguments, @NotNull TrampolineLdapConnection connection,
            long endNanos) throws Throwable {
        @NotNull List<@NotNull String> attributes=new ArrayList<>();
        @NotNull DerefAliases derefAliases=DerefAliases.DEREF_ALWAYS;
        boolean manageDsaIt=false;
        @NotNull Scope scope=Scope.WHOLE_SUBTREE;
        int sizeLimitEntries=0;
        int timeLimitSeconds=0;
        boolean typesOnly=false;
        while (!arguments.isEmpty()) {
            if (arguments.removeOptional(SEARCH_OPTION_ATTRIBUTE)) {
                attributes.add(arguments.removeFirst("missing attribute name"));
            }
            else if (arguments.removeOptional(SEARCH_OPTION_DEREF_ALIASES_ALWAYS)) {
                derefAliases=DerefAliases.DEREF_ALWAYS;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_DEREF_ALIASES_FINDING_BASE_OBJ)) {
                derefAliases=DerefAliases.DEREF_FINDING_BASE_OBJ;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_DEREF_ALIASES_IN_SEARCHING)) {
                derefAliases=DerefAliases.DEREF_IN_SEARCHING;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_DEREF_ALIASES_NEVER)) {
                derefAliases=DerefAliases.NEVER_DEREF_ALIASES;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_DON_T_MANAGE_DSA_IT)) {
                manageDsaIt=false;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_MANAGE_DSA_IT)) {
                manageDsaIt=true;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_SCOPE_BASE_OBJECT)) {
                scope=Scope.BASE_OBJECT;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_SCOPE_SINGLE_LEVEL)) {
                scope=Scope.SINGLE_LEVEL;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_SCOPE_WHOLE_SUBTREE)) {
                scope=Scope.WHOLE_SUBTREE;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_SIZE_LIMIT_ENTRIES)) {
                sizeLimitEntries=arguments.removeFirstInt("missing size limit");
            }
            else if (arguments.removeOptional(SEARCH_OPTION_TIME_LIMIT_SECS)) {
                timeLimitSeconds=arguments.removeFirstInt("missing time limit");
            }
            else if (arguments.removeOptional(SEARCH_OPTION_TYPES_AND_VALUES)) {
                typesOnly=false;
            }
            else if (arguments.removeOptional(SEARCH_OPTION_TYPES_ONLY)) {
                typesOnly=true;
            }
            else {
                break;
            }
        }
        @NotNull String baseObject=arguments.removeFirst("missing base object");
        @NotNull Filter filter=Filter.parse(arguments.removeFirst("missing filter"));
        boolean manageDsaIt2=manageDsaIt;
        SearchRequest searchRequest=new SearchRequest(
                attributes, baseObject, derefAliases, filter, scope, sizeLimitEntries, timeLimitSeconds, typesOnly);
        System.out.printf("search%n");
        System.out.printf("\tattributes: %s%n", searchRequest.attributes());
        System.out.printf("\tbase object: %s%n", searchRequest.baseObject());
        System.out.printf("\tderef. aliases: %s%n", searchRequest.derefAliases());
        System.out.printf("\tfilter: %s%n", searchRequest.filter());
        System.out.printf("\tmanage dsa it: %s%n", manageDsaIt2);
        System.out.printf("\tscope: %s%n", searchRequest.scope());
        System.out.printf("\tsize limit: %,d entries%n", searchRequest.sizeLimitEntries());
        System.out.printf("\ttime limit: %,d sec%n", searchRequest.timeLimitSeconds());
        System.out.printf("\ttypes only: %s%n", searchRequest.typesOnly());
        List<SearchResult> searchResults=connection.search(endNanos, manageDsaIt2, searchRequest);
        for (SearchResult searchResult: searchResults) {
            searchResult.visit(new SearchResult.Visitor<>() {
                @Override
                public Void done(@NotNull SearchResult.Done done) {
                    System.out.printf("search done%n");
                    return null;
                }

                @Override
                public Void entry(@NotNull SearchResult.Entry entry) {
                    System.out.printf("search entry%n");
                    System.out.printf("\tdn: %s%n", entry.objectName());
                    for (Map.Entry<String, PartialAttribute> attribute:
                            entry.attributes().entrySet()) {
                        System.out.printf(
                                "\t%s: %s%n",
                                attribute.getKey(),
                                attribute.getValue());
                    }
                    return null;
                }

                @Override
                public Void referral(@NotNull SearchResult.Referral referral) {
                    System.out.printf("search referral%n");
                    for (String uri: referral.uris()) {
                        System.out.printf("\turi: %s%n", uri);
                    }
                    return null;
                }
            });
        }
    }

    private static @NotNull Config config(@NotNull Arguments arguments) throws Throwable {
        enum Tls {
            PLAINTEXT, STARTTLS, TLS
        }
        Integer port=null;
        long timeoutNanos=10_000_000_000L;
        Tls tls=Tls.STARTTLS;
        TlsSettings.Builder tlsSettings=TlsSettings.tls()
                .client(true)
                .clientAuthenticationNone()
                .noPrivateKey()
                .trustEverything()
                .verifyHostname(true);
        while (!arguments.isEmpty()) {
            if (arguments.removeOptional(CONNECTION_OPTION_DON_T_VERIFY_HOSTNAME)) {
                tlsSettings=tlsSettings.verifyHostname(false);
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_PLAINTEXT)) {
                tls=Tls.PLAINTEXT;
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_PORT)) {
                port=arguments.removeFirstInt("missing port number");
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_PORT_DEFAULT)) {
                port=null;
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_STARTTLS)) {
                tls=Tls.STARTTLS;
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_TIMEOUT_NANOS)) {
                timeoutNanos=arguments.removeFirstLong("missing timeout value");
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_TLS)) {
                tls=Tls.TLS;
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_TRUST)) {
                Path path=Paths.get(arguments.removeFirst("missing certificate path"));
                try (InputStream fis=Files.newInputStream(path)) {
                    tlsSettings=tlsSettings.trustCertificates(CryptoUtil.loadPEM(fis));
                }
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_TRUST_EVERYONE)) {
                tlsSettings=tlsSettings.trustEverything();
            }
            else if (arguments.removeOptional(CONNECTION_OPTION_VERIFY_HOSTNAME)) {
                tlsSettings=tlsSettings.verifyHostname(true);
            }
            else {
                break;
            }
        }
        String host=arguments.removeFirst("missing hostname");
        if (null==port) {
            port=switch (tls) {
                case PLAINTEXT, STARTTLS -> 389;
                case TLS -> 636;
            };
        }
        InetSocketAddress remoteAddress=new InetSocketAddress(host, port);
        TlsSettings.Tls tlsSettings2=tlsSettings
                .startTls(Tls.STARTTLS.equals(tls))
                .build();
        Tls tls2=tls;
        return new Config(
                (endNanos)->(log)->TrampolineLdapConnection.createJavaPoll(
                        endNanos,
                        log,
                        remoteAddress,
                        Tls.PLAINTEXT.equals(tls2)
                                ?TlsSettings.noTls()
                                :tlsSettings2),
                remoteAddress,
                timeoutNanos);
    }

    public static void main(@NotNull String[] args) throws Throwable {
        try {
            Arguments arguments=new Arguments(args);
            try (Trampoline trampoline=new Trampoline(Log.systemErr())) {
                @NotNull Config config=config(arguments);
                long endNanos=trampoline.clock().delayNanosToEndNanos(config.timeoutNanos);
                System.out.printf("connecting to %s%n", config.remoteAddress);
                TrampolineLdapConnection connection=config.connectionFactory.apply(endNanos).apply(Log.systemErr());
                try {
                    System.out.printf("connected%n");
                    while (!arguments.isEmpty()) {
                        command(arguments, connection, endNanos);
                    }
                }
                finally {
                    connection.close(endNanos);
                }
            }
        }
        catch (SyntaxException ex) {
            System.out.println(ex.getMessage());
            printUsage();
        }
    }

    public static void printUsage() {
        System.out.printf("ldap4j [connect-parameters...] host [command command-parameters...]...%n");
        System.out.printf("\tconnect parameters%n");
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_DON_T_VERIFY_HOSTNAME);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_PLAINTEXT);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_PORT);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_PORT_DEFAULT);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_STARTTLS);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_TIMEOUT_NANOS);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_TLS);
        System.out.printf("\t\t%s certificate-file%n", CONNECTION_OPTION_TRUST);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_TRUST_EVERYONE);
        System.out.printf("\t\t%s%n", CONNECTION_OPTION_VERIFY_HOSTNAME);
        System.out.printf("\tcommands:%n");
        System.out.printf("\t\t%s name %s password%n", COMMAND_BIND_SIMPLE, BIND_SIMPLE_ARGUMENT);
        System.out.printf("\t\t%s name %s%n", COMMAND_BIND_SIMPLE, BIND_SIMPLE_CONSOLE);
        System.out.printf("\t\t%s name %s password-file%n", COMMAND_BIND_SIMPLE, BIND_SIMPLE_FILE);
        System.out.printf("\t\t%s%n", COMMAND_FAST_BIND);
        System.out.printf("\t\t%s [parameter ...] base-object filter%n", COMMAND_SEARCH);
        System.out.printf("\t\t\t%s attribute-name%n", SEARCH_OPTION_ATTRIBUTE);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_DEREF_ALIASES_ALWAYS);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_DEREF_ALIASES_FINDING_BASE_OBJ);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_DEREF_ALIASES_IN_SEARCHING);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_DEREF_ALIASES_NEVER);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_DON_T_MANAGE_DSA_IT);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_MANAGE_DSA_IT);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_SCOPE_BASE_OBJECT);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_SCOPE_SINGLE_LEVEL);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_SCOPE_WHOLE_SUBTREE);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_SIZE_LIMIT_ENTRIES);
        System.out.printf("\t\t\t%s%n", SEARCH_OPTION_TIME_LIMIT_SECS);
    }
}
