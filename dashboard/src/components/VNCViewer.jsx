import { useRef, useEffect, useState, useCallback } from 'react';

/**
 * HVNC Screen Viewer — renders real-time screen frames from a device.
 * Listens for 'screen_frame' custom events dispatched by useWS.
 */
export default function VNCViewer({ deviceId, width = 360, height = 720 }) {
  const canvasRef = useRef(null);
  const [connected, setConnected] = useState(false);
  const [lastFrame, setLastFrame] = useState(null);

  useEffect(() => {
    if (!deviceId) return;

    const handler = (event) => {
      const { deviceId: devId, data, timestamp } = event.detail;
      if (devId !== deviceId) return;

      setLastFrame(timestamp);
      if (!connected) setConnected(true);

      const canvas = canvasRef.current;
      if (!canvas) return;

      const ctx = canvas.getContext('2d');
      const img = new Image();
      img.onload = () => {
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      };
      img.src = `data:image/jpeg;base64,${data}`;
    };

    window.addEventListener('screen_frame', handler);

    // Initial connection check
    const checkInterval = setInterval(() => {
      const now = Date.now();
      if (lastFrame && now - lastFrame > 10000) {
        setConnected(false);
      }
    }, 5000);

    return () => {
      window.removeEventListener('screen_frame', handler);
      clearInterval(checkInterval);
    };
  }, [deviceId, connected, lastFrame]);

  return (
    <div className="relative bg-surface-950 rounded-lg border border-surface-800 overflow-hidden">
      {/* Status bar */}
      <div className="absolute top-0 left-0 right-0 z-10 flex items-center justify-between px-3 py-1.5 bg-gradient-to-b from-surface-950/90 to-transparent">
        <div className="flex items-center gap-2">
          <span className={`status-dot ${connected ? 'online animate-pulse-dot' : 'offline'}`} />
          <span className={`text-xs ${connected ? 'text-success' : 'text-surface-400'}`}>
            {connected ? 'Streaming' : 'Waiting...'}
          </span>
        </div>
        <div className="text-[10px] text-surface-500 font-mono">
          {deviceId?.slice(0, 8)}...
        </div>
      </div>

      {/* Canvas */}
      <canvas
        ref={canvasRef}
        width={width}
        height={height}
        className="block w-full"
        style={{ aspectRatio: `${width}/${height}` }}
      />

      {/* No signal overlay */}
      {!connected && (
        <div className="absolute inset-0 flex items-center justify-center bg-surface-950/80">
          <div className="text-center">
            <div className="text-3xl mb-2 opacity-30">📱</div>
            <p className="text-sm text-surface-400">No screen stream</p>
            <p className="text-xs text-surface-500 mt-1">Select a device to view</p>
          </div>
        </div>
      )}
    </div>
  );
}
