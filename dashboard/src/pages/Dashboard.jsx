import { useEffect } from 'react';
import { useStore } from '../lib/store.js';
import { usePolling } from '../hooks/useAPI.js';
import { getDevices, getDeviceStats, getGroups } from '../lib/api.js';
import DeviceCard from '../components/DeviceCard.jsx';
import DeviceMap from '../components/DeviceMap.jsx';

export default function Dashboard() {
  const setDevices = useStore(s => s.setDevices);
  const setStats = useStore(s => s.setStats);
  const setGroups = useStore(s => s.setGroups);
  const devices = useStore(s => s.devices);
  const stats = useStore(s => s.stats);

  // Poll devices every 10s
  const { data: devicesData } = usePolling(
    () => getDevices({ online: 'true' }),
    10000
  );

  // Poll stats every 15s
  const { data: statsData } = usePolling(getDeviceStats, 15000);

  // Poll groups every 30s
  const { data: groupsData } = usePolling(getGroups, 30000);

  // Sync to store
  useEffect(() => {
    if (devicesData?.devices) setDevices(devicesData.devices);
  }, [devicesData, setDevices]);

  useEffect(() => {
    if (statsData) setStats(statsData);
  }, [statsData, setStats]);

  useEffect(() => {
    if (groupsData?.groups) setGroups(groupsData.groups);
  }, [groupsData, setGroups]);

  // Top stat cards
  const statCards = [
    { label: 'Total Devices', value: stats.totalDevices, color: 'text-info' },
    { label: 'Online Now', value: stats.onlineDevices, color: 'text-success' },
    { label: 'Groups', value: stats.totalGroups, color: 'text-warning' },
    { label: 'Commands Today', value: stats.commandsToday, color: 'text-accent' },
  ];

  return (
    <div className="space-y-6 animate-slide-in">
      <div>
        <h1 className="text-lg font-semibold text-gray-100">Dashboard</h1>
        <p className="text-xs text-surface-400 mt-0.5">Real-time overview of all devices</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map(card => (
          <div
            key={card.label}
            className="bg-surface-900 border border-surface-800 rounded-lg p-4"
          >
            <p className="text-xs text-surface-400 font-mono uppercase tracking-wider">{card.label}</p>
            <p className={`text-2xl font-bold mt-1 ${card.color}`}>{card.value}</p>
          </div>
        ))}
      </div>

      {/* Map */}
      <div className="bg-surface-900 border border-surface-800 rounded-lg p-3">
        <p className="text-xs text-surface-400 font-mono uppercase tracking-wider mb-2">Device Locations</p>
        <DeviceMap devices={devices} height="320px" />
      </div>

      {/* Online devices */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-medium text-gray-100">
            Online Devices
            <span className="text-surface-500 ml-2 text-xs">({devices.length})</span>
          </h2>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {devices.length === 0 && (
            <p className="text-xs text-surface-500 col-span-full text-center py-8">
              No devices online
            </p>
          )}
          {devices.map(device => (
            <DeviceCard key={device.id} device={device} />
          ))}
        </div>
      </div>
    </div>
  );
}
