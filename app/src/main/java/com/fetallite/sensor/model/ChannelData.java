package com.fetallite.sensor.model;

/**
 * Represents decoded channel data to be displayed on UI
 */
public class ChannelData {
    private final int channelNumber;
    private final double value;
    private final int sampleNumber;

    public ChannelData(int channelNumber, double value, int sampleNumber) {
        this.channelNumber = channelNumber;
        this.value = value;
        this.sampleNumber = sampleNumber;
    }

    public int getChannelNumber() {
        return channelNumber;
    }

    public double getValue() {
        return value;
    }

    public int getSampleNumber() {
        return sampleNumber;
    }

    public String getFormattedValue() {
        return String.format("Ch%d: %.4f V (Sample #%d)", channelNumber, value, sampleNumber);
    }
}
