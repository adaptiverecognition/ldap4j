package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.OID;
import hu.gds.ldap4j.ldap.PartialAttribute;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchRequest;
import hu.gds.ldap4j.ldap.SearchResult;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 3674
 */
public class FeatureDiscovery {
    public static final @NotNull String NAMING_CONTEXTS="namingContexts";
    public static final @NotNull String SUPPORTED_CAPABILITIES="supportedCapabilities";
    public static final @NotNull String SUPPORTED_CONTROL="supportedControl";
    public static final @NotNull String SUPPORTED_EXTENSION="supportedExtension";
    public static final @NotNull String SUPPORTED_FEATURES="supportedFeatures";
    public static final @NotNull String SUPPORTED_LDAP_VERSION="supportedLDAPVersion";
    public static final @NotNull String SUPPORTED_SASL_MECHANISMS="supportedSASLMechanisms";
    public static final @NotNull String VENDOR_NAME="vendorName";
    public static final @NotNull String VENDOR_VERSION="vendorVersion";

    public final @NotNull Set<@NotNull String> namingContexts=new TreeSet<>();
    public final @NotNull List<@NotNull String> referralUris=new ArrayList<>();
    public final @NotNull Set<@NotNull String> supportedCapabilities=new TreeSet<>(OID.COMPARATOR);
    public final @NotNull Set<@NotNull String> supportedControls=new TreeSet<>(OID.COMPARATOR);
    public final @NotNull Set<@NotNull String> supportedExtensions=new TreeSet<>(OID.COMPARATOR);
    public final @NotNull Set<@NotNull String> supportedFeatures=new TreeSet<>(OID.COMPARATOR);
    public final @NotNull Set<@NotNull String> supportedLdapVersions=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedSaslMechanisms=new TreeSet<>();
    public final @NotNull List<@NotNull PartialAttribute> unrecognizedAttributes=new ArrayList<>();
    public final @NotNull Set<@NotNull String> vendorNames=new TreeSet<>();
    public final @NotNull Set<@NotNull String> vendorVersions=new TreeSet<>();

    private void add(@NotNull PartialAttribute attribute) {
        switch (attribute.type()) {
            case NAMING_CONTEXTS -> namingContexts.addAll(attribute.values());
            case SUPPORTED_CAPABILITIES -> supportedCapabilities.addAll(attribute.values());
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
        if (4>=length) {
            return " ".repeat(length);
        }
        return ' '+String.valueOf(padding).repeat(length-2)+' ';
    }

    public void prettyPrint() {
        prettyPrint("", System.out, "    ");
    }

    public void prettyPrint(
            @NotNull String headerIndent,
            @NotNull PrintStream stream,
            @NotNull String valueIndent) {
        prettyPrintValues(headerIndent, VENDOR_NAME, stream, valueIndent, vendorNames);
        prettyPrintValues(headerIndent, VENDOR_VERSION, stream, valueIndent, vendorVersions);
        prettyPrintValues(headerIndent, SUPPORTED_LDAP_VERSION, stream, valueIndent, supportedLdapVersions);
        prettyPrintValues(headerIndent, SUPPORTED_SASL_MECHANISMS, stream, valueIndent, supportedSaslMechanisms);
        prettyPrintValues(headerIndent, NAMING_CONTEXTS, stream, valueIndent, namingContexts);
        prettyPrintValues(headerIndent, "referral URIs", stream, valueIndent, referralUris);
        prettyPrintOIDs(headerIndent, SUPPORTED_CAPABILITIES, supportedCapabilities, stream, valueIndent);
        prettyPrintOIDs(headerIndent, SUPPORTED_CONTROL, supportedControls, stream, valueIndent);
        prettyPrintOIDs(headerIndent, SUPPORTED_EXTENSION, supportedExtensions, stream, valueIndent);
        prettyPrintOIDs(headerIndent, SUPPORTED_FEATURES, supportedFeatures, stream, valueIndent);
        stream.print(headerIndent);
        stream.println("unrecognized attributes");
        for (@NotNull PartialAttribute attribute: unrecognizedAttributes) {
            System.out.print(valueIndent);
            System.out.println(attribute);
        }
    }

    private void prettyPrintOIDs(
            @NotNull String headerIndent,
            @NotNull String name,
            @NotNull Collection<@NotNull String> OIDs,
            @NotNull PrintStream stream,
            @NotNull String valueIndent) {
        stream.print(headerIndent);
        stream.println(name);
        int maxOidLength=0;
        for (@NotNull String oid: OIDs) {
            maxOidLength=Math.max(maxOidLength, oid.length());
        }
        boolean dots=true;
        for (@NotNull String oid: OIDs) {
            @Nullable String oidName=OID.name(oid);
            System.out.print(valueIndent);
            if (null==oidName) {
                System.out.println(oid);
                dots=true;
            }
            else {
                System.out.print(oid);
                System.out.print(padding(dots?'.':' ', maxOidLength+2-oid.length()));
                System.out.println(oidName);
                dots=!dots;
            }
        }
    }

    private void prettyPrintValues(
            @NotNull String headerIndent,
            @NotNull String name,
            @NotNull PrintStream stream,
            @NotNull String valueIndent,
            @NotNull Collection<@NotNull String> values) {
        stream.print(headerIndent);
        stream.println(name);
        for (@NotNull String value: values) {
            System.out.print(valueIndent);
            System.out.println(value);
        }
    }

    public static @NotNull ControlsMessage<SearchRequest> searchRequest() throws Throwable {
        return new SearchRequest(
                List.of(
                        SearchRequest.ALL_ATTRIBUTES,
                        AllOperationAttributes.ALL_OPERATIONAL_ATTRIBUTES,
                        NAMING_CONTEXTS,
                        SUPPORTED_CAPABILITIES,
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
                +", supportedCapabilities: %s"
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
                        supportedCapabilities,
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
