package hu.gds.ldap4j.ldap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <a href="https://ldap.com/ldap-oid-reference-guide/">ldap.com oid reference</a>
 */
public enum Feature {
    ABSOLUTE_TRUE_AND_FALSE_FILTERS, // RFC 4526
    ALL_OPERATIONAL_ATTRIBUTES, // RFC 3673
    APACHE_DS_CASCADE_CONTROL,
    ASSERTION_CONTROL, // RFC 4528
    AUTHORIZATION_IDENTITY_REQUEST, // RFC 3829
    CONTENT_SYNCHRONIZATION_DONE_CONTROL, // RFC 4533
    CONTENT_SYNCHRONIZATION_REQUEST_CONTROL, // RFC 4533
    CONTENT_SYNCHRONIZATION_STATE_CONTROL, // RFC 4533
    DON_T_USE_COPY_CONTROL, // RFC 6171
    DRAFT_ENTRY_CHANGE_NOTIFICATION_RESPONSE_CONTROL,
    DRAFT_NO_OP_REQUEST_CONTROL,
    DRAFT_PASSWORD_EXPIRED_RESPONSE_CONTROL,
    DRAFT_PASSWORD_POLICY_REQUEST_AND_RESPONSE_CONTROL,
    DRAFT_PERSISTENT_SEARCH_REQUEST_CONTROL,
    DRAFT_PROXIED_AUTHORIZATION_CONTROL,
    DRAFT_RELAX_RULES_CONTROL,
    DRAFT_SUBENTRIES_REQUEST_CONTROL_DRAFT,
    DRAFT_TREE_DELETE_REQUEST_CONTROL,
    DRAFT_VIRTUAL_LIST_VIEW_REQUEST_CONTROL,
    DRAFT_VIRTUAL_LIST_VIEW_RESPONSE_CONTROL,
    END_TRANSACTION_EXTENDED_REQUEST, // RFC 5805
    MANAGE_DSA_IT_CONTROL, // RFC 3296
    MODIFY_INCREMENT, // RFC 4525
    MS_AD_DIRSYNC_REQUEST_CONTROL,
    MS_AD_PERMISSIVE_MODIFY_REQUEST_CONTROL,
    MS_AD_SERVER_NOTIFICATION_CONTROL,
    MS_AD_SERVER_POLICY_HINTS_CONTROL,
    MS_AD_SHOW_DELETED_CONTROL,
    NOTICE_OF_DISCONNECT_UNSOLICITED_NOTIFICATION, // RFC 4511
    PASSWORD_MODIFY_EXTENDED_OPERATION, // RFC 3062
    PING_IDENTITY_DS_IGNORE_NO_USER_MODIFICATION_REQUEST_CONTROL,
    POST_READ_CONTROL, // RFC 4527
    PRE_READ_CONTROL, // RFC 4527
    PROXIED_AUTHORIZATION_CONTROL, // RFC 4370
    REQUESTING_ATTRIBUTES_BY_OBJECT_CLASS, // RFC 4529
    START_TLS_EXTENDED_OPERATION, // RFC 4511
    START_TRANSACTION_EXTENDED_REQUEST, // RFC 5805
    SERVER_SIDE_SORTING_REQUEST_CONTROL, // RFC 2891
    SERVER_SIDE_SORTING_RESPONSE_CONTROL, // RFC 2891
    SIMPLE_PAGED_RESULTS_CONTROL, // RFC 2696
    SUBENTRIES_CONTROL, // RFC 3672
    TRANSACTION_SPECIFICATION_CONTROL, // RFC 5805
    WHO_AM_I_OPERATION; // RFC 4532

    private static final @NotNull Map<@NotNull String, @NotNull Feature> FEATURES;

    static {
        @NotNull Map<@NotNull String, @NotNull Feature> features=new HashMap<>();
        features.put(
                "1.3.6.1.4.1.4203.1.5.3",
                ABSOLUTE_TRUE_AND_FALSE_FILTERS);
        features.put(
                "1.3.6.1.4.1.4203.1.5.1",
                ALL_OPERATIONAL_ATTRIBUTES);
        features.put(
                "1.3.6.1.4.1.18060.0.0.1",
                APACHE_DS_CASCADE_CONTROL);
        features.put(
                "1.3.6.1.1.12",
                ASSERTION_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.16",
                AUTHORIZATION_IDENTITY_REQUEST);
        features.put(
                "1.3.6.1.4.1.4203.1.9.1.3",
                CONTENT_SYNCHRONIZATION_DONE_CONTROL);
        features.put(
                "1.3.6.1.4.1.4203.1.9.1.1",
                CONTENT_SYNCHRONIZATION_REQUEST_CONTROL);
        features.put(
                "1.3.6.1.4.1.4203.1.9.1.2",
                CONTENT_SYNCHRONIZATION_STATE_CONTROL);
        features.put(
                "1.3.6.1.1.22",
                DON_T_USE_COPY_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.7",
                DRAFT_ENTRY_CHANGE_NOTIFICATION_RESPONSE_CONTROL);
        features.put(
                "1.3.6.1.4.1.4203.1.10.2",
                DRAFT_NO_OP_REQUEST_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.4",
                DRAFT_PASSWORD_EXPIRED_RESPONSE_CONTROL);
        features.put(
                "1.3.6.1.4.1.42.2.27.8.5.1",
                DRAFT_PASSWORD_POLICY_REQUEST_AND_RESPONSE_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.3",
                DRAFT_PERSISTENT_SEARCH_REQUEST_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.12",
                DRAFT_PROXIED_AUTHORIZATION_CONTROL);
        features.put(
                "1.3.6.1.4.1.4203.666.5.12",
                DRAFT_RELAX_RULES_CONTROL);
        features.put(
                "1.3.6.1.4.1.7628.5.101.1",
                DRAFT_SUBENTRIES_REQUEST_CONTROL_DRAFT);
        features.put(
                "1.2.840.113556.1.4.805",
                DRAFT_TREE_DELETE_REQUEST_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.9",
                DRAFT_VIRTUAL_LIST_VIEW_REQUEST_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.10",
                DRAFT_VIRTUAL_LIST_VIEW_RESPONSE_CONTROL);
        features.put(
                Ldap.EXTENDED_REQUEST_END_TRANSACTION_OID,
                END_TRANSACTION_EXTENDED_REQUEST);
        features.put(
                Ldap.CONTROL_MANAGE_DSA_IT_OID,
                MANAGE_DSA_IT_CONTROL);
        features.put(
                "1.3.6.1.1.14",
                MODIFY_INCREMENT);
        features.put(
                "1.2.840.113556.1.4.841",
                MS_AD_DIRSYNC_REQUEST_CONTROL);
        features.put(
                "1.2.840.113556.1.4.1413",
                MS_AD_PERMISSIVE_MODIFY_REQUEST_CONTROL);
        features.put(
                "1.2.840.113556.1.4.528",
                MS_AD_SERVER_NOTIFICATION_CONTROL);
        features.put(
                "1.2.840.113556.1.4.2239",
                MS_AD_SERVER_POLICY_HINTS_CONTROL);
        features.put(
                "1.2.840.113556.1.4.417",
                MS_AD_SHOW_DELETED_CONTROL);
        features.put(
                Ldap.NOTICE_OF_DISCONNECTION_OID,
                NOTICE_OF_DISCONNECT_UNSOLICITED_NOTIFICATION);
        features.put(
                Ldap.EXTENDED_REQUEST_PASSWORD_MODIFY,
                PASSWORD_MODIFY_EXTENDED_OPERATION);
        features.put(
                "1.3.6.1.4.1.30221.2.5.5",
                PING_IDENTITY_DS_IGNORE_NO_USER_MODIFICATION_REQUEST_CONTROL);
        features.put(
                "1.3.6.1.1.13.2",
                POST_READ_CONTROL);
        features.put(
                "1.3.6.1.1.13.1",
                PRE_READ_CONTROL);
        features.put(
                "2.16.840.1.113730.3.4.18",
                PROXIED_AUTHORIZATION_CONTROL);
        features.put(
                "1.3.6.1.4.1.4203.1.5.2",
                REQUESTING_ATTRIBUTES_BY_OBJECT_CLASS);
        features.put(
                "1.2.840.113556.1.4.473",
                SERVER_SIDE_SORTING_REQUEST_CONTROL);
        features.put(
                "1.2.840.113556.1.4.474",
                SERVER_SIDE_SORTING_REQUEST_CONTROL);
        features.put(
                "1.2.840.113556.1.4.319",
                SIMPLE_PAGED_RESULTS_CONTROL);
        features.put(
                Ldap.EXTENDED_REQUEST_START_TLS_OID,
                START_TLS_EXTENDED_OPERATION);
        features.put(
                Ldap.EXTENDED_REQUEST_START_TRANSACTION_OID,
                START_TRANSACTION_EXTENDED_REQUEST);
        features.put(
                "1.3.6.1.4.1.4203.1.10.1",
                SUBENTRIES_CONTROL);
        features.put(
                Ldap.CONTROL_TRANSACTION_SPECIFICATION_OID,
                TRANSACTION_SPECIFICATION_CONTROL);
        features.put(
                "1.3.6.1.4.1.4203.1.11.3",
                WHO_AM_I_OPERATION);
        FEATURES=Collections.unmodifiableMap(features);
    }

    public static @Nullable Feature feature(@NotNull String oid) {
        Objects.requireNonNull(oid, "oid");
        return FEATURES.get(oid);
    }
}
