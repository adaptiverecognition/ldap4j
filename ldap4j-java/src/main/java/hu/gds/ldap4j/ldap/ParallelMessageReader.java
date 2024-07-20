package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record ParallelMessageReader<T, U>(
        @NotNull Function<@NotNull LdapMessage<T>, U> function,
        @NotNull MessageReader<T> messageReader) {
    public ParallelMessageReader(
            @NotNull Function<@NotNull LdapMessage<T>, U> function,
            @NotNull MessageReader<T> messageReader) {
        this.function=Objects.requireNonNull(function, "function");
        this.messageReader=Objects.requireNonNull(messageReader, "messageReader");
    }

    /**
     * Unlike most Lava's, creating this reads the message.
     */
    public @NotNull Lava<U> readMessageChecked(int messageId, @NotNull ByteBuffer.Reader reader) throws Throwable {
        @NotNull T message=messageReader.read(reader);
        @NotNull List<@NotNull Control> controls=LdapMessage.controls(reader);
        return Lava.supplier(()->{
            messageReader.check(controls, message, messageId);
            @NotNull LdapMessage<T> ldapMessage=new LdapMessage<>(controls, message, messageId);
            U result=function.apply(ldapMessage);
            return Lava.complete(result);
        });
    }
}
