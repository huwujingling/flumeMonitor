package com.csot.flume.domain;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/*
* 此类用于构建flume上下文对象
* */
public class FlumeContext {
    private String confName;
    private ConcurrentHashMap monitorMap;

    public FlumeContext(String confName, ConcurrentHashMap monitorMap) {
        this.confName = confName;
        this.monitorMap = monitorMap;
    }

    public String getConfName() {
        return confName;
    }

    public void setConfName(String confName) {
        this.confName = confName;
    }

    public ConcurrentHashMap getMonitorMap() {
        return monitorMap;
    }

    public void setMonitorMap(ConcurrentHashMap monitorMap) {
        this.monitorMap = monitorMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlumeContext that = (FlumeContext) o;
        return Objects.equals(confName, that.confName) &&
                Objects.equals(monitorMap, that.monitorMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(confName, monitorMap);
    }

    @Override
    public String toString() {
        return "FlumeContext{" +
                "confName='" + confName + '\'' +
                ", monitorMap=" + monitorMap.toString() +
                '}';
    }
}
