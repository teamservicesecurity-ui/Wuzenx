import { useState, useEffect } from 'react';
import { useStore } from '../lib/store.js';
import { useAPI } from '../hooks/useAPI.js';
import { getDevices, getGroups, broadcastCommand } from '../lib/api.js';
import DeviceCard from '../components/DeviceCard.jsx';

export default function Devices() {
  const [search, setSearch] = useState('');
  const [filterOnline, setFilterOnline] = useState(false);
  const [filterGroup, setFilterGroup] = useState('');

  const { data, loading, refetch } = useAPI(() =>
    getDevices({ search, online: filterOnline ? true : undefined, group: filterGroup })
  , [search, filterOnline, filterGroup]);

  const { data: groupsData } = useAPI(getGroups);
  const devices = data?.devices || [];
  const groups = groupsData?.groups || [];

  const setDevices = useStore(s => s.setDevices);

  useEffect(() => {
    if (devices.length > 0) setDevices(devices);
  }, [devices, setDevices]);

  const handleBroadcast = async () => {
    const type = prompt('Command type (e.g., shell, sensor, overlay):');
    if (!type) return;
    const payload = prompt('Payload:');
    if (!payload) return;
    try {
      await broadcastCommand(type, payload, filterGroup || undefined);
      alert('Command broadcast to online devices');
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  return (
    <div className="space-y-6 animate-slide-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-100">Devices</h1>
          <p className="text-xs text-surface-400 mt-0.5">{devices.length} device(s)</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={refetch}
            className="h-8 px-3 text-xs bg-surface-800 hover:bg-surface-700 text-gray-200 rounded transition-colors"
          >
            ⟳ Refresh
          </button>
          <button
            onClick={handleBroadcast}
            className="h-8 px-3 text-xs bg-warning/10 hover:bg-warning/20 text-warning rounded transition-colors"
          >
            Broadcast
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search by name, model, IP..."
          className="h-8 px-3 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 outline-none focus:border-accent/50 transition-colors w-64"
        />
        <label className="flex items-center gap-2 text-xs text-surface-400 cursor-pointer">
          <input
            type="checkbox"
            checked={filterOnline}
            onChange={e => setFilterOnline(e.target.checked)}
            className="accent-accent"
          />
          Online only
        </label>
        <select
          value={filterGroup}
          onChange={e => setFilterGroup(e.target.value)}
          className="h-8 px-2 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 outline-none focus:border-accent/50 transition-colors"
        >
          <option value="">All groups</option>
          {groups.map(g => (
            <option key={g.id} value={g.id}>{g.name}</option>
          ))}
        </select>
      </div>

      {/* Device grid */}
      {loading ? (
        <div className="flex items-center justify-center py-16">
          <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin" />
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {devices.length === 0 && (
            <p className="text-xs text-surface-500 col-span-full text-center py-8">
              {search || filterOnline || filterGroup
                ? 'No devices match your filters'
                : 'No devices connected yet'}
            </p>
          )}
          {devices.map(device => (
            <DeviceCard key={device.id} device={device} />
          ))}
        </div>
      )}
    </div>
  );
}
