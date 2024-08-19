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

    @NotNull M self();

    @NotNull ByteBuffer write() throws Throwable;
}
