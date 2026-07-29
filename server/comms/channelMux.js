import { log } from '../utils.js';

/**
 * Channel Multiplexer.
 * 
 * Routes outgoing commands to devices across available channels.
 * Primary: WebSocket (persistent)
 * Fallback: DNS (polling)
 * Emergency: HTTP (scheduled)
 * 
 * If a device is not connected via WebSocket but is known,
 * we mark commands as 'pending' — they'll be delivered when
 * the device next checks in via any channel.
 */
export function createChannelMux(wsServer, dnsServer, db, config) {
  const channels = config.channels || {};

  /**
   * Attempt to deliver a command to a device.
   * Tries channels in priority order.
   * Returns the channel that successfully delivered, or null.
   */
  function deliverCommand(deviceId, command) {
    // Try WebSocket first (lowest latency)
    if (channels.primary?.enabled !== false) {
      const sent = wsServer.sendCommand(deviceId, command);
      if (sent) {
        log('debug', 'Command delivered via WebSocket', {
          deviceId: deviceId.slice(0, 8),
          commandId: command.id,
        });
        return 'ws';
      }
    }

    // DNS channel is device-initiated (pull), so we can't push.
    // We queue the command and mark it pending — the device
    // will receive it on its next DNS query.
    log('debug', 'Device not on WebSocket, command queued for next check-in', {
      deviceId: deviceId.slice(0, 8),
      commandId: command.id,
    });

    // The command is already in the DB as 'pending' from the API layer.
    // When the device does a DNS query (or reconnects via WS),
    // it will receive pending commands.
    return null;
  }

  /**
   * Deliver a broadcast command.
   */
  function deliverBroadcast(command, groupId) {
    const wsCount = wsServer.broadcastCommand(command, groupId);
    log('info', 'Broadcast delivered', {
      wsCount,
      groupId: groupId || 'all',
    });
    return wsCount;
  }

  /**
   * Get status of all channels.
   */
  function getChannelStatus() {
    return {
      primary: {
        type: 'ws',
        enabled: channels.primary?.enabled !== false,
        connectedDevices: wsServer.getConnectedDeviceIds().length,
      },
      fallback: {
        type: 'dns',
        enabled: channels.fallback?.enabled !== false,
        domain: channels.fallback?.domain || config.dnsDomain,
      },
      emergency: {
        type: 'http',
        enabled: channels.emergency?.enabled === true,
      },
    };
  }

  return {
    deliverCommand,
    deliverBroadcast,
    getChannelStatus,
  };
}
