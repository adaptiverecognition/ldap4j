package hu.gds.ldap4j;

import hu.gds.ldap4j.net.CryptoUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Exceptions {
    private static final String MESSAGE="message.";
    private static final String TYPE="type.";

    private record ExceptionPredicate(
            @Nullable Pattern message,
            @NotNull List<@NotNull Class<?>> types)
            implements Predicate<@NotNull Throwable> {
        private ExceptionPredicate(@Nullable Pattern message, @NotNull List<@NotNull Class<?>> types) {
            this.message=message;
            this.types=Objects.requireNonNull(types, "types");
        }

        public static ExceptionPredicate createResource(String file) throws IOException {
            Properties properties=new Properties();
            try (InputStream stream=CryptoUtil.resource(file, Exceptions.class)) {
                properties.load(stream);
            }
            Set<@NotNull String> messages=new HashSet<>();
            List<@NotNull Class<?>> types=new ArrayList<>();
            for (String propertyName: properties.stringPropertyNames()) {
                String value=properties.getProperty(propertyName);
                if (null==value) {
                    throw new RuntimeException(
                            "property name %s in file %s doesn't have a value".formatted(propertyName, file));
                }
                if (propertyName.startsWith(MESSAGE)) {
                    messages.add(value);
                }
                else if (propertyName.startsWith(TYPE)) {
                    try {
                        types.add(Thread.currentThread().getContextClassLoader().loadClass(value));
                    }
                    catch (ClassNotFoundException ignore) {
                    }
                }
                else {
                    throw new RuntimeException(
                            "property name %s in file %s doesn't starts with neither %s nor %s".formatted(
                                    propertyName,
                                    file,
                                    MESSAGE,
                                    TYPE));
                }
            }
            return new ExceptionPredicate(pattern(messages), types);
        }

        private static @Nullable Pattern pattern(@NotNull Set<@NotNull String> patterns) {
            if (patterns.isEmpty()) {
                return null;
            }
            StringBuilder sb=new StringBuilder();
            boolean first=true;
            for (String pattern: patterns) {
                if (first) {
                    first=false;
                }
                else {
                    sb.append('|');
                }
                sb.append('(');
                sb.append(pattern);
                sb.append(')');
            }
            return Pattern.compile(sb.toString());
        }

        @Override
        public boolean test(@NotNull Throwable throwable) {
            Objects.requireNonNull(throwable, "throwable");
            for (Class<?> type: types) {
                if (type.isAssignableFrom(throwable.getClass())) {
                    return true;
                }
            }
            if (null==message) {
                return false;
            }
            String message=throwable.getMessage();
            if (null==message) {
                return false;
            }
            message=message.toLowerCase();
            return this.message.matcher(message).matches();
        }
    }

    private static final @NotNull ExceptionPredicate CONNECTION_CLOSE_PREDICATE;
    private static final @NotNull ExceptionPredicate TIMEOUT_PREDICATE;
    private static final @NotNull ExceptionPredicate UNKNOWN_HOST_PREDICATE;

    static {
        try {
            CONNECTION_CLOSE_PREDICATE=ExceptionPredicate.createResource(
                    "Exceptions.connection.closed.properties");
            TIMEOUT_PREDICATE=ExceptionPredicate.createResource("Exceptions.timeout.properties");
            UNKNOWN_HOST_PREDICATE=ExceptionPredicate.createResource("Exceptions.unknown.host.properties");
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Exceptions() {
    }

    public static void addSuppressed(@NotNull Throwable suppressed, @NotNull Throwable throwable) {
        Objects.requireNonNull(suppressed, "suppressed");
        Objects.requireNonNull(throwable, "throwable");
        if (!contains(suppressed, throwable)) {
            throwable.addSuppressed(suppressed);
        }
    }

    public static boolean contains(@NotNull Throwable suppressed, @Nullable Throwable throwable) {
        Objects.requireNonNull(suppressed, "suppressed");
        if (null==throwable) {
            return false;
        }
        if (suppressed.equals(throwable)) {
            return true;
        }
        if (contains(suppressed, throwable.getCause())) {
            return true;
        }
        @NotNull Throwable[] throwable2=throwable.getSuppressed();
        for (Throwable throwable3: throwable2) {
            if (contains(suppressed, throwable3)) {
                return true;
            }
        }
        return false;
    }

    public static @NotNull TimeoutException asTimeoutException(@NotNull Throwable throwable) {
        if (throwable instanceof TimeoutException ex) {
            return ex;
        }
        return new TimeoutException(throwable.toString());
    }

    public static @NotNull UnknownHostException asUnknownHostException(@NotNull Throwable throwable) {
        if (throwable instanceof UnknownHostException ex) {
            return ex;
        }
        return new UnknownHostException(throwable.toString());
    }

    public static <T> @Nullable T findCause(@NotNull Class<T> causeType, @NotNull Throwable throwable) {
        for (Throwable throwable2=throwable; null!=throwable2; throwable2=throwable2.getCause()) {
            if (causeType.isAssignableFrom(throwable2.getClass())) {
                return causeType.cast(throwable2);
            }
        }
        return null;
    }

    public static <T> @NotNull T findCauseOrThrow(
            @NotNull Class<T> causeType, @NotNull Throwable throwable) throws Throwable {
        T cause=findCause(causeType, throwable);
        if (null==cause) {
            throw throwable;
        }
        return cause;
    }

    public static boolean isConnectionClosedException(@NotNull Throwable throwable) {
        return CONNECTION_CLOSE_PREDICATE.test(throwable);
    }

    public static boolean isTimeoutException(@NotNull Throwable throwable) {
        return TIMEOUT_PREDICATE.test(throwable);
    }

    public static boolean isUnknownHostException(@NotNull Throwable throwable) {
        return UNKNOWN_HOST_PREDICATE.test(throwable);
    }

    public static @Nullable Throwable join(@Nullable Throwable throwable0, @Nullable Throwable throwable1) {
        if (null==throwable0) {
            return throwable1;
        }
        if (null==throwable1) {
            return throwable0;
        }
        addSuppressed(throwable1, throwable0);
        return throwable0;
    }

    public static @NotNull Throwable joinNotNull(@Nullable Throwable throwable0, @NotNull Throwable throwable1) {
        if (null==throwable0) {
            return throwable1;
        }
        addSuppressed(throwable1, throwable0);
        return throwable0;
    }
}
