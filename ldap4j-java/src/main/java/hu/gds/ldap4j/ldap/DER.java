package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.net.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public abstract class DER {
    public static final byte BOOLEAN=0x01;
    public static final byte OCTET_STRING=0x04;
    public static final byte ENUMERATED=0x0a;
    public static final byte INTEGER=0x02;
    public static final byte SEQUENCE=0x30;
    public static final byte SET=0x31;

    public enum TagClass {
        APPLICATION, CONTEXT_SPECIFIC, PRIVATE, UNIVERSAL
    }

    private DER() {
    }

    public static @NotNull TagClass getTagClass(byte tag) {
        return switch (tag&0xc0) {
            case 0x00 -> TagClass.UNIVERSAL;
            case 0x40 -> TagClass.APPLICATION;
            case 0x80 -> TagClass.CONTEXT_SPECIFIC;
            default -> TagClass.PRIVATE;
        };
    }

    public static int getTagType(byte tag) {
        return tag&31;
    }

    public static boolean isTagConstructed(byte tag) {
        return 0!=(tag&32);
    }

    public static boolean readBooleanNoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        byte result=0;
        while (reader.hasRemainingBytes()) {
            result|=reader.readByte();
        }
        return 0!=result;
    }

    public static boolean readBooleanTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return DER.readTag(DER::readBooleanNoTag, reader, BOOLEAN);
    }

    public static int readEnumeratedNoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return readIntegerNoTag(false, reader);
    }

    public static int readEnumeratedTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return readTag(DER::readEnumeratedNoTag, reader, ENUMERATED);
    }

    public static int readIntegerNoTag(boolean nonNegative, @NotNull ByteBuffer.Reader reader) throws Throwable {
        if (4<reader.remainingBytes()) {
            throw new RuntimeException("integer too large, size: %,d bytes".formatted(reader.remainingBytes()));
        }
        int result=0;
        if (reader.hasRemainingBytes()) {
            result=reader.readByte()&0xff;
            if (0!=(result&0x80)) {
                result^=0xffffff00;
            }
            while (reader.hasRemainingBytes()) {
                result=(result<<8)|(reader.readByte()&0xff);
            }
        }
        if (nonNegative && (0>result)) {
            throw new RuntimeException("negative integer %,d".formatted(result));
        }
        return result;
    }

    public static int readIntegerTag(boolean nonNegative, @NotNull ByteBuffer.Reader reader) throws Throwable {
        return readTag((reader2)->DER.readIntegerNoTag(nonNegative, reader2), reader, INTEGER);
    }

    public static int readLength(@NotNull ByteBuffer.Reader reader) throws Throwable {
        byte bb=reader.readByte();
        if (0==(bb&128)) {
            return bb&127;
        }
        bb&=127;
        if (0==bb) {
            throw new RuntimeException("indefinite length is not supported yet");
        }
        if (4<bb) {
            throw new RuntimeException("length too large, size: %,d bytes".formatted(bb));
        }
        int result=0;
        for (; 0<bb; --bb) {
            result=(result<<8)|(reader.readByte()&0xff);
        }
        if (0>result) {
            throw new RuntimeException("length too large, size: %,d".formatted(result));
        }
        return result;
    }

    public static byte@NotNull[] readOctetStringNoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return reader.readReaminingByteBuffer().arrayCopy();
    }

    public static <T> T readOptionalTag(
            @NotNull Function<ByteBuffer.@NotNull Reader, T> function,
            @NotNull ByteBuffer.Reader reader,
            @NotNull Supplier<T> supplier,
            byte tag) throws Throwable {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(supplier, "supplier");
        if (reader.hasRemainingBytes()) {
            return readTag(
                    (Byte tag2)->{
                        if (tag==tag2) {
                            return Either.left(function);
                        }
                        else {
                            return Either.right(supplier.get());
                        }
                    },
                    reader);
        }
        else {
            return supplier.get();
        }
    }

    public static <T> T readSequence(
            @NotNull Function<ByteBuffer.@NotNull Reader, T> function, ByteBuffer.Reader reader) throws Throwable {
        return readTag(function, reader, SEQUENCE);
    }

    public static byte readTag(boolean peek, @NotNull ByteBuffer.Reader reader) throws Throwable {
        byte tag=peek
                ?reader.peekByte()
                :reader.readByte();
        if (31==(tag&31)) {
            throw new RuntimeException("long form tags are not supported yet, first byte: 0x%x".formatted(tag));
        }
        return tag;
    }

    public static byte readTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return readTag(false, reader);
    }

    public static <T> T readTag(
            @NotNull Function<
                    @NotNull Byte,
                    @NotNull Either<@NotNull Function<ByteBuffer.@NotNull Reader, T>, T>> function,
            @NotNull ByteBuffer.Reader reader) throws Throwable {
        Objects.requireNonNull(function, "function");
        byte tag=readTag(true, reader);
        @NotNull Either<@NotNull Function<ByteBuffer.@NotNull Reader, T>, T> either
                =Objects.requireNonNull(function.apply(tag), "function.apply(tag)");
        if (either.isRight()) {
            return either.right();
        }
        @NotNull Function<ByteBuffer.@NotNull Reader, T> function2
                =Objects.requireNonNull(either.left(), "either.left()");
        readTag(false, reader);
        int length=readLength(reader);
        return reader.readBytes(length, function2);
    }

    public static <T> T readTag(
            @NotNull Function<ByteBuffer.@NotNull Reader, T> function,
            @NotNull ByteBuffer.Reader reader,
            byte tag) throws Throwable {
        Objects.requireNonNull(function, "function");
        return readTag(
                (tag2)->{
                    if (tag!=tag2) {
                        throw new RuntimeException("expected tag 0x%x, got 0x%x".formatted(tag, tag2));
                    }
                    return Either.left(function);
                },
                reader);
    }

    public static @NotNull String readUtf8NoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return new String(reader.readReaminingByteBuffer().arrayCopy(), StandardCharsets.UTF_8);
    }

    public static @NotNull String readUtf8Tag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return readTag(DER::readUtf8NoTag, reader, OCTET_STRING);
    }

    public static @NotNull ByteBuffer writeBooleanNoTag(boolean value) {
        return ByteBuffer.create((byte)(value?1:0));
    }

    public static @NotNull ByteBuffer writeBooleanTag(boolean value) {
        return DER.writeTag(BOOLEAN, writeBooleanNoTag(value));
    }

    public static @NotNull ByteBuffer writeEnumeratedNoTag(int value) {
        return writeIntegerNoTag(value);
    }

    public static @NotNull ByteBuffer writeEnumeratedTag(int value) {
        return writeTag(ENUMERATED, writeEnumeratedNoTag(value));
    }

    public static @NotNull ByteBuffer writeIntegerNoTag(int value) {
        byte b0=(byte)(value&0xff);
        byte b1=(byte)((value>>8)&0xff);
        byte b2=(byte)((value>>16)&0xff);
        byte b3=(byte)((value>>24)&0xff);
        if (0<=value) {
            if ((0!=b3) || (0!=(b2&0x80))) {
                return ByteBuffer.create(b3, b2, b1, b0);
            }
            else if ((0!=b2) || (0!=(b1&0x80))) {
                return ByteBuffer.create(b2, b1, b0);
            }
            else if ((0!=b1) || (0!=(b0&0x80))) {
                return ByteBuffer.create(b1, b0);
            }
            else {
                return ByteBuffer.create(b0);
            }
        }
        else {
            if ((0xff!=(b3&0xff)) || (0==(b2&0x80))) {
                return ByteBuffer.create(b3, b2, b1, b0);
            }
            else if ((0xff!=(b2&0xff)) || (0==(b1&0x80))) {
                return ByteBuffer.create(b2, b1, b0);
            }
            else if ((0xff!=(b1&0xff)) || (0==(b0&0x80))) {
                return ByteBuffer.create(b1, b0);
            }
            else {
                return ByteBuffer.create(b0);
            }
        }
    }

    public static @NotNull ByteBuffer writeIntegerTag(int value) {
        return writeTag(INTEGER, writeIntegerNoTag(value));
    }

    public static <T> @NotNull ByteBuffer writeIterable(
            @NotNull Function<T, @NotNull ByteBuffer> function, @NotNull Iterable<T> iterable) throws Throwable {
        ByteBuffer result=ByteBuffer.EMPTY;
        for (T value: iterable) {
            result=result.append(function.apply(value));
        }
        return result;
    }

    public static @NotNull ByteBuffer writeLength(int length) {
        if ((0>length)) {
            throw new IllegalArgumentException("invalid length %d".formatted(length));
        }
        if (127>=length) {
            return ByteBuffer.create((byte)length);
        }
        byte b0=(byte)(length&0xff);
        byte b1=(byte)((length>>8)&0xff);
        byte b2=(byte)((length>>16)&0xff);
        byte b3=(byte)((length>>24)&0xff);
        if (0!=b3) {
            return ByteBuffer.create((byte)0x84, b3, b2, b1, b0);
        }
        else if (0!=b2) {
            return ByteBuffer.create((byte)0x83, b2, b1, b0);
        }
        else if (0!=b1) {
            return ByteBuffer.create((byte)0x82, b1, b0);
        }
        else {
            return ByteBuffer.create((byte)0x81, b0);
        }
    }

    public static @NotNull ByteBuffer writeSequence(@NotNull ByteBuffer byteBuffer) {
        return writeTag(SEQUENCE, byteBuffer);
    }

    public static @NotNull ByteBuffer writeTag(byte tag) {
        if (31==(tag&31)) {
            throw new RuntimeException("long form tags are not supported yet, first byte: 0x%x".formatted(tag));
        }
        return ByteBuffer.create(tag);
    }

    public static @NotNull ByteBuffer writeTag(byte tag, @NotNull ByteBuffer byteBuffer) {
        return writeTag(tag)
                .append(writeLength(byteBuffer.size()))
                .append(byteBuffer);
    }

    public static @NotNull ByteBuffer writeUtf8NoTag(char[] value) {
        return ByteBuffer.create(StandardCharsets.UTF_8.encode(CharBuffer.wrap(value)));
    }

    public static @NotNull ByteBuffer writeUtf8NoTag(@NotNull String value) {
        return writeUtf8NoTag(value.toCharArray());
    }

    public static @NotNull ByteBuffer writeUtf8Tag(@NotNull String value) {
        return writeTag(OCTET_STRING, writeUtf8NoTag(value));
    }
}
