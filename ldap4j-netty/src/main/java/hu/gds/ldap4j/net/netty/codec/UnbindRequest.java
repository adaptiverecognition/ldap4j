package hu.gds.ldap4j.net.netty.codec;

import org.jetbrains.annotations.NotNull;

public record UnbindRequest()
        implements Request {
    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.unbindRequest(this);
    }
}
