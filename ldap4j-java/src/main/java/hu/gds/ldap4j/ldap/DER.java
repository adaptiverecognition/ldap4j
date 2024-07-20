package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.net.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public static int readIntegerNoTag(boolean integer, @NotNull ByteBuffer.Reader reader) throws Throwable {
        if (4<reader.remainingBytes()) {
            throw new RuntimeException("integer too large, size: %d".formatted(reader.remainingBytes()));
        }
        boolean negative=false;
        int result=0;
        if (reader.hasRemainingBytes()) {
            result=reader.readByte()&255;
            if (integer && (0!=(result&0x80))) {
                negative=true;
                result^=0xffffff00;
            }
            while (reader.hasRemainingBytes()) {
                result=(result<<8)|(reader.readByte()&255);
            }
        }
        if (negative) {
            throw new RuntimeException("negative integer %,d".formatted(result));
        }
        return result;
    }

    public static int readIntegerTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return readTag((reader2)->DER.readIntegerNoTag(true, reader2), reader, INTEGER);
    }

    public static int readLength(ByteBuffer.Reader reader) throws Throwable {
        byte bb=reader.readByte();
        if (0==(bb&128)) {
            return bb&127;
        }
        bb&=127;
        if (0==bb) {
            throw new RuntimeException("indefinite length is not supported yet");
        }
        return reader.readBytes(bb, (reader2)->DER.readIntegerNoTag(false, reader2));
    }

    public static <T> T readSequence(
            @NotNull Function<ByteBuffer.Reader, T> function, ByteBuffer.Reader reader) throws Throwable {
        return readTag(function, reader, SEQUENCE);
    }

    public static byte readTag(ByteBuffer.Reader reader) throws Throwable {
        byte tag=reader.readByte();
        if (31==(tag&31)) {
            throw new RuntimeException("long form tags are not supported yet, first byte: 0x%x".formatted(tag));
        }
        return tag;
    }

    public static <T> T readTag(
            @NotNull Function<ByteBuffer.Reader, T> function, ByteBuffer.Reader reader, byte tag) throws Throwable {
        byte tag2=readTag(reader);
        if (tag!=tag2) {
            throw new RuntimeException("expected tag 0x%x, got 0x%x".formatted(tag, tag2));
        }
        int length=readLength(reader);
        return reader.readBytes(length, function);
    }

    public static <T> T readTags(
            @NotNull Map<@NotNull Byte, @NotNull Function<ByteBuffer.Reader, ? extends T>> map,
            @NotNull ByteBuffer.Reader reader) throws Throwable {
        byte tag=readTag(reader);
        Function<ByteBuffer.Reader, ? extends T> function=map.get(tag);
        if (null==function) {
            throw new RuntimeException("unexpected tag 0x%x, expected tags %s".formatted(tag, map.keySet()));
        }
        int length=readLength(reader);
        return reader.readBytes(length, function);
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

    public static @NotNull ByteBuffer writeBooleanTag(boolean value) throws Throwable {
        return DER.writeTag(BOOLEAN, writeBooleanNoTag(value));
    }

    public static @NotNull ByteBuffer writeEnumeratedNoTag(int value) throws Throwable {
        return writeIntegerNoTag(false, null, value);
    }

    public static @NotNull ByteBuffer writeEnumeratedTag(int value) throws Throwable {
        return writeTag(ENUMERATED, writeIntegerNoTag(false, null, value));
    }

    public static @NotNull ByteBuffer writeIntegerNoTag(
            boolean integer, @Nullable Function<@NotNull Integer, @NotNull ByteBuffer> size, int value)
            throws Throwable {
        if ((0>value)) {
            throw new IllegalArgumentException("invalid integer value %d".formatted(value));
        }
        byte b0=(byte)(value&255);
        byte b1=(byte)((value>>8)&255);
        byte b2=(byte)((value>>16)&255);
        byte b3=(byte)((value>>24)&255);
        ByteBuffer value2;
        if (0!=b3 || (integer && (0!=(b2&0x80)))) {
            value2=ByteBuffer.create(b3, b2, b1, b0);
        }
        else if ((0!=b2) || (integer && (0!=(b1&0x80)))) {
            value2=ByteBuffer.create(b2, b1, b0);
        }
        else if ((0!=b1) || (integer && (0!=(b0&0x80)))) {
            value2=ByteBuffer.create(b1, b0);
        }
        else {
            value2=ByteBuffer.create(b0);
        }
        if (null==size) {
            return value2;
        }
        return size.apply(value2.size()).append(value2);
    }

    public static @NotNull ByteBuffer writeIntegerNoTag(int value) throws Throwable {
        return writeIntegerNoTag(true, null, value);
    }

    public static @NotNull ByteBuffer writeIntegerTag(int value) throws Throwable {
        return writeTag(INTEGER, writeIntegerNoTag(true, null, value));
    }

    public static <T> @NotNull ByteBuffer writeIterable(
            @NotNull Function<T, @NotNull ByteBuffer> function, @NotNull Iterable<T> iterable) throws Throwable {
        ByteBuffer result=ByteBuffer.EMPTY;
        for (T value: iterable) {
            result=result.append(function.apply(value));
        }
        return result;
    }

    public static @NotNull ByteBuffer writeLength(int length) throws Throwable {
        if ((0>length)) {
            throw new IllegalArgumentException("invalid length %d".formatted(length));
        }
        if (127>=length) {
            return ByteBuffer.create((byte)length);
        }
        return writeIntegerNoTag(
                false,
                (size)->{
                    if ((1>size) || (126<size)) {
                        throw new IllegalArgumentException("invalid size %,d".formatted(size));
                    }
                    return ByteBuffer.create((byte)(size|0x80));
                },
                length);
    }

    public static @NotNull ByteBuffer writeNullNoTag() {
        return ByteBuffer.EMPTY;
    }

    public static @NotNull ByteBuffer writeSequence(@NotNull ByteBuffer byteBuffer) throws Throwable {
        return writeTag(SEQUENCE, byteBuffer);
    }

    public static @NotNull ByteBuffer writeTag(byte tag) {
        if (31==(tag&31)) {
            throw new RuntimeException("long form tags are not supported yet, first byte: 0x%x".formatted(tag));
        }
        return ByteBuffer.create(tag);
    }

    public static @NotNull ByteBuffer writeTag(byte tag, @NotNull ByteBuffer byteBuffer) throws Throwable {
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

    public static @NotNull ByteBuffer writeUtf8Tag(@NotNull String value) throws Throwable {
        return writeTag(OCTET_STRING, writeUtf8NoTag(value));
    }
}
