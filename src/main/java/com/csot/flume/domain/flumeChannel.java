package com.csot.flume.domain;

public class flumeChannel {
    private String ChannelCapacity;
    private String ChannelFillPercentage;
    private String Type;
    private String EventTakeSuccessCount;
    private String ChannelSize;
    private String EventTakeAttemptCount;
    private String StartTime;
    private String EventPutAttemptCount;
    private String EventPutSuccessCount;
    private String Open;
    private String StopTime;

    public flumeChannel() {
    }

    public flumeChannel(String channelCapacity, String channelFillPercentage, String type, String eventTakeSuccessCount, String channelSize, String eventTakeAttemptCount, String startTime, String eventPutAttemptCount, String eventPutSuccessCount, String open, String stopTime) {
        ChannelCapacity = channelCapacity;
        ChannelFillPercentage = channelFillPercentage;
        Type = type;
        EventTakeSuccessCount = eventTakeSuccessCount;
        ChannelSize = channelSize;
        EventTakeAttemptCount = eventTakeAttemptCount;
        StartTime = startTime;
        EventPutAttemptCount = eventPutAttemptCount;
        EventPutSuccessCount = eventPutSuccessCount;
        Open = open;
        StopTime = stopTime;
    }

    public String getChannelCapacity() {
        return ChannelCapacity;
    }

    public void setChannelCapacity(String channelCapacity) {
        ChannelCapacity = channelCapacity;
    }

    public String getChannelFillPercentage() {
        return ChannelFillPercentage;
    }

    public void setChannelFillPercentage(String channelFillPercentage) {
        ChannelFillPercentage = channelFillPercentage;
    }

    public String getType() {
        return Type;
    }

    public void setType(String type) {
        Type = type;
    }

    public String getEventTakeSuccessCount() {
        return EventTakeSuccessCount;
    }

    public void setEventTakeSuccessCount(String eventTakeSuccessCount) {
        EventTakeSuccessCount = eventTakeSuccessCount;
    }

    public String getChannelSize() {
        return ChannelSize;
    }

    public void setChannelSize(String channelSize) {
        ChannelSize = channelSize;
    }

    public String getEventTakeAttemptCount() {
        return EventTakeAttemptCount;
    }

    public void setEventTakeAttemptCount(String eventTakeAttemptCount) {
        EventTakeAttemptCount = eventTakeAttemptCount;
    }

    public String getStartTime() {
        return StartTime;
    }

    public void setStartTime(String startTime) {
        StartTime = startTime;
    }

    public String getEventPutAttemptCount() {
        return EventPutAttemptCount;
    }

    public void setEventPutAttemptCount(String eventPutAttemptCount) {
        EventPutAttemptCount = eventPutAttemptCount;
    }

    public String getEventPutSuccessCount() {
        return EventPutSuccessCount;
    }

    public void setEventPutSuccessCount(String eventPutSuccessCount) {
        EventPutSuccessCount = eventPutSuccessCount;
    }

    public String getOpen() {
        return Open;
    }

    public void setOpen(String open) {
        Open = open;
    }

    public String getStopTime() {
        return StopTime;
    }

    public void setStopTime(String stopTime) {
        StopTime = stopTime;
    }

    @Override
    public String toString() {
        return "flumeChannel{" +
                "ChannelCapacity='" + ChannelCapacity + '\'' +
                ", ChannelFillPercentage='" + ChannelFillPercentage + '\'' +
                ", Type='" + Type + '\'' +
                ", EventTakeSuccessCount='" + EventTakeSuccessCount + '\'' +
                ", ChannelSize='" + ChannelSize + '\'' +
                ", EventTakeAttemptCount='" + EventTakeAttemptCount + '\'' +
                ", StartTime='" + StartTime + '\'' +
                ", EventPutAttemptCount='" + EventPutAttemptCount + '\'' +
                ", EventPutSuccessCount='" + EventPutSuccessCount + '\'' +
                ", Open='" + Open + '\'' +
                ", StopTime='" + StopTime + '\'' +
                '}';
    }
}
