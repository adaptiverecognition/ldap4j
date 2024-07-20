package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface MessageReader<T> {
    void check(@NotNull List<@NotNull Control> controls, @NotNull T message, int messageId) throws Throwable;

    default <U> @NotNull ParallelMessageReader<T, U> parallel(@NotNull Function<@NotNull LdapMessage<T>, U> function) {
        return new ParallelMessageReader<>(function, this);
    }

    @NotNull T read(@NotNull ByteBuffer.Reader reader) throws Throwable;
}
