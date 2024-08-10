package hu.gds.ldap4j.net.netty.codec;

import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.Response;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ResponseRequest<M extends hu.gds.ldap4j.ldap.Request<M, R>, R extends Response>(
        @NotNull ControlsMessage<M> request)
        implements Request {
    public ResponseRequest(
            @NotNull ControlsMessage<M> request) {
        this.request=Objects.requireNonNull(request, "request");
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.responseRequest(this);
    }
}
