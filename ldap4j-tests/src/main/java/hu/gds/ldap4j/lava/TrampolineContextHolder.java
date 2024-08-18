package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.trampoline.Trampoline;
import org.jetbrains.annotations.NotNull;

public class TrampolineContextHolder extends ContextHolder {
    private final Trampoline trampoline;

    public TrampolineContextHolder(@NotNull Log log) {
        super(log);
        trampoline=new Trampoline(log);
    }

    @Override
    public @NotNull Clock clock() {
        return Clock.SYSTEM_NANO_TIME;
    }

    @Override
    public void close() {
        trampoline.close();
    }

    @Override
    public @NotNull Context context() {
        throw new UnsupportedOperationException();
    }

    public static @NotNull Function<@NotNull Log, @NotNull ContextHolder> factory() {
        return new Function<>() {
            @Override
            public ContextHolder apply(@NotNull Log log) {
                return new TrampolineContextHolder(log);
            }

            @Override
            public String toString() {
                return "TrampolineContextHolder.factory()";
            }
        };
    }

    @Override
    public <T> T getOrTimeoutEndNanos(long endNanos, Lava<T> lava) throws Throwable {
        return trampoline.contextEndNanos(endNanos).get(true, true, lava);
    }

    @Override
    public void start() {
    }

    @Override
    public String toString() {
        return "TrampolineContextHolder()";
    }
}
