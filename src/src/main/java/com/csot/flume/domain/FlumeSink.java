package com.csot.flume.domain;

public class FlumeSink {
    private String ConnectionCreatedCount;
    private String ConnectionClosedCount;
    private String Type;
    private String BatchCompleteCount;
    private String BatchEmptyCount;
    private String EventDrainAttemptCount;
    private String StartTime;
    private String EventDrainSuccessCount;
    private String BatchUnderflowCount;
    private String StopTime;
    private String ConnectionFailedCount;

    public FlumeSink(){}

    public FlumeSink(String connectionCreatedCount, String connectionClosedCount, String type, String batchCompleteCount, String batchEmptyCount, String eventDrainAttemptCount, String startTime, String eventDrainSuccessCount, String batchUnderflowCount, String stopTime, String connectionFailedCount) {
        ConnectionCreatedCount = connectionCreatedCount;
        ConnectionClosedCount = connectionClosedCount;
        Type = type;
        BatchCompleteCount = batchCompleteCount;
        BatchEmptyCount = batchEmptyCount;
        EventDrainAttemptCount = eventDrainAttemptCount;
        StartTime = startTime;
        EventDrainSuccessCount = eventDrainSuccessCount;
        BatchUnderflowCount = batchUnderflowCount;
        StopTime = stopTime;
        ConnectionFailedCount = connectionFailedCount;
    }

    public String getConnectionCreatedCount() {
        return ConnectionCreatedCount;
    }

    public void setConnectionCreatedCount(String connectionCreatedCount) {
        ConnectionCreatedCount = connectionCreatedCount;
    }

    public String getConnectionClosedCount() {
        return ConnectionClosedCount;
    }

    public void setConnectionClosedCount(String connectionClosedCount) {
        ConnectionClosedCount = connectionClosedCount;
    }

    public String getType() {
        return Type;
    }

    public void setType(String type) {
        Type = type;
    }

    public String getBatchCompleteCount() {
        return BatchCompleteCount;
    }

    public void setBatchCompleteCount(String batchCompleteCount) {
        BatchCompleteCount = batchCompleteCount;
    }

    public String getBatchEmptyCount() {
        return BatchEmptyCount;
    }

    public void setBatchEmptyCount(String batchEmptyCount) {
        BatchEmptyCount = batchEmptyCount;
    }

    public String getEventDrainAttemptCount() {
        return EventDrainAttemptCount;
    }

    public void setEventDrainAttemptCount(String eventDrainAttemptCount) {
        EventDrainAttemptCount = eventDrainAttemptCount;
    }

    public String getStartTime() {
        return StartTime;
    }

    public void setStartTime(String startTime) {
        StartTime = startTime;
    }

    public String getEventDrainSuccessCount() {
        return EventDrainSuccessCount;
    }

    public void setEventDrainSuccessCount(String eventDrainSuccessCount) {
        EventDrainSuccessCount = eventDrainSuccessCount;
    }

    public String getBatchUnderflowCount() {
        return BatchUnderflowCount;
    }

    public void setBatchUnderflowCount(String batchUnderflowCount) {
        BatchUnderflowCount = batchUnderflowCount;
    }

    public String getStopTime() {
        return StopTime;
    }

    public void setStopTime(String stopTime) {
        StopTime = stopTime;
    }

    public String getConnectionFailedCount() {
        return ConnectionFailedCount;
    }

    public void setConnectionFailedCount(String connectionFailedCount) {
        ConnectionFailedCount = connectionFailedCount;
    }

    @Override
    public String toString() {
        return "FlumeSink{" +
                "ConnectionCreatedCount='" + ConnectionCreatedCount + '\'' +
                ", ConnectionClosedCount='" + ConnectionClosedCount + '\'' +
                ", Type='" + Type + '\'' +
                ", BatchCompleteCount='" + BatchCompleteCount + '\'' +
                ", BatchEmptyCount='" + BatchEmptyCount + '\'' +
                ", EventDrainAttemptCount='" + EventDrainAttemptCount + '\'' +
                ", StartTime='" + StartTime + '\'' +
                ", EventDrainSuccessCount='" + EventDrainSuccessCount + '\'' +
                ", BatchUnderflowCount='" + BatchUnderflowCount + '\'' +
                ", StopTime='" + StopTime + '\'' +
                ", ConnectionFailedCount='" + ConnectionFailedCount + '\'' +
                '}';
    }
}
