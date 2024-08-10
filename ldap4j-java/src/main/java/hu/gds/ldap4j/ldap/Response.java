package hu.gds.ldap4j.ldap;

import org.jetbrains.annotations.NotNull;

public interface Response {
    interface Visitor<T> {
        T addResponse(@NotNull AddResponse addResponse) throws Throwable;
        T bindResponse(@NotNull BindResponse bindResponse) throws Throwable;
        T compareResponse(@NotNull CompareResponse compareResponse) throws Throwable;
        T deleteResponse(@NotNull DeleteResponse deleteResponse) throws Throwable;
        T extendedResponse(@NotNull ExtendedResponse extendedResponse) throws Throwable;
        T modifyDNResponse(@NotNull ModifyDNResponse modifyDNResponse) throws Throwable;
        T modifyResponse(@NotNull ModifyResponse modifyResponse) throws Throwable;
    }

    <T> T visit(@NotNull Visitor<T> visitor) throws Throwable;
}
