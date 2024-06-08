package org.slf4j;

public interface Logger {
    default void debug(String ignore) {
    }

    default void debug(String ignore0, Object ignore1) {
    }

    default void debug(String ignore0, Object ignore1, Object ignore2) {
    }

    default void debug(String ignore0, Object... ignore1) {
    }

    default void debug(String ignore0, Throwable ignore1) {
    }

    default void error(String ignore) {
    }

    default void error(String ignore0, Object ignore1) {
    }

    default void error(String ignore0, Object ignore1, Object ignore2) {
    }

    default void error(String ignore0, Object... ignore1) {
    }

    default void error(String ignore0, Throwable ignore1) {
    }

    default String getName() {
        return "";
    }

    default void info(String ignore) {
    }

    default void info(String ignore0, Object ignore1) {
    }

    default void info(String ignore0, Object ignore1, Object ignore2) {
    }

    default void info(String ignore0, Object... ignore1) {
    }

    default void info(String ignore0, Throwable ignore1) {
    }

    default boolean isDebugEnabled() {
        return false;
    }

    default boolean isErrorEnabled() {
        return false;
    }

    default boolean isInfoEnabled() {
        return false;
    }

    default boolean isTraceEnabled() {
        return false;
    }

    default boolean isWarnEnabled() {
        return false;
    }

    default void trace(String ignore) {
    }

    default void trace(String ignore0, Object ignore1) {
    }

    default void trace(String ignore0, Object ignore1, Object ignore2) {
    }

    default void trace(String ignore0, Object... ignore1) {
    }

    default void trace(String ignore0, Throwable ignore1) {
    }

    default void warn(String ignore) {
    }

    default void warn(String ignore0, Object ignore1) {
    }

    default void warn(String ignore0, Object ignore1, Object ignore2) {
    }

    default void warn(String ignore0, Object... ignore1) {
    }

    default void warn(String ignore0, Throwable ignore1) {
    }
}
