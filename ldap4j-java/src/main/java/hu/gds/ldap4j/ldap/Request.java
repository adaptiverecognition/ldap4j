package hu.gds.ldap4j.ldap;

import org.jetbrains.annotations.NotNull;

public interface Request<M, R extends Response> extends Message<M> {
    interface Visitor<T> {
        T addRequest(@NotNull AddRequest addRequest) throws Throwable;
        T bindRequest(@NotNull BindRequest bindRequest) throws Throwable;
        T compareRequest(@NotNull CompareRequest compareRequest) throws Throwable;
        T deleteRequest(@NotNull DeleteRequest deleteRequest) throws Throwable;
        T extendedRequest(@NotNull ExtendedRequest extendedRequest) throws Throwable;
        T modifyDNRequest(@NotNull ModifyDNRequest modifyDNRequest) throws Throwable;
        T modifyRequest(@NotNull ModifyRequest modifyRequest) throws Throwable;
    }
    
    @NotNull MessageReader<R> responseReader();

    <T> T visit(@NotNull Visitor<T> visitor) throws Throwable;
}
