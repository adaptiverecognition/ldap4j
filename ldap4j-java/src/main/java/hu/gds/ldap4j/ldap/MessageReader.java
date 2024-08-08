package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MessageReader<T> {
    void check(@NotNull List<@NotNull Control> controls, @NotNull T message, int messageId) throws Throwable;

    static <T> @NotNull MessageReader<T> fail(@NotNull Supplier<@Nullable Throwable> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new MessageReader<>() {
            @Override
            public void check(@NotNull List<@NotNull Control> controls, @NotNull T message, int messageId) {
            }

            @Override
            public @NotNull T read(ByteBuffer.@NotNull Reader reader) throws Throwable {
                Throwable throwable;
                try {
                    throwable=supplier.get();
                }
                catch (Throwable throwable2) {
                    throwable=throwable2;
                }
                if (null==throwable) {
                    throwable=new NullPointerException();
                }
                throw throwable;
            }
        };
    }

    default <U> @NotNull ParallelMessageReader<T, U> parallel(@NotNull Function<@NotNull LdapMessage<T>, U> function) {
        return new ParallelMessageReader<>(function, this);
    }

    @NotNull T read(@NotNull ByteBuffer.Reader reader) throws Throwable;
}
