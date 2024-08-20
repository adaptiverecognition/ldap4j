package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.NoSuchControlException;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RFC 2696
 */
public class SimplePagedResults {
    public record SearchControlValue(@NotNull ByteBuffer cookie, int size) {
        public SearchControlValue(@NotNull ByteBuffer cookie, int size) {
            this.cookie=Objects.requireNonNull(cookie, "cookie");
            this.size=size;
        }

        public @NotNull Control continueRequest(boolean criticality, int size) {
            if (cookie.isEmpty()) {
                throw new RuntimeException("empty cookie");
            }
            return control(cookie, criticality, size);
        }

        public @NotNull Control continueRequest(int size) {
            return continueRequest(true, size);
        }

        public static @NotNull SearchControlValue read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readSequence(
                    (reader2)->{
                        int size=BER.readIntegerTag(true, reader2);
                        @NotNull ByteBuffer cookie=BER.readOctetStringTag(reader2);
                        return new SearchControlValue(cookie, size);
                    },
                    reader);
        }

        public @NotNull ByteBuffer write() {
            return BER.writeSequence(
                    BER.writeIntegerTag(size)
                            .append(BER.writeOctetStringTag(cookie)));
        }
    }

    public static final @NotNull String CONTROL_OID="1.2.840.113556.1.4.319";
    public static final @NotNull String START_COOKIE="";

    private SimplePagedResults() {
    }

    public static @NotNull Control control(@NotNull ByteBuffer cookie, boolean criticality, int size) {
        return Control.create(
                CONTROL_OID,
                new SearchControlValue(cookie, size)
                        .write(),
                criticality);
    }

    public static @Nullable SearchControlValue responseControl(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        return Control.findOne(
                controls,
                (control)->{
                    if (!CONTROL_OID.equals(control.controlType().utf8())) {
                        return null;
                    }
                    if (null==control.controlValue()) {
                        return null;
                    }
                    return control.controlValue()
                            .read(SearchControlValue::read);
                });
    }

    public static @NotNull SearchControlValue responseControlCheck(
            @NotNull List<@NotNull Control> controls) throws Throwable {
        @Nullable SearchControlValue searchControlValue=responseControl(controls);
        if (null==searchControlValue) {
            throw new NoSuchControlException();
        }
        return searchControlValue;
    }

    public static @NotNull Control startRequest(boolean criticality, int size) {
        return control(ByteBuffer.create(START_COOKIE), criticality, size);
    }

    public static @NotNull Control startRequest(int size) {
        return startRequest(true, size);
    }
}
