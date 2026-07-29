package com.payload.core;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Hidden API Bypass
 *
 * Android 9+ restricts access to non-SDK interfaces (hidden APIs).
 * This class uses the LSPosed technique of meta-reflection to bypass
 * those restrictions entirely.
 *
 * The key insight: VMRuntime.setHiddenApiExemptions() accepts a list
 * of signature prefixes. If you pass "L" (which matches ALL classes
 * since all Java class signatures start with 'L'), ALL hidden APIs
 * become accessible for the current process.
 *
 * The trick: VMRuntime is itself a hidden class. So we first reflect
 * on "dalvik.system.VMRuntime" (which is a public class with public methods),
 * get the runtime instance, then reflectively call setHiddenApiExemptions.
 * This is "double reflection" — reflecting on the reflection infrastructure
 * itself — and cannot be blocked because the exemption check runs in
 * native code that checks if the current method signature is in the exemption list.
 */
public class HiddenApiBypass {

    private static final String TAG = "CyberAI-HiddenAPI";
    private static boolean bypassed = false;

    /**
     * Unlock ALL hidden APIs for the current process.
     * Call this once at initialization.
     */
    public static synchronized void unlockAll() {
        if (bypassed) return;

        try {
            // Step 1: Get VMRuntime class (public class in dalvik.system package)
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");

            // Step 2: Get the current runtime instance (public static method)
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            Object runtime = getRuntime.invoke(null);

            // Step 3: Call setHiddenApiExemptions(new String[]{"L"})
            // "L" matches ALL class signatures (everything starts with L)
            Method setExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions", String[].class
            );
            setExemptions.invoke(runtime, new Object[]{new String[]{"L"}});

            bypassed = true;
            Log.i(TAG, "Hidden API restrictions bypassed successfully (exempted all classes)");

        } catch (Exception e) {
            Log.e(TAG, "Failed to bypass hidden API restrictions: " + e.getMessage());

            // Fallback: try using LSPosed library if available
            try {
                Class<?> lsposedBypass = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass");
                Method addExemptions = lsposedBypass.getMethod("addHiddenApiExemptions", String[].class);
                addExemptions.invoke(null, new Object[]{new String[]{"L"}});
                bypassed = true;
                Log.i(TAG, "Hidden API bypassed via LSPosed library");
            } catch (Exception ex) {
                Log.e(TAG, "LSPosed bypass also failed: " + ex.getMessage());

                // Final fallback: use FreeReflection library if present
                try {
                    Class<?> freeReflection = Class.forName("me.weishu.reflection.Reflection");
                    Method doUnlock = freeReflection.getMethod("unlock", Class[].class);
                    doUnlock.invoke(null, (Object) new Class[]{String.class});
                    bypassed = true;
                    Log.i(TAG, "Hidden API bypassed via FreeReflection");
                } catch (Exception exc) {
                    Log.e(TAG, "All bypass methods failed. Hidden APIs will be restricted.");
                }
            }
        }
    }

    /**
     * Add specific signature prefixes to the exemption list.
     * Useful if you want fine-grained control instead of the blanket "L".
     */
    public static void addExemptions(String... prefixes) {
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            Object runtime = getRuntime.invoke(null);
            Method setExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions", String[].class
            );
            setExemptions.invoke(runtime, new Object[]{prefixes});
        } catch (Exception e) {
            Log.e(TAG, "Failed to add exemptions: " + e.getMessage());
        }
    }

    public static boolean isBypassed() {
        return bypassed;
    }
}
