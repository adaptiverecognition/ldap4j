package hu.gds.ldap4j.ldap;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ControlsMessage<T>(
        @NotNull List<@NotNull Control> controls,
        @NotNull T message) {
    public ControlsMessage(@NotNull List<@NotNull Control> controls, @NotNull T message) {
        this.controls=Objects.requireNonNull(controls, "controls");
        this.message=Objects.requireNonNull(message, "message");
    }
}
