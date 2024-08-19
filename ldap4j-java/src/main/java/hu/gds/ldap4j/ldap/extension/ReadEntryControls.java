package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.NoSuchControlException;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 4527
 */
public class ReadEntryControls {
    public static final @NotNull String POST_READ_CONTROL_OID="1.3.6.1.1.13.2";
    public static final @NotNull String PRE_READ_CONTROL_OID="1.3.6.1.1.13.1";

    private ReadEntryControls() {
    }

    public static @NotNull Control postRequest(@NotNull List<@NotNull String> attributeSelections) {
        return postRequest(attributeSelections, true);
    }

    public static @NotNull Control postRequest(
            @NotNull List<@NotNull String> attributeSelections, boolean criticality) {
        return request(attributeSelections, POST_READ_CONTROL_OID, criticality);
    }

    public static @NotNull SearchResult.Entry postResponseCheck(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        return responseCheck(controls, POST_READ_CONTROL_OID);
    }

    public static @NotNull Control preRequest(@NotNull List<@NotNull String> attributeSelections) {
        return preRequest(attributeSelections, true);
    }

    public static @NotNull Control preRequest(
            @NotNull List<@NotNull String> attributeSelections, boolean criticality) {
        return request(attributeSelections, PRE_READ_CONTROL_OID, criticality);
    }

    public static @NotNull SearchResult.Entry preResponseCheck(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        return responseCheck(controls, PRE_READ_CONTROL_OID);
    }

    public static @NotNull Control request(
            @NotNull List<@NotNull String> attributeSelections, @NotNull String controlType, boolean criticality) {
        @NotNull ByteBuffer buffer=ByteBuffer.EMPTY;
        for (@NotNull String attributeSelection: attributeSelections) {
            buffer=buffer.append(BER.writeUtf8Tag(attributeSelection));
        }
        return new Control(
                controlType,
                BER.writeSequence(buffer)
                        .arrayCopy(),
                criticality);
    }

    public static @Nullable SearchResult.Entry response(
            @NotNull List<@NotNull Control> controls, @NotNull String controlType) throws Throwable {
        return Control.findOne(
                controls,
                (control)->{
                    if (!controlType.equals(control.controlType())) {
                        return null;
                    }
                    if (null==control.controlValue()) {
                        return null;
                    }
                    return ByteBuffer.create(control.controlValue())
                            .read((reader)->BER.readTag(
                                    SearchResult.Entry::readNoTag,
                                    reader,
                                    BER.SEQUENCE));
                });
    }

    public static @NotNull SearchResult.Entry responseCheck(
            @NotNull List<@NotNull Control> controls, @NotNull String controlType) throws Throwable {
        @Nullable SearchResult.Entry searchResultEntry=response(controls, controlType);
        if (null==searchResultEntry) {
            throw new NoSuchControlException();
        }
        return searchResultEntry;
    }
}
