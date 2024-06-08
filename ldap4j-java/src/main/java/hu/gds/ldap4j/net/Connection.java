package hu.gds.ldap4j.net;

import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import org.jetbrains.annotations.NotNull;

public interface Connection extends Closeable {
    default @NotNull Lava<Void> checkOpenAndNotFailed() {
        return isOpenAndNotFailed()
                .compose((result)->{
                    if (result) {
                        return Lava.VOID;
                    }
                    throw new RuntimeException("not open or failed");
                });
    }

    @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed();
}
