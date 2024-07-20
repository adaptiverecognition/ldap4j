package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface Message<M> {
    default @NotNull ControlsMessage<M> controls(@NotNull List<@NotNull Control> controls) {
        return new ControlsMessage<>(controls, self());
    }

    default @NotNull ControlsMessage<M> controlsEmpty() {
        return controls(List.of());
    }

    default @NotNull ControlsMessage<M> controlsManageDsaIt() {
        return controls(List.of(Control.nonCritical(Ldap.CONTROL_MANAGE_DSA_IT_OID)));
    }

    default @NotNull ControlsMessage<M> controlsManageDsaIt(boolean manageDsaIt) {
        return manageDsaIt
                ?controlsManageDsaIt()
                :controlsEmpty();
    }

    @NotNull M self();

    @NotNull ByteBuffer write() throws Throwable;
}
