package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Function;
import java.io.EOFException;
import java.util.Arrays;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public sealed interface ByteBuffer {
    @NotNull ByteBuffer EMPTY=new ByteArray(new byte[0], 0, 0);
    int SMALL_BUFFER_SIZE=64;

    sealed abstract class Abstract implements ByteBuffer {
        @Override
        public String toString() {
            return "ByteBuffer(size:"+size()+")";
        }
    }

    final class ByteArray extends Abstract {
        private final byte[] array;
        private final int length;
        private final int offset;

        private ByteArray(byte[] array, int length, int offset) {
            if (0>length) {
                throw new IllegalArgumentException("0 > length %d".formatted(length));
            }
            if (0<length) {
                if (0>offset) {
                    throw new IllegalArgumentException("0 > offset %d".formatted(offset));
                }
                if (array.length<offset+length) {
                    throw new IllegalArgumentException("array.length %d < offset %d + length %d".formatted(
                            array.length, offset, length));
                }
            }
            this.array=Objects.requireNonNull(array, "array");
            this.length=length;
            this.offset=offset;
        }

        @Override
        public int size() {
            return length;
        }

        @Override
        public void write(@NotNull Writer writer) {
            if (0<length) {
                writer.write(array, offset, length);
            }
        }
    }

    final class Concat extends Abstract {
        private final @NotNull ByteBuffer left;
        private final @NotNull ByteBuffer right;
        private final int size;

        private Concat(@NotNull ByteBuffer left, @NotNull ByteBuffer right) {
            this.left=Objects.requireNonNull(left, "left");
            this.right=Objects.requireNonNull(right, "right");
            size=left.size()+right.size();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void write(@NotNull Writer writer) {
            left.write(writer);
            right.write(writer);
        }
    }

    /**
     * Read methods throw EOFException on no more bytes.
     */
    class Reader {
        private byte[] firstArray;
        private int firstLength;
        private int firstOffset;
        private int offset;
        private int remainingBytes;
        private @NotNull ByteBuffer rest;

        private Reader(@NotNull ByteBuffer byteBuffer) {
            rest=Objects.requireNonNull(byteBuffer, "byteBuffer");
            remainingBytes=byteBuffer.size();
        }

        public void assertNoRemainingBytes() {
            if (hasRemainingBytes()) {
                throw new RuntimeException("remainingBytes %d".formatted(remainingBytes()));
            }
        }

        private void ensureOneByte() throws Throwable {
            if (!hasRemainingBytes()) {
                throw new EOFException("remainingBytes %d".formatted(remainingBytes()));
            }
            while (0>=firstLength) {
                ByteBuffer byteBuffer=rest;
                rest=ByteBuffer.EMPTY;
                while (byteBuffer instanceof Concat concat) {
                    rest=concat.right.append(rest);
                    byteBuffer=concat.left;
                }
                ByteArray byteArray=(ByteArray)byteBuffer;
                firstArray=byteArray.array;
                firstLength=byteArray.length;
                firstOffset=byteArray.offset;
            }
        }

        public boolean hasRemainingBytes() {
            return 0<remainingBytes;
        }

        public int offset() {
            return offset;
        }

        public byte peekByte() throws Throwable {
            ensureOneByte();
            return firstArray[firstOffset];
        }

        public byte readByte() throws Throwable {
            ensureOneByte();
            byte result=firstArray[firstOffset];
            firstLength-=1;
            firstOffset+=1;
            ++offset;
            --remainingBytes;
            if (0>=firstLength) {
                firstArray=null;
            }
            return result;
        }

        public ByteBuffer readByteBuffer(int bytes) throws Throwable {
            if (remainingBytes()<bytes) {
                throw new EOFException("remainingBytes %d < bytes %d".formatted(remainingBytes(), bytes));
            }
            if (0>=bytes) {
                return EMPTY;
            }
            ByteBuffer result;
            if (firstLength>=bytes) {
                if (firstLength==bytes) {
                    result=create(firstArray, firstOffset, bytes);
                    firstArray=null;
                }
                else {
                    result=createCopy(firstArray, firstOffset, bytes);
                    firstOffset+=bytes;
                }
                firstLength-=bytes;
                offset+=bytes;
                remainingBytes-=bytes;
                return result;
            }
            if (0<firstLength) {
                result=create(firstArray, firstOffset, firstLength);
                offset+=firstLength;
                remainingBytes-=firstLength;
                firstArray=null;
                firstLength=0;
            }
            else {
                result=EMPTY;
            }
            while (result.size()<bytes) {
                ByteBuffer byteBuffer=rest;
                rest=ByteBuffer.EMPTY;
                while ((result.size()+byteBuffer.size()>bytes) && (byteBuffer instanceof Concat concat)) {
                    rest=concat.right.append(rest);
                    byteBuffer=concat.left;
                }
                if (result.size()+byteBuffer.size()<=bytes) {
                    result=result.append(byteBuffer);
                    offset+=byteBuffer.size();
                    remainingBytes-=byteBuffer.size();
                }
                else {
                    ByteArray byteArray=(ByteArray)byteBuffer;
                    int remaining=bytes-result.size();
                    result=result.append(createCopy(byteArray.array, byteArray.offset, remaining));
                    firstArray=byteArray.array;
                    firstLength=byteArray.length-remaining;
                    firstOffset=byteArray.offset+remaining;
                    offset+=remaining;
                    remainingBytes-=remaining;
                }
            }
            return result;
        }

        public <T> T readBytes(int bytes, @NotNull Function<@NotNull Reader, T> function) throws Throwable {
            ByteBuffer.Reader reader=readByteBuffer(bytes).reader();
            T result=function.apply(reader);
            reader.assertNoRemainingBytes();
            return result;
        }

        public long readLong() throws Throwable {
            return ((readByte()&255L)<<56)
                    |((readByte()&255L)<<48)
                    |((readByte()&255L)<<40)
                    |((readByte()&255L)<<32)
                    |((readByte()&255L)<<24)
                    |((readByte()&255L)<<16)
                    |((readByte()&255L)<<8)
                    |(readByte()&255L);
        }

        public ByteBuffer readReaminingByteBuffer() throws Throwable {
            return readByteBuffer(remainingBytes());
        }

        public int remainingBytes() {
            return remainingBytes;
        }

        @Override
        public String toString() {
            return "ByteBuffer.Reader(firstArray: "+Arrays.toString(firstArray)
                    +", firstLength: "+firstLength
                    +", firstOffset: "+firstOffset
                    +", offset: "+offset
                    +", remainingBytes: "+remainingBytes
                    +", rest: "+rest+")";
        }
    }

    interface Writer {
        class Array implements Writer {
            public final byte@NotNull[] array;
            public int arrayOffset;

            public Array(byte@NotNull[] array, int arrayOffset) {
                this.array=Objects.requireNonNull(array, "array");
                this.arrayOffset=arrayOffset;
            }

            public Array(int size) {
                this(new byte[size], 0);
            }

            @Override
            public void write(byte[] array, int offset, int length) {
                if (0<length) {
                    System.arraycopy(array, offset, this.array, arrayOffset, length);
                    arrayOffset+=length;
                }
            }
        }

        class NioByteBuffer implements Writer {
            public final @NotNull java.nio.ByteBuffer byteBuffer;

            public NioByteBuffer(@NotNull java.nio.ByteBuffer byteBuffer) {
                this.byteBuffer=Objects.requireNonNull(byteBuffer, "byteBuffer");
            }

            public NioByteBuffer(int size) {
                this(java.nio.ByteBuffer.allocate(size));
            }

            @Override
            public void write(byte[] array, int offset, int length) {
                if (0<length) {
                    byteBuffer.put(array, offset, length);
                }
            }
        }

        void write(byte[] array, int offset, int length);
    }

    default @NotNull ByteBuffer append(@NotNull ByteBuffer buffer) {
        return concat(this, buffer);
    }

    default @NotNull ByteBuffer appendLong(long value) {
        return append(createLong(value));
    }

    default byte@NotNull[] arrayCopy() {
        Writer.Array array=new Writer.Array(size());
        write(array);
        if (size()!=array.arrayOffset) {
            throw new IllegalStateException("size() %d != array.arrayOffset %d".formatted(size(), array.arrayOffset));
        }
        return array.array;
    }

    static @NotNull ByteBuffer concat(@NotNull ByteBuffer left, @NotNull ByteBuffer right) {
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        if (SMALL_BUFFER_SIZE>=left.size()+right.size()) {
            return concatCopy(left, right);
        }
        if (left instanceof Concat leftConcat) {
            if (SMALL_BUFFER_SIZE>=leftConcat.right.size()+right.size()) {
                return new Concat(leftConcat.left, concatCopy(leftConcat.right, right));
            }
        }
        if (right instanceof Concat rightConcat) {
            if (SMALL_BUFFER_SIZE>=left.size()+rightConcat.left.size()) {
                return new Concat(concatCopy(left, rightConcat.left), rightConcat.right);
            }
        }
        return new Concat(left, right);
    }

    static @NotNull ByteBuffer concatCopy(@NotNull ByteBuffer left, @NotNull ByteBuffer right) {
        int size=left.size()+right.size();
        if (0>=size) {
            return EMPTY;
        }
        Writer.Array writer=new Writer.Array(size);
        left.write(writer);
        if (left.size()!=writer.arrayOffset) {
            throw new IllegalStateException(
                    "left.size() %d != writer.arrayOffset %d".formatted(left.size(), writer.arrayOffset));
        }
        right.write(writer);
        if (size!=writer.arrayOffset) {
            throw new IllegalStateException("size %d != writer.arrayOffset %d".formatted(size, writer.arrayOffset));
        }
        return new ByteArray(writer.array, size, 0);
    }

    static @NotNull ByteBuffer create(byte... array) {
        return create(array, 0, array.length);
    }

    static @NotNull ByteBuffer create(byte[] array, int offset, int length) {
        if (0>=length) {
            return EMPTY;
        }
        return new ByteArray(array, length, offset);
    }

    static @NotNull ByteBuffer create(@NotNull java.nio.ByteBuffer byteBuffer) {
        if (0>=byteBuffer.remaining()) {
            return EMPTY;
        }
        byte[] array=new byte[byteBuffer.remaining()];
        byteBuffer.get(array);
        return create(array);
    }

    static @NotNull ByteBuffer createCopy(byte[] array, int offset, int length) {
        if (0>=length) {
            return EMPTY;
        }
        return new ByteArray(Arrays.copyOfRange(array, offset, offset+length), length, 0);
    }

    static ByteBuffer createLong(long value) {
        byte[] array=new byte[8];
        array[0]=(byte)(value >>> 56);
        array[1]=(byte)(value >>> 48);
        array[2]=(byte)(value >>> 40);
        array[3]=(byte)(value >>> 32);
        array[4]=(byte)(value >>> 24);
        array[5]=(byte)(value >>> 16);
        array[6]=(byte)(value >>> 8);
        array[7]=(byte)value;
        return new ByteArray(array, 8, 0);
    }

    default  boolean isEmpty() {
        return 0>=size();
    }

    default java.nio.ByteBuffer nioByteBufferCopy() {
        Writer.NioByteBuffer nioByteBuffer=new Writer.NioByteBuffer(size());
        write(nioByteBuffer);
        if (nioByteBuffer.byteBuffer.hasRemaining()) {
            throw new IllegalStateException(
                    "nioByteBuffer.byteBuffer.hasRemaining(), remaining %d"
                            .formatted(nioByteBuffer.byteBuffer.remaining()));
        }
        return nioByteBuffer.byteBuffer.flip();
    }

    default <T> T read(@NotNull Function<@NotNull Reader, T> function) throws Throwable {
        @NotNull Reader reader=reader();
        T result=function.apply(reader);
        reader.assertNoRemainingBytes();
        return result;
    }

    default @NotNull Reader reader() {
        return new Reader(this);
    }

    int size();

    default @NotNull ByteBuffer subBuffer(int from, int to) throws Throwable {
        if (from>=to) {
            return EMPTY;
        }
        if ((0>from) || (size()<to)) {
            throw new IndexOutOfBoundsException("buffer size: %,d, from %,d, to%,d".formatted(size(), from, to));
        }
        Reader reader=reader();
        reader.readByteBuffer(from);
        return reader.readByteBuffer(to-from);
    }

    void write(@NotNull Writer writer);
}
