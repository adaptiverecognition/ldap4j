package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ExtendedResponse(
        @NotNull LdapResult ldapResult,
        @Nullable String responseName,
        @Nullable String responseValue) {
    public static abstract class Reader implements MessageReader<ExtendedResponse> {
        public static class Cancel extends ExtendedResponse.Reader {
            @Override
            public void check(@NotNull ExtendedResponse message) throws Throwable {
                message.ldapResult.checkCancel();
            }
        }

        public static class Success extends ExtendedResponse.Reader {
            @Override
            public void check(@NotNull ExtendedResponse message) throws Throwable {
                message.ldapResult.checkSuccess();
            }
        }

        @Override
        public @NotNull ExtendedResponse read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return DER.readTag(
                    (reader2)->{
                        LdapResult ldapResult=LdapResult.read(reader2);
                        Pair<String, String> nameValue=readOptionalNameValues(
                                Ldap.PROTOCOL_OP_EXTENDED_RESPONSE_NAME,
                                reader2,
                                Ldap.PROTOCOL_OP_EXTENDED_RESPONSE_VALUE);
                        return new ExtendedResponse(ldapResult, nameValue.first(), nameValue.second());
                    },
                    reader,
                    Ldap.PROTOCOL_OP_EXTENDED_RESPONSE);
        }

        private static @Nullable Either<@NotNull String, @NotNull String> readOptionalNameValue(
                byte nameTag, @NotNull ByteBuffer.Reader reader, byte valueTag) throws Throwable {
            if (reader.hasRemainingBytes()) {
                return DER.readTags(
                        Map.of(
                                nameTag, (reader2)->Either.left(DER.readUtf8NoTag(reader2)),
                                valueTag, (reader2)->Either.right(DER.readUtf8NoTag(reader2))),
                        reader);
            }
            else {
                return null;
            }
        }

        private static @NotNull Pair<@Nullable String, @Nullable String> readOptionalNameValues(
                byte nameTag, @NotNull ByteBuffer.Reader reader, byte valueTag) throws Throwable {
            String name=null;
            String value=null;
            for (@Nullable Either<@NotNull String, @NotNull String> either;
                 null!=(either=readOptionalNameValue(nameTag, reader, valueTag)); ) {
                if (either.isLeft()) {
                    if (null!=name) {
                        throw new RuntimeException("multiple names %s, %s".formatted(name, either.left()));
                    }
                    name=either.left();
                }
                else {
                    if (null!=value) {
                        throw new RuntimeException("multiple values %s, %s".formatted(value, either.right()));
                    }
                    value=either.right();
                }
            }
            return Pair.of(name, value);
        }
    }

    public static final @NotNull MessageReader<ExtendedResponse> READER_CANCEL=new Reader.Cancel();
    public static final @NotNull MessageReader<ExtendedResponse> READER_SUCCESS=new Reader.Success();

    public ExtendedResponse(
            @NotNull LdapResult ldapResult,
            @Nullable String responseName,
            @Nullable String responseValue) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
        this.responseName=responseName;
        this.responseValue=responseValue;
    }
}
