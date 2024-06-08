package org.slf4j.spi;

import java.util.Map;

public interface MDCAdapter {
    void clear();

    Map<String, String> getCopyOfContextMap();

    void setContextMap(Map<String, String> contextMap);

}
