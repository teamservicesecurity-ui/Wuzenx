import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';

// ===================================================================== Logger
let _logLevel = 'info';
const LEVELS = { debug: 0, info: 1, warn: 2, error: 3 };

export function createLogger(level) {
  _logLevel = level || 'info';
}

export function log(level, message, meta = {}) {
  if (LEVELS[level] < LEVELS[_logLevel]) return;
  const entry = {
    t: new Date().toISOString(),
    l: level,
    m: message,
    ...meta,
  };
  if (level === 'error') {
    console.error(JSON.stringify(entry));
  } else {
    console.log(JSON.stringify(entry));
  }
}

// ===================================================================== Crypto

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 16;
const TAG_LENGTH = 16;
const KEY_LENGTH = 32;

/**
 * Derive a 256-bit key from a password + salt using PBKDF2.
 */
export function deriveKey(password, salt) {
  return crypto.pbkdf2Sync(password, salt, 100000, KEY_LENGTH, 'sha512');
}

/**
 * Encrypt plaintext with AES-256-GCM.
 * Returns base64-encoded: iv + ciphertext + authTag
 */
export function encrypt(plaintext, key) {
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  const encrypted = Buffer.concat([cipher.update(plaintext, 'utf-8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return Buffer.concat([iv, encrypted, authTag]).toString('base64');
}

/**
 * Decrypt AES-256-GCM ciphertext (base64: iv + ciphertext + authTag).
 */
export function decrypt(ciphertextB64, key) {
  const buffer = Buffer.from(ciphertextB64, 'base64');
  const iv = buffer.subarray(0, IV_LENGTH);
  const authTag = buffer.subarray(buffer.length - TAG_LENGTH);
  const encrypted = buffer.subarray(IV_LENGTH, buffer.length - TAG_LENGTH);
  const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
  decipher.setAuthTag(authTag);
  return decipher.update(encrypted) + decipher.final('utf-8');
}

/**
 * Hash a password with bcrypt-compatible salt (using PBKDF2 for zero deps).
 * We import bcryptjs in auth routes directly; this is for internal use.
 */
export function hashToken(token, salt) {
  return crypto
    .pbkdf2Sync(token, salt, 10000, 32, 'sha256')
    .toString('hex');
}

/**
 * Generate a random device token.
 */
export function generateDeviceToken() {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Generate a random build ID.
 */
export function generateBuildId() {
  return 'bld_' + crypto.randomBytes(12).toString('hex');
}

// ===================================================================== JWT

export function signJwt(payload, secret, expiresIn = '24h') {
  return jwt.sign(payload, secret, { expiresIn, algorithm: 'HS256' });
}

export function verifyJwt(token, secret) {
  return jwt.verify(token, secret, { algorithms: ['HS256'] });
}

// ===================================================================== Validators

export function isValidIp(ip) {
  const ipv4 = /^(\d{1,3}\.){3}\d{1,3}$/;
  if (ipv4.test(ip)) return ip.split('.').every(n => parseInt(n, 10) <= 255);
  return false;
}

export function isValidPackageName(name) {
  return /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(name);
}

// ===================================================================== Misc

export function now() {
  return Date.now();
}

export function isoNow() {
  return new Date().toISOString();
}

export function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}
