package org.slf4j.spi;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public class SLF4JServiceProviderImpl implements SLF4JServiceProvider {
    @Override
    public ILoggerFactory getLoggerFactory() {
        return LoggerFactory.getILoggerFactory();
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return new MDCAdapterImpl();
    }
}
