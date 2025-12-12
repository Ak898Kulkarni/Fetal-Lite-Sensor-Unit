# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep sensor data models
-keep class com.fetallite.sensor.model.** { *; }

# Keep service classes
-keep class com.fetallite.sensor.service.** { *; }
-keep class com.fetallite.sensor.decoder.** { *; }
