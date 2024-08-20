package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Function;
import java.io.EOFException;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed abstract class ByteBuffer {
    protected static final class Array extends Direct {
        public static final @NotNull ByteBuffer EMPTY=new ByteBuffer.Array(new byte[0], 0, 0);

        private final byte @NotNull [] array;

        public Array(byte @NotNull [] array, int from, int to) {
            super(from, hashCode(array, from, to), array.length, to);
            this.array=Objects.requireNonNull(array, "buffer");
        }

        @Override
        protected byte getRaw(int index) {
            return array[index];
        }

        public static int hashCode(byte @NotNull [] array, int from, int to) {
            int result=0;
            for (int ii=to-1; from<=ii; --ii) {
                result=HASHCODE_MULTIPLIER*result+(array[ii]&255);
            }
            return result;
        }

        @Override
        protected @NotNull ByteBuffer subBufferRaw(int from, int to) {
            return new Array(array, from, to);
        }

        @Override
        protected void writeDirect(@NotNull Write write) {
            write.array(array, from, to);
        }
    }

    protected static final class Concat extends ByteBuffer {
        private final @NotNull ByteBuffer left;
        private final @NotNull ByteBuffer right;

        public Concat(@NotNull ByteBuffer left, @NotNull ByteBuffer right) {
            super(
                    left.hashCode+hashMultiplierPower(left.size())*right.hashCode,
                    left.size()+right.size());
            this.left=Objects.requireNonNull(left, "left");
            this.right=Objects.requireNonNull(right, "right");
        }

        @Override
        protected @NotNull ByteBuffer subBufferImpl(int from, int to) throws EOFException {
            @NotNull Reader reader=reader();
            reader.dropBytes(from);
            return reader.readByteBuffer(to-from);
        }
    }

    protected static sealed abstract class Direct extends ByteBuffer {
        protected final int from;
        protected final int to;

        public Direct(int from, int hashCode, int length, int to) {
            super(hashCode, checkIndicesAndSize(from, length, to));
            this.from=from;
            this.to=to;
        }

        protected static int checkIndicesAndSize(int from, int length, int to) {
            if (from>to) {
                throw new IndexOutOfBoundsException("length: %,d, from: %,d, to: %,d".formatted(length, from, to));
            }
            if ((from<to) && ((0>from) || (length<to))) {
                throw new IndexOutOfBoundsException("length: %,d, from: %,d, to: %,d".formatted(length, from, to));
            }
            return to-from;
        }

        protected byte getDirect(int index) {
            if ((0>index) || (size<index)) {
                throw new IndexOutOfBoundsException("size: %,d, index: %,d".formatted(size, index));
            }
            return getRaw(from+index);
        }

        protected abstract byte getRaw(int index);

        @Override
        protected @NotNull ByteBuffer subBufferImpl(int from, int to) {
            return subBufferRaw(this.from+from, this.from+to);
        }

        protected abstract @NotNull ByteBuffer subBufferRaw(int from, int to);

        protected abstract void writeDirect(@NotNull Write write);
    }

    protected static final class NioBuffer extends Direct {
        private final @NotNull java.nio.ByteBuffer buffer;

        public NioBuffer(@NotNull java.nio.ByteBuffer buffer, int from, int to) {
            super(from, hashCode(buffer), buffer.capacity(), to);
            this.buffer=Objects.requireNonNull(buffer, "buffer");
        }

        @Override
        protected byte getRaw(int index) {
            return buffer.get(index);
        }

        public static int hashCode(@NotNull java.nio.ByteBuffer buffer) {
            int result=0;
            for (int ii=buffer.limit()-1; buffer.position()<=ii; --ii) {
                result=HASHCODE_MULTIPLIER*result+(buffer.get(ii)&255);
            }
            return result;
        }

        @Override
        protected @NotNull ByteBuffer subBufferRaw(int from, int to) {
            return new NioBuffer(buffer, from, to);
        }

        @Override
        protected void writeDirect(@NotNull Write write) {
            @NotNull java.nio.ByteBuffer buffer2=this.buffer.duplicate();
            buffer2.limit(to);
            buffer2.position(from);
            write.nioBuffer(buffer2);
        }
    }

    public static final class Reader {
        private @Nullable Direct first;
        private int offset;
        private int remainingFirst;
        private int remaining;
        private final @NotNull Deque<@NotNull ByteBuffer> rest=new LinkedList<>();

        public Reader(@NotNull ByteBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer");
            rest.addLast(buffer);
            remaining=buffer.size;
        }

        public void assertNoRemainingBytes() {
            if (hasRemainingBytes()) {
                throw new RuntimeException("remaining bytes %,d".formatted(remainingBytes()));
            }
        }

        public void dropBytes(int bytes) throws EOFException {
            while (0<bytes) {
                if (0>=remainingFirst) {
                    if (rest.isEmpty()) {
                        throw new EOFException();
                    }
                    @NotNull ByteBuffer buffer=rest.removeFirst();
                    while ((buffer.size>bytes) && (buffer instanceof Concat concat)) {
                        rest.addFirst(concat.right);
                        buffer=concat.left;
                    }
                    if (buffer.size<=bytes) {
                        bytes-=buffer.size;
                        offset+=buffer.size;
                        remaining-=buffer.size;
                        continue;
                    }
                    first=(Direct)buffer;
                    remainingFirst=buffer.size;
                }
                Objects.requireNonNull(first, "first");
                int size2=Math.min(bytes, remainingFirst);
                bytes-=size2;
                offset+=size2;
                remaining-=size2;
                remainingFirst-=size2;
                if (0==remainingFirst) {
                    first=null;
                }
            }
        }

        private void ensureFirst() throws EOFException {
            while (0>=remainingFirst) {
                first=null;
                if (rest.isEmpty()) {
                    throw new EOFException();
                }
                @NotNull ByteBuffer buffer=rest.removeFirst();
                while (buffer instanceof Concat concat) {
                    rest.addFirst(concat.right);
                    buffer=concat.left;
                }
                first=(Direct)buffer;
                remainingFirst=first.size;
            }
        }

        public boolean hasRemainingBytes() {
            return 0<remainingBytes();
        }

        public int offset() {
            return offset;
        }

        public byte peekByte() throws EOFException {
            ensureFirst();
            Objects.requireNonNull(first, "first");
            return first.getDirect(first.size-remainingFirst);
        }

        public byte readByte() throws EOFException {
            ensureFirst();
            Objects.requireNonNull(first, "first");
            byte result=first.getDirect(first.size-remainingFirst);
            ++offset;
            --remainingFirst;
            --remaining;
            if (0>=remainingFirst) {
                first=null;
            }
            return result;
        }

        public @NotNull ByteBuffer readByteBuffer(int bytes) throws EOFException {
            @NotNull ByteBuffer result=empty();
            while (0<bytes) {
                if (0>=remainingFirst) {
                    if (rest.isEmpty()) {
                        throw new EOFException();
                    }
                    @NotNull ByteBuffer buffer=rest.removeFirst();
                    while ((buffer.size>bytes) && (buffer instanceof Concat concat)) {
                        rest.addFirst(concat.right);
                        buffer=concat.left;
                    }
                    if (buffer.size<=bytes) {
                        result=result.append(buffer);
                        bytes-=buffer.size;
                        offset+=buffer.size;
                        remaining-=buffer.size;
                        continue;
                    }
                    first=(Direct)buffer;
                    remainingFirst=buffer.size;
                }
                Objects.requireNonNull(first, "first");
                int size2=Math.min(bytes, remainingFirst);
                result=result.append(first.subBufferImpl(first.size-remainingFirst, first.size-remainingFirst+size2));
                bytes-=size2;
                offset+=size2;
                remaining-=size2;
                remainingFirst-=size2;
                if (0>=remainingFirst) {
                    first=null;
                }
            }
            return result;
        }

        public @NotNull ByteBuffer readReaminingByteBuffer() throws EOFException {
            return readByteBuffer(remainingBytes());
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

        public int remainingBytes() {
            return remaining;
        }
    }

    public interface Write {
        class Array implements Write {
            public final byte@NotNull[] array;
            public int offset;

            public Array(byte @NotNull [] array, int offset) {
                this.array=Objects.requireNonNull(array, "array");
                this.offset=offset;
            }

            @Override
            public void array(byte @NotNull [] array, int from, int to) {
                int size=to-from;
                System.arraycopy(array, from, this.array, offset, size);
                offset+=size;
            }

            @Override
            public void nioBuffer(java.nio.@NotNull ByteBuffer buffer) {
                int size=buffer.remaining();
                buffer.get(array, offset, size);
                offset+=size;
            }
        }

        class NioBuffer implements Write {
            public final @NotNull java.nio.ByteBuffer buffer;

            public NioBuffer(java.nio.ByteBuffer buffer) {
                this.buffer=Objects.requireNonNull(buffer, "buffer");
            }

            @Override
            public void array(byte @NotNull [] array, int from, int to) {
                buffer.put(array, from, to-from);
            }

            @Override
            public void nioBuffer(java.nio.@NotNull ByteBuffer buffer) {
                this.buffer.put(buffer);
            }
        }

        void array(byte@NotNull[] array, int from, int to);

        void nioBuffer(@NotNull java.nio.ByteBuffer buffer);
    }

    static final int HASHCODE_MULTIPLIER=13;

    protected final int hashCode;
    protected final int size;

    private ByteBuffer(int hashCode, int size) {
        if (0>size) {
            throw new IllegalArgumentException("0 > size %,d".formatted(size));
        }
        this.hashCode=hashCode;
        this.size=size;
    }

    public @NotNull ByteBuffer append(@NotNull ByteBuffer buffer) {
        if (isEmpty()) {
            return buffer;
        }
        if (buffer.isEmpty()) {
            return this;
        }
        return new Concat(this, buffer);
    }

    public byte@NotNull[] arrayCopy() {
        @NotNull Write.Array write=new Write.Array(new byte[size], 0);
        write(write);
        return write.array;
    }

    public static @NotNull ByteBuffer create(byte @NotNull ... array) {
        return create(array, 0, array.length);
    }

    public static @NotNull ByteBuffer create(byte @NotNull [] array, int from, int to) {
        if (from>=to) {
            return empty();
        }
        return new Array(array, from, to);
    }

    public static @NotNull ByteBuffer create(@NotNull java.nio.ByteBuffer buffer) {
        return new NioBuffer(buffer, buffer.position(), buffer.limit());
    }

    public static @NotNull ByteBuffer createCopy(byte @NotNull [] array, int from, int to) {
        return create(Arrays.copyOfRange(array, from, to));
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
        return create(array);
    }

    @Override
    public boolean equals(Object obj) {
        if (this==obj) {
            return true;
        }
        if (!(obj instanceof ByteBuffer buffer)) {
            return false;
        }
        if ((hashCode!=buffer.hashCode) || (size!=buffer.size)) {
            return false;
        }
        try {
            for (int ii=size; 0<ii; --ii) {
                @NotNull Reader reader0=reader();
                @NotNull Reader reader1=buffer.reader();
                if (reader0.readByte()!=reader1.readByte()) {
                    return false;
                }
            }
            return true;
        }
        catch (EOFException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static @NotNull ByteBuffer empty() {
        return Array.EMPTY;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int hashMultiplierPower(int exponent) {
        int base=HASHCODE_MULTIPLIER;
        int result=1;
        while (0<exponent) {
            if (0==(exponent&1)) {
                base*=base;
                exponent >>= 1;
            }
            else {
                result*=base;
                --exponent;
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return 0>=size();
    }

    public @NotNull java.nio.ByteBuffer nioByteBufferCopy() {
        @NotNull Write.NioBuffer write=new Write.NioBuffer(java.nio.ByteBuffer.allocate(size));
        write(write);
        return write.buffer.flip();
    }

    public <T> T read(@NotNull Function<@NotNull Reader, T> function) throws Throwable {
        @NotNull Reader reader=reader();
        T result=function.apply(reader);
        reader.assertNoRemainingBytes();
        return result;
    }

    public @NotNull Reader reader() {
        return new Reader(this);
    }

    public @NotNull ByteBuffer subBuffer(int from, int to) throws EOFException {
        if (from>=to) {
            return empty();
        }
        Direct.checkIndicesAndSize(from, size, to);
        if ((0==from) && (size==to)) {
            return this;
        }
        return subBufferImpl(from, to);
    }

    protected abstract @NotNull ByteBuffer subBufferImpl(int from, int to) throws EOFException;

    @Override
    public String toString() {
        try {
            @NotNull StringBuilder sb=new StringBuilder(size);
            @NotNull Reader reader=reader();
            while (reader.hasRemainingBytes()) {
                char cc=(char)(reader.readByte()&255);
                if ((32<=cc) && (127>=cc)) {
                    sb.append(cc);
                }
                else {
                    sb.append(".");
                }
            }
            return sb.toString();
        }
        catch (EOFException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public int size() {
        return size;
    }

    public void write(@NotNull Write write) {
        @NotNull Deque<@NotNull ByteBuffer> rest=new LinkedList<>();
        rest.add(this);
        while (!rest.isEmpty()) {
            @NotNull ByteBuffer buffer2=rest.removeFirst();
            while (buffer2 instanceof Concat concat) {
                rest.addFirst(concat.right);
                buffer2=concat.left;
            }
            @NotNull Direct direct=(Direct)buffer2;
            direct.writeDirect(write);
        }
    }
}
