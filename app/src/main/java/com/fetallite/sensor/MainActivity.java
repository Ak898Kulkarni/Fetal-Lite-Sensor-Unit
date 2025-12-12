package com.fetallite.sensor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.fetallite.sensor.service.SensorDataService;

/**
 * Main Activity displaying video player and 4 channel value TextViews
 */
public class MainActivity extends AppCompatActivity implements SensorDataService.SensorDataListener {
    private static final String TAG = "MainActivity";
    private static final String SENSOR_DATA_FILE = "sensor_data.txt";
    private static final String VIDEO_FILE = "sensor_video.mp4";

    private VideoView videoView;
    private TextView tvChannel1;
    private TextView tvChannel2;
    private TextView tvChannel3;
    private TextView tvChannel4;
    private TextView tvStatus;

    private SensorDataService sensorDataService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SensorDataService.LocalBinder binder = (SensorDataService.LocalBinder) service;
            sensorDataService = binder.getService();
            sensorDataService.setListener(MainActivity.this);
            serviceBound = true;
            Log.d(TAG, "Service bound");
            
            // Start processing sensor data
            startSensorDataProcessing();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            sensorDataService = null;
            Log.d(TAG, "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupVideoPlayer();
        startAndBindService();
    }

    private void initViews() {
        videoView = findViewById(R.id.videoView);
        tvChannel1 = findViewById(R.id.tvChannel1);
        tvChannel2 = findViewById(R.id.tvChannel2);
        tvChannel3 = findViewById(R.id.tvChannel3);
        tvChannel4 = findViewById(R.id.tvChannel4);
        tvStatus = findViewById(R.id.tvStatus);

        // Set initial values
        tvChannel1.setText("Channel 1: --");
        tvChannel2.setText("Channel 2: --");
        tvChannel3.setText("Channel 3: --");
        tvChannel4.setText("Channel 4: --");
        tvStatus.setText("Status: Initializing...");
    }

    private void setupVideoPlayer() {
        try {
            // Try to load video from assets (copy to cache first for VideoView)
            String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.sensor_video;
            Uri videoUri = Uri.parse(videoPath);
            
            videoView.setVideoURI(videoUri);
            
            // Set up looping
            videoView.setOnCompletionListener(mp -> {
                videoView.seekTo(0);
                videoView.start();
            });

            videoView.setOnPreparedListener(mp -> {
                Log.d(TAG, "Video prepared, starting playback");
                mp.setLooping(true);
                videoView.start();
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Video error: what=" + what + ", extra=" + extra);
                tvStatus.setText("Status: Video error - " + what);
                return true;
            });

        } catch (Exception e) {
            Log.e(TAG, "Error setting up video player", e);
            tvStatus.setText("Status: Video not available");
        }
    }

    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, SensorDataService.class);
        
        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // Bind to service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startSensorDataProcessing() {
        if (sensorDataService != null) {
            Log.d(TAG, "Starting sensor data processing");
            sensorDataService.startProcessingFromAsset(SENSOR_DATA_FILE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && !videoView.isPlaying()) {
            videoView.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (videoView != null) {
            videoView.stopPlayback();
        }
        
        if (serviceBound) {
            if (sensorDataService != null) {
                sensorDataService.stopProcessing();
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    // SensorDataService.SensorDataListener implementation

    @Override
    public void onChannelValueUpdated(int channel, double value, int sampleNumber) {
        String formattedValue = String.format("Channel %d: %.6f V (#%d)", channel, value, sampleNumber);
        
        switch (channel) {
            case 1:
                tvChannel1.setText(formattedValue);
                break;
            case 2:
                tvChannel2.setText(formattedValue);
                break;
            case 3:
                tvChannel3.setText(formattedValue);
                break;
            case 4:
                tvChannel4.setText(formattedValue);
                break;
        }
    }

    @Override
    public void onProcessingStarted() {
        tvStatus.setText("Status: Processing sensor data...");
        Toast.makeText(this, "Sensor data processing started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProcessingStopped(long totalSamples) {
        tvStatus.setText("Status: Completed - " + totalSamples + " samples processed");
        Toast.makeText(this, "Processing completed: " + totalSamples + " samples", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onError(String message) {
        tvStatus.setText("Status: Error - " + message);
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
    }
}
