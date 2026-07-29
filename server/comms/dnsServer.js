import dgram from 'node:dgram';
import { log } from '../utils.js';

/**
 * DNS Tunneling Server (Fallback C2 Channel).
 * 
 * Devices that can't maintain a persistent WebSocket connection
 * can use DNS queries to communicate.
 * 
 * Protocol:
 *   Device sends: TXT query to <base64-payload>.<domain>
 *   Server responds: TXT record with response payload
 * 
 * This is intentionally simple — full DNS tunneling would require
 * encoding command/response in DNS record types.
 * We use TXT records for simplicity.
 * 
 * Note: This runs on port 5353 by default (non-privileged).
 * For port 53, you need root or capabilities.
 */
export function createDnsServer(db, config) {
  const socket = dgram.createSocket('udp4');
  const domain = config.dnsDomain;
  const secret = config.dnsSecret;
  const buffer = Buffer.alloc(512);

  socket.on('message', (msg, rinfo) => {
    try {
      handleDnsQuery(msg, rinfo, buffer);
    } catch (err) {
      log('error', 'DNS query handler error', { error: err.message, from: rinfo.address });
    }
  });

  socket.on('listening', () => {
    const addr = socket.address();
    log('info', 'DNS tunnel listening', { address: addr.address, port: addr.port });
  });

  socket.on('error', (err) => {
    log('error', 'DNS server error', { error: err.message });
  });

  /**
   * Minimal DNS response builder.
   * Only handles TXT queries for our domain.
   */
  function handleDnsQuery(msg, rinfo, buf) {
    if (msg.length < 12) return;

    const id = msg.readUInt16BE(0);
    const flags = msg.readUInt16BE(2);
    const questions = msg.readUInt16BE(4);

    // We only respond to standard queries (no recursion desired check needed)
    if (questions === 0) return;

    // Parse the question
    let offset = 12;
    let qname = '';
    let qnameLen = 0;

    while (offset < msg.length) {
      const len = msg.readUInt8(offset);
      if (len === 0) {
        offset++;
        break;
      }
      if (qname) qname += '.';
      qname += msg.toString('utf-8', offset + 1, offset + 1 + len);
      offset += 1 + len;
      qnameLen++;
    }

    const qtype = msg.readUInt16BE(offset);
    offset += 2;
    const qclass = msg.readUInt16BE(offset);

    // Only respond to TXT queries for our domain
    if (qtype !== 16 || !qname.endsWith(domain)) {
      return;
    }

    // Extract the base64-encoded payload from the subdomain
    const subdomain = qname.slice(0, -domain.length - 1);
    let responseData = '';

    try {
      // Decode the base64 payload
      const decoded = Buffer.from(subdomain.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf-8');
      const payload = JSON.parse(decoded);

      log('debug', 'DNS query received', {
        from: rinfo.address,
        type: payload.type,
        deviceId: payload.deviceId?.slice(0, 8),
      });

      // Handle different message types
      if (payload.type === 'ping') {
        responseData = JSON.stringify({ type: 'pong', ts: Date.now() });
      } else if (payload.type === 'auth') {
        const device = db.stmts.getDeviceByToken.get(payload.token);
        if (device) {
          db.stmts.setDeviceOnline.run(new Date().toISOString(), device.id);

          // Check for pending commands
          const pendingCommands = db.stmts.getPendingCommandsForDevice.all(device.id);
          responseData = JSON.stringify({
            type: 'auth_ok',
            deviceId: device.id,
            pendingCommands: pendingCommands.map(c => ({
              id: c.id,
              type: c.type,
              payload: c.payload,
            })),
          });

          // Mark commands as sent
          for (const cmd of pendingCommands) {
            db.stmts.updateCommandStatus.run('sent', new Date().toISOString(), null, null, null, cmd.id);
          }
        } else {
          responseData = JSON.stringify({ type: 'auth_error', message: 'Invalid token' });
        }
      } else if (payload.type === 'telemetry') {
        if (payload.deviceId) {
          db.stmts.setDeviceOnline.run(new Date().toISOString(), payload.deviceId);
        }
        responseData = JSON.stringify({ type: 'ack' });
      } else if (payload.type === 'result') {
        if (payload.commandId) {
          db.stmts.updateCommandStatus.run(
            payload.error ? 'failed' : 'completed',
            null,
            payload.result || null,
            new Date().toISOString(),
            payload.error || null,
            payload.commandId
          );
        }
        responseData = JSON.stringify({ type: 'ack' });
      } else {
        responseData = JSON.stringify({ type: 'error', message: 'Unknown type' });
      }
    } catch {
      responseData = JSON.stringify({ type: 'error', message: 'Invalid payload' });
    }

    // Build DNS response
    writeDnsResponse(buf, id, qname, qtype, qclass, responseData, rinfo, socket);
  }

  function writeDnsResponse(buf, id, qname, qtype, qclass, responseData, rinfo, socket) {
    let offset = 0;

    // Header
    buf.writeUInt16BE(id, offset); offset += 2;
    buf.writeUInt16BE(0x8180, offset); offset += 2; // Standard response, no error
    buf.writeUInt16BE(1, offset); offset += 2; // 1 question
    buf.writeUInt16BE(1, offset); offset += 2; // 1 answer
    buf.writeUInt16BE(0, offset); offset += 2; // NS count
    buf.writeUInt16BE(0, offset); offset += 2; // AR count

    // Question (echo back)
    const nameParts = qname.split('.');
    for (const part of nameParts) {
      buf.writeUInt8(part.length, offset); offset += 1;
      buf.write(part, offset, 'utf-8'); offset += part.length;
    }
    buf.writeUInt8(0, offset); offset += 1; // Root label
    buf.writeUInt16BE(qtype, offset); offset += 2;
    buf.writeUInt16BE(qclass, offset); offset += 2;

    // Answer
    // Name pointer (0xc00c = pointer to byte 12, start of question name)
    buf.writeUInt16BE(0xc00c, offset); offset += 2;
    buf.writeUInt16BE(16, offset); offset += 2; // TXT
    buf.writeUInt16BE(1, offset); offset += 2;  // Class IN
    buf.writeUInt32BE(60, offset); offset += 4; // TTL 60s

    // TXT record data
    const txtData = Buffer.from(responseData, 'utf-8');
    buf.writeUInt16BE(txtData.length + 1, offset); offset += 2; // RDLENGTH
    buf.writeUInt8(txtData.length, offset); offset += 1;       // Character length
    txtData.copy(buf, offset); offset += txtData.length;

    socket.send(buf.subarray(0, offset), rinfo.port, rinfo.address);
  }

  function listen(port, host) {
    socket.bind(port, host || '0.0.0.0');
  }

  function close() {
    try { socket.close(); } catch {}
  }

  // Auto-start if port configured
  if (config.dnsPort) {
    listen(config.dnsPort, config.host);
  }

  return { socket, listen, close };
}
