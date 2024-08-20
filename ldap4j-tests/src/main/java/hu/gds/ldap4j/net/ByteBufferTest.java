package hu.gds.ldap4j.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ByteBufferTest {
    @Test
    public void test() throws Throwable {
        ByteBuffer buffer0=ByteBuffer.create((byte)65);
        ByteBuffer buffer1=ByteBuffer.create((byte)66, (byte)67);
        ByteBuffer buffer2=ByteBuffer.create((byte)68, (byte)69, (byte)70);
        assertNotEquals(null, buffer0);
        assertNotEquals(buffer0, buffer1);

        ByteBuffer concat0=buffer0.append(buffer1).append(buffer2);
        ByteBuffer concat1=buffer0.append(buffer1.append(buffer2));
        assertEquals(concat0.hashCode(), concat1.hashCode());
        assertEquals(concat0, concat1);
        assertEquals(concat0.toString(), concat1.toString());
        assertEquals("A", buffer0.toString());
        assertEquals("BC", buffer1.toString());
        assertEquals("DEF", buffer2.toString());
        assertEquals("ABCDEF", concat0.toString());
        assertEquals("ABCDEF", concat1.toString());
        assertEquals("ABCDEF", concat1.utf8());

        ByteBuffer concat2=concat0.append(concat1);
        ByteBuffer concat3=concat1.append(concat0);
        assertEquals(concat2, concat3);
        assertEquals("ABCDEFABCDEF", concat2.toString());
        assertEquals("ABCDEFABCDEF", concat3.toString());
        for (int from=0; concat2.size()>=from; ++from) {
            for (int to=from; concat2.size()>=to; ++to) {
                ByteBuffer subBuffer0=concat2.subBuffer(from, to);
                ByteBuffer subBuffer1=concat3.subBuffer(from, to);
                assertEquals(subBuffer0.hashCode(), subBuffer1.hashCode());
                assertEquals(subBuffer0, subBuffer1);
                assertEquals(subBuffer0.toString(), subBuffer1.toString());
                assertEquals("ABCDEFABCDEF".substring(from, to), subBuffer1.toString());
            }
        }

        ByteBuffer hash0=ByteBuffer.create((byte)0, (byte)1);
        ByteBuffer hash1=ByteBuffer.create((byte)ByteBuffer.HASHCODE_MULTIPLIER, (byte)0);
        ByteBuffer hash2=ByteBuffer.create((byte)0).append(ByteBuffer.create((byte)1));
        ByteBuffer hash3=ByteBuffer.create((byte)ByteBuffer.HASHCODE_MULTIPLIER).append(ByteBuffer.create((byte)0));
        assertEquals(hash0.hashCode(), hash1.hashCode());
        assertEquals(hash0.hashCode(), hash2.hashCode());
        assertEquals(hash0.hashCode(), hash3.hashCode());
        assertEquals(hash0, hash2);
        assertEquals(hash1, hash3);
        assertNotEquals(hash0, hash1);
        assertNotEquals(hash2, hash3);
    }
}
