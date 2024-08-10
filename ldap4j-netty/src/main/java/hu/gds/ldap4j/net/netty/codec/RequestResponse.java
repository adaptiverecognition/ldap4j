package hu.gds.ldap4j.net.netty.codec;

import hu.gds.ldap4j.ldap.ControlsMessage;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record RequestResponse<M>(
        @NotNull ControlsMessage<M> response)
        implements Response {
    public RequestResponse(
            @NotNull ControlsMessage<M> response) {
        this.response=Objects.requireNonNull(response, "response");
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.requestResponse(this);
    }
}
