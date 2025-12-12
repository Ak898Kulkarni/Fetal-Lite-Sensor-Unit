# Fetal Lite Sensor Android Application

## Overview
This Android application reads sensor data from a file in the background, decodes 4 channels using separate threads, and displays every 100th sample value on the UI while playing a video in loop.

## Architecture

### Components

1. **MainActivity** (`MainActivity.java`)
   - Displays VideoView for looping video playback
   - Shows 4 TextViews for channel values
   - Binds to SensorDataService

2. **SensorDataService** (`service/SensorDataService.java`)
   - Background foreground service for reading sensor data
   - Reads from asset file at 1000 samples/second rate
   - Accounts for read time and processing time

3. **ChannelDecoderService** (`decoder/ChannelDecoderService.java`)
   - ExecutorService with 4 threads (one per channel)
   - Processes samples and displays every 100th value (every 100ms)
   - Uses BlockingQueues for thread-safe sample distribution

4. **SensorDataParser** (`parser/SensorDataParser.java`)
   - Parses sample format: `!<SampleNumberMSB><SampleNumberLSB><Ch1><Ch2><Ch3><Ch4>`
   - Converts 6-byte hex values to voltage (24-bit ADC)

5. **Models** (`model/`)
   - `SensorSample.java` - Represents a single sample with 4 channel values
   - `ChannelData.java` - Represents decoded channel data for UI display

## Sample Data Format

```
<timestamp>!<sample1>!<sample2>...
```

Each sample: `!<SampleNumber:4hex><Ch1:6hex><Ch2:6hex><Ch3:6hex><Ch4:6hex>`

Example: `!""01487A0245F9022AC6E004EB`
- `""` - Sample number (2 bytes)
- `01487A` - Channel 1 (3 bytes → voltage)
- `0245F9` - Channel 2 (3 bytes → voltage)
- `022AC6` - Channel 3 (3 bytes → voltage)
- `E004EB` - Channel 4 (3 bytes → voltage)

## Setup Instructions

### Required Files

1. **Sensor Data File**: Place your sensor data file at:
   ```
   app/src/main/assets/sensor_data.txt
   ```

2. **Video File**: Place your video file (MP4 format) at:
   ```
   app/src/main/res/raw/sensor_video.mp4
   ```
   
   Note: The video file must be named `sensor_video.mp4`

### Building the App

1. Open the project in Android Studio
2. Sync Gradle files
3. Ensure you have added the required files (sensor data and video)
4. Build and run on device/emulator (API 24+)

## Key Features

- **Real-time Streaming**: Data is read at 1000 samples/second rate
- **Multi-threaded Decoding**: 4 separate threads decode each channel
- **Timed Display Updates**: Every 100th sample displayed (100ms interval)
- **Video Loop**: Background video plays continuously
- **Foreground Service**: Processing continues even when app is backgrounded

## Voltage Conversion

The hex values are converted to voltage using:
```
voltage = (rawValue / 2^24) * 3.3V
```

Where:
- `rawValue` is the 24-bit integer from 6 hex characters
- Reference voltage is 3.3V (configurable)
