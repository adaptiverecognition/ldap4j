package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class DERDump {
    public static final String INDENT="      ";
    public static final String INDENT_ELEMENT=" ---- ";
    public static final int MAX_BYTES_PER_LINE=64;
    public static final int MAX_CHARACTERS_PER_LINE=120;
    public static final boolean PRINT_ASCII=true;
    public static final boolean PRINT_OFFSET=true;
    public static final Map<Byte, Pair<String, Function<ByteBuffer.Reader, Object>>> TAGS;

    static {
        Map<Byte, Pair<String, Function<ByteBuffer.Reader, Object>>> tags=new HashMap<>();
        java.util.function.Function<Byte, BiConsumer<String, Function<ByteBuffer.Reader, Object>>> put
                =(tag)->(name, reader)->{
            Pair<String, Function<ByteBuffer.Reader, Object>> pair=tags.get(tag);
            if (null==pair) {
                tags.put(tag, Pair.of(name, reader));
            }
            else if (null==reader) {
                tags.put(tag, Pair.of(pair.first()+", "+name, pair.second()));
            }
            else if (null==pair.second()) {
                tags.put(tag, Pair.of(pair.first()+", "+name, reader));
            }
            else {
                throw new RuntimeException(
                        "more than one reader for tag 0x%02x, %s, %s".formatted(tag, name, pair));
            }
        };
        put.apply(DER.BOOLEAN).accept("BOOLEAN", DER::readBooleanNoTag);
        put.apply(DER.ENUMERATED).accept("ENUMERATED", DER::readEnumeratedNoTag);
        put.apply(DER.INTEGER).accept("INTEGER", (reader)->DER.readIntegerNoTag(true, reader));
        put.apply(DER.OCTET_STRING).accept("OCTET STRING", DER::readUtf8NoTag);
        put.apply(DER.SEQUENCE).accept("SEQUENCE", null);
        put.apply(Ldap.FILTER_AND).accept("LDAP and filter?", null);
        put.apply(Ldap.FILTER_PRESENT).accept("LDAP present filter?", null);
        put.apply(Ldap.MESSAGE_CONTROLS).accept("LDAP message controls?", null);
        put.apply(Ldap.PROTOCOL_OP_SEARCH_REQUEST).accept("LDAP search request?", null);
        TAGS=Collections.unmodifiableMap(tags);
    }

    public interface Format {
        static Format bin() {
            return (stream)->{
                ByteBuffer byteBuffer=ByteBuffer.EMPTY;
                byte[] array=new byte[4096];
                while (true) {
                    int rr=stream.read(array);
                    if (0>rr) {
                        break;
                    }
                    byteBuffer=byteBuffer.append(ByteBuffer.createCopy(array, 0, rr));
                }
                return byteBuffer;
            };
        }

        static Format hex() {
            return (stream)->{
                ByteBuffer byteBuffer=ByteBuffer.EMPTY;
                byte[] array=new byte[4096];
                int bits=0;
                int bitsSize=0;
                while (true) {
                    int rr=stream.read(array);
                    if (0>rr) {
                        break;
                    }
                    for (int ii=0; rr>ii; ++ii) {
                        int bb=array[ii]&255;
                        if (('0'<=bb) && ('9'>=bb)) {
                            bits=(bits<<4)|(bb-'0');
                            bitsSize+=4;
                        }
                        else if (('A'<=bb) && ('F'>=bb)) {
                            bits=(bits<<4)|(bb-'A'+10);
                            bitsSize+=4;
                        }
                        else if (('a'<=bb) && ('f'>=bb)) {
                            bits=(bits<<4)|(bb-'a'+10);
                            bitsSize+=4;
                        }
                        if (8==bitsSize) {
                            byteBuffer=byteBuffer.append(ByteBuffer.create((byte) (bits&255)));
                            bitsSize=0;
                        }
                    }
                }
                if (0!=bitsSize) {
                    throw new RuntimeException("bits remained, size %d".formatted(bitsSize));
                }
                return byteBuffer;
            };
        }

        ByteBuffer read(InputStream stream) throws Throwable;
    }

    private static void derDump(
            @NotNull ByteBuffer byteBuffer, @NotNull String indent, int maxBytesPerLine,
            int maxCharactersPerLine, int minBytesPerLine, int offset, String path,
            boolean printAscii, boolean printOffset, PrintWriter writer) throws Throwable {
        int offsetCharacters=offsetCharacters(byteBuffer.size());
        writer.printf("%sPath: %s%n", indent, path);
        writer.printf("%sOffset: 0x%s%n", indent, offset(offset, offsetCharacters));
        writer.printf("%sSize: %,d bytes%n", indent, byteBuffer.size());
        try {
            String indent2=indent+INDENT;
            ByteBuffer.Reader reader=byteBuffer.reader();
            for (int element=0; reader.hasRemainingBytes(); ++element) {
                writer.printf("%s%sElement: %s/%,d%n", indent, INDENT_ELEMENT, path, element);
                byte tag=DER.readTag(reader);
                Pair<String, Function<ByteBuffer.Reader, Object>> tagType=TAGS.get(tag);
                writer.printf(
                        "%sTag: 0x%02x, class: %s, %s, type: 0x%02x%s%n",
                        indent2,
                        tag,
                        switch (DER.getTagClass(tag)) {
                            case APPLICATION -> "application";
                            case CONTEXT_SPECIFIC -> "context-specific";
                            case PRIVATE -> "private";
                            case UNIVERSAL -> "universal";
                        },
                        DER.isTagConstructed(tag) ? "constructed" : "primitive",
                        DER.getTagType(tag),
                        (null==tagType) ? "" : (", %s".formatted(tagType.first())));
                int length=DER.readLength(reader);
                writer.printf("%sLength: %,d bytes%n", indent2, length);
                int offset2=offset+reader.offset();
                ByteBuffer byteBuffer2=reader.readByteBuffer(length);
                if (DER.isTagConstructed(tag)) {
                    derDump(
                            byteBuffer2, indent2, maxBytesPerLine, maxCharactersPerLine, minBytesPerLine,
                            offset2, path+"/"+element, printAscii, printOffset, writer);
                }
                else {
                    if (DER.TagClass.UNIVERSAL.equals(DER.getTagClass(tag))
                            && (null!=tagType)
                            && (null!=tagType.second())) {
                        ByteBuffer.Reader reader2=byteBuffer2.reader();
                        Object value=tagType.second().apply(reader2);
                        reader2.assertNoRemainingBytes();
                        writer.printf("%sValue: %s%n", indent2, value);
                    }
                    else {
                        hexDump(
                                byteBuffer2, indent2, maxBytesPerLine, maxCharactersPerLine,
                                minBytesPerLine, printAscii, printOffset, writer);
                    }
                }
            }
        } catch (Throwable throwable) {
            writer.printf("%sError:%n", indent);
            hexDump(
                    byteBuffer, indent, maxBytesPerLine, maxCharactersPerLine,
                    minBytesPerLine, printAscii, printOffset, writer);
            throwable.printStackTrace(writer);
        }
    }

    public static void hexDump(
            @NotNull ByteBuffer byteBuffer, @NotNull String indent, int maxBytesPerLine, int maxCharactersPerLine,
            int minBytesPerLine, boolean printAscii, boolean printOffset, PrintWriter writer) throws Throwable {
        int offsetCharacters=offsetCharacters(byteBuffer.size());
        int bytesPerLine=Math.min(1, minBytesPerLine);
        while (true) {
            int newBytesPerLine=2*bytesPerLine;
            if (newBytesPerLine>maxBytesPerLine) {
                break;
            }
            String newLine=hexDumpLine(
                    new byte[newBytesPerLine], newBytesPerLine,
                    newBytesPerLine, indent, 0, offsetCharacters, printAscii, printOffset);
            if (newLine.length()>maxCharactersPerLine) {
                break;
            }
            bytesPerLine=newBytesPerLine;
        }
        byte[] bytes=new byte[bytesPerLine];
        int bytesLength;
        int offset2=0;
        ByteBuffer.Reader reader=byteBuffer.reader();
        while (true) {
            bytesLength=0;
            while ((bytesPerLine>bytesLength) && reader.hasRemainingBytes()) {
                bytes[bytesLength]=reader.readByte();
                ++bytesLength;
            }
            if (0>=bytesLength) {
                break;
            }
            writer.println(hexDumpLine(
                    bytes, bytesLength, bytesPerLine, indent, offset2, offsetCharacters, printAscii, printOffset));
            offset2+=bytesPerLine;
        }
    }

    private static String hexDumpLine(
            byte[] bytes, int bytesLength, int bytesPerLine,
            @NotNull String indent, int offset, int offsetCharacters, boolean printAscii, boolean printOffset) {
        StringBuilder sb=new StringBuilder();
        sb.append(indent);
        if (printOffset) {
            sb.append(offset(offset, offsetCharacters));
            sb.append(":  ");
        }
        for (int ii=0; bytesPerLine>ii; ++ii) {
            if (0<ii) {
                if (0==(ii&3)) {
                    sb.append("  ");
                }
                else {
                    sb.append(' ');
                }
            }
            if (bytesLength>ii) {
                sb.append("%02x".formatted(bytes[ii]));
            }
            else {
                sb.append("  ");
            }
        }
        if (printAscii) {
            sb.append("  ");
            for (int ii=0; bytesPerLine>ii; ++ii) {
                if ((0<ii) && (0==(ii&3))) {
                    sb.append(' ');
                }
                if (bytesLength<=ii) {
                    sb.append(' ');
                }
                else {
                    int bb=bytes[ii]&255;
                    if ((32<=bb) && (127>=bb)) {
                        sb.append((char) bb);
                    }
                    else {
                        sb.append('.');
                    }
                }
            }
        }
        String result=sb.toString();
        int resultLength=result.length();
        while ((0<resultLength) && Character.isWhitespace(result.charAt(resultLength-1))) {
            --resultLength;
        }
        return result.substring(0, resultLength);
    }

    public static void main(String[] args) throws Throwable {
        if (2!=args.length) {
            printUsage();
            return;
        }
        Format format;
        switch (args[0].toLowerCase().trim()) {
            case "bin" -> format=Format.bin();
            case "hex" -> format=Format.hex();
            default -> {
                printUsage();
                return;
            }
        }
        Path path=Paths.get(args[1]).toAbsolutePath().toRealPath();
        ByteBuffer byteBuffer;
        try (InputStream fis=Files.newInputStream(path);
             InputStream bis=new BufferedInputStream(fis)) {
            byteBuffer=format.read(bis);
        }
        PrintWriter writer=new PrintWriter(System.out);
        writer.printf("Size: %,d bytes%n", byteBuffer.size());
        writer.printf("Hex dump:%n");
        hexDump(
                byteBuffer, "", MAX_BYTES_PER_LINE, MAX_CHARACTERS_PER_LINE,
                8, PRINT_ASCII, PRINT_OFFSET, writer);
        writer.printf("DER dump:%n");
        derDump(
                byteBuffer, "", MAX_BYTES_PER_LINE, MAX_CHARACTERS_PER_LINE,
                4, 0, "root", PRINT_ASCII, PRINT_OFFSET, writer);
        writer.flush();
    }

    private static String offset(int offset, int offsetCharacters) {
        return ("%0"+offsetCharacters+"x").formatted(offset);
    }

    private static int offsetCharacters(int size) {
        int offsetCharacters=2;
        int size2=256;
        while (size>size2) {
            offsetCharacters*=2;
            size2*=size2;
        }
        return offsetCharacters;
    }

    private static void printUsage() {
        System.out.printf("usage: DERDump bin|hex filename%n");
        System.exit(1);
    }
}
