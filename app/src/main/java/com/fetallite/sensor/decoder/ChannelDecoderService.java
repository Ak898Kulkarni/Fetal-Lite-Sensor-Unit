package com.fetallite.sensor.decoder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fetallite.sensor.model.ChannelData;
import com.fetallite.sensor.model.SensorSample;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Channel Decoder Service that runs 4 threads to decode each channel
 * and displays every 100th value on the UI.
 */
public class ChannelDecoderService {
    private static final String TAG = "ChannelDecoderService";
    private static final int NUM_CHANNELS = 4;
    private static final int DISPLAY_INTERVAL = 100; // Display every 100th sample
    private static final long DISPLAY_RATE_MS = 100; // 100ms display rate (1000 samples/sec, show every 100th)

    private final ExecutorService decoderExecutor;
    private final BlockingQueue<SensorSample>[] channelQueues;
    private final AtomicBoolean isRunning;
    private final AtomicInteger[] sampleCounters;
    private final Handler mainHandler;
    
    private ChannelDataListener listener;
    private long lastDisplayTime = 0;

    public interface ChannelDataListener {
        void onChannelDataDecoded(ChannelData data);
        void onDecodingError(int channel, String error);
    }

    @SuppressWarnings("unchecked")
    public ChannelDecoderService() {
        this.decoderExecutor = Executors.newFixedThreadPool(NUM_CHANNELS);
        this.channelQueues = new LinkedBlockingQueue[NUM_CHANNELS];
        this.sampleCounters = new AtomicInteger[NUM_CHANNELS];
        this.isRunning = new AtomicBoolean(false);
        this.mainHandler = new Handler(Looper.getMainLooper());

        for (int i = 0; i < NUM_CHANNELS; i++) {
            channelQueues[i] = new LinkedBlockingQueue<>(10000);
            sampleCounters[i] = new AtomicInteger(0);
        }
    }

    public void setListener(ChannelDataListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Starting channel decoder service with 4 threads");
            
            // Start 4 decoder threads, one for each channel
            for (int i = 0; i < NUM_CHANNELS; i++) {
                final int channelIndex = i;
                decoderExecutor.submit(() -> decodeChannel(channelIndex));
            }
        }
    }

    public void stop() {
        isRunning.set(false);
        // Clear all queues
        for (BlockingQueue<SensorSample> queue : channelQueues) {
            queue.clear();
        }
    }

    public void shutdown() {
        stop();
        decoderExecutor.shutdown();
    }

    /**
     * Submit a sample to be decoded by all channel threads
     */
    public void submitSample(SensorSample sample) {
        if (!isRunning.get()) {
            return;
        }

        // Add sample to each channel's queue
        for (int i = 0; i < NUM_CHANNELS; i++) {
            try {
                channelQueues[i].offer(sample);
            } catch (Exception e) {
                Log.e(TAG, "Failed to queue sample for channel " + (i + 1), e);
            }
        }
    }

    /**
     * Decoder thread for a specific channel
     */
    private void decodeChannel(int channelIndex) {
        Log.d(TAG, "Channel " + (channelIndex + 1) + " decoder thread started");

        while (isRunning.get()) {
            try {
                // Take sample from queue (blocking)
                SensorSample sample = channelQueues[channelIndex].poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                
                if (sample == null) {
                    continue;
                }

                // Process the channel data
                double channelValue = sample.getChannelValues()[channelIndex];
                int count = sampleCounters[channelIndex].incrementAndGet();

                // Display every 100th sample
                if (count % DISPLAY_INTERVAL == 0) {
                    // Account for timing - ensure we're displaying at correct rate
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastDisplay = currentTime - lastDisplayTime;
                    
                    if (timeSinceLastDisplay < DISPLAY_RATE_MS && lastDisplayTime > 0) {
                        // Wait to maintain display rate
                        try {
                            Thread.sleep(DISPLAY_RATE_MS - timeSinceLastDisplay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    lastDisplayTime = System.currentTimeMillis();
                    
                    // Create channel data and notify listener on main thread
                    ChannelData channelData = new ChannelData(
                            channelIndex + 1,
                            channelValue,
                            sample.getSampleNumber()
                    );

                    if (listener != null) {
                        mainHandler.post(() -> listener.onChannelDataDecoded(channelData));
                    }

                    Log.d(TAG, "Channel " + (channelIndex + 1) + " decoded sample #" + 
                          sample.getSampleNumber() + ": " + channelValue + "V");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.d(TAG, "Channel " + (channelIndex + 1) + " decoder thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error decoding channel " + (channelIndex + 1), e);
                if (listener != null) {
                    final int channel = channelIndex + 1;
                    final String error = e.getMessage();
                    mainHandler.post(() -> listener.onDecodingError(channel, error));
                }
            }
        }

        Log.d(TAG, "Channel " + (channelIndex + 1) + " decoder thread stopped");
    }

    /**
     * Get the number of samples processed by a channel
     */
    public int getProcessedCount(int channelIndex) {
        if (channelIndex >= 0 && channelIndex < NUM_CHANNELS) {
            return sampleCounters[channelIndex].get();
        }
        return 0;
    }

    /**
     * Get total samples processed across all channels
     */
    public int getTotalProcessedCount() {
        int total = 0;
        for (AtomicInteger counter : sampleCounters) {
            total += counter.get();
        }
        return total / NUM_CHANNELS; // Average since all channels process same samples
    }
}
