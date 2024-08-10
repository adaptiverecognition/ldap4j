package hu.gds.ldap4j.net.netty.codec;

import hu.gds.ldap4j.ldap.ControlsMessage;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record SearchRequest(
        @NotNull ControlsMessage<hu.gds.ldap4j.ldap.SearchRequest> searchRequest)
        implements Request {
    public SearchRequest(@NotNull ControlsMessage<hu.gds.ldap4j.ldap.SearchRequest> searchRequest) {
        this.searchRequest=Objects.requireNonNull(searchRequest, "bindRequest");
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.searchRequest(this);
    }
}
