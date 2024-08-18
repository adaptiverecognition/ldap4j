package hu.gds.ldap4j.ldap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FeatureDiscovery {
    public static final @NotNull String NAMING_CONTEXTS="namingContexts";
    public static final @NotNull String SUPPORTED_CONTROL="supportedControl";
    public static final @NotNull String SUPPORTED_EXTENSION="supportedExtension";
    public static final @NotNull String SUPPORTED_FEATURES="supportedFeatures";
    public static final @NotNull String SUPPORTED_LDAP_VERSION="supportedLDAPVersion";
    public static final @NotNull String SUPPORTED_SASL_MECHANISMS="supportedSASLMechanisms";
    public static final @NotNull String VENDOR_NAME="vendorName";
    public static final @NotNull String VENDOR_VERSION="vendorVersion";

    public final @NotNull Set<@NotNull String> namingContexts=new TreeSet<>();
    public final @NotNull List<@NotNull String> referralUris=new ArrayList<>();
    public final @NotNull Set<@NotNull String> supportedControls=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedExtensions=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedFeatures=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedLdapVersions=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedSaslMechanisms=new TreeSet<>();
    public final @NotNull List<@NotNull PartialAttribute> unrecognizedAttributes=new ArrayList<>();
    public final @NotNull Set<@NotNull String> vendorNames=new TreeSet<>();
    public final @NotNull Set<@NotNull String> vendorVersions=new TreeSet<>();

    private void add(@NotNull PartialAttribute attribute) {
        switch (attribute.type()) {
            case NAMING_CONTEXTS -> namingContexts.addAll(attribute.values());
            case SUPPORTED_CONTROL -> supportedControls.addAll(attribute.values());
            case SUPPORTED_EXTENSION -> supportedExtensions.addAll(attribute.values());
            case SUPPORTED_FEATURES -> supportedFeatures.addAll(attribute.values());
            case SUPPORTED_LDAP_VERSION -> supportedLdapVersions.addAll(attribute.values());
            case SUPPORTED_SASL_MECHANISMS -> supportedSaslMechanisms.addAll(attribute.values());
            case VENDOR_NAME -> vendorNames.addAll(attribute.values());
            case VENDOR_VERSION -> vendorVersions.addAll(attribute.values());
            default -> unrecognizedAttributes.add(attribute);
        }
    }

    public void add(@NotNull ControlsMessage<SearchResult> searchResult) throws Throwable {
        searchResult.message()
                .visit(new SearchResult.Visitor<Void>() {
                    @Override
                    public Void done(SearchResult.@NotNull Done done) {
                        return null;
                    }

                    @Override
                    public Void entry(SearchResult.@NotNull Entry entry) {
                        for (@NotNull PartialAttribute attribute: entry.attributes()) {
                            add(attribute);
                        }
                        return null;
                    }

                    @Override
                    public Void referral(SearchResult.@NotNull Referral referral) {
                        referralUris.addAll(referral.uris());
                        return null;
                    }
                });
    }

    public void addAll(@NotNull List<@NotNull ControlsMessage<SearchResult>> searchResults) throws Throwable {
        for (@NotNull ControlsMessage<SearchResult> searchResult: searchResults) {
            add(searchResult);
        }
    }

    public static @NotNull FeatureDiscovery create(
            @NotNull List<@NotNull ControlsMessage<SearchResult>> searchResults) throws Throwable {
        @NotNull FeatureDiscovery featureDiscovery=new FeatureDiscovery();
        featureDiscovery.addAll(searchResults);
        return featureDiscovery;
    }

    private static @NotNull String padding(char padding, int length) {
        if (0>=length) {
            return "";
        }
        if (1==length) {
            return " ";
        }
        return ' '+String.valueOf(padding).repeat(length-2)+' ';
    }

    public void prettyPrint() {
        prettyPrint(System.out);
    }

    public void prettyPrint(@NotNull PrintStream stream) {
        prettyPrintValues(VENDOR_NAME, stream, vendorNames);
        prettyPrintValues(VENDOR_VERSION, stream, vendorVersions);
        prettyPrintValues(SUPPORTED_LDAP_VERSION, stream, supportedLdapVersions);
        prettyPrintValues(SUPPORTED_SASL_MECHANISMS, stream, supportedSaslMechanisms);
        prettyPrintValues(NAMING_CONTEXTS, stream, namingContexts);
        prettyPrintValues("referral URIs", stream, referralUris);
        prettyPrintOIDs(SUPPORTED_CONTROL, supportedControls, stream);
        prettyPrintOIDs(SUPPORTED_EXTENSION, supportedExtensions, stream);
        prettyPrintOIDs(SUPPORTED_FEATURES, supportedFeatures, stream);
        stream.println("unrecognized attributes");
        for (@NotNull PartialAttribute attribute: unrecognizedAttributes) {
            System.out.print("\t");
            System.out.println(attribute);
        }
    }

    private void prettyPrintOIDs(
            @NotNull String name,
            @NotNull Collection<@NotNull String> OIDs,
            @NotNull PrintStream stream) {
        stream.println(name);
        int maxOidLength=0;
        for (var oid: OIDs) {
            maxOidLength=Math.max(maxOidLength, oid.length());
        }
        int line=0;
        for (@NotNull String oid: OIDs) {
            @Nullable String oidName=OID.name(oid);
            System.out.print("\t");
            if (null==oidName) {
                System.out.println(oid);
            }
            else {
                System.out.print(oid);
                System.out.print(padding((0==(line&1))?' ':'.', maxOidLength+2-oid.length()));
                System.out.println(oidName);
            }
            ++line;
        }
    }

    private void prettyPrintValues(
            @NotNull String name,
            @NotNull PrintStream stream,
            @NotNull Collection<@NotNull String> values) {
        stream.println(name);
        for (@NotNull String value: values) {
            System.out.print("\t");
            System.out.println(value);
        }
    }

    public static @NotNull ControlsMessage<SearchRequest> searchRequest() throws Throwable {
        return new SearchRequest(
                List.of(
                        Ldap.ALL_ATTRIBUTES,
                        Ldap.ALL_OPERATIONAL_ATTRIBUTES,
                        NAMING_CONTEXTS,
                        SUPPORTED_CONTROL,
                        SUPPORTED_EXTENSION,
                        SUPPORTED_FEATURES,
                        SUPPORTED_LDAP_VERSION,
                        SUPPORTED_SASL_MECHANISMS,
                        VENDOR_NAME,
                        VENDOR_VERSION),
                "",
                DerefAliases.NEVER_DEREF_ALIASES,
                Filter.parse("(objectClass=*)"),
                Scope.BASE_OBJECT,
                0,
                0,
                false)
                .controlsEmpty();
    }

    @Override
    public String toString() {
        return ("FeatureDiscovery("
                +"namingContexts: %s"
                +", referralUris: %s"
                +", supportedControls: %s"
                +", supportedExtensions: %s"
                +", supportedFeatures: %s"
                +", supportedLdapVersions: %s"
                +", supportedSaslMechanisms: %s"
                +", unrecognizedAttributes: %s"
                +", vendorNames: %s"
                +", vendorVersions: %s)")
                .formatted(
                        namingContexts,
                        referralUris,
                        supportedControls,
                        supportedExtensions,
                        supportedFeatures,
                        supportedLdapVersions,
                        supportedSaslMechanisms,
                        unrecognizedAttributes,
                        vendorNames,
                        vendorVersions);
    }
}
