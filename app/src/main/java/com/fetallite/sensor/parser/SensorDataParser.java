package com.fetallite.sensor.parser;

import com.fetallite.sensor.model.SensorSample;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for sensor data samples.
 * Sample format: !<SampleNumberMSB><SampleNumberLSB><Ch1Data><Ch2Data><Ch3Data><Ch4Data>
 * Example: !""01487A0245F9022AC6E004EB
 * 
 * - Sample Number: 2 bytes (4 hex chars)
 * - Each Channel: 3 bytes (6 hex chars)
 * - Total sample length: 1 (!) + 4 (sample#) + 24 (4 channels * 6) = 29 chars
 */
public class SensorDataParser {
    
    private static final char SAMPLE_START_MARKER = '!';
    private static final int SAMPLE_NUMBER_LENGTH = 4; // 2 bytes = 4 hex chars
    private static final int CHANNEL_DATA_LENGTH = 6;  // 3 bytes = 6 hex chars
    private static final int NUM_CHANNELS = 4;
    private static final int TOTAL_SAMPLE_LENGTH = 1 + SAMPLE_NUMBER_LENGTH + (CHANNEL_DATA_LENGTH * NUM_CHANNELS); // 29
    
    // Reference voltage for ADC conversion (assuming 3.3V reference, 24-bit ADC)
    private static final double VREF = 3.3;
    private static final double MAX_ADC_VALUE = Math.pow(2, 24) - 1; // 24-bit max value

    /**
     * Parse a single line containing timestamp and multiple samples
     * @param line The input line (format: <timestamp><samples>)
     * @param lineTimestamp The timestamp for this line
     * @return List of parsed sensor samples
     */
    public List<SensorSample> parseLine(String line, long lineTimestamp) {
        List<SensorSample> samples = new ArrayList<>();
        
        if (line == null || line.isEmpty()) {
            return samples;
        }
        
        // Find all sample markers and parse
        int index = 0;
        while (index < line.length()) {
            int markerPos = line.indexOf(SAMPLE_START_MARKER, index);
            if (markerPos == -1) {
                break;
            }
            
            // Check if we have enough data for a complete sample
            if (markerPos + TOTAL_SAMPLE_LENGTH <= line.length()) {
                try {
                    SensorSample sample = parseSample(line.substring(markerPos, markerPos + TOTAL_SAMPLE_LENGTH), lineTimestamp);
                    if (sample != null) {
                        samples.add(sample);
                    }
                } catch (Exception e) {
                    // Skip malformed samples
                }
            }
            
            index = markerPos + 1;
        }
        
        return samples;
    }

    /**
     * Parse a single sample string
     * @param sampleStr The sample string starting with '!'
     * @param timestamp The timestamp for this sample
     * @return Parsed SensorSample or null if invalid
     */
    public SensorSample parseSample(String sampleStr, long timestamp) {
        if (sampleStr == null || sampleStr.length() < TOTAL_SAMPLE_LENGTH) {
            return null;
        }
        
        if (sampleStr.charAt(0) != SAMPLE_START_MARKER) {
            return null;
        }
        
        try {
            int pos = 1; // Skip the '!' marker
            
            // Parse sample number (2 bytes)
            String sampleNumHex = sampleStr.substring(pos, pos + SAMPLE_NUMBER_LENGTH);
            int sampleNumber = parseHexToInt(sampleNumHex);
            pos += SAMPLE_NUMBER_LENGTH;
            
            // Parse 4 channel values
            double[] channelValues = new double[NUM_CHANNELS];
            for (int i = 0; i < NUM_CHANNELS; i++) {
                String channelHex = sampleStr.substring(pos, pos + CHANNEL_DATA_LENGTH);
                channelValues[i] = hexToVoltage(channelHex);
                pos += CHANNEL_DATA_LENGTH;
            }
            
            return new SensorSample(sampleNumber, channelValues, timestamp);
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert hex string to integer
     */
    private int parseHexToInt(String hex) {
        return Integer.parseInt(hex, 16);
    }

    /**
     * Convert 6-character hex string to voltage value
     * Assumes 24-bit ADC with configurable reference voltage
     */
    public double hexToVoltage(String hex) {
        if (hex == null || hex.length() != CHANNEL_DATA_LENGTH) {
            return 0.0;
        }
        
        // Parse hex to long (3 bytes = 24 bits)
        long rawValue = Long.parseLong(hex, 16);
        
        // Convert to voltage (assuming linear ADC conversion)
        return (rawValue / MAX_ADC_VALUE) * VREF;
    }

    /**
     * Extract timestamp from the beginning of a line
     * Assumes timestamp is numeric and ends at first non-numeric character
     */
    public long extractTimestamp(String line) {
        if (line == null || line.isEmpty()) {
            return System.currentTimeMillis();
        }
        
        StringBuilder timestampStr = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (Character.isDigit(c)) {
                timestampStr.append(c);
            } else {
                break;
            }
        }
        
        if (timestampStr.length() > 0) {
            try {
                return Long.parseLong(timestampStr.toString());
            } catch (NumberFormatException e) {
                return System.currentTimeMillis();
            }
        }
        
        return System.currentTimeMillis();
    }

    /**
     * Get the data portion of a line (after timestamp)
     */
    public String getDataPortion(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        
        int dataStart = 0;
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isDigit(line.charAt(i))) {
                dataStart = i;
                break;
            }
        }
        
        return line.substring(dataStart);
    }
}
