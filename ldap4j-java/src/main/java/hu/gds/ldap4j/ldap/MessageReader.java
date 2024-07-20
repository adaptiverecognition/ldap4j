package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public interface MessageReader<T> {
    void check(@NotNull T message) throws Throwable;

    @NotNull T read(@NotNull ByteBuffer.Reader reader) throws Throwable;
}
