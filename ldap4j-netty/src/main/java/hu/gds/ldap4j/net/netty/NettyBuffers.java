package hu.gds.ldap4j.net.netty;

import hu.gds.ldap4j.net.ByteBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

public class NettyBuffers {
    private NettyBuffers() {
    }

    public static @NotNull ByteBuffer fromNetty(@NotNull ByteBuf byteBuf) {
        byte[] array=new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(array);
        return ByteBuffer.create(array);
    }

    public static @NotNull ByteBuf toNetty(@NotNull ByteBuffer byteBuffer) {
        ByteBuf byteBuf=Unpooled.buffer(byteBuffer.size());
        byteBuffer.write(new ByteBuffer.Write() {
            @Override
            public void array(byte @NotNull [] array, int from, int to) {
                byteBuf.writeBytes(array, from, to-from);
            }

            @Override
            public void nioBuffer(java.nio.@NotNull ByteBuffer buffer) {
                byteBuf.writeBytes(buffer);
            }
        });
        return byteBuf;
    }
}
