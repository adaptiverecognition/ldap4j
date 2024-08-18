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
    public final @NotNull Set<@NotNull Feature> supportedControlsFeature=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedControlsOid=new TreeSet<>();
    public final @NotNull Set<@NotNull Feature> supportedExtensionsFeature=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedExtensionsOid=new TreeSet<>();
    public final @NotNull Set<@NotNull Feature> supportedFeaturesFeature=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedFeaturesOid=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedLdapVersions=new TreeSet<>();
    public final @NotNull Set<@NotNull String> supportedSaslMechanisms=new TreeSet<>();
    public final @NotNull List<@NotNull PartialAttribute> unrecognizedAttributes=new ArrayList<>();
    public final @NotNull Set<@NotNull String> vendorNames=new TreeSet<>();
    public final @NotNull Set<@NotNull String> vendorVersions=new TreeSet<>();

    private void add(@NotNull PartialAttribute attribute) {
        switch (attribute.type()) {
            case NAMING_CONTEXTS -> namingContexts.addAll(attribute.values());
            case SUPPORTED_CONTROL -> add(supportedControlsFeature, supportedControlsOid, attribute.values());
            case SUPPORTED_EXTENSION -> add(supportedExtensionsFeature, supportedExtensionsOid, attribute.values());
            case SUPPORTED_FEATURES -> add(supportedFeaturesFeature, supportedFeaturesOid, attribute.values());
            case SUPPORTED_LDAP_VERSION -> supportedLdapVersions.addAll(attribute.values());
            case SUPPORTED_SASL_MECHANISMS -> supportedSaslMechanisms.addAll(attribute.values());
            case VENDOR_NAME -> vendorNames.addAll(attribute.values());
            case VENDOR_VERSION -> vendorVersions.addAll(attribute.values());
            default -> unrecognizedAttributes.add(attribute);
        }
    }

    private static void add(
            @NotNull Set<@NotNull Feature> features,
            @NotNull String oid,
            @NotNull Set<@NotNull String> oids) {
        @Nullable Feature feature=Feature.feature(oid);
        if (null!=feature) {
            features.add(feature);
        }
        oids.add(oid);
    }

    private static void add(
            @NotNull Set<@NotNull Feature> features,
            @NotNull Set<@NotNull String> oids,
            @NotNull List<@NotNull String> values) {
        for (@NotNull String value: values) {
            add(features, value, oids);
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

    public void prettyPrint() {
        prettyPrint(System.out);
    }

    public void prettyPrint(@NotNull PrintStream stream) {
        prettyPrint(null, VENDOR_NAME, stream, vendorNames);
        prettyPrint(null, VENDOR_VERSION, stream, vendorVersions);
        prettyPrint(null, SUPPORTED_LDAP_VERSION, stream, supportedLdapVersions);
        prettyPrint(null, SUPPORTED_SASL_MECHANISMS, stream, supportedSaslMechanisms);
        prettyPrint(null, NAMING_CONTEXTS, stream, namingContexts);
        prettyPrint(null, "referral URIs", stream, referralUris);
        prettyPrint(supportedControlsFeature, SUPPORTED_CONTROL, stream, supportedControlsOid);
        prettyPrint(supportedExtensionsFeature, SUPPORTED_EXTENSION, stream, supportedExtensionsOid);
        prettyPrint(supportedFeaturesFeature, SUPPORTED_FEATURES, stream, supportedFeaturesOid);
        stream.println("unrecognized attributes");
        for (@NotNull PartialAttribute attribute: unrecognizedAttributes) {
            System.out.print("\t");
            System.out.println(attribute);
        }
    }

    private void prettyPrint(
            @Nullable Collection<@NotNull Feature> features,
            @NotNull String name,
            @NotNull PrintStream stream,
            @NotNull Collection<@NotNull String> values) {
        stream.println(name);
        if (null!=features) {
            for (@NotNull Feature feature: features) {
                System.out.print("\t");
                System.out.println(feature);
            }
        }
        for (@NotNull String value: values) {
            @Nullable Feature feature=Feature.feature(value);
            if ((null==feature) || (null==features) || (!features.contains(feature))) {
                System.out.print("\t");
                System.out.println(value);
            }
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
                +", supportedControlsFeature: %s"
                +", supportedControlsOid: %s"
                +", supportedExtensionsFeature: %s"
                +", supportedExtensionsOid: %s"
                +", supportedFeaturesFeature: %s"
                +", supportedFeaturesOid: %s"
                +", supportedLdapVersions: %s"
                +", supportedSaslMechanisms: %s"
                +", unrecognizedAttributes: %s"
                +", vendorNames: %s"
                +", vendorVersions: %s)")
                .formatted(
                        namingContexts,
                        referralUris,
                        supportedControlsFeature,
                        supportedControlsOid,
                        supportedExtensionsFeature,
                        supportedExtensionsOid,
                        supportedFeaturesFeature,
                        supportedFeaturesOid,
                        supportedLdapVersions,
                        supportedSaslMechanisms,
                        unrecognizedAttributes,
                        vendorNames,
                        vendorVersions);
    }
}
