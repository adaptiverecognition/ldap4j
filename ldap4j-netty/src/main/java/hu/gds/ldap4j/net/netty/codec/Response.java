package hu.gds.ldap4j.net.netty.codec;

import org.jetbrains.annotations.NotNull;

public sealed interface Response permits RequestResponse, SearchResponse {
    interface Visitor<T> {
        T requestResponse(@NotNull RequestResponse<?> requestResponse) throws Throwable;
        T searchResponse(@NotNull SearchResponse searchResponse) throws Throwable;
    }

    <T> T visit(@NotNull Visitor<T> visitor) throws Throwable;
}
