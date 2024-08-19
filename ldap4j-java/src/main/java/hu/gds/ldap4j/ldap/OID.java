package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.ldap.extension.AbsoluteTrueAndFalseFilters;
import hu.gds.ldap4j.ldap.extension.AllOperationAttributes;
import hu.gds.ldap4j.ldap.extension.AssertionControl;
import hu.gds.ldap4j.ldap.extension.AttributesByObjectClass;
import hu.gds.ldap4j.ldap.extension.Cancel;
import hu.gds.ldap4j.ldap.extension.DonTUseCopyControl;
import hu.gds.ldap4j.ldap.extension.FastBind;
import hu.gds.ldap4j.ldap.extension.ManageDsaIt;
import hu.gds.ldap4j.ldap.extension.ModifyIncrement;
import hu.gds.ldap4j.ldap.extension.PasswordModify;
import hu.gds.ldap4j.ldap.extension.ReadEntryControls;
import hu.gds.ldap4j.ldap.extension.ServerSideSorting;
import hu.gds.ldap4j.ldap.extension.SimplePagedResults;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <a href="https://ldap.com/ldap-oid-reference-guide/">ldap.com oid reference</a>
 */
public class OID {
    public static final @NotNull Comparator<@Nullable String> COMPARATOR=(value0, value1)->{
        if (null==value0) {
            if (null==value1) {
                return 0;
            }
            else {
                return -1;
            }
        }
        if (null==value1) {
            return 1;
        }
        String[] parts0=value0.split("\\.");
        String[] parts1=value1.split("\\.");
        for (int ii=0, ss=Math.min(parts0.length, parts1.length); ss>ii; ++ii) {
            int cc=compareParts(parts0[ii], parts1[ii]);
            if (0!=cc) {
                return cc;
            }
        }
        return Integer.compare(parts0.length, parts1.length);
    };
    private static final @NotNull Map<@NotNull String, @NotNull String> NAMES;

    static {
        @NotNull Map<@NotNull String, @NotNull String> names=new HashMap<>();
        names.put(
                "1.2.826.0.1.3344810.2.3",
                "Returning Matched Values Control, RFC 3876");
        names.put(
                SimplePagedResults.CONTROL_OID,
                "Simple Paged Results Control, RFC 2696");
        names.put(
                "1.2.840.113556.1.4.417",
                "Show Deleted Control, MS AD");
        names.put(
                ServerSideSorting.REQUEST_CONTROL_OID, // "1.2.840.113556.1.4.473",
                "Server Side Sorting Request Control, RFC 2891");
        names.put(
                ServerSideSorting.RESPONSE_CONTROL_OID, // "1.2.840.113556.1.4.474",
                "Server Side Sorting Response Control, RFC 2891");
        names.put(
                "1.2.840.113556.1.4.521",
                "CrossDom Move Target Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.528",
                "Notification Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.529",
                "Extended DN Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.619",
                "Lazy Commit Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.800",
                "Active Directory Capability, MS AD");
        names.put(
                "1.2.840.113556.1.4.801",
                "SD Flags Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.802",
                "Range Option Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.805",
                "Tree Delete Control, draft");
        names.put(
                "1.2.840.113556.1.4.841",
                "DirSync Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.970",
                "Get Stats Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1338",
                "Verify Name Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1339",
                "Domain Scope Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1340",
                "Search Options Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1341",
                "RODC DCPROMO Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1413",
                "Permissive Modify Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1504",
                "ASQ Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1670",
                "Active Directory V51 Capability, MS AD");
        names.put(
                FastBind.REQUEST_OPERATION_OID, //"1.2.840.113556.1.4.1781",
                "Fast Bind Operation, MS AD");
        names.put(
                "1.2.840.113556.1.4.1791",
                "Active Directory Integ Capability, MS AD");
        names.put(
                "1.2.840.113556.1.4.1852",
                "Quota Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1907",
                "Shutdown Notify Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1935",
                "Active Directory V60 Capability, MS AD");
        names.put(
                "1.2.840.113556.1.4.1948",
                "Range Retrieval NoErr Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.1974",
                "Force Update Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2026",
                "DN Input Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2064",
                "Show Recycled Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2065",
                "Show Deactivated Link Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2066",
                "Policy Hints Deprecated Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2080",
                "Active Directory V61 R2 Capability, MS AD");
        names.put(
                "1.2.840.113556.1.4.2090",
                "DirSync Ex Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2204",
                "Tree Delete Ex Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2205",
                "Update Stats Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2206",
                "Search Hints Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2212",
                "Batch Request Operation, MS AD");
        names.put(
                "1.2.840.113556.1.4.2211",
                "Expected Entry Count Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2237",
                "Active Directory W8 Capability, MS AD");
        names.put(
                "1.2.840.113556.1.4.2255",
                "Set Owner Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2256",
                "Bypass Quota Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2239",
                "Policy Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2309",
                "Link TTL Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2330",
                "Set Correlation Id Control, MS AD");
        names.put(
                "1.2.840.113556.1.4.2354",
                "Thread Trace Override Control, MS AD");
        names.put(
                Cancel.REQUEST_OPERATION_OID, // "1.3.6.1.1.8"
                "Cancel Operation, RFC 3909");
        names.put(
                AssertionControl.CONTROL_OID, // "1.3.6.1.1.12"
                "Assertion Control, RFC 4528");
        names.put(
                ReadEntryControls.PRE_READ_CONTROL_OID, // "1.3.6.1.1.13.1"
                "Pre-read entry control, RFC 4527");
        names.put(
                ReadEntryControls.POST_READ_CONTROL_OID, // "1.3.6.1.1.13.2"
                "Post-read entry control, RFC 4527");
        names.put(
                ModifyIncrement.FEATURE_OID, //"1.3.6.1.1.14"
                "Modify-Increment Extension, RFC 4525");
        names.put(
                "1.3.6.1.1.21.1",
                "Start Transaction Request and Response, RFC 5805");
        names.put(
                "1.3.6.1.1.21.2",
                "Transaction Specification Control, RFC 5805");
        names.put(
                "1.3.6.1.1.21.3",
                "End Transactions Request and Response, RFC 5805");
        names.put(
                DonTUseCopyControl.CONTROL_OID, // "1.3.6.1.1.22"
                "Don't Use Copy Control, RFC 6171");
        names.put(
                "1.3.6.1.4.1.42.2.27.8.5.1",
                "Password Policy Control, draft");
        names.put(
                "1.3.6.1.4.1.1466.101.119.1",
                "Refresh Operation, RFC 2589");
        names.put(
                ExtendedResponse.NOTICE_OF_DISCONNECTION_OID, // "1.3.6.1.4.1.1466.20036"
                "Notice of Disconnection Unsolicited Notification, RFC 4511");
        names.put(
                StartTls.REQUEST_OID, // "1.3.6.1.4.1.1466.20037"
                "StartTLS Request, RFC 4511");
        names.put(
                AllOperationAttributes.FEATURE_OID, // "1.3.6.1.4.1.4203.1.5.1"
                "All Operational Attributes, RFC 3673");
        names.put(
                AttributesByObjectClass.FEATURE_OID, //"1.3.6.1.4.1.4203.1.5.2"
                "Requesting Attributes by Object Class, RFC 4529");
        names.put(
                AbsoluteTrueAndFalseFilters.FEATURE_OID, // "1.3.6.1.4.1.4203.1.5.3"
                "Absolute True and False Filters, RFC 4526");
        names.put(
                "1.3.6.1.4.1.4203.1.5.4",
                "Language Tag Options, RFC 3866");
        names.put(
                "1.3.6.1.4.1.4203.1.5.5",
                "Language Range Options, RFC 3866");
        names.put(
                "1.3.6.1.4.1.4203.1.9.1.1",
                "Content Synchronization Request Control, RFC 4533");
        names.put(
                "1.3.6.1.4.1.4203.1.9.1.2",
                "Content Synchronization State Control, RFC 4533");
        names.put(
                "1.3.6.1.4.1.4203.1.9.1.3",
                "Content Synchronization Done Control, RFC 4533");
        names.put(
                "1.3.6.1.4.1.4203.1.10.1",
                "Subentries Control, RFC 3672");
        names.put(
                "1.3.6.1.4.1.4203.1.10.2",
                "No-Op Control, draft");
        names.put(
                PasswordModify.REQUEST_OPERATION_IOD, // "1.3.6.1.4.1.4203.1.11.1"
                "Password Modify Extended Operation, RFC 3062");
        names.put(
                "1.3.6.1.4.1.4203.1.11.3",
                "\"Who am I?\" Operation, RFC 4532");
        names.put(
                "1.3.6.1.4.1.4203.666.5.12",
                "Relax Rules Control, draft");
        names.put(
                "1.3.6.1.4.1.7628.5.101.1",
                "Subentries Control, draft");
        names.put(
                "1.3.6.1.4.1.18060.0.0.1",
                "Cascade Control, Apache DS");
        names.put(
                "1.3.6.1.4.1.30221.2.5.5",
                "Ignore NO-USER-MODIFICATION Request Control, Ping Identity DS");
        names.put(
                ManageDsaIt.REQUEST_CONTROL_OID, // "2.16.840.1.113730.3.4.2"
                "ManageDsaIT Control, RFC 3296");
        names.put(
                "2.16.840.1.113730.3.4.3",
                "Persistent Search Control, draft");
        names.put(
                "2.16.840.1.113730.3.4.4",
                "Password Policy Control, draft");
        names.put(
                "2.16.840.1.113730.3.4.7",
                "Entry Change Notification Control, draft");
        names.put(
                "2.16.840.1.113730.3.4.9",
                "Virtual List View Request Control, draft");
        names.put(
                "2.16.840.1.113730.3.4.10",
                "Virtual List View Response Control, draft");
        names.put(
                "2.16.840.1.113730.3.4.12",
                "Proxied Authorization Control, draft");
        names.put(
                "2.16.840.1.113730.3.4.15",
                "Authorization Identity Response Control, RFC 3829");
        names.put(
                "2.16.840.1.113730.3.4.16",
                "Authorization Identity Request Control, RFC 3829");
        names.put(
                "2.16.840.1.113730.3.4.18",
                "Proxy Authorization Control, RFC 4370");
        NAMES=Collections.unmodifiableMap(names);
    }

    private OID() {
    }

    public static @Nullable String name(@NotNull String oid) {
        return NAMES.get(oid);
    }

    private static int compareParts(@NotNull String part0, @NotNull String part1) {
        @Nullable BigInteger value0=null;
        @Nullable BigInteger value1=null;
        try {
            value0=new BigInteger(part0);
        }
        catch (NumberFormatException ignore) {
        }
        try {
            value1=new BigInteger(part1);
        }
        catch (NumberFormatException ignore) {
        }
        if (null==value0) {
            if (null==value1) {
                return part0.compareTo(part1);
            }
            return 1;
        }
        if (null==value1) {
            return -1;
        }
        return value0.compareTo(value1);
    }
}
