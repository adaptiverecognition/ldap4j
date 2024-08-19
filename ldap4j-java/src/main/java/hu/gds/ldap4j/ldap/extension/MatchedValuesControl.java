package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.BER;
import hu.gds.ldap4j.ldap.Control;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * RFC 3876
 */
public class MatchedValuesControl {
    public static final @NotNull String CONTROL_OID="1.2.826.0.1.3344810.2.3";

    private MatchedValuesControl() {
    }

    public static @NotNull Control requestControl(
            boolean criticality,
            @NotNull List<@NotNull Filter> filters)
            throws Throwable {
        @NotNull ByteBuffer buffer=ByteBuffer.EMPTY;
        for (@NotNull Filter filter: filters) {
            buffer=buffer.append(filter.write());
        }
        return new Control(
                CONTROL_OID,
                BER.writeSequence(buffer)
                        .arrayCopy(),
                criticality);
    }

    public static @NotNull Control requestControl(@NotNull List<@NotNull Filter> filters) throws Throwable {
        return requestControl(true, filters);
    }
}
