import { useNavigate } from 'react-router-dom';

export default function DeviceCard({ device }) {
  const navigate = useNavigate();

  const batteryColor =
    device.battery_level >= 80
      ? 'text-success'
      : device.battery_level >= 30
        ? 'text-warning'
        : 'text-danger';

  return (
    <div
      onClick={() => navigate(`/devices/${device.id}`)}
      className="bg-surface-900 rounded-lg border border-surface-800 p-4 cursor-pointer hover:border-accent/30 hover:bg-surface-800/50 transition-all duration-200 animate-slide-in group"
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-3">
        <div className="min-w-0">
          <h3 className="text-sm font-medium text-gray-100 truncate">
            {device.name || device.model || 'Unknown Device'}
          </h3>
          <p className="text-xs text-surface-400 mt-0.5">
            {device.manufacturer && device.model
              ? `${device.manufacturer} ${device.model}`
              : device.model || 'Unknown model'}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <span className={`status-dot ${device.is_online ? 'online' : 'offline'}`} />
          <span className={`text-xs ${device.is_online ? 'text-success' : 'text-surface-400'}`}>
            {device.is_online ? 'Online' : 'Offline'}
          </span>
        </div>
      </div>

      {/* Details grid */}
      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        <div>
          <span className="text-surface-500">Android</span>
          <p className="text-gray-300">{device.android_version || '—'}</p>
        </div>
        <div>
          <span className="text-surface-500">SDK</span>
          <p className="text-gray-300">{device.sdk_version || '—'}</p>
        </div>
        <div>
          <span className="text-surface-500">IP</span>
          <p className="text-gray-300 font-mono text-[11px]">{device.public_ip || '—'}</p>
        </div>
        <div>
          <span className="text-surface-500">Battery</span>
          <p className={batteryColor}>
            {device.battery_level >= 0 ? `${device.battery_level}%` : '—'}
            {device.is_charging ? ' ⚡' : ''}
          </p>
        </div>
        <div>
          <span className="text-surface-500">Carrier</span>
          <p className="text-gray-300">{device.carrier || '—'}</p>
        </div>
        <div>
          <span className="text-surface-500">Last seen</span>
          <p className="text-gray-300">
            {device.last_seen_at
              ? new Date(device.last_seen_at).toLocaleString()
              : '—'}
          </p>
        </div>
      </div>

      {/* Tags */}
      {device.tags && device.tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-3">
          {(typeof device.tags === 'string' ? JSON.parse(device.tags) : device.tags).map((tag, i) => (
            <span
              key={i}
              className="px-2 py-0.5 text-[10px] bg-surface-800 text-surface-300 rounded"
            >
              {tag}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
