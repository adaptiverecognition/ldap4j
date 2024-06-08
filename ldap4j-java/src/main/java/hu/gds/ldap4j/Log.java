package hu.gds.ldap4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public interface Log {
    class AppendableLog implements Log {
        private final @NotNull Appendable appendable;
        private final @NotNull Object lock;

        public AppendableLog(@NotNull Appendable appendable, @NotNull Object lock) {
            this.appendable=Objects.requireNonNull(appendable, "appendable");
            this.lock=Objects.requireNonNull(lock, "lock");
        }

        @Override
        public void error(@NotNull Class<?> component, @NotNull Throwable throwable) {
            StringWriter writer=new StringWriter();
            PrintWriter printWriter=new PrintWriter(writer);
            printWriter.append("%s - %s%n".formatted(Instant.now(), component));
            throwable.printStackTrace(printWriter);
            printWriter.flush();
            String output=writer.toString();
            try {
                synchronized (lock) {
                    appendable.append(output);
                }
            }
            catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    class Slf4jLog implements Log {
        @Override
        public void error(@NotNull Class<?> component, @NotNull Throwable throwable) {
            LoggerFactory.getLogger(component).error("");
        }
    }

    void error(@NotNull Class<?> component, @NotNull Throwable throwable);

    static Log slf4j() {
        return new Slf4jLog();
    }

    static Log systemErr() {
        return new AppendableLog(System.err, System.err);
    }
}
