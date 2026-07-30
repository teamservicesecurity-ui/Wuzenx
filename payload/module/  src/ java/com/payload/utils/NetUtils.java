// ============================================================
// FILE 19: utils/NetUtils.java
// ============================================================
package io.hackerai.implant.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * NetUtils — network state and device info utilities.
 */
public class NetUtils {
    private static final String TAG = "NetUtils";

    /** Check if the device has an active internet connection. */
    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network nw = cm.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapacts cap = cm.getNetworkCapabilities(nw);
            return cap != null && (
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    /** Get device IP address (prefers non-local, non-loopback). */
    public static String getDeviceIp() {
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : interfaces) {
                List<InetAddress> addrs = Collections.list(iface.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress()
                            && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getDeviceIp error", e);
        }
        return "127.0.0.1";
    }

    /** Get MAC address of the WiFi interface. */
    public static String getMacAddress() {
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : interfaces) {
                if (iface.getName().equalsIgnoreCase("wlan0")) {
                    byte[] mac = iface.getHardwareAddress();
                    if (mac == null) return "02:00:00:00:00:00";
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02x:", b & 0xff));
                    }
                    if (sb.length() > 0) sb.setLength(sb.length() - 1);
                    return sb.toString().toUpperCase();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMacAddress error", e);
        }
        return "02:00:00:00:00:00";
    }
                    }
