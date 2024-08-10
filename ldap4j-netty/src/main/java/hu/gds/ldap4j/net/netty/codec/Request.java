package hu.gds.ldap4j.net.netty.codec;

import org.jetbrains.annotations.NotNull;

public sealed interface Request
        permits ResponseRequest, SearchRequest, UnbindRequest {
    interface Visitor<T> {
        T responseRequest(@NotNull ResponseRequest<?, ?> responseRequest) throws Throwable;
        T searchRequest(@NotNull SearchRequest searchRequest) throws Throwable;
        T unbindRequest(@NotNull UnbindRequest unbindRequest) throws Throwable;
    }

    <T> T visit(@NotNull Visitor<T> visitor) throws Throwable;
}
