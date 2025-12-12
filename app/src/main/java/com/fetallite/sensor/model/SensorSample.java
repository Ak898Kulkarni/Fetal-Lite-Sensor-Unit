package com.fetallite.sensor.model;

/**
 * Represents a single sensor sample with 4 channel values.
 * Sample format: !<SampleNumberMSB><SampleNumberLSB><Ch1Data><Ch2Data><Ch3Data><Ch4Data>
 * Each channel data is 6-byte hex-encoded voltage value.
 */
public class SensorSample {
    private final int sampleNumber;
    private final double[] channelValues;
    private final long timestamp;

    public SensorSample(int sampleNumber, double[] channelValues, long timestamp) {
        this.sampleNumber = sampleNumber;
        this.channelValues = channelValues;
        this.timestamp = timestamp;
    }

    public int getSampleNumber() {
        return sampleNumber;
    }

    public double getChannel1() {
        return channelValues[0];
    }

    public double getChannel2() {
        return channelValues[1];
    }

    public double getChannel3() {
        return channelValues[2];
    }

    public double getChannel4() {
        return channelValues[3];
    }

    public double[] getChannelValues() {
        return channelValues;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SensorSample{" +
                "sampleNumber=" + sampleNumber +
                ", ch1=" + channelValues[0] +
                ", ch2=" + channelValues[1] +
                ", ch3=" + channelValues[2] +
                ", ch4=" + channelValues[3] +
                ", timestamp=" + timestamp +
                '}';
    }
}
