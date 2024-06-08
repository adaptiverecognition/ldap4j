package hu.gds.ldap4j;

import java.io.PrintStream;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrettyPrinter {
    public interface PrettyPrintable {
        void prettyPrint(PrettyPrinter printer);
    }

    private static final String INDENT=".   ";

    private final @NotNull String indent;
    private final @NotNull IdentityHashMap<@NotNull Object, Void> parents;
    private final @NotNull PrintStream stream;

    public PrettyPrinter(
            @NotNull String indent, IdentityHashMap<@NotNull Object, Void> parents, @NotNull PrintStream stream) {
        this.indent=Objects.requireNonNull(indent, "indent");
        this.parents=Objects.requireNonNull(parents, "parents");
        this.stream=Objects.requireNonNull(stream, "stream");
    }

    public static @NotNull PrettyPrinter create(@NotNull PrintStream stream) {
        return new PrettyPrinter("", new IdentityHashMap<>(), stream);
    }

    public void printInstance(@NotNull Object object) {
        Objects.requireNonNull(object, "object");
        printObject("type", object.getClass());
        printObject("%x", "instance", System.identityHashCode(object));
    }
    
    public void printObject(@NotNull String key, @Nullable Object object) {
        printObject("%s", key, object);
    }

    public void printObject(@NotNull String format, @NotNull String key, @Nullable Object object) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(key, "key");
        if (object instanceof PrettyPrintable printable) {
            stream.printf("%s%s:%n", indent, key);
            if (parents.containsKey(printable)) {
                printInstance(printable);
                printObject("circular", true);
            }
            else {
                parents.put(printable, null);
                try {
                    printable.prettyPrint(new PrettyPrinter(indent+INDENT, parents, stream));
                }
                finally {
                    parents.remove(printable);
                }
            }
        }
        else {
            stream.printf("%s%s: "+format+"%n", indent, key, object);
        }
    }
}
