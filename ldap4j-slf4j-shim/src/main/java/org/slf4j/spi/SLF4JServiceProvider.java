package org.slf4j.spi;

import org.slf4j.ILoggerFactory;

public interface SLF4JServiceProvider {
    ILoggerFactory getLoggerFactory();

    MDCAdapter getMDCAdapter();

}
