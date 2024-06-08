package org.slf4j;

import org.slf4j.spi.SLF4JServiceProvider;
import org.slf4j.spi.SLF4JServiceProviderImpl;

public class LoggerFactory {
    public static ILoggerFactory getILoggerFactory() {
        return new ILoggerFactoryImpl();
    }

    public static Logger getLogger(Class<?> ignore) {
        return new LoggerImpl();
    }

    public static Logger getLogger(String ignore) {
        return new LoggerImpl();
    }

    public static SLF4JServiceProvider getProvider() {
        return new SLF4JServiceProviderImpl();
    }
}
