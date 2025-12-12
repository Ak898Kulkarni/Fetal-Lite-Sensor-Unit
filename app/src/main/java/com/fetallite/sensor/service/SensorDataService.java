package com.fetallite.sensor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fetallite.sensor.R;
import com.fetallite.sensor.decoder.ChannelDecoderService;
import com.fetallite.sensor.model.ChannelData;
import com.fetallite.sensor.model.SensorSample;
import com.fetallite.sensor.parser.SensorDataParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background service for reading sensor data from file and processing it.
 * Reads samples from file accounting for read time and processing time.
 * Sampling frequency: 1000 samples/second
 */
public class SensorDataService extends Service implements ChannelDecoderService.ChannelDataListener {
    private static final String TAG = "SensorDataService";
    private static final String CHANNEL_ID = "sensor_data_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int SAMPLING_FREQUENCY = 1000; // 1000 samples per second
    private static final long SAMPLE_INTERVAL_NS = 1_000_000; // 1ms in nanoseconds

    private final IBinder binder = new LocalBinder();
    private ExecutorService fileReaderExecutor;
    private ChannelDecoderService channelDecoderService;
    private SensorDataParser parser;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private Handler mainHandler;
    
    private SensorDataListener listener;
    private long totalSamplesProcessed = 0;
    private long startTime = 0;

    public interface SensorDataListener {
        void onChannelValueUpdated(int channel, double value, int sampleNumber);
        void onProcessingStarted();
        void onProcessingStopped(long totalSamples);
        void onError(String message);
    }

    public class LocalBinder extends Binder {
        public SensorDataService getService() {
            return SensorDataService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        fileReaderExecutor = Executors.newSingleThreadExecutor();
        channelDecoderService = new ChannelDecoderService();
        channelDecoderService.setListener(this);
        parser = new SensorDataParser();
        mainHandler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProcessing();
        if (fileReaderExecutor != null) {
            fileReaderExecutor.shutdown();
        }
        if (channelDecoderService != null) {
            channelDecoderService.shutdown();
        }
        Log.d(TAG, "Service destroyed");
    }

    public void setListener(SensorDataListener listener) {
        this.listener = listener;
    }

    /**
     * Start processing sensor data from an asset file
     */
    public void startProcessingFromAsset(String assetFileName) {
        if (isProcessing.compareAndSet(false, true)) {
            channelDecoderService.start();
            
            fileReaderExecutor.submit(() -> {
                try {
                    InputStream inputStream = getAssets().open(assetFileName);
                    processInputStream(inputStream);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading asset file", e);
                    notifyError("Failed to read asset file: " + e.getMessage());
                }
            });

            if (listener != null) {
                mainHandler.post(() -> listener.onProcessingStarted());
            }
        }
    }

    /**
     * Start processing sensor data from an input stream
     */
    public void startProcessingFromStream(InputStream inputStream) {
        if (isProcessing.compareAndSet(false, true)) {
            channelDecoderService.start();
            fileReaderExecutor.submit(() -> processInputStream(inputStream));

            if (listener != null) {
                mainHandler.post(() -> listener.onProcessingStarted());
            }
        }
    }

    /**
     * Stop processing sensor data
     */
    public void stopProcessing() {
        if (isProcessing.compareAndSet(true, false)) {
            channelDecoderService.stop();
            
            if (listener != null) {
                final long total = totalSamplesProcessed;
                mainHandler.post(() -> listener.onProcessingStopped(total));
            }
        }
    }

    /**
     * Process input stream reading samples at the correct rate
     */
    private void processInputStream(InputStream inputStream) {
        Log.d(TAG, "Starting to process input stream");
        startTime = System.nanoTime();
        totalSamplesProcessed = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            long sampleIndex = 0;
            
            while ((line = reader.readLine()) != null && isProcessing.get()) {
                // Extract timestamp from line
                long lineTimestamp = parser.extractTimestamp(line);
                String dataPortion = parser.getDataPortion(line);
                
                // Parse all samples from the line
                List<SensorSample> samples = parser.parseLine(dataPortion, lineTimestamp);
                
                for (SensorSample sample : samples) {
                    if (!isProcessing.get()) {
                        break;
                    }
                    
                    // Calculate expected time for this sample
                    long expectedTimeNs = startTime + (sampleIndex * SAMPLE_INTERVAL_NS);
                    long currentTimeNs = System.nanoTime();
                    
                    // Wait if we're ahead of schedule
                    if (currentTimeNs < expectedTimeNs) {
                        long waitTimeNs = expectedTimeNs - currentTimeNs;
                        if (waitTimeNs > 0) {
                            try {
                                long waitTimeMs = waitTimeNs / 1_000_000;
                                int waitTimeNanos = (int) (waitTimeNs % 1_000_000);
                                Thread.sleep(waitTimeMs, waitTimeNanos);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    
                    // Submit sample to decoder service
                    channelDecoderService.submitSample(sample);
                    totalSamplesProcessed++;
                    sampleIndex++;
                    
                    // Log progress every 1000 samples
                    if (sampleIndex % 1000 == 0) {
                        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                        double actualRate = sampleIndex * 1000.0 / elapsedMs;
                        Log.d(TAG, "Processed " + sampleIndex + " samples in " + elapsedMs + 
                              "ms (rate: " + String.format("%.1f", actualRate) + " samples/sec)");
                    }
                }
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading input stream", e);
            notifyError("Error reading data: " + e.getMessage());
        }
        
        long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;
        Log.d(TAG, "Finished processing. Total samples: " + totalSamplesProcessed + 
              ", Time: " + totalTimeMs + "ms");
        
        isProcessing.set(false);
        if (listener != null) {
            final long total = totalSamplesProcessed;
            mainHandler.post(() -> listener.onProcessingStopped(total));
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onError(message));
        }
    }

    // ChannelDecoderService.ChannelDataListener implementation
    @Override
    public void onChannelDataDecoded(ChannelData data) {
        if (listener != null) {
            listener.onChannelValueUpdated(data.getChannelNumber(), data.getValue(), data.getSampleNumber());
        }
    }

    @Override
    public void onDecodingError(int channel, String error) {
        Log.e(TAG, "Decoding error on channel " + channel + ": " + error);
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }

    public long getTotalSamplesProcessed() {
        return totalSamplesProcessed;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sensor Data Processing",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notifications for sensor data processing service");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fetal Lite Sensor")
                .setContentText("Processing sensor data...")
                .setSmallIcon(R.drawable.ic_sensor)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
