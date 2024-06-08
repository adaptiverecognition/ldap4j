package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record ExtendedResponse(
        @NotNull LdapResult ldapResult,
        @Nullable String responseName,
        @Nullable String responseValue) {
    public ExtendedResponse(
            @NotNull LdapResult ldapResult,
            @Nullable String responseName,
            @Nullable String responseValue) {
        this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
        this.responseName=responseName;
        this.responseValue=responseValue;
    }

    public static @NotNull ExtendedResponse read(@NotNull ByteBuffer.Reader reader) throws Throwable {
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

    public static @Nullable Either<@NotNull String, @NotNull String> readOptionalNameValue(
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

    public static @NotNull Pair<@Nullable String, @Nullable String> readOptionalNameValues(
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
