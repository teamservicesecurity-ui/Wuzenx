import crypto from 'node:crypto';
import { encrypt, deriveKey, log } from '../utils.js';

/**
 * Encrypt a raw DEX payload for a specific device.
 * The key is derived from:
 *   - A server-side secret (PAYLOAD_KEY)
 *   - The device's unique identifiers (Android ID, build fingerprint)
 * 
 * This ensures the payload can ONLY be decrypted on the target device.
 */
export function encryptPayload(rawDexBytes, deviceIdentifier, serverKey) {
  // Derive a device-specific encryption key
  const salt = crypto.createHash('sha256').update(deviceIdentifier).digest().subarray(0, 16);
  const key = deriveKey(serverKey, salt);

  // Encrypt the DEX bytes
  const encrypted = encrypt(rawDexBytes.toString('base64'), key);

  log('debug', 'Payload encrypted', {
    deviceIdentifier: deviceIdentifier.slice(0, 16) + '...',
    rawSize: rawDexBytes.length,
    encryptedSize: encrypted.length,
  });

  return {
    encrypted,
    salt: salt.toString('base64'),
    // Device needs: key = PBKDF2(serverKey, salt)
    // Server sends: encrypted payload + salt
    // Device computes: key server-side match
  };
}

/**
 * Encrypt the payload for bulk distribution (generic key).
 * Used when we don't know the target device yet.
 */
export function encryptPayloadGeneric(rawDexBytes, serverKey) {
  const salt = crypto.randomBytes(16);
  const key = deriveKey(serverKey, salt);

  const encrypted = encrypt(rawDexBytes.toString('base64'), key);

  return {
    encrypted,
    salt: salt.toString('base64'),
  };
}
