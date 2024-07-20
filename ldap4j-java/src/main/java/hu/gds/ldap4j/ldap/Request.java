package hu.gds.ldap4j.ldap;

import org.jetbrains.annotations.NotNull;

public interface Request<M, R> extends Message<M> {
    @NotNull MessageReader<R> responseReader();
}
