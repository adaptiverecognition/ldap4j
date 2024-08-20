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

    private static @NotNull List<@NotNull ByteBuffer> fromStrings(@NotNull List<@NotNull String> list) {
        return list.stream()
                .map(ByteBuffer::create)
                .toList();
    }

    public static @NotNull Control postRequest(@NotNull List<@NotNull ByteBuffer> attributeSelections) {
        return postRequest(attributeSelections, true);
    }

    public static @NotNull Control postRequest(
            @NotNull List<@NotNull ByteBuffer> attributeSelections, boolean criticality) {
        return request(attributeSelections, ByteBuffer.create(POST_READ_CONTROL_OID), criticality);
    }

    public static @NotNull Control postRequestString(@NotNull List<@NotNull String> attributeSelections) {
        return postRequest(fromStrings(attributeSelections));
    }

    public static @NotNull SearchResult.Entry postResponseCheck(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        return responseCheck(controls, POST_READ_CONTROL_OID);
    }

    public static @NotNull Control preRequest(@NotNull List<@NotNull ByteBuffer> attributeSelections) {
        return preRequest(attributeSelections, true);
    }

    public static @NotNull Control preRequest(
            @NotNull List<@NotNull ByteBuffer> attributeSelections, boolean criticality) {
        return request(attributeSelections, ByteBuffer.create(PRE_READ_CONTROL_OID), criticality);
    }

    public static @NotNull Control preRequestString(@NotNull List<@NotNull String> attributeSelections) {
        return preRequest(fromStrings(attributeSelections));
    }

    public static @NotNull SearchResult.Entry preResponseCheck(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        return responseCheck(controls, PRE_READ_CONTROL_OID);
    }

    public static @NotNull Control request(
            @NotNull List<@NotNull ByteBuffer> attributeSelections,
            @NotNull ByteBuffer controlType,
            boolean criticality) {
        @NotNull ByteBuffer buffer=ByteBuffer.empty();
        for (@NotNull ByteBuffer attributeSelection: attributeSelections) {
            buffer=buffer.append(BER.writeOctetStringTag(attributeSelection));
        }
        return Control.create(
                controlType,
                BER.writeSequence(buffer),
                criticality);
    }

    public static @Nullable SearchResult.Entry response(
            @NotNull List<@NotNull Control> controls, @NotNull String controlType) throws Throwable {
        return Control.findOne(
                controls,
                (control)->{
                    if (!controlType.equals(control.controlType().utf8())) {
                        return null;
                    }
                    if (null==control.controlValue()) {
                        return null;
                    }
                    return control.controlValue()
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
