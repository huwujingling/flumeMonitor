package com.csot.flume.domain;

public class flumeSource {
    private String EventReceivedCount;
    private String AppendBatchAcceptedCount;
    private String Type;
    private String EventAcceptedCount;
    private String AppendReceivedCount;
    private String StartTime;
    private String AppendAcceptedCount;
    private String OpenConnectionCount;
    private String AppendBatchReceivedCount;
    private String StopTime;

    public flumeSource(String eventReceivedCount, String appendBatchAcceptedCount, String type, String eventAcceptedCount, String appendReceivedCount, String startTime, String appendAcceptedCount, String openConnectionCount, String appendBatchReceivedCount, String stopTime) {
        EventReceivedCount = eventReceivedCount;
        AppendBatchAcceptedCount = appendBatchAcceptedCount;
        Type = type;
        EventAcceptedCount = eventAcceptedCount;
        AppendReceivedCount = appendReceivedCount;
        StartTime = startTime;
        AppendAcceptedCount = appendAcceptedCount;
        OpenConnectionCount = openConnectionCount;
        AppendBatchReceivedCount = appendBatchReceivedCount;
        StopTime = stopTime;
    }

    public String getEventReceivedCount() {
        return EventReceivedCount;
    }

    public void setEventReceivedCount(String eventReceivedCount) {
        EventReceivedCount = eventReceivedCount;
    }

    public String getAppendBatchAcceptedCount() {
        return AppendBatchAcceptedCount;
    }

    public void setAppendBatchAcceptedCount(String appendBatchAcceptedCount) {
        AppendBatchAcceptedCount = appendBatchAcceptedCount;
    }

    public String getType() {
        return Type;
    }

    public void setType(String type) {
        Type = type;
    }

    public String getEventAcceptedCount() {
        return EventAcceptedCount;
    }

    public void setEventAcceptedCount(String eventAcceptedCount) {
        EventAcceptedCount = eventAcceptedCount;
    }

    public String getAppendReceivedCount() {
        return AppendReceivedCount;
    }

    public void setAppendReceivedCount(String appendReceivedCount) {
        AppendReceivedCount = appendReceivedCount;
    }

    public String getStartTime() {
        return StartTime;
    }

    public void setStartTime(String startTime) {
        StartTime = startTime;
    }

    public String getAppendAcceptedCount() {
        return AppendAcceptedCount;
    }

    public void setAppendAcceptedCount(String appendAcceptedCount) {
        AppendAcceptedCount = appendAcceptedCount;
    }

    public String getOpenConnectionCount() {
        return OpenConnectionCount;
    }

    public void setOpenConnectionCount(String openConnectionCount) {
        OpenConnectionCount = openConnectionCount;
    }

    public String getAppendBatchReceivedCount() {
        return AppendBatchReceivedCount;
    }

    public void setAppendBatchReceivedCount(String appendBatchReceivedCount) {
        AppendBatchReceivedCount = appendBatchReceivedCount;
    }

    public String getStopTime() {
        return StopTime;
    }

    public void setStopTime(String stopTime) {
        StopTime = stopTime;
    }

    public flumeSource() {
    }

    @Override
    public String toString() {
        return "flumeSource{" +
                "EventReceivedCount='" + EventReceivedCount + '\'' +
                ", AppendBatchAcceptedCount='" + AppendBatchAcceptedCount + '\'' +
                ", Type='" + Type + '\'' +
                ", EventAcceptedCount='" + EventAcceptedCount + '\'' +
                ", AppendReceivedCount='" + AppendReceivedCount + '\'' +
                ", StartTime='" + StartTime + '\'' +
                ", AppendAcceptedCount='" + AppendAcceptedCount + '\'' +
                ", OpenConnectionCount='" + OpenConnectionCount + '\'' +
                ", AppendBatchReceivedCount='" + AppendBatchReceivedCount + '\'' +
                ", StopTime='" + StopTime + '\'' +
                '}';
    }
}
