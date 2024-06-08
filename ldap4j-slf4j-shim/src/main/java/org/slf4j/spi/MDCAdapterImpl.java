package org.slf4j.spi;

import java.util.HashMap;
import java.util.Map;

public class MDCAdapterImpl implements MDCAdapter {
    private Map<String, String> contextMap = new HashMap<>();

    @Override
    public void clear() {
        contextMap.clear();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        return new HashMap<>(contextMap);
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        this.contextMap = new HashMap<>(contextMap);
    }
}
