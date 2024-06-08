package hu.gds.ldap4j.reactor;

import hu.gds.ldap4j.Log;
import org.jetbrains.annotations.NotNull;
import reactor.util.Loggers;

public class ReactorLog implements Log {
    public static @NotNull ReactorLog create() {
        return new ReactorLog();
    }

    @Override
    public void error(@NotNull Class<?> component, @NotNull Throwable throwable) {
        Loggers.getLogger(component).error(throwable.toString(), throwable);
    }
}
