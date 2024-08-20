package hu.gds.ldap4j.net;

import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.Function;
import java.io.EOFException;
import org.jetbrains.annotations.NotNull;

public class ReadBuffer {
    private @NotNull ByteBuffer buffer=ByteBuffer.empty();
    private boolean endOfStream;

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public <T> @NotNull Lava<T> read(
            @NotNull DuplexConnection connection,
            @NotNull Function<ByteBuffer.Reader, @NotNull Lava<T>> function) {
        return Lava.checkEndNanos(ReadBuffer.class+".read() timeout")
                .composeIgnoreResult(()->Lava.catchErrors(
                        (eof)->{
                            if (endOfStream) {
                                throw eof;
                            }
                            else {
                                return connection.read()
                                        .compose((result)->{
                                            if (null==result) {
                                                endOfStream=true;
                                                throw eof;
                                            }
                                            else {
                                                buffer=buffer.append(result);
                                                return read(connection, function);
                                            }
                                        });
                            }
                        },
                        ()->{
                            ByteBuffer.Reader reader=buffer.reader();
                            return function.apply(reader)
                                    .compose((result)->{
                                        buffer=reader.readReaminingByteBuffer();
                                        return Lava.complete(result);
                                    });
                        },
                        EOFException.class));
    }

    public @NotNull Lava<@NotNull Long> readLong(@NotNull DuplexConnection connection) {
        return read(connection, (reader)->Lava.complete(reader.readLong()));
    }
}
