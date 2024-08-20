package hu.gds.ldap4j.ldap.extension;

import hu.gds.ldap4j.ldap.Control;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * RFC 3296
 */
public class ManageDsaIt {
    public static final @NotNull String REQUEST_CONTROL_OID="2.16.840.1.113730.3.4.2";
    
    private ManageDsaIt() {
    }

    public static @NotNull Control requestControl(boolean criticality) {
        return Control.create(REQUEST_CONTROL_OID, null, criticality);
    }

    public static @NotNull List<@NotNull Control> requestControls(boolean criticality, boolean manageDsaIt) {
        return manageDsaIt
                ?List.of(requestControl(criticality))
                :List.of();
    }

    public static @NotNull List<@NotNull Control> requestControls(boolean manageDsaIt) {
        return requestControls(manageDsaIt, manageDsaIt);
    }
}
